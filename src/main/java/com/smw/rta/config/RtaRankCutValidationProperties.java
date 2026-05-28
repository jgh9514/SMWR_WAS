package com.smw.rta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * {@link com.smw.monster.batch.RtaRankCutSnapshotAggJob} 랭크컷 스냅 적재 후
 * 급격한 증감·역전 등 이상 징후 검증 튜닝.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "smw.rta.batch.rank-cut-validation")
public class RtaRankCutValidationProperties {

	/** false 이면 검증 생략 */
	private boolean enabled = true;

	/**
	 * 직전 스냅 대비 컷 점수 절대 변화량이 이 값을 초과하면 이상 후보.
	 * (동시에 {@link #maxPctScoreDelta} 도 만족해야 알림 — 둘 중 하나만 크면 정상 변동일 수 있음)
	 */
	private long maxAbsScoreDelta = 250L;

	/** 직전 스냅 대비 상대 변화율(0.2 = 20%) 초과 시 이상 후보 — {@code prevScore >= minBaselineScore} 일 때만 */
	private double maxPctScoreDelta = 0.20d;

	/** 상대 변화율 검사 최소 기준 점수 — 시즌 초반 저점 구간은 pct 검사 생략 */
	private long minBaselineScore = 800L;

	/** 허용 컷 점수 상한(비정상 값) */
	private long maxReasonableScore = 15_000L;

	/** 같은 시간대 티어 간 sort_order 오름차순 cutoff 역전 허용 오차(점) */
	private long monotonicityTolerance = 0L;

	/** 시즌×티어 총경기 수가 이전 대비 감소하고, 감소율·절대량이 임계 초과 시 이상 */
	private boolean matchTotalDropCheckEnabled = true;

	private long matchTotalDropAbsThreshold = 500L;

	private double matchTotalDropPctThreshold = 0.05d;

	/** 이상 건수가 임계 이상이면 Slack 알림(토큰·채널 설정 시) */
	private int slackAlertMinAnomalies = 1;

	/** 배치 로그·Slack 본문에 포함할 샘플 상한 */
	private int maxSamplesInLog = 15;
}
