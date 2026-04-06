package com.smw.monster.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Summoners War Exporter 가 쓰는 full_log 를 로컬 폴더에서 읽어 DB 반영할 때 사용.
 * <p>
 * 감시 스케줄러는 항상 {@code ranker_rtpvp_replay_raw} 에만 INSERT 하며, {@code rta_match} 정규화는 WAS 배치가 처리한다.
 * <p>
 * {@code enabled=false} 가 기본 — 로그 수집 PC에서만 {@code application.yml} 또는 프로필로 켠다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "smw.rta.exporter")
public class RtaExporterProperties {

	/** true 일 때만 감시 스레드 동작 */
	private boolean enabled = false;

	/**
	 * Exporter 가 full_log 를 두는 디렉터리 (절대 경로 권장).
	 * 비어 있으면 스케줄러가 아무 것도 하지 않음.
	 */
	private String watchDirectory = "";

	/** watchDirectory 아래 작업용 하위 폴더 (이동·처리 후 성공 시 삭제) */
	private String tempSubdir = "temp";

	/** 감시 파일명 접두사 (예: full_log.txt, full_log_1.txt) */
	private String fileNamePrefix = "full_log";

	/**
	 * 이전 감시 구간(버스트) 종료 후 다음 구간까지 대기(ms). 기본 5분.
	 * 한 구간 안에서는 {@link #pollBurstDurationMs}·{@link #pollScanIntervalMs} 로 폴더를 반복 스캔한다.
	 */
	private long pollIntervalMs = 300_000L;

	/**
	 * 5분마다 시작하는 감시 구간 길이(ms). 기본 1분 — 이 동안 폴더를 {@link #pollScanIntervalMs} 마다 훑는다.
	 */
	private long pollBurstDurationMs = 60_000L;

	/**
	 * 한 버스트 구간 안에서 폴더 스캔(후보 탐색·처리 시도) 사이 대기(ms). 기본 20초.
	 */
	private long pollScanIntervalMs = 20_000L;

	/** 한 파일 최대 크기(MB). 초과 시 건너뜀 */
	private int maxFileSizeMb = 128;

	/**
	 * 후보 파일 안정화: 연속 두 크기 샘플 사이 대기(ms). 한 번의 처리 시도 안에서만 사용.
	 */
	private long stableMillis = 2_000L;

	/**
	 * 후보 파일 안정화 최대 대기(ms). 한 번의 처리 시도 안에서만 사용.
	 */
	private long stableMaxWaitMs = 60_000L;
}
