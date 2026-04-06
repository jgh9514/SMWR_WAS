package com.smw.monster.batch;

import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaBatchAggregationService;

/**
 * 소환사 랭킹 스냅샷 재적재 호출(집계 테이블 미사용 시 Mapper no-op; API는 라이브 집계).
 * <p>
 * 운영 스케줄은 {@link RtaUnifiedPipelineAggJob} 로 통합하는 것을 권장한다.
 * <p>
 * 스케줄: DB {@code sys_batch_config.cron_expr} (기본 비활성화, bat_id 10005).
 */
public class RtaSummonerRankingAggJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
		RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);
		RtaBatchAggregationService aggregationService = applicationContext.getBean(RtaBatchAggregationService.class);

		addLog("소환사 랭킹 스냅샷 재적재(집계 테이블 없으면 no-op)");
		RtaBatchAggregationService.SummonerRankingRebuildResult rank = aggregationService.rebuildSummonerRankingAgg(rtaMapper);
		addLog("소환사 랭킹 스냅샷 합계(0행=no-op): %d행", rank.totalRows());

		rtaCacheEvictor.evictAllRtaCaches();
		addLog("RTA 조회 캐시 무효화 (소환사 랭킹 집계 갱신)");
	}

	@Override
	protected String getBatchName() {
		return "RTA 소환사 랭킹 집계 갱신";
	}
}
