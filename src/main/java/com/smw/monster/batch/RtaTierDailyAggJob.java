package com.smw.monster.batch;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaBatchAggregationService;

/**
 * 대시보드용 {@code rta_agg_tier_daily} 만 시즌별 전량 재적재한다(당일 증분 아님). participant 풀스캔으로 무겁기 때문에
 * {@link RtaUnifiedPipelineAggJob}(짧은 주기)와 분리해 <b>1시간 등 긴 주기</b>로 돌리는 것을 권장한다.
 * <p>
 * DB 등록: {@code sql/migrate_add_rta_tier_daily_batch_config.sql} 로 {@code sys_batch_config} 에 삽입(중복 시 생략).
 * 크론 예: 매시 정각 {@code 0 0 * * * ?} — {@code use_yn=Y} 이고 {@code smw.batch.quartz.enabled=true} 일 때만 스케줄 등록.
 * <p>
 * 통합 Job 쪽은 {@code smw.rta.batch.skip-tier-agg-daily-in-unified-job=true} 로 티어 단계를 끄고,
 * 이 Job 만으로 티어 일별을 갱신하는 구성이 일반적이다.
 */
@DisallowConcurrentExecution
public class RtaTierDailyAggJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
		RtaBatchAggregationService aggregationService = applicationContext.getBean(RtaBatchAggregationService.class);
		RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);

		addLog("--- rta_agg_tier_daily 재적재 (시즌별 DELETE+UPSERT) ---");
		RtaBatchAggregationService.TierDailyAggRebuildResult tier = aggregationService.rebuildTierAggDaily(rtaMapper);
		addLog("적재 행 합계: %d, 전체 소요: %dms, 최장 시즌: sid=%d (%dms)",
				tier.totalRows(), tier.wallMs(), tier.slowestSeasonId(), tier.maxSeasonMs());

		rtaCacheEvictor.evictAllRtaCaches();
		addLog("RTA 조회 캐시 무효화 완료");
	}

	@Override
	protected String getBatchName() {
		return "RTA 티어 일별 집계(rta_agg_tier_daily)";
	}
}
