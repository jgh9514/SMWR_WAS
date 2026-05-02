package com.smw.monster.batch;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaBatchAggregationService;

/**
 * RTA 소환사 랭킹 상위 500 ({@code rta_agg_summoner_ranking_snap}) 시즌별 전량 재적재 및
 * 검색용 {@code rta_agg_summoner_search_snap} upsert 1회.
 * 무거운 몬스터/픽턴/H2H 스냅은 {@link RtaSummonerRankingAggJob} 과 별도 스케줄로 둔다.
 */
@DisallowConcurrentExecution
public class RtaSummonerRankingTopSnapJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
		RtaBatchAggregationService aggregationService = applicationContext.getBean(RtaBatchAggregationService.class);
		RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);

		addLog("--- rta 랭킹·검색 스냅 재적재 (시즌별 상위 500 + 시즌 전체 wizard 검색용) ---");
		RtaBatchAggregationService.SummonerRankingRebuildResult ranking = aggregationService.rebuildSummonerRankingAgg(rtaMapper);
		addLog("랭킹 스냅 %d행(%dms), 검색 스냅 %d행(%dms), 전체 %dms",
				ranking.rankingRows(), ranking.rankingMs(), ranking.searchRows(), ranking.searchMs(), ranking.wallMs());

		rtaCacheEvictor.evictAllRtaCaches();
		addLog("RTA 조회 캐시 무효화 완료");
	}

	@Override
	protected String getBatchName() {
		return "RTA 소환사 랭킹·검색 스냅샷";
	}
}
