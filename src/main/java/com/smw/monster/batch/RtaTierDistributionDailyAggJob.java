package com.smw.monster.batch;

import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaBatchAggregationService;

/**
 * 티어 일별 분포 집계 호출(집계 테이블 미사용 시 Mapper no-op). (통합 5분 Job과 분리)
 * <p>
 * 스케줄: DB {@code sys_batch_config.cron_expr} (기본 매시 정각, bat_id 10002).
 */
public class RtaTierDistributionDailyAggJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
		RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);
		RtaBatchAggregationService aggregationService = applicationContext.getBean(RtaBatchAggregationService.class);

		int tierRows = aggregationService.rebuildTierDistributionDailyAgg(rtaMapper);
		addLog("티어 일별 분포 재적재(0행=no-op): %d행", tierRows);

		rtaCacheEvictor.evictAllRtaCaches();
		addLog("RTA 조회 캐시 무효화 (티어 일별 분포)");
	}

	@Override
	protected String getBatchName() {
		return "RTA 티어 일별 분포 집계";
	}
}
