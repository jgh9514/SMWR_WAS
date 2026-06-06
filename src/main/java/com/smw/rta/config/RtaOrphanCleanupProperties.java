package com.smw.rta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * RTA 고아 행 정리 배치({@link com.smw.monster.batch.ArenaRtaOrphanCleanupBatchJob}) 점검 범위.
 * <p>
 * 일상은 최근 {@link #lookbackHours} 구간의 replay_id 만 증분 점검하고,
 * {@link #fullScanDayOfWeek} 에만 전수 스캔(레거시·FK 이상 대비).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "smw.rta.orphan-cleanup")
public class RtaOrphanCleanupProperties {

	/** rta_match.played_at 기준 증분 점검 lookback(시간). */
	private int lookbackHours = 48;

	/** 최근 경기가 없을 때 MIN(replay_id) 대신 MAX(replay_id)-padding 을 바닥으로 쓴다. */
	private long replayIdPadding = 100_000L;

	/**
	 * 전수 스캔 요일(0=일요일 … 6=토요일). {@code -1} 이면 전수 스캔 비활성(증분만).
	 */
	private int fullScanDayOfWeek = 0;
}
