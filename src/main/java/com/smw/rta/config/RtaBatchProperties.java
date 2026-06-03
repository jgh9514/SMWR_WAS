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

	/** {@code synergy_applied_at IS NULL}(미집계) rid 를 한 라운드에서 가져오는 건수. {@code smw.rta.batch.synergy-batch-size} 와 맞출 것. */
	private int synergyBatchSize = 10_000;

	/**
	 * {@link com.smw.monster.batch.RtaSynergyOnlyAggJob} 가 한 실행에서 처리하는 시너지 drain 라운드 상한.
	 * {@code 1}(기본)이면 라운드 1회만 수행하고 잔여 pending 은 다음 스케줄에서 이어 처리한다.
	 * {@code 0} 이하면 상한 없음(기존처럼 pending 소진까지 반복).
	 */
	private int synergyMaxRoundsPerJob = 1;

	/**
	 * 시너지 라운드 사이 대기(ms). 0 이면 생략. GC·DB 부하 완화.
	 */
	private int synergyPauseMsBetweenRounds = 50;

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
	 * 시너지 단독 Job({@link com.smw.monster.batch.RtaSynergyOnlyAggJob})에서
	 * 각 라운드가 끝날 때마다 RTA 조회 캐시를 무효화할지 여부.
	 * <p>
	 * {@code true} — 라운드마다 캐시 무효화. pending 이 많을 때 중간 결과를 즉시 조회 가능(부하↑).
	 * {@code false}(기본) — 전체 drain 완료 후 한 번만 무효화. 메모리·CPU 절약.
	 */
	private boolean synergyOnlyEvictCachesEachRound = false;

	/** 미사용 — 카운터 테이블이 solo/duo/trio 로 분리되면서 별도 인덱스 DROP/REBUILD 불필요. 하위 호환 유지용. */
	@Deprecated
	private boolean dropCounterMatchupQueryIndexDuringUnifiedJob = false;

	/**
	 * 시너지 staging(COPY+merge)과 카운터 staging(COPY+merge)을 한 rid 라운드 끝에서
	 * <strong>병렬(스레드 2)</strong>로 flush 할지, <strong>순차</strong>로 할지.
	 * <p>
	 * false — 처리량은 다소 느려질 수 있으나 동시에 다른 잡·Pod·세션과 {@code rta_agg_*} 락이 겹쳐
	 * {@code lock timeout} 이 나는 환경에서 유리.
	 */
	private boolean parallelSynergyCounterStagingFlush = false;

	/**
	 * {@link com.smw.monster.batch.RtaMonsterDailySnapJob}에서 pick-slot snap drain 시
	 * 한 번에 가져오는 rid 건수. {@code smw.rta.batch.pick-slot-drain-batch-size}
	 */
	private int pickSlotDrainBatchSize = 2000;

	/**
	 * true 이면 미처리 건수에 따라 Job 당 라운드·배치 상한을 자동 상향(catch-up).
	 * 평소에는 yml 기본값 유지, backlog 가 쌓이면 {@link com.smw.rta.service.RtaBatchBacklogScaler} 가 상한 계산.
	 */
	private boolean backlogScalingEnabled = true;

	/** backlog catch-up 시 시너지 Job 당 라운드 절대 상한. {@code synergy-max-rounds-per-job} 기본(1)보다 커야 효과 있음. */
	private int synergyMaxRoundsCap = 10;

	/**
	 * {@link com.smw.monster.batch.RtaSynergyOnlyAggJob} 1회 최대 벽시계 실행 시간(ms).
	 * {@code 0} 이면 제한 없음. 초과 시 잔여 pending 은 다음 스케줄에서 이어 처리(장시간 점유 방지).
	 */
	private long synergyMaxWallClockMsPerJob = 0L;

	/** pick-slot drain catch-up 시 Job 당 라운드 절대 상한. 0 이하면 drain-until-empty. */
	private int pickSlotMaxRoundsCap = 10;

	/**
	 * 이 건수 이상 pending 이면 catch-up 모드(라운드 간 pause 생략 등).
	 * {@code synergy-batch-size} 와 비교해 더 큰 값을 쓴다.
	 */
	private long backlogHighWatermark = 10_000L;

	/** 배치 실패 시 Slack 알림용 Bot Token (xoxb-...). 비어 있으면 알림 생략. */
	private String slackToken = "";

	/** 배치 실패 알림을 보낼 Slack 채널 ID. */
	private String slackChannelId = "";

	/**
	 * {@link com.smw.monster.batch.RtaRankCutSnapshotAggJob} 1회당 채울 누락 시간대(시간) 상한.
	 * 기존 720은 한 실행이 수 시간 걸릴 수 있어 다른 RTA 배치·API와 participant 락/풀 경합을 유발한다.
	 */
	private int rankCutMissingHoursPerRun = 48;

	/**
	 * true 이면 participant 에 {@code played_at} 이 집계 시각 이후인 행이 없을 때
	 * {@code rta_agg_season_rating_match_total} 전 시즌 GROUP BY 재집계를 생략한다.
	 */
	private boolean skipSeasonRatingMatchTotalIfFresh = true;
}
