package com.smw.monster.batch;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaBatchAggregationService;

/**
 * RTA 소환사 무거운 스냅({@link RtaBatchAggregationService#rebuildSummonerMonsterSnapAgg} 단독 진입점).
 * {@link RtaUnifiedPipelineAggJob} 은 raw·부가만 수행하며 본 집계를 포함하지 않는다.
 * 대상: {@code rta_agg_summoner_season_fight_snap},
 * {@code rta_agg_summoner_monster_snap},
 * {@code rta_agg_summoner_pick_turn_snap},
 * {@code rta_agg_summoner_opponent_h2h_snap}(라이벌),
 * 청크별 {@code rta_agg_summoner_owned_box_snap} MERGE.
 * <p>
 * 미처리 매치가 없으면 갱신량 0·캐시 무효화 생략.
 * 시즌 분모 스냅은 원천 UPSERT, 청크 대상은 {@code summoner_ranking_apply_result IS NULL} 만.
 * 상위 500 랭킹·검색 스냅은 {@link RtaSummonerRankingTopSnapJob} 별도.
 */
@DisallowConcurrentExecution
public class RtaSummonerRankingAggJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
		RtaBatchAggregationService aggregationService = applicationContext.getBean(RtaBatchAggregationService.class);
		RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);

		addLog("--- 소환사×몬스터·시즌 전투 스냅(픽/밴/승·선첫비밴) — 미처리 매치 있는 시즌만 ---");
		RtaBatchAggregationService.SummonerMonsterSnapRebuildResult monSnap = aggregationService.rebuildSummonerMonsterSnapAgg(rtaMapper);
		addLog("대상 시즌 %s · 전투 분모 UPSERT 합계 %d행 · 몬스터 청크합 %d · 픽턴 청크합 %d · owned_box_snap MERGE 누적 영향 행 %d · H2H INSERT 합계 %d",
				monSnap.seasonsWithPendingWork().isEmpty() ? "(없음)" : monSnap.seasonsWithPendingWork().toString(),
				monSnap.fightRows(), monSnap.monsterRows(), monSnap.pickTurnRows(), monSnap.ownedBoxUpsertRows(),
				monSnap.opponentH2hInsertRows());
		addLog("%s", monSnap.perfSummary());

		if (monSnap.seasonsWithPendingWork().isEmpty()) {
			addLog("--- 캐시: 변경 없음 — 스킵 ---");
			return;
		}

		Long h2hTotal = rtaMapper.countRtaSummonerOpponentH2hSnapRows();
		addLog("H2H 스냅 현재 총 행: %d", h2hTotal);

		Long boxTotal = null;
		if (monSnap.ownedBoxUpsertRows() > 0) {
			boxTotal = rtaMapper.countRtaSummonerOwnedBoxSnapRows();
			addLog("--- owned_box_snap: 청크별 MERGE 완료 — 현재 스냅 총 %d행", boxTotal);
		}

		rtaCacheEvictor.evictAllRtaCaches();
		addLog("RTA 조회 캐시 무효화 완료");
	}

	@Override
	protected String getBatchName() {
		return "RTA 소환사 무거운 스냅 집계(전투·몬·픽턴·H2H)";
	}
}
