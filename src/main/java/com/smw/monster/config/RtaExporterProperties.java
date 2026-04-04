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

	/** 이전 폴링 완료 후 다음 실행까지 대기(ms). 기본 5분. */
	private long pollIntervalMs = 300_000L;

	/** 한 파일 최대 크기(MB). 초과 시 건너뜀 */
	private int maxFileSizeMb = 128;

	/**
	 * 안정화 검사 시 크기 샘플 간격(ms). 이전 크기와 sleep 후 크기가 같으면 한 번의 "안정" 후보.
	 */
	private long stableMillis = 2_000L;

	/**
	 * 안정화 검사 최대 대기(ms). 이 안에 크기가 안정되지 않으면 이번 폴링은 건너뛰고 다음 폴링에서 재시도.
	 * Exporter 쓰기 구간(~수십 초) + 여유를 두려면 60_000 등.
	 */
	private long stableMaxWaitMs = 60_000L;
}
