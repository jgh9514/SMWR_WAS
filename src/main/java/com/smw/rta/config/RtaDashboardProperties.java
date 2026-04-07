package com.smw.rta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * RTA 대시보드 API 튜닝 (티어 분포·랭크컷 앵커 등).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "smw.rta.dashboard")
public class RtaDashboardProperties {

	/**
	 * 랭크 컷 앵커(3h~7d×티어) 조회 소스.
	 * <ul>
	 * <li>{@code snap} — {@code rta_rank_cutoff_anchor_snap} 만 (비어 있으면 빈 목록)</li>
	 * <li>{@code live} — {@code rta_match} 라이브 집계만 (시즌 구간 반영)</li>
	 * <li>{@code snap_then_live} — 스냅에 행이 있으면 스냅, 없으면 라이브 (기본)</li>
	 * </ul>
	 * 스냅은 배치 적재 시점·적재 범위 기준이며, 라이브와 시즌 경계가 다를 수 있음.
	 */
	private String rankCutAnchorSource = "snap_then_live";
}
