package com.smw.monster.batch;

import org.quartz.JobExecutionContext;

import com.smw.rta.mapper.RtaMapper;

/**
 * 대시보드용 티어 일별 분포·랭크 컷(앵커) 스냅샷을 원천과 동일한 SQL로 재적재한다.
 * <p>
 * 스케줄: DB {@code sys_batch_config.cron_expr} (기본 5분마다, bat_id 10004).
 */
public class RtaTierRankcutAggJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);

		rtaMapper.deleteAllRtaTierDistributionDailyAgg();
		int tierRows = rtaMapper.insertRtaTierDistributionDailyAggFromLive();
		addLog("rta_tier_distribution_daily_agg 적재: %d행", tierRows);

		rtaMapper.deleteAllRtaRankCutoffSnapshot();
		int cutRows = rtaMapper.insertRtaRankCutoffSnapshotFromLive();
		addLog("rta_rank_cutoff_snapshot 적재: %d행", cutRows);
	}

	@Override
	protected String getBatchName() {
		return "RTA 티어 분포·랭크 컷 집계 갱신";
	}
}
