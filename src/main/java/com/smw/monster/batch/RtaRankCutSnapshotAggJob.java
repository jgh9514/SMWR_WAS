package com.smw.monster.batch;

import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaBatchAggregationService;

/**
 * RTA 랭크 컷 집계: 앵커(3h~7d×티어) {@code rta_rank_cutoff_anchor_snap}, 등급별 컷 {@code rta_snapshot_rank_cut}.
 * <p>
 * 메인 화면 <b>소환사 티어별 분포</b>({@code rta_agg_tier_daily})는 이 Job 이 채우지 않는다 — {@link com.smw.monster.batch.RtaTierDailyAggJob} 별도 실행.
 * <p>
 * 통합 raw/시너지 Job 과 분리 — 부하·주기 다름. DB {@code sys_batch_config} 에 등록 (예: 매시 정각 {@code 0 0 * * * ?}).
 */
public class RtaRankCutSnapshotAggJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
		RtaBatchAggregationService aggregationService = applicationContext.getBean(RtaBatchAggregationService.class);
		RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);

		RtaBatchAggregationService.RankCutSnapshotRebuildResult r = aggregationService.rebuildRankCutSnapshots(rtaMapper);
		addLog("시즌×티어 총경기 %d행, 앵커 스냅샷 %d행, 등급 컷 스냅샷 %d행",
				r.matchTotalRows(), r.anchorRows(), r.snapshotRows());

		rtaCacheEvictor.evictRtaRankCutoffLiveCache();
		rtaCacheEvictor.evictAllRtaCaches();
		addLog("RTA 캐시 무효화 (랭크컷 스냅샷)");
	}

	@Override
	protected String getBatchName() {
		return "RTA 랭크컷 스냅샷 집계";
	}
}
