package com.smw.monster.batch;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;

import com.smw.monster.service.summonerswarService;
import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.config.RtaBatchProperties;
import com.smw.rta.config.RtaRawApplyProperties;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaBatchAggregationService;
import com.smw.rta.service.RtaBatchBacklogScaler;

/**
 * RTA 관련 집계를 한 번의 스케줄로 순서대로 수행한다.
 * <ol>
 * <li>리플레이 raw 정규화 — 매 실행마다 최대 {@code max-batches-per-job} 회, 회당 최대 {@code max-rows-per-run}건 SELECT·처리(빈 조회 전까지).</li>
 * <li>부가 집계 — 레거시 몬스터 통계·티어 일별(각각 설정에서 켠 경우만).</li>
 * </ol>
 * 소환사 무거운 스냅(전투·몬·픽턴·owned_box·<strong>H2H 라이벌</strong>)은 통합 파이프라인에 포함하지 않는다 —
 * {@link RtaSummonerRankingAggJob} 에서만 {@link RtaBatchAggregationService#rebuildSummonerMonsterSnapAgg} 를 수행한다.
 * 시너지·랭킹 상위 스냅 등은 아래 목록처럼 <b>별도 Quartz Job</b>이다.
 * <p>
 * <b>통합 Job 밖에서 도는 것들</b> (짧은 주기 통합과 주기·부하가 다름):
 * <ul>
 * <li>{@code rta_agg_tier_daily} → {@link RtaTierDailyAggJob}</li>
 * <li>{@code rta_agg_summoner_ranking_snap}·검색 스냅 → {@link RtaSummonerRankingTopSnapJob}</li>
 * <li>소환사 전투·몬·픽턴·H2H·보유박스 스냅 → {@link RtaSummonerRankingAggJob}</li>
 * <li>랭크컷 앵커·등급 컷 스냅샷 → {@link RtaRankCutSnapshotAggJob}</li>
 * </ul>
 * 대시보드 티어 일별 분포는 {@code getRtaTierDistributionDaily} + 캐시이며, 풀스캔 재적재는 {@link RtaTierDailyAggJob} 권장.
 * <p>
 * 스케줄: DB {@code sys_batch_config.cron_expr} (기본 5분, bat_id 10001).
 * <p>
 * 동일 Job 이 겹쳐 실행되면 DB 부하가 배가 될 수 있어 {@link DisallowConcurrentExecution} 으로 한 번에 하나만 실행한다.
 */
@DisallowConcurrentExecution
public class RtaUnifiedPipelineAggJob extends BaseBatchJob {

	/**
	 * 로그 상 고정 단계: ① raw ② 부가(레거시 몬스터·티어일별, 설정 시만).
	 */
	private static final int PIPELINE_STEPS = 2;

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		summonerswarService summonerswarService = applicationContext.getBean(summonerswarService.class);
		RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
		RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);
		RtaBatchAggregationService aggregationService = applicationContext.getBean(RtaBatchAggregationService.class);
		RtaBatchProperties rtaBatchProperties = applicationContext.getBean(RtaBatchProperties.class);
		RtaRawApplyProperties rtaRawApplyProperties = applicationContext.getBean(RtaRawApplyProperties.class);
		RtaBatchBacklogScaler backlogScaler = applicationContext.getBean(RtaBatchBacklogScaler.class);

		int step = 0;

		addLog("[시작] RTA 통합 파이프라인 — 핵심 %d단계 (raw → 부가)", PIPELINE_STEPS);

		RtaBatchBacklogScaler.RtaBatchBacklogCounts backlog = backlogScaler.snapshot();
		int rowsPerBatch = Math.max(1, rtaRawApplyProperties.getMaxRowsPerRun());
		int configuredRawBatches = Math.max(1, rtaRawApplyProperties.getMaxBatchesPerJob());
		int rawMaxBatches = backlogScaler.resolveRawMaxBatches(
				backlog.rawPending(), rowsPerBatch, configuredRawBatches);

		step++;
		addLog("[%d/%d] RTA raw 정규화 — LIMIT %d행×최대 %d회(설정 %d, backlog raw %,d → 이번 Job %d회)",
				step, PIPELINE_STEPS,
				rowsPerBatch,
				rawMaxBatches,
				configuredRawBatches,
				backlog.rawPending(),
				rawMaxBatches);
		RtaBatchAggregationService.RawApplyDrainResult raw = aggregationService.drainReplayRawPending(
				summonerswarService, rawMaxBatches);
		addLog("[%d/%d] · 완료 — 누적 적용 %d건, %s",
				step, PIPELINE_STEPS,
				raw.totalApplied(),
				raw.stopReason());

		// 시너지 집계는 RtaSynergyOnlyAggJob 에서 별도로 수행 — 통합 Job 에서는 생략.
		// 소환사 무거운 스냅·H2H 는 RtaSummonerRankingAggJob 전용(rebuildSummonerMonsterSnapAgg).
		step++;

		boolean runMonster = !rtaBatchProperties.isSkipMonsterStatsInUnifiedJob();
		boolean runTier = !rtaBatchProperties.isSkipTierAggDailyInUnifiedJob();
		addLog("[%d/%d] RTA 부가 집계 — 실행: 레거시몬스터=%s, 티어일별=%s",
				step, PIPELINE_STEPS,
				runMonster ? "Y" : "N",
				runTier ? "Y" : "N");

		if (runMonster) {
			addLog("[%d/%d] · 몬스터 통계 집계(no-op — API는 rta_agg_synergy_solo/duo/trio 직접 조회)", step, PIPELINE_STEPS);
			RtaBatchAggregationService.MonsterStatsRebuildResult mon = aggregationService.rebuildMonsterStatsAgg(rtaMapper);
			addLog("[%d/%d] · 몬스터 통계 완료 — meta=%d, pick=%d", step, PIPELINE_STEPS, mon.metaRows(), mon.pickRows());
		}
		if (runTier) {
			addLog("[%d/%d] · 티어 일별(rta_agg_tier_daily) participant 풀스캔", step, PIPELINE_STEPS);
			RtaBatchAggregationService.TierDailyAggRebuildResult tier = aggregationService.rebuildTierAggDaily(rtaMapper);
			addLog("[%d/%d] · 티어 일별 완료 — %d행", step, PIPELINE_STEPS, tier.totalRows());
		}
		if (!runMonster && !runTier) {
			addLog("[%d/%d] · 부가 실행 없음 — 티어 일별은 RtaTierDailyAggJob, 랭킹·검색 스냅은 RtaSummonerRankingTopSnapJob, 무거운 소환사 스냅+H2H는 RtaSummonerRankingAggJob, 랭크컷 스냅샷은 RtaRankCutSnapshotAggJob 등 별도 스케줄에서 처리하는 구성이 일반적입니다.",
					step, PIPELINE_STEPS);
		}

		addLog("[종료] (%d/%d) RTA 조회 캐시 무효화", PIPELINE_STEPS, PIPELINE_STEPS);
		rtaCacheEvictor.evictAllRtaCaches();
	}

	@Override
	protected String getBatchName() {
		return "RTA 전체 집계 파이프라인";
	}
}
