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
	private int synergyBatchSize = 100000;

	/** 매치 스냅샷 pending 을 한 라운드에서 처리하는 rid 건수 */
	private int snapshotBatchSize = 100000;

	/** 통합 Job 한 실행에서 스냅샷 drain 라운드 상한 (레거시 단계; 대부분 no-op) */
	private int snapshotMaxRoundsPerJob = 80;

	/** 통합 Job 한 실행에서 시너지 집계 라운드 상한 */
	private int synergyMaxRoundsPerJob = 40;

	/**
	 * 시너지 라운드 사이 대기(ms). 0 이면 생략. 연속 부하·락 완화에 사용.
	 */
	private int synergyPauseMsBetweenRounds = 0;

	/**
	 * true 이면 스냅샷 drain 단계를 생략한다. v2 스키마에서 pending 스냅샷이 항상 비어 있을 때 불필요한 SELECT 를 줄인다.
	 */
	private boolean skipLegacySnapshotStep = false;

	/**
	 * true 이면 통합 Job 에서 {@link RtaBatchAggregationService#rebuildMonsterStatsAgg} 단계 호출 생략 (해당 단계는 no-op).
	 */
	private boolean skipMonsterStatsInUnifiedJob = false;

	/**
	 * true 이면 통합 Job 에서 {@link RtaBatchAggregationService#rebuildTierAggDaily} 단계 생략.
	 * 티어 일별은 부하가 커서 {@link com.smw.monster.batch.RtaTierDailyAggJob} 등 긴 주기로 분리하는 것을 권장(기본 true).
	 */
	private boolean skipTierAggDailyInUnifiedJob = true;

	/**
	 * true 이면 통합 Job 에서 user_monster_owned_agg 전량 삭제·재적재 단계 생략.
	 */
	private boolean skipUserMonsterOwnedAggInUnifiedJob = false;
}
