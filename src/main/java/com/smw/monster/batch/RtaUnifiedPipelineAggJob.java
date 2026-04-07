package com.smw.monster.batch;

import org.quartz.JobExecutionContext;

import com.smw.account.mapper.AccountSummaryMapper;
import com.smw.monster.mapper.summonerswarMapper;
import com.smw.monster.service.summonerswarService;
import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.config.RtaBatchProperties;
import com.smw.rta.config.RtaRawApplyProperties;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaBatchAggregationService;
import com.smw.rta.service.RtaSynergyAggService;

/**
 * RTA 관련 집계를 한 번의 스케줄로 순서대로 수행한다.
 * <ol>
 * <li>리플레이 raw 정규화 (설정 라운드·건수 상한, 나머지는 다음 스케줄)</li>
 * <li>매치 스냅샷 pending (설정 라운드 상한)</li>
 * <li>시너지 집계 pending (설정 라운드 상한)</li>
 * <li>소환사 랭킹 agg 재적재</li>
 * <li>몬스터 통계 agg 재적재</li>
 * <li>사용자 보유 몬스터 집계 (SWEX → {@code user_monster_owned_agg})</li>
 * </ol>
 * 대시보드 티어 일별 분포는 별도 배치 없이 {@code getRtaTierDistributionDailyFromAgg} 라이브 쿼리 + 캐시.
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
		RtaBatchProperties rtaBatchProperties = applicationContext.getBean(RtaBatchProperties.class);
		RtaRawApplyProperties rtaRawApplyProperties = applicationContext.getBean(RtaRawApplyProperties.class);

		addLog("--- 1) RTA raw 정규화 (라운드·건수 상한) ---");
		int notApplied = summonerswarMapper.selectRtaReplayRawNotAppliedCount();
		addLog("미적용 raw 건수(참고): %d", notApplied);
		int rawMaxRounds = Math.max(1, rtaRawApplyProperties.getMaxRoundsPerUnifiedJob());
		addLog("raw 정규화 최대 라운드: %d (회당 최대 %d행)", rawMaxRounds, rtaRawApplyProperties.getMaxRowsPerRun());
		RtaBatchAggregationService.RawApplyDrainResult raw = aggregationService.drainReplayRawPending(
				summonerswarService,
				rawMaxRounds);
		addLog("raw: 고아 정리 %d건, 라운드 %d, 적용 누적 %d건, 종료: %s",
				raw.orphansDeleted(),
				raw.rounds(),
				raw.totalApplied(),
				raw.stopReason());

		int snapshotBatch = Math.max(1, rtaBatchProperties.getSnapshotBatchSize());
		int synergyBatch = Math.max(1, rtaBatchProperties.getSynergyBatchSize());

		if (rtaBatchProperties.isSkipLegacySnapshotStep()) {
			addLog("--- 2) 매치 스냅샷: 설정에 의해 단계 생략 (smw.rta.batch.skip-legacy-snapshot-step=true) ---");
		} else {
			addLog("--- 2) 매치 스냅샷 pending 소진 (배치 %d건/라운드) ---", snapshotBatch);
			int snapMaxRounds = Math.max(1, rtaBatchProperties.getSnapshotMaxRoundsPerJob());
			RtaBatchAggregationService.SnapshotDrainResult snap = aggregationService.drainPendingSnapshots(
					rtaMapper,
					rtaCacheEvictor,
					snapshotBatch,
					snapMaxRounds,
					false);
			addLog("스냅샷: 라운드 %d, rids 누적 %d건, upsert %d, done %d, 종료: %s",
					snap.rounds(),
					snap.totalRidsTouched(),
					snap.totalUpserted(),
					snap.totalMarked(),
					snap.stopReason());
		}

		addLog("--- 3) 시너지 집계 pending 소진 (배치 %d건/라운드) ---", synergyBatch);
		int synMaxRounds = Math.max(1, rtaBatchProperties.getSynergyMaxRoundsPerJob());
		RtaBatchAggregationService.SynergyDrainResult syn = aggregationService.drainSynergyPending(
				rtaMapper,
				synergyAggService,
				rtaCacheEvictor,
				synergyBatch,
				synMaxRounds,
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
		addLog("몬스터 통계 집계 테이블 합계(meta/pick): meta=%d, pick=%d",
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
