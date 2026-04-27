package com.smw.monster.batch;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaBatchAggregationService;

/**
 * RTA 소환사 랭킹 스냅샷({@code rta_agg_summoner_ranking_snap})을 시즌별로 전량 재적재하고, 검색용
 * {@code rta_agg_summoner_search_snap} 은 시즌 비저장·wizard_id 기준 participant 대표행 upsert 1회로 갱신한다. 이어서
 * {@code rta_agg_summoner_season_fight_snap} / {@code rta_agg_summoner_monster_snap}(소환사×몬스터 픽/밴/승/선피·보유) 및
 * 시즌 전체 상대 H2H({@code rta_agg_summoner_opponent_h2h_snap})를 갱신한다.
 * 보유 박스({@code rta_agg_summoner_owned_box_snap})는 SWEX 직후 {@link RtaUnifiedPipelineAggJob}에서 갱신한다.
 * 전체 티어 합산 상위 100({@code rta_agg_monster_stats_tier_top_snap})은 {@link RtaMonsterStatsTierTopSnapJob} 에서 별도 스케줄.
 * <p>
 * participant 시즌 집계를 다시 읽는 무거운 작업이라 {@link RtaUnifiedPipelineAggJob}(짧은 주기)와 분리해
 * 긴 주기로 실행하는 것을 권장한다. 보유 몬스터 컬럼이 필요하면 통합 Job 의 SWEX {@code user_monster_owned_agg} 이후에 배치하거나,
 * 랭킹 Job 직전에 보유 집계 1회를 수행한다.
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

		addLog("--- 소환사×몬스터·시즌 전투 스냅(픽/밴/승/선첫비밴·보유) ---");
		RtaBatchAggregationService.SummonerMonsterSnapRebuildResult monSnap = aggregationService.rebuildSummonerMonsterSnapAgg(rtaMapper);
		addLog("전투 분모 스냅 %d행, 몬스터 스냅 %d행", monSnap.fightRows(), monSnap.monsterRows());

		addLog("--- 소환사×상대 H2H 스냅(시즌 전체) ---");
		RtaBatchAggregationService.SummonerOpponentH2hSnapRebuildResult h2h = aggregationService.rebuildSummonerOpponentH2hSnapAgg(rtaMapper);
		addLog("H2H 스냅 INSERT 합계(시즌별 합): %d, 스냅 총 행: %d", h2h.insertReported(), h2h.totalRows());

		rtaCacheEvictor.evictAllRtaCaches();
		addLog("RTA 조회 캐시 무효화 완료");
	}

	@Override
	protected String getBatchName() {
		return "RTA 소환사 랭킹·검색 스냅샷 집계";
	}
}
