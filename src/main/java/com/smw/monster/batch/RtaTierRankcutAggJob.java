package com.smw.monster.batch;

import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaBatchAggregationService;

/**
 * 대시보드용 티어 일별 분포·랭크 컷 스냅샷을 재적재한다.
 * <p>
 * 운영 스케줄은 {@link RtaUnifiedPipelineAggJob} 로 통합하는 것을 권장한다.
 * <p>
 * 스케줄: DB {@code sys_batch_config.cron_expr} (기본 비활성화, bat_id 10004).
 */
public class RtaTierRankcutAggJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
		RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);
		RtaBatchAggregationService aggregationService = applicationContext.getBean(RtaBatchAggregationService.class);

		RtaBatchAggregationService.TierRankcutRebuildResult tier = aggregationService.rebuildTierRankcut(rtaMapper);
		addLog("rta_tier_distribution_daily_agg 적재: %d행", tier.tierRows());
		addLog("rta_rank_cutoff_snapshot 적재: %d행", tier.cutRows());

		rtaCacheEvictor.evictAllRtaCaches();
		addLog("RTA 조회 캐시 무효화 (대시보드 집계 갱신)");
	}

	@Override
	protected String getBatchName() {
		return "RTA 티어 분포·랭크 컷 집계 갱신";
	}
}
