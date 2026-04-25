package com.smw.monster.batch;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaBatchAggregationService;

/**
 * RTA 소환사 랭킹 스냅샷({@code rta_agg_summoner_ranking_snap})과 시즌 전체 검색용 스냅(
 * {@code rta_agg_summoner_search_snap})을 시즌별로 전량 재적재한다.
 * <p>
 * participant 시즌 집계를 다시 읽는 무거운 작업이라 {@link RtaUnifiedPipelineAggJob}(짧은 주기)와 분리해
 * 긴 주기로 실행하는 것을 권장한다.
 */
@DisallowConcurrentExecution
public class RtaSummonerRankingAggJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
		RtaBatchAggregationService aggregationService = applicationContext.getBean(RtaBatchAggregationService.class);
		RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);

		addLog("--- rta 랭킹·검색 스냅 재적재 (시즌별 상위 500 + 시즌 전체 wizard) ---");
		RtaBatchAggregationService.SummonerRankingRebuildResult ranking = aggregationService.rebuildSummonerRankingAgg(rtaMapper);
		addLog("랭킹 스냅 행 합계: %d, 검색 스냅 행 합계: %d", ranking.rankingRows(), ranking.searchRows());

		rtaCacheEvictor.evictAllRtaCaches();
		addLog("RTA 조회 캐시 무효화 완료");
	}

	@Override
	protected String getBatchName() {
		return "RTA 소환사 랭킹·검색 스냅샷 집계";
	}
}
