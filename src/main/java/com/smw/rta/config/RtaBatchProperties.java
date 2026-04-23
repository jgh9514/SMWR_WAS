package com.smw.rta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * RTA 통합 배치({@code RtaUnifiedPipelineAggJob}) 튜닝. 운영에서 부하·지연에 맞게 조정한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "smw.rta.batch")
public class RtaBatchProperties {

	/** {@code synergy_applied_at IS NULL}(미집계) rid 를 한 라운드에서 가져오는 건수. 집계 후 결과는 {@code synergy_apply_result}. */
	private int synergyBatchSize = 1000;

	/**
	 * 시너지 라운드 사이 대기(ms). 0 이면 생략. 연속 부하·락 완화에 사용.
	 */
	private int synergyPauseMsBetweenRounds = 0;

	/**
	 * true 이면 통합 Job 에서 {@link RtaBatchAggregationService#rebuildMonsterStatsAgg} 생략 (no-op).
	 * 기본 true — application.yml 과 동일.
	 */
	private boolean skipMonsterStatsInUnifiedJob = true;

	/**
	 * true 이면 통합 Job 에서 {@link RtaBatchAggregationService#rebuildTierAggDaily} 단계 생략.
	 * 티어 일별은 부하가 커서 {@link com.smw.monster.batch.RtaTierDailyAggJob} 등 긴 주기로 분리하는 것을 권장(기본 true).
	 */
	private boolean skipTierAggDailyInUnifiedJob = true;

	/**
	 * true 이면 통합 Job 의 <b>시너지 단계 직후</b>에 두는 {@code user_monster_owned_agg} 재적재를 생략한다.
	 */
	private boolean skipUserMonsterOwnedAggInUnifiedJob = false;

	/**
	 * 시너지 단독 Job({@link com.smw.monster.batch.RtaSynergyOnlyAggJob})에서
	 * 각 라운드가 끝날 때마다 RTA 조회 캐시를 무효화할지 여부.
	 * <p>
	 * {@code true}(기본) — 라운드마다 캐시 무효화. pending 이 많을 때 중간 결과를 즉시 조회 가능.
	 * {@code false} — 전체 drain 완료 후 한 번만 무효화. 캐시 무효화 오버헤드를 줄이고 싶을 때.
	 */
	private boolean synergyOnlyEvictCachesEachRound = true;

	/** 미사용 — 카운터 테이블이 solo/duo/trio 로 분리되면서 별도 인덱스 DROP/REBUILD 불필요. 하위 호환 유지용. */
	@Deprecated
	private boolean dropCounterMatchupQueryIndexDuringUnifiedJob = false;

	/** 배치 실패 시 Slack 알림용 Bot Token (xoxb-...). 비어 있으면 알림 생략. */
	private String slackToken = "";

	/** 배치 실패 알림을 보낼 Slack 채널 ID. */
	private String slackChannelId = "";
}
