package com.smw.monster.batch;

import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaBatchAggregationService;

/**
 * {@code rta_tier_distribution_daily_agg} 만 재적재한다. (통합 5분 Job과 분리)
 * <p>
 * 스케줄: DB {@code sys_batch_config.cron_expr} (기본 매시 정각, bat_id 10004).
 */
public class RtaTierDistributionDailyAggJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
		RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);
		RtaBatchAggregationService aggregationService = applicationContext.getBean(RtaBatchAggregationService.class);

		int tierRows = aggregationService.rebuildTierDistributionDailyAgg(rtaMapper);
		addLog("rta_tier_distribution_daily_agg 적재: %d행", tierRows);

		rtaCacheEvictor.evictAllRtaCaches();
		addLog("RTA 조회 캐시 무효화 (티어 일별 분포)");
	}

	@Override
	protected String getBatchName() {
		return "RTA 티어 일별 분포 집계";
	}
}
