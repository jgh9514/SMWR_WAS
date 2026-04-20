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
	 * true 이면 통합 Job 에서 {@link RtaBatchAggregationService#rebuildMonsterStatsAgg} 생략 (레거시 no-op, API 는 {@code rta_agg_synergy_combo}).
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
	 * true 이면 통합 Job 의 시너지 drain 동안 {@code idx_rta_agg_counter_matchup_season_subject} 조회 인덱스를 잠시 내린다.
	 * 카운터 matchup 대량 UPSERT 중 보조 인덱스 유지 비용을 줄이기 위한 옵션이다.
	 */
	private boolean dropCounterMatchupQueryIndexDuringUnifiedJob = true;
}
