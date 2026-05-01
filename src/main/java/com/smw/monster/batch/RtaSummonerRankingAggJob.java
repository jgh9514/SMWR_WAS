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
 * {@code rta_agg_summoner_owned_box_snap} 은 매치 청크 트랜잭션마다 스테이징 rid 에 해당하는
 * {@code rta_match_unit_pick} 으로 MERGE 증분한다(실행 말미 전량 DELETE+INSERT 없음).
 * 미처리 매치가 없으면 H2H·캐시 무효화를 생략한다.
 * <p>
 * 시즌 분모 스냅은 원천 기준 UPSERT 로 갱신하고, 리플레이 ID 키셋은 {@code summoner_ranking_apply_result IS NULL} 만 청크 적재한 뒤
 * 해당 rid 에 {@code summoner_ranking_apply_result='S'} 를 남긴다(몬·픽턴 스냅 시즌 전량 DELETE·매치 플래그 일괄 리셋 없음).
 * 상위 500 랭킹·검색 스냅은 {@link RtaSummonerRankingTopSnapJob} 에서 별도 실행.
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
		addLog("대상 시즌 %s · 전투 분모 UPSERT 합계 %d행 · 몬스터 청크합 %d · 픽턴 청크합 %d · owned_box_snap MERGE 누적 영향 행 %d",
				monSnap.seasonsWithPendingWork().isEmpty() ? "(없음)" : monSnap.seasonsWithPendingWork().toString(),
				monSnap.fightRows(), monSnap.monsterRows(), monSnap.pickTurnRows(), monSnap.ownedBoxUpsertRows());

		if (monSnap.seasonsWithPendingWork().isEmpty()) {
			addLog("--- H2H / 캐시: 변경 없음 — 스킵 ---");
			return;
		}

		addLog("--- 소환사×상대 H2H 스냅 (갱신한 시즌만) ---");
		RtaBatchAggregationService.SummonerOpponentH2hSnapRebuildResult h2h =
				aggregationService.rebuildSummonerOpponentH2hSnapAgg(rtaMapper, monSnap.seasonsWithPendingWork());
		if (h2h.totalRows() != null) {
			addLog("H2H 스냅 INSERT 합계(시즌별 합): %d, 스냅 총 행: %d", h2h.insertReported(), h2h.totalRows());
		}

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
