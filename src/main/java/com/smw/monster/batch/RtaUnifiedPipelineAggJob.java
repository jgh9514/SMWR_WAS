package com.smw.monster.batch;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;

import com.smw.account.mapper.AccountSummaryMapper;
import com.smw.monster.service.summonerswarService;
import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.config.RtaBatchProperties;
import com.smw.rta.config.RtaRawApplyProperties;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaBatchAggregationService;

/**
 * RTA 관련 집계를 한 번의 스케줄로 순서대로 수행한다.
 * <ol>
 * <li>리플레이 raw 정규화 ({@code max-rows-per-run} 행을 1회 SELECT·처리. 잔여는 다음 스케줄에서)</li>
 * <li>시너지 집계 pending — pending 이 모두 소진될 때까지 전량 처리</li>
 * <li>부가 집계 — 설정에서 켠 항목만 실행. 티어 일별·랭크컷·레거시 몬스터 통계는 대부분 <b>별도 Quartz Job</b>이 담당.</li>
 * </ol>
 * <p>
 * <b>통합 Job 밖에서 도는 것들</b> (짧은 주기 통합과 주기·부하가 다름):
 * <ul>
 * <li>{@code rta_agg_tier_daily} → {@link RtaTierDailyAggJob}</li>
 * <li>{@code rta_agg_summoner_ranking_snap} → {@link RtaSummonerRankingAggJob}</li>
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
	 * 로그 상 고정 단계: ① raw ② 시너지(+선택 보유몬) ③ 부가(레거시 몬스터·티어일별, 설정 시만).
	 */
	private static final int PIPELINE_STEPS = 3;

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		summonerswarService summonerswarService = applicationContext.getBean(summonerswarService.class);
		RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
		RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);
		RtaBatchAggregationService aggregationService = applicationContext.getBean(RtaBatchAggregationService.class);
		RtaBatchProperties rtaBatchProperties = applicationContext.getBean(RtaBatchProperties.class);
		RtaRawApplyProperties rtaRawApplyProperties = applicationContext.getBean(RtaRawApplyProperties.class);

		int step = 0;

		addLog("[시작] RTA 통합 파이프라인 — 핵심 %d단계 (raw → 시너지·보유몬 → 부가)", PIPELINE_STEPS);

		step++;
		addLog("[%d/%d] RTA raw 정규화 — LIMIT %d행 1회 처리 (잔여 있어도 다음 스케줄에서)",
				step, PIPELINE_STEPS,
				rtaRawApplyProperties.getMaxRowsPerRun());
		RtaBatchAggregationService.RawApplyDrainResult raw = aggregationService.drainReplayRawPending(summonerswarService);
		addLog("[%d/%d] · 완료 — 누적 적용 %d건, %s",
				step, PIPELINE_STEPS,
				raw.totalApplied(),
				raw.stopReason());

		// 시너지 집계는 RtaSynergyOnlyAggJob 에서 별도로 수행 — 통합 Job 에서는 생략
		// int synergyBatch = Math.max(1, rtaBatchProperties.getSynergyBatchSize());
		// step++;
		// addLog("[%d/%d] 시너지 집계 pending — 배치 %d건/라운드, 완료까지 전량 처리", step, PIPELINE_STEPS, synergyBatch);
		// int synPause = Math.max(0, rtaBatchProperties.getSynergyPauseMsBetweenRounds());
		// if (synPause > 0) {
		//     addLog("[%d/%d] · 라운드 간 대기: %dms", step, PIPELINE_STEPS, synPause);
		// }
		// RtaBatchAggregationService.SynergyDrainResult syn = aggregationService.drainSynergyPending(
		//         rtaMapper, synergyAggService, rtaCacheEvictor, synergyBatch, false, synPause);
		// addLog("[%d/%d] · 시너지 완료 — 라운드 %d, ok %d, fail %d, %s",
		//         step, PIPELINE_STEPS, syn.rounds(), syn.totalOk(), syn.totalFail(), syn.stopReason());
		step++;

		if (!rtaBatchProperties.isSkipUserMonsterOwnedAggInUnifiedJob()) {
			addLog("[%d/%d] · 사용자 보유 몬스터(SWEX → user_monster_owned_agg)", step, PIPELINE_STEPS);
			AccountSummaryMapper accountSummaryMapper = applicationContext.getBean(AccountSummaryMapper.class);
			accountSummaryMapper.deleteAllUserMonsterOwnedAgg();
			accountSummaryMapper.insertUserMonsterOwnedAggFromSwex();
			long ownedRows = accountSummaryMapper.countUserMonsterOwnedAgg();
			addLog("[%d/%d] · 보유 몬스터 완료 — %d행", step, PIPELINE_STEPS, ownedRows);
			RtaBatchAggregationService.SummonerOwnedBoxSnapRebuildResult boxSnap = aggregationService.rebuildSummonerOwnedBoxSnap(rtaMapper);
			addLog("[%d/%d] · 보유 박스 스냅(rta_agg_summoner_owned_box_snap) %d행", step, PIPELINE_STEPS, boxSnap.rows());
		} else {
			addLog("[%d/%d] · 보유 몬스터 집계 생략 (smw.rta.batch.skip-user-monster-owned-agg-in-unified-job)", step, PIPELINE_STEPS);
		}

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
			addLog("[%d/%d] · 부가 실행 없음 — 티어 일별은 RtaTierDailyAggJob, 랭킹 스냅샷은 RtaSummonerRankingAggJob, 랭크컷 스냅샷은 RtaRankCutSnapshotAggJob 등 별도 스케줄에서 처리하는 구성이 일반적입니다.",
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
