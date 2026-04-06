package com.smw.monster.schedule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.smw.monster.config.RtaExporterProperties;
import com.smw.monster.service.summonerswarService;
import com.smw.monster.util.RankerRtpvpReplayLogParser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Exporter 가 만든 full_log 를 watch 디렉터리에서 {@code temp} 로 옮긴 뒤 파싱하여
 * <strong>원본 JSON 스테이징 테이블에만 INSERT</strong> 한다. {@code rta_match} 등 정규화는 WAS 배치가 수행한다.
 * 성공 시 temp 파일을 삭제한다. 처리 실패 시 {@code *.failed} 로 남기며, 이후 스캔에서는
 * watch 에 새 로그가 계속 있어도 temp 내 가장 오래된 {@code *.failed} 를 먼저 {@code retry_*} 로 재시도한다.
 * <p>
 * 스케줄: 이전 구간 종료 후 {@link RtaExporterProperties#getPollIntervalMs()} 만큼 대기한 뒤,
 * {@link RtaExporterProperties#getPollBurstDurationMs()} 동안 {@link RtaExporterProperties#getPollScanIntervalMs()} 간격으로 폴더를 반복 스캔한다.
 * <p>
 * {@code smw.rta.exporter.enabled=true} 인 인스턴스에서만 등록된다 (로그 PC 한 대만 켜기).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "smw.rta.exporter", name = "enabled", havingValue = "true")
public class RtaExporterFullLogIngestScheduler {

	private final RtaExporterProperties props;
	private final summonerswarService summonerswarService;

	private final Object ingestLock = new Object();

	@Scheduled(fixedDelayString = "${smw.rta.exporter.poll-interval-ms:300000}")
	public void pollAndIngest() {
		String dir = props.getWatchDirectory();
		if (dir == null || dir.isBlank()) {
			log.warn("[rta-exporter] smw.rta.exporter.watch-directory 가 비어 있어 감시를 건너뜁니다.");
			return;
		}
		Path watchDir = Path.of(dir.trim()).normalize().toAbsolutePath();
		if (!Files.isDirectory(watchDir)) {
			log.warn("[rta-exporter] 감시 디렉터리가 없습니다: {}", watchDir);
			return;
		}
		Path tempDir = watchDir.resolve(props.getTempSubdir()).normalize();
		try {
			Files.createDirectories(tempDir);
		} catch (IOException e) {
			log.error("[rta-exporter] temp 폴더 생성 실패: {}", tempDir, e);
			return;
		}

		String prefix = props.getFileNamePrefix() != null ? props.getFileNamePrefix() : "full_log";
		long maxBytes = maxBytesFromMb(props.getMaxFileSizeMb());

		long burstMs = Math.max(1_000L, props.getPollBurstDurationMs());
		long scanIntervalMs = Math.max(1_000L, props.getPollScanIntervalMs());
		long burstEnd = System.currentTimeMillis() + burstMs;

		log.debug("[rta-exporter] 감시 버스트 시작 {}ms 동안 {}ms 간격 스캔", burstMs, scanIntervalMs);

		while (System.currentTimeMillis() < burstEnd) {
			synchronized (ingestLock) {
				tryIngestOneCandidate(watchDir, tempDir, prefix, maxBytes);
			}
			long remaining = burstEnd - System.currentTimeMillis();
			if (remaining <= 0) {
				break;
			}
			long sleepMs = Math.min(scanIntervalMs, remaining);
			try {
				Thread.sleep(sleepMs);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	/** 한 번: temp 의 *.failed 재시도 1개 → 없으면 watch 에서 후보 1개 → 파싱·DB. */
	private void tryIngestOneCandidate(Path watchDir, Path tempDir, String prefix, long maxBytes) {
		Path workFile;
		try {
			workFile = prepareOneWorkFile(watchDir, tempDir, prefix, maxBytes);
		} catch (IOException e) {
			log.error("[rta-exporter] 작업 파일 준비 실패", e);
			return;
		}
		if (workFile == null) {
			return;
		}

		try {
			String text = Files.readString(workFile, StandardCharsets.UTF_8);
			List<Map<String, Object>> parsed = RankerRtpvpReplayLogParser.extractReplayItemsFromLogText(text);
			if (parsed.isEmpty()) {
				log.info("[rta-exporter] 추출된 전투 없음, temp 파일 삭제: {}", workFile);
				Files.deleteIfExists(workFile);
				return;
			}
			@SuppressWarnings("unchecked")
			List<Map<String, ?>> items = (List<Map<String, ?>>) (List<?>) parsed;
			Map<String, Integer> counts = summonerswarService.applyArenaRtaUploadRawOnlyFromParsedItems(items);
			log.info("[rta-exporter] raw 적재 완료 {} → success={} fail={}", workFile,
					counts.get("success"), counts.get("fail"));
			Files.deleteIfExists(workFile);
		} catch (Exception e) {
			log.error("[rta-exporter] 처리 실패, temp 에 유지: {}", workFile, e);
			try {
				Path failed = workFile.resolveSibling(workFile.getFileName().toString() + ".failed");
				Files.move(workFile, failed, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e2) {
				log.debug("[rta-exporter] 실패 파일 rename 생략", e2);
			}
		}
	}

	/**
	 * 우선순위: (1) temp 내 가장 오래된 {@code *.failed} → 재시도용 이름으로 이동
	 * (2) 없으면 watch 의 full_log 후보 → temp 로 이동. watch 에 로그가 끊이 없을 때도 실패 큐가 처리되도록 한다.
	 * @return 작업 파일 경로, 없으면 null
	 */
	private Path prepareOneWorkFile(Path watchDir, Path tempDir, String prefix, long maxBytes) throws IOException {
		Path failed = findNextFailedRetry(tempDir);
		if (failed != null) {
			long size = Files.size(failed);
			if (size > maxBytes) {
				log.warn("[rta-exporter] 실패 재시도 파일이 너무 큼 ({} MB 한도), 건너뜀: {}", props.getMaxFileSizeMb(), failed);
				return null;
			}
			String stripped = stripFailedSuffix(failed.getFileName().toString());
			Path workFile = tempDir.resolve("retry_" + System.currentTimeMillis() + "_" + stripped);
			try {
				Files.move(failed, workFile, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				log.warn("[rta-exporter] failed → 재시도 작업명 이동 실패: {} → {}", failed, workFile, e);
				return null;
			}
			log.info("[rta-exporter] 이전 실패 파일 재시도: {} → {}", failed, workFile);
			return workFile;
		}

		Path candidate = findNextCandidate(watchDir, prefix);
		if (candidate == null) {
			return null;
		}
		boolean stable = waitUntilFileStable(candidate, props.getStableMillis(), props.getStableMaxWaitMs());
		if (!stable) {
			log.debug("[rta-exporter] 아직 크기 변동(쓰기 중 추정), 다음 스캔에서 재시도: {}", candidate);
			return null;
		}
		long size = Files.size(candidate);
		if (size > maxBytes) {
			log.warn("[rta-exporter] 파일이 너무 큼 ({} MB 한도 초과), 건너뜀: {}", props.getMaxFileSizeMb(), candidate);
			return null;
		}
		String baseName = candidate.getFileName().toString();
		Path workFile = tempDir.resolve(System.currentTimeMillis() + "_" + baseName);
		try {
			Files.move(candidate, workFile, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			log.warn("[rta-exporter] temp 로 이동 실패 (다른 프로세스가 잠금 가능): {} → {}", candidate, workFile, e);
			return null;
		}
		return workFile;
	}

	/**
	 * @param maxFileSizeMb {@code 0} 이하이면 무제한({@link Long#MAX_VALUE}).
	 */
	private static long maxBytesFromMb(int maxFileSizeMb) {
		if (maxFileSizeMb <= 0) {
			return Long.MAX_VALUE;
		}
		return (long) maxFileSizeMb * 1024L * 1024L;
	}

	private static String stripFailedSuffix(String fileName) {
		if (fileName != null && fileName.endsWith(".failed")) {
			return fileName.substring(0, fileName.length() - ".failed".length());
		}
		return fileName != null ? fileName : "";
	}

	/** temp 폴더에서 {@code *.failed} 중 수정 시각이 가장 오래된 1개 (FIFO 재시도). */
	private static Path findNextFailedRetry(Path tempDir) throws IOException {
		if (!Files.isDirectory(tempDir)) {
			return null;
		}
		try (Stream<Path> stream = Files.list(tempDir)) {
			return stream
					.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().endsWith(".failed"))
					.min(Comparator.comparingLong(path -> {
						try {
							return Files.getLastModifiedTime(path).toMillis();
						} catch (IOException e) {
							return Long.MAX_VALUE;
						}
					}))
					.orElse(null);
		}
	}

	private static Path findNextCandidate(Path watchDir, String prefix) throws IOException {
		String p = prefix.toLowerCase();
		try (Stream<Path> stream = Files.list(watchDir)) {
			return stream
					.filter(Files::isRegularFile)
					.filter(path -> {
						String name = path.getFileName().toString();
						return name.toLowerCase().startsWith(p);
					})
					.min(Comparator.comparingLong(path -> {
						try {
							return Files.getLastModifiedTime(path).toMillis();
						} catch (IOException e) {
							return Long.MAX_VALUE;
						}
					}))
					.orElse(null);
		}
	}

	/**
	 * 크기를 읽고 {@code intervalMs} 만큼 대기한 뒤 다시 읽어, 두 값이 같으면 쓰기 완료로 본다.
	 * 총 대기 상한은 {@code maxWaitMs}.
	 */
	private static boolean waitUntilFileStable(Path file, long intervalMs, long maxWaitMs) throws IOException {
		long interval = Math.max(1_000L, intervalMs);
		long deadline = System.currentTimeMillis() + Math.max(interval, maxWaitMs);
		while (System.currentTimeMillis() < deadline) {
			long s0 = Files.size(file);
			try {
				Thread.sleep(interval);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
			long s1 = Files.size(file);
			if (s0 == s1) {
				return true;
			}
		}
		return false;
	}
}
