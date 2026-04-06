package com.smw.monster.batch;

import org.quartz.JobExecutionContext;

import com.smw.account.mapper.AccountSummaryMapper;
import com.smw.monster.mapper.summonerswarMapper;
import com.smw.monster.service.summonerswarService;
import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaBatchAggregationService;
import com.smw.rta.service.RtaSynergyAggService;

/**
 * RTA 관련 집계를 한 번의 스케줄로 순서대로 수행한다.
 * <ol>
 * <li>리플레이 raw 정규화 (미적용 건 전량, 상한 내)</li>
 * <li>매치 스냅샷 pending 전량</li>
 * <li>시너지 집계 pending 전량</li>
 * <li>소환사 랭킹 agg 재적재</li>
 * <li>몬스터 통계 agg 재적재</li>
 * <li>사용자 보유 몬스터 집계 (SWEX → {@code user_monster_owned_agg})</li>
 * </ol>
 * 티어 일별 분포는 {@link RtaTierDistributionDailyAggJob}(bat_id 10002, 매시)와 분리. (집계 테이블 미사용 시 Mapper no-op)
 * <p>
 * 스케줄: DB {@code sys_batch_config.cron_expr} (기본 5분, bat_id 10001).
 */
public class RtaUnifiedPipelineAggJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		summonerswarService summonerswarService = applicationContext.getBean(summonerswarService.class);
		summonerswarMapper summonerswarMapper = applicationContext.getBean(summonerswarMapper.class);
		RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
		RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);
		RtaBatchAggregationService aggregationService = applicationContext.getBean(RtaBatchAggregationService.class);
		RtaSynergyAggService synergyAggService = applicationContext.getBean(RtaSynergyAggService.class);

		addLog("--- 1) RTA raw 정규화 (미적용 전량) ---");
		int notApplied = summonerswarMapper.selectRtaReplayRawNotAppliedCount();
		addLog("미적용 raw 건수(참고): %d", notApplied);
		RtaBatchAggregationService.RawApplyDrainResult raw = aggregationService.drainReplayRawPending(
				summonerswarService,
				RtaBatchAggregationService.MAX_RAW_APPLY_ROUNDS_PER_JOB);
		addLog("raw: 고아 정리 %d건, 라운드 %d, 적용 누적 %d건, 종료: %s",
				raw.orphansDeleted(),
				raw.rounds(),
				raw.totalApplied(),
				raw.stopReason());

		addLog("--- 2) 매치 스냅샷 pending 소진 (배치 %d건/라운드) ---",
				RtaBatchAggregationService.SNAPSHOT_BATCH_SIZE);
		RtaBatchAggregationService.SnapshotDrainResult snap = aggregationService.drainPendingSnapshots(
				rtaMapper,
				rtaCacheEvictor,
				RtaBatchAggregationService.SNAPSHOT_BATCH_SIZE,
				RtaBatchAggregationService.MAX_SNAPSHOT_ROUNDS_PER_JOB,
				false);
		addLog("스냅샷: 라운드 %d, rids 누적 %d건, upsert %d, done %d, 종료: %s",
				snap.rounds(),
				snap.totalRidsTouched(),
				snap.totalUpserted(),
				snap.totalMarked(),
				snap.stopReason());

		addLog("--- 3) 시너지 집계 pending 소진 (배치 %d건/라운드) ---",
				RtaBatchAggregationService.SYNERGY_BATCH_SIZE);
		RtaBatchAggregationService.SynergyDrainResult syn = aggregationService.drainSynergyPending(
				rtaMapper,
				synergyAggService,
				rtaCacheEvictor,
				RtaBatchAggregationService.SYNERGY_BATCH_SIZE,
				RtaBatchAggregationService.MAX_SYNERGY_ROUNDS_PER_JOB,
				false);
		addLog("시너지: 라운드 %d, ok %d, fail %d, 종료: %s",
				syn.rounds(),
				syn.totalOk(),
				syn.totalFail(),
				syn.stopReason());

		addLog("--- 4) 소환사 랭킹 집계 재적재 ---");
		RtaBatchAggregationService.SummonerRankingRebuildResult rank = aggregationService.rebuildSummonerRankingAgg(rtaMapper);
		addLog("소환사 랭킹 스냅샷 재적재(0행=no-op): %d행", rank.totalRows());

		addLog("--- 5) 몬스터 통계 집계 재적재 ---");
		RtaBatchAggregationService.MonsterStatsRebuildResult mon = aggregationService.rebuildMonsterStatsAgg(rtaMapper);
		addLog("rta_monster_stats_* 합계: meta=%d, pick=%d",
				mon.metaRows(),
				mon.pickRows());

		addLog("--- 6) 사용자 보유 몬스터 집계 (SWEX → user_monster_owned_agg) ---");
		AccountSummaryMapper accountSummaryMapper = applicationContext.getBean(AccountSummaryMapper.class);
		accountSummaryMapper.deleteAllUserMonsterOwnedAgg();
		int ownedRows = accountSummaryMapper.insertUserMonsterOwnedAggFromSwex();
		addLog("user_monster_owned_agg 적재: %d행", ownedRows);

		rtaCacheEvictor.evictAllRtaCaches();
		addLog("RTA 조회 캐시 무효화 (전체 파이프라인 완료)");
	}

	@Override
	protected String getBatchName() {
		return "RTA 전체 집계 파이프라인";
	}
}
