package com.smw.rta.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.smw.monster.service.summonerswarService;
import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * RTA 관련 집계 공통 로직 (raw 정규화·시너지·랭킹·몬스터 통계·티어 일별).
 * <p>
 * 배치 Job 여러 개가 동일 규칙을 쓰도록 묶는다.
 */
@Slf4j
@Service
public class RtaBatchAggregationService {

	private final TransactionTemplate transactionTemplate;

	/** {@code insertRtaMonsterStatsTierTop*SnapForSeason} 및 API 스냅 폴백과 동일 */
	@Value("${smw.rta.monster-stats.min-pick-count:10}")
	private int monsterStatsMinPickCount;

	/**
	 * {@code rta_agg_summoner_monster_snap}: 시즌 INSERT 를 리플레이 ID 키셋 청크로 분할(한 문 집계 부담·I/O 오류 완화).
	 */
	@Value("${smw.rta.batch.summoner-monster-snap-replay-chunk-size:3000}")
	private int summonerMonsterSnapReplayChunkSize;

	public RtaBatchAggregationService(
			@Qualifier("rtaJdbcTransactionManager") PlatformTransactionManager transactionManager) {
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	/**
	 * {@link com.smw.monster.batch.RtaSummonerRankingTopSnapJob} 전용: 시즌별 상위 500 랭킹 스냅 + 검색 스냅.
	 * {@code rta_match} 플래그와 무관하다.
	 */
	public SummonerRankingRebuildResult rebuildSummonerRankingAgg(RtaMapper rtaMapper) {
		List<Map<String, Object>> seasons = rtaMapper.listRtaSeasons();
		for (Map<String, Object> row : seasons) {
			Long seasonId = pickSeasonId(row);
			if (seasonId == null) {
				continue;
			}
			long sid = seasonId.longValue();
			// 시즌당 독립 TX: advisory lock + 해당 시즌 랭킹 스냅만 전량 DELETE 후 INSERT
			transactionTemplate.executeWithoutResult(status -> {
				rtaMapper.acquireRtaSummonerSnapSeasonXactLock(sid);
				rtaMapper.deleteRtaSummonerRankingSnapBySeason(sid);
				rtaMapper.insertRtaSummonerRankingSnapForSeason(sid);
			});
		}
		transactionTemplate.executeWithoutResult(status -> {
			rtaMapper.acquireRtaSummonerSearchSnapGlobalXactLock();
			rtaMapper.upsertRtaSummonerSearchSnap();
		});
		return new SummonerRankingRebuildResult(
				(int) safeCount(rtaMapper.countRtaSummonerRankingSnapRows()),
				(int) safeCount(rtaMapper.countRtaSummonerSearchSnapRows()));
	}

	/**
	 * 시즌별 소환사×몬스터 통계·전투 분모 스냅·픽턴(snake) 소환사 스냅
	 * ({@code rta_agg_summoner_season_fight_snap}, {@code rta_agg_summoner_monster_snap},
	 * {@code rta_agg_summoner_pick_turn_snap} — 슬롯 구간 API는 이 테이블 롤업).
	 * <p>
	 * {@link com.smw.monster.batch.RtaSummonerRankingAggJob}에서 호출.
	 * 시즌 분모 스냅은 participant·픽 원천 기준 전원 UPSERT 로 갱신하고,
	 * 몬·픽턴은 {@code rta_match.summoner_ranking_apply_result IS NULL} 인 리플레이만 키셋 청크로 적재한 뒤
	 * 해당 rid 에 {@code summoner_ranking_apply_result='S'} 를 남긴다(시즌 단위 스냅 DELETE·매치 플래그 일괄 리셋 없음).
	 * 청크마다 동일 트랜잭션에서 {@code rta_agg_summoner_owned_box_snap} 을 staging replay 기준 MERGE 증분한다(말미 전량 재교체 없음).
	 * SWEX {@code user_monster_owned_agg} 는 통합 배치 직전 갱신돼 있어야 owned_copy_count 가 채워진다.
	 * <p>
	 * 미처리 매치가 없는 시즌은 스캔하지 않는다({@link RtaMapper#selectSeasonIdsWithPendingSummonerRankingReplays}).
	 */
	public SummonerMonsterSnapRebuildResult rebuildSummonerMonsterSnapAgg(RtaMapper rtaMapper) {
		List<Long> targetSeasons = rtaMapper.selectSeasonIdsWithPendingSummonerRankingReplays();
		if (targetSeasons == null || targetSeasons.isEmpty()) {
			return new SummonerMonsterSnapRebuildResult(0, 0, 0, 0, List.of());
		}
		int fightRows = 0;
		int monRows = 0;
		int pickTurnRows = 0;
		int ownedBoxUpserts = 0;
		for (long sid : targetSeasons) {
			fightRows += rtaMapper.insertRtaSummonerSeasonFightSnapForSeason(sid);
			ChunkTotals ct = insertSummonerMonsterAndPickTurnSnapForSeasonChunked(rtaMapper, sid);
			monRows += ct.monsterInserted;
			pickTurnRows += ct.pickTurnInserted;
			ownedBoxUpserts += ct.ownedBoxUpserted;
		}
		return new SummonerMonsterSnapRebuildResult(fightRows, monRows, pickTurnRows, ownedBoxUpserts, List.copyOf(targetSeasons));
	}

	/**
	 * 시즌별 소환사×상대 H2H 스냅({@code rta_agg_summoner_opponent_h2h_snap}) — participant 1:1 집계,
	 * 시즌마다 DELETE 후 INSERT. API는 이 스냅만 조회(라이브 폴백 없음).
	 * <p>
	 * {@code seasonIds} 가 비면 DB 작업 없이 반환한다. 무거운 INSERT·스캔이므로 실제 갱신이 필요한 시즌만 넘긴다.
	 */
	public SummonerOpponentH2hSnapRebuildResult rebuildSummonerOpponentH2hSnapAgg(
			RtaMapper rtaMapper,
			Collection<Long> seasonIds) {
		if (seasonIds == null || seasonIds.isEmpty()) {
			return new SummonerOpponentH2hSnapRebuildResult(0, null);
		}
		int inserted = 0;
		for (long sid : new TreeSet<>(seasonIds)) {
			rtaMapper.deleteRtaSummonerOpponentH2hSnapBySeason(sid);
			inserted += rtaMapper.insertRtaSummonerOpponentH2hSnapForSeason(sid);
		}
		return new SummonerOpponentH2hSnapRebuildResult(inserted,
				safeCount(rtaMapper.countRtaSummonerOpponentH2hSnapRows()));
	}

	/**
	 * 등록된 모든 RTA 시즌에 대해 H2H 스냅 전량 재적재 (수동·점검용).
	 */
	public SummonerOpponentH2hSnapRebuildResult rebuildSummonerOpponentH2hSnapAgg(RtaMapper rtaMapper) {
		List<Long> ids = new ArrayList<>();
		for (Map<String, Object> row : rtaMapper.listRtaSeasons()) {
			Long seasonId = pickSeasonId(row);
			if (seasonId != null) {
				ids.add(seasonId);
			}
		}
		return rebuildSummonerOpponentH2hSnapAgg(rtaMapper, ids);
	}

	/**
	 * {@code rta_match_unit_pick} 기준 소환사별 RTA 사용 몬스터 DISTINCT 로 {@code rta_agg_summoner_owned_box_snap}
	 * 테이블을 전량 DELETE 후 채운다(시즌 무관 한 번에 교체).
	 * 정식 무거운 스냅에서는 사용하지 않는다 —
	 * {@link com.smw.monster.batch.RtaSummonerRankingAggJob} 매치 청크마다
	 * {@link RtaMapper#mergeRtaSummonerOwnedBoxSnapFromStagingReplayChunk} 로 증분 MERGE 한다.
	 * 수동({@link com.smw.monster.batch.RtaSummonerOwnedBoxSnapJob}) 또는 정합 진단 때만 호출한다.
	 */
	public SummonerOwnedBoxSnapRebuildResult rebuildSummonerOwnedBoxSnap(RtaMapper rtaMapper) {
		transactionTemplate.executeWithoutResult(status -> {
			rtaMapper.deleteAllRtaSummonerOwnedBoxSnap();
			rtaMapper.insertRtaSummonerOwnedBoxSnapFromRtaUnitPicks();
		});
		long snapRows = safeCount(rtaMapper.countRtaSummonerOwnedBoxSnapRows());
		if (snapRows == 0L) {
			log.warn(
					"rebuildSummonerOwnedBoxSnap: rta_agg_summoner_owned_box_snap 비었음 — rta_match_unit_pick 에 unit_master_id 가 채워진 행이 있는지 확인");
		}
		return new SummonerOwnedBoxSnapRebuildResult((int) snapRows);
	}

	/**
	 * 스테이징 {@code staging_rta_summoner_snap_rid} 에 rid 를 올린 뒤 몬·픽턴 UPSERT 와
	 * {@code markSummonerRankingAggDoneForStagingSeason} 을 청크당 한 트랜잭션으로 실행한다.
	 */
	private static final class ChunkTotals {
		final int monsterInserted;
		final int pickTurnInserted;
		final int ownedBoxUpserted;

		ChunkTotals(int monsterInserted, int pickTurnInserted, int ownedBoxUpserted) {
			this.monsterInserted = monsterInserted;
			this.pickTurnInserted = pickTurnInserted;
			this.ownedBoxUpserted = ownedBoxUpserted;
		}
	}

	private ChunkTotals insertSummonerMonsterAndPickTurnSnapForSeasonChunked(RtaMapper rtaMapper, long seasonId) {
		int limit = Math.max(100, summonerMonsterSnapReplayChunkSize);
		int monTotal = 0;
		int pickTurnTotal = 0;
		int ownedBoxTotal = 0;
		long afterExclusive = -1L;
		while (true) {
			List<Long> rids = rtaMapper.selectReplayIdsForSummonerMonsterSnapKeyset(seasonId, afterExclusive, limit);
			if (rids == null || rids.isEmpty()) {
				break;
			}
			final List<Long> chunkRids = rids;
			int[] mon = { 0 };
			int[] pick = { 0 };
			int[] box = { 0 };
			transactionTemplate.executeWithoutResult(status -> {
				rtaMapper.truncateStagingRtaSummonerSnapRid();
				rtaMapper.insertStagingRtaSummonerSnapRidBatch(seasonId, chunkRids);
				mon[0] = rtaMapper.insertRtaSummonerMonsterSnapForSeasonReplayChunk(seasonId);
				pick[0] = rtaMapper.insertRtaSummonerPickTurnSnapForSeasonReplayChunk(seasonId);
				box[0] = rtaMapper.mergeRtaSummonerOwnedBoxSnapFromStagingReplayChunk(seasonId);
				rtaMapper.markSummonerRankingAggDoneForStagingSeason(seasonId);
			});
			monTotal += mon[0];
			pickTurnTotal += pick[0];
			ownedBoxTotal += box[0];
			if (rids.size() < limit) {
				break;
			}
			afterExclusive = rids.get(rids.size() - 1);
		}
		return new ChunkTotals(monTotal, pickTurnTotal, ownedBoxTotal);
	}

	/**
	 * {@code rta_agg_monster_stats_tier_top_snap}: 시즌 전체 합산 솔/듀/트 각 상위 100(티어 컬럼 없음).
	 * 원천 {@code rta_agg_synergy_*} — {@link com.smw.monster.batch.RtaMonsterStatsTierTopSnapJob}(권장 1h)에서 호출;
	 * 시너지 집계가 먼저 반영돼 있어야 함.
	 */
	public MonsterStatsTierTopSnapRebuildResult rebuildMonsterStatsTierTopSnap(RtaMapper rtaMapper) {
		AtomicInteger total = new AtomicInteger(0);
		List<Map<String, Object>> seasons = rtaMapper.listRtaSeasons();
		for (Map<String, Object> row : seasons) {
			Long seasonId = pickSeasonId(row);
			if (seasonId == null) {
				continue;
			}
			long sid = seasonId.longValue();
			transactionTemplate.executeWithoutResult(status -> {
				rtaMapper.deleteRtaMonsterStatsTierTopSnapBySeason(sid);
				int n = 0;
				n += rtaMapper.insertRtaMonsterStatsTierTopSoloSnapForSeason(sid, monsterStatsMinPickCount);
				n += rtaMapper.insertRtaMonsterStatsTierTopDuoSnapForSeason(sid, monsterStatsMinPickCount);
				n += rtaMapper.insertRtaMonsterStatsTierTopTrioSnapForSeason(sid, monsterStatsMinPickCount);
				total.addAndGet(n);
			});
		}
		return new MonsterStatsTierTopSnapRebuildResult(total.get());
	}

	/**
	 * 원본 스테이징 미적용 건을 정규화 테이블로 반영한다.
	 * {@link summonerswarService#applyPendingArenaReplayRawFromDb()} 는 {@code max-rows-per-run} 행을
	 * 1회만 조회·처리하고 종료한다. 잔여 행은 다음 스케줄에서 처리된다. 고아 행 삭제는 통합 Job 에서 하지 않는다.
	 */
	public RawApplyDrainResult drainReplayRawPending(summonerswarService service) {
		int totalApplied = service.applyPendingArenaReplayRawFromDb();
		String stopReason = totalApplied == 0 ? "적용할 raw 없음" : "완료";
		return new RawApplyDrainResult(totalApplied, stopReason);
	}

	/**
	 * {@code rta_match.synergy_applied_at IS NULL} 인 rid 를 배치 단위로
	 * {@code rta_agg_synergy_solo/duo/trio} 및 {@code rta_agg_counter_solo/duo/trio}에 반영한다.
	 * 완료 시 {@code synergy_apply_result='S'}. pending 이 모두 소진될 때까지 반복한다 (라운드 상한 없음).
	 *
	 * @param pauseMsBetweenRounds 라운드 사이 대기(ms), 0 이면 생략
	 */
	public SynergyDrainResult drainSynergyPending(
			RtaMapper rtaMapper,
			RtaSynergyAggService synergyAggService,
			RtaCacheEvictor cacheEvictor,
			int batchSize,
			boolean evictCachesEachRound,
			int pauseMsBetweenRounds) {
		int rounds = 0;
		int totalOk = 0;
		int totalFail = 0;

		// idx_rta_match_synergy_pending(partial index) 강제 사용 — Seq Scan 방지
		rtaMapper.hintBatchDisableSeqScan();
		try {
		while (true) {
			List<Long> rids = rtaMapper.selectPendingSynergyAggRids(batchSize);
			if (rids == null || rids.isEmpty()) {
				break;
			}
			// 첫 rid 실패 시 예외로 상위 Quartz Job 이 FAILED 처리되도록 전파
			RtaSynergyAggService.SynergyBatchApplyResult batch = synergyAggService.applySynergyBatch(rids);
			int ok = batch.ok();
			totalOk += ok;
			totalFail += batch.fail();
			rounds++;

			if (evictCachesEachRound && ok > 0) {
				cacheEvictor.evictAllRtaCaches();
			}

			if (pauseMsBetweenRounds > 0) {
				sleepQuiet(pauseMsBetweenRounds);
			}
		}
		} finally {
			// 세션 플래너 설정 복원 — 예외 발생 시에도 반드시 복원
			rtaMapper.hintBatchEnableSeqScan();
		}
		return new SynergyDrainResult(rounds, totalOk, totalFail, "완료");
	}

	/** no-op — 몬스터 통계는 {@code rta_agg_synergy_solo/duo/trio} 집계 테이블에서 직접 조회. */
	public MonsterStatsRebuildResult rebuildMonsterStatsAgg(RtaMapper rtaMapper) {
		return new MonsterStatsRebuildResult(0, 0);
	}

	/**
	 * 시즌별 {@code rta_agg_tier_daily} 전량 재적재.
	 * <p>
	 * 당일만 MERGE하는 증분이 아니라, 시즌마다 해당 {@code season_id} 행을 모두 DELETE 한 뒤
	 * {@code rta_season} 기준(서울) 일자 구간에 대해 누적 집계를 처음부터 다시 넣는다.
	 * 배치가 중간에 실패했더라도 다음 실행에서 같은 방식으로 전 일자가 일관되게 복구된다.
	 */
	public TierDailyAggRebuildResult rebuildTierAggDaily(RtaMapper rtaMapper) {
		List<Map<String, Object>> seasons = rtaMapper.listRtaSeasons();
		int totalRows = 0;
		for (Map<String, Object> row : seasons) {
			Long seasonId = pickSeasonId(row);
			if (seasonId == null) {
				continue;
			}
			totalRows += rtaMapper.insertRtaTierAggDailyForSeason(seasonId.longValue());
		}
		return new TierDailyAggRebuildResult(totalRows);
	}

	/**
	 * 시즌×티어 총 경기 수({@code rta_agg_season_rating_match_total})를 먼저 재집계한 뒤,
	 * 랭크 컷 앵커({@code rta_rank_cutoff_anchor_snap})를 DELETE 후 라이브와 동일 로직으로 재적재,
	 * 시즌×등급 컷({@code rta_snapshot_rank_cut}) 히스토리 1회 적재.
	 */
	public RankCutSnapshotRebuildResult rebuildRankCutSnapshots(RtaMapper rtaMapper) {
		rtaMapper.rebuildRtaSeasonRatingMatchTotal();
		rtaMapper.deleteAllRtaRankCutoffAnchorSnap();
		Long defaultSid = rtaMapper.selectDefaultSeasonIdForNow();
		if (defaultSid != null) {
			rtaMapper.insertRtaRankCutoffAnchorSnapFromLive(defaultSid.longValue());
		}
		rtaMapper.insertRtaSnapshotRankCutForAllSeasons();
		long matchTotalRows = safeCount(rtaMapper.countRtaSeasonRatingMatchTotalRows());
		long anchorRows = safeCount(rtaMapper.countRtaRankCutoffAnchorSnapRows());
		long snapshotRows = safeCount(rtaMapper.countRtaSnapshotRankCutAtLatestSnapshot());
		return new RankCutSnapshotRebuildResult(matchTotalRows, anchorRows, snapshotRows);
	}

	private static long safeCount(Long n) {
		return n != null && n >= 0 ? n : 0L;
	}

	private static Long pickSeasonId(Map<String, Object> row) {
		Object o = row.get("seasonId");
		if (o == null) {
			o = row.get("season_id");
		}
		if (o instanceof Number) {
			return ((Number) o).longValue();
		}
		return null;
	}

	private static void sleepQuiet(int ms) {
		if (ms <= 0) {
			return;
		}
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** @param rankingRows {@code rta_agg_summoner_ranking_snap} 합, @param searchRows {@code rta_agg_summoner_search_snap} 합 */
	public record SummonerRankingRebuildResult(int rankingRows, int searchRows) {
	}

	/**
	 * @param fightRows {@code rta_agg_summoner_season_fight_snap} 합(적재 루프 합이 아닌 전체 count),
	 * @param monsterRows {@code INSERT rta_agg_summoner_monster_snap} 청크 적재가 반환한 행 합(근사),
	 * @param pickTurnRows 픽턴(선후 라인) 소환사 스냅 청크 행 합(근사),
	 * @param ownedBoxUpsertRows {@code mergeRtaSummonerOwnedBoxSnapFromStagingReplayChunk} 의 JDBC 영향 행 합(MERGE 포함),
	 * @param seasonsWithPendingWork 이번 실행에서 분모·청크를 돌린 시즌 ID 목록(미처리 매치가 없으면 빈 목록).
	 */
	public record SummonerMonsterSnapRebuildResult(
			int fightRows,
			int monsterRows,
			int pickTurnRows,
			int ownedBoxUpsertRows,
			List<Long> seasonsWithPendingWork) {
	}

	/**
	 * @param insertReported 루프에서 INSERT 반환 합(시즌별),
	 * @param totalRows 스냅 전체 행 수 로깅용 — 스킵 시 {@code null} 이어 COUNT 를 생략한다.
	 */
	public record SummonerOpponentH2hSnapRebuildResult(int insertReported, Long totalRows) {
	}

	/** @param rows {@code rta_agg_summoner_owned_box_snap} 전체 건수(적재 후 COUNT) */
	public record SummonerOwnedBoxSnapRebuildResult(int rows) {
	}

	/**
	 * @param totalInserts {@code rta_agg_monster_stats_tier_top_snap} INSERT 가 반환한 row 합(솔+듀+트).
	 */
	public record MonsterStatsTierTopSnapRebuildResult(int totalInserts) {
	}

	public record RawApplyDrainResult(int totalApplied, String stopReason) {
	}

	public record SynergyDrainResult(int rounds, int totalOk, int totalFail, String stopReason) {
	}

	public record MonsterStatsRebuildResult(int metaRows, int pickRows) {
	}

	public record TierDailyAggRebuildResult(int totalRows) {
	}

	public record RankCutSnapshotRebuildResult(long matchTotalRows, long anchorRows, long snapshotRows) {
	}
}
