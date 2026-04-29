package com.smw.monster.batch;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaBatchAggregationService;

/**
 * RTA 소환사 무거운 소환사 스냅: {@code rta_agg_summoner_season_fight_snap} /
 * {@code rta_agg_summoner_monster_snap}(소환사×몬 픽/밴/승 등) /
 * {@code rta_agg_summoner_pick_turn_snap}(슬롯 구간 API 롤업 원천) /
 * {@code rta_agg_summoner_opponent_h2h_snap}.
 * <p>
 * 시즌별 스냅 DELETE 후 {@code rta_match} 의 {@code summoner_ranking_applied_at} 를 NULL 로 리셋하고,
 * 리플레이 ID 키셋(미처리 건만)으로 몬·픽턴을 청크 적재한 뒤 해당 rid 에 {@code summoner_ranking_apply_result='S'} 를 남긴다.
 * 상위 500 랭킹·검색 스냅은 {@link RtaSummonerRankingTopSnapJob} 에서 별도 실행.
 * 보유 박스({@code rta_agg_summoner_owned_box_snap})는 SWEX 직후 {@link RtaUnifiedPipelineAggJob}에서 갱신한다.
 */
@DisallowConcurrentExecution
public class RtaSummonerRankingAggJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
		RtaBatchAggregationService aggregationService = applicationContext.getBean(RtaBatchAggregationService.class);
		RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);

		addLog("--- 소환사×몬스터·시즌 전투 스냅(픽/밴/승/선첫비밴·보유) ---");
		RtaBatchAggregationService.SummonerMonsterSnapRebuildResult monSnap = aggregationService.rebuildSummonerMonsterSnapAgg(rtaMapper);
		addLog("전투 분모 스냅 %d행, 몬스터 청크합 %d, 픽턴(선후) 청크합 %d",
				monSnap.fightRows(), monSnap.monsterRows(), monSnap.pickTurnRows());

		addLog("--- 소환사×상대 H2H 스냅(시즌 전체) ---");
		RtaBatchAggregationService.SummonerOpponentH2hSnapRebuildResult h2h = aggregationService.rebuildSummonerOpponentH2hSnapAgg(rtaMapper);
		addLog("H2H 스냅 INSERT 합계(시즌별 합): %d, 스냅 총 행: %d", h2h.insertReported(), h2h.totalRows());

		rtaCacheEvictor.evictAllRtaCaches();
		addLog("RTA 조회 캐시 무효화 완료");
	}

	@Override
	protected String getBatchName() {
		return "RTA 소환사 무거운 스냅 집계(전투·몬·픽턴·H2H)";
	}
}
