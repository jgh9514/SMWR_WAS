package com.smw.monster.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Summoners War Exporter 가 쓰는 full_log 를 로컬 폴더에서 읽어 DB 반영할 때 사용.
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
	 * true 이면 full_log 파싱 후 {@code ranker_rtpvp_replay_raw} 만 적재(pending).
	 * {@code ranker_rtpvp_replay_list} 등 정규화는 {@code RtaReplayRawApplyJob} 배치가 수행한다.
	 */
	private boolean rawOnly = false;

	/**
	 * Exporter 가 full_log 를 두는 디렉터리 (절대 경로 권장).
	 * 비어 있으면 스케줄러가 아무 것도 하지 않음.
	 */
	private String watchDirectory = "";

	/** watchDirectory 아래 작업용 하위 폴더 (이동·처리 후 성공 시 삭제) */
	private String tempSubdir = "temp";

	/** 감시 파일명 접두사 (예: full_log.txt, full_log_1.txt) */
	private String fileNamePrefix = "full_log";

	/** 이전 폴링 완료 후 다음 실행까지 대기(ms) */
	private long pollIntervalMs = 30_000L;

	/** 한 파일 최대 크기(MB). 초과 시 건너뜀 */
	private int maxFileSizeMb = 128;

	/**
	 * 파일 크기가 이 시간(ms) 동안 변하지 않으면 "쓰기 완료"로 간주 후 이동.
	 * Exporter 가 계속 쓰는 동안에는 이동하지 않음.
	 */
	private long stableMillis = 2_000L;
}
