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
 * Exporter 가 만든 full_log 를 watch 디렉터리에서 {@code temp} 로 옮긴 뒤 파싱·DB 반영하고,
 * 성공 시 temp 파일을 삭제한다.
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
		synchronized (ingestLock) {
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
			long maxBytes = (long) props.getMaxFileSizeMb() * 1024L * 1024L;

			Path candidate;
			try {
				candidate = findNextCandidate(watchDir, prefix);
			} catch (IOException e) {
				log.error("[rta-exporter] 감시 디렉터리 목록 실패", e);
				return;
			}
			if (candidate == null) {
				return;
			}

			// 쓰기 중인 파일은 크기가 변함 → stableMillis 간격으로 최대 stableMaxWaitMs 동안 반복 검사
			boolean stable;
			try {
				stable = waitUntilFileStable(candidate, props.getStableMillis(), props.getStableMaxWaitMs());
			} catch (IOException e) {
				log.warn("[rta-exporter] 파일 안정화 검사 실패: {}", candidate, e);
				return;
			}
			if (!stable) {
				log.info("[rta-exporter] {}ms 안에 크기 안정 없음(아직 쓰기 중 추정), 다음 폴링에서 재시도: {}",
						props.getStableMaxWaitMs(), candidate);
				return;
			}

			long size;
			try {
				size = Files.size(candidate);
			} catch (IOException e) {
				log.warn("[rta-exporter] 파일 크기 확인 실패: {}", candidate, e);
				return;
			}
			if (size > maxBytes) {
				log.warn("[rta-exporter] 파일이 너무 큼 ({} MB 한도 초과), 건너뜀: {}", props.getMaxFileSizeMb(), candidate);
				return;
			}

			String baseName = candidate.getFileName().toString();
			Path workFile = tempDir.resolve(System.currentTimeMillis() + "_" + baseName);
			try {
				Files.move(candidate, workFile, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				log.warn("[rta-exporter] temp 로 이동 실패 (다른 프로세스가 잠금 가능): {} → {}", candidate, workFile, e);
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
				Map<String, Integer> counts = props.isRawOnly()
						? summonerswarService.applyArenaRtaUploadRawOnlyFromParsedItems(items)
						: summonerswarService.applyArenaRtaUploadFromParsedItems(items);
				log.info("[rta-exporter] 반영 완료 {} (rawOnly={}) → success={} fail={}", workFile, props.isRawOnly(),
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
	 * {@code intervalMs} 마다 크기를 비교해, 연속 두 샘플이 같으면 쓰기 완료로 본다.
	 * 총 대기 상한은 {@code maxWaitMs} (한 번의 폴링 안에서만 사용).
	 */
	private static boolean waitUntilFileStable(Path file, long intervalMs, long maxWaitMs) throws IOException {
		long interval = Math.max(200L, intervalMs);
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
