package com.smw.rta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * 수동 배치 SSE·Redis 로그 브릿지·stale RUNNING 정리 설정.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "smw.batch")
public class BatchLogProperties {

	private Log log = new Log();
	private RunHis runHis = new RunHis();
	private Quartz quartz = new Quartz();

	@Getter
	@Setter
	public static class Log {
		/** true: Pod 간 SSE를 Redis pub/sub으로 중계 (smw-app ↔ smw-batch) */
		private boolean redisBridgeEnabled = true;
		/** SseEmitter 타임아웃(ms). 장시간 RTA 배치용 */
		private long sseTimeoutMs = 7_200_000L;
	}

	@Getter
	@Setter
	public static class RunHis {
		private boolean staleCleanupEnabled = true;
		/** RUNNING 고착 판정 시간(시간) */
		private int staleRunningHours = 6;
	}

	@Getter
	@Setter
	public static class Quartz {
		/**
		 * true: JDBC 클러스터에 트리거만 발행(standby). Job 실행은 smw-batch 등 워커 Pod.
		 * smw-app + quartz-jdbc 프로필에서 사용.
		 */
		private boolean triggerOnly = false;
	}
}
