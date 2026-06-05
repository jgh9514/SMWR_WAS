package com.smw.rta.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.smw.monster.service.summonerswarService;
import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.config.RtaBatchProperties;
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
	private final RtaBatchProperties rtaBatchProperties;

	/** {@code insertRtaMonsterStatsTierTop*SnapForSeason} 및 API 스냅 폴백과 동일 */
	@Value("${smw.rta.monster-stats.min-pick-count:10}")
	private int monsterStatsMinPickCount;

	/**
	 * {@code rta_agg_summoner_monster_snap}: 시즌 INSERT 를 리플레이 ID 키셋 청크로 분할(한 문 집계 부담·I/O 오류 완화).
	 */
	@Value("${smw.rta.batch.summoner-monster-snap-replay-chunk-size:1000}")
	private int summonerMonsterSnapReplayChunkSize;

	/**
	 * 키셋 청크 내 실제 DB 트랜잭션 replay 상한 — UPSERT·픽턴·flat 을 짧은 TX 로 쪼개 JDBC 타임아웃 완화.
	 */
	@Value("${smw.rta.batch.summoner-monster-snap-tx-replay-size:200}")
	private int summonerMonsterSnapTxReplaySize;

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Autowired
	private RtaRankCutSnapValidator rankCutSnapValidator;

	public RtaBatchAggregationService(
			@Qualifier("rtaJdbcTransactionManager") PlatformTransactionManager transactionManager,
			RtaBatchProperties rtaBatchProperties) {
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.rtaBatchProperties = rtaBatchProperties;
	}

	/**
	 * {@link com.smw.monster.batch.RtaSummonerRankingTopSnapJob} 전용: 시즌별 상위 500 랭킹 스냅 + 검색 스냅.
	 * {@code rta_match} 플래그와 무관하다.
	 */
	public SummonerRankingRebuildResult rebuildSummonerRankingAgg(RtaMapper rtaMapper) {
		final long wallStart = System.nanoTime();
		Long seasonId = rtaMapper.selectDefaultSeasonIdForNow();
		long rankingMs = 0L;
		long searchMs = 0L;
		if (seasonId == null) {
			log.warn("rebuildSummonerRankingAgg: 현재 시즌 없음 — 랭킹 스냅 생략, 검색 스냅만 갱신");
		} else {
			long sid = seasonId.longValue();
			long t0 = System.nanoTime();
			rebuildSummonerRankingSnapForSeasonWithRetry(rtaMapper, sid);
			rankingMs = msSinceNanos(t0);
		}
		// 검색 스냅은 현재 시즌 participant 기준 upsert — 과거 항목은 유지(삭제 없음)
		long t1 = System.nanoTime();
		if (seasonId != null) {
			rebuildSummonerSearchSnapWithRetry(rtaMapper, seasonId.longValue());
		} else {
			log.warn("rebuildSummonerRankingAgg: 현재 시즌 없음 — 검색 스냅 생략");
		}
		searchMs = msSinceNanos(t1);
		long wallMs = msSinceNanos(wallStart);
		log.debug("랭킹 스냅 {}ms, 검색 스냅 {}ms, 전체 {}ms", rankingMs, searchMs, wallMs);
		return new SummonerRankingRebuildResult(
				(int) safeCount(rtaMapper.countRtaSummonerRankingSnapRows()),
				(int) safeCount(rtaMapper.countRtaSummonerSearchSnapRows()),
				rankingMs, searchMs, wallMs);
	}

	private void rebuildSummonerRankingSnapForSeasonWithRetry(RtaMapper rtaMapper, long seasonId) {
		// DELETE 는 짧은 TX — INSERT(대용량 CTE)와 분리해 idle-in-tx·커넥션 유지 시간을 줄인다.
		transactionTemplate.executeWithoutResult(status -> {
			applyBatchTxSessionGuards(rtaMapper);
			long t0 = System.nanoTime();
			rtaMapper.acquireRtaSummonerSnapSeasonXactLock(seasonId);
			log.debug("ranking snap delete-phase lock seasonId={} {}ms", seasonId, msSinceNanos(t0));
			long t1 = System.nanoTime();
			rtaMapper.deleteRtaSummonerRankingSnapBySeason(seasonId);
			log.debug("ranking snap delete seasonId={} {}ms", seasonId, msSinceNanos(t1));
		});

		int attempt = 0;
		while (true) {
			try {
				transactionTemplate.executeWithoutResult(status -> {
					applyBatchTxSessionGuards(rtaMapper);
					long t0 = System.nanoTime();
					rtaMapper.acquireRtaSummonerSnapSeasonXactLock(seasonId);
					log.debug("ranking snap insert-phase lock seasonId={} {}ms", seasonId, msSinceNanos(t0));
					long t1 = System.nanoTime();
					rtaMapper.insertRtaSummonerRankingSnapForSeason(seasonId);
					log.debug("ranking snap insert seasonId={} {}ms", seasonId, msSinceNanos(t1));
				});
				return;
			} catch (Exception e) {
				if (!isInfraRetryable(e) || ++attempt >= 3) {
					log.error("rebuildSummonerRankingSnap insert 실패 seasonId={} attempt={}", seasonId, attempt, e);
					throw e;
				}
				log.warn("rebuildSummonerRankingSnap insert 재시도 {}/3 seasonId={}: {}",
						attempt, seasonId, e.getMessage());
				sleepQuiet(3_000 * attempt);
			}
		}
	}

	private void rebuildSummonerSearchSnapWithRetry(RtaMapper rtaMapper, long seasonId) {
		int attempt = 0;
		while (true) {
			try {
				transactionTemplate.executeWithoutResult(status -> {
					applyBatchTxSessionGuards(rtaMapper);
					long t0 = System.nanoTime();
					rtaMapper.acquireRtaSummonerSearchSnapGlobalXactLock();
					log.debug("search snap lock seasonId={} {}ms", seasonId, msSinceNanos(t0));
					long t1 = System.nanoTime();
					rtaMapper.upsertRtaSummonerSearchSnap(seasonId);
					log.debug("search snap upsert seasonId={} {}ms", seasonId, msSinceNanos(t1));
				});
				return;
			} catch (Exception e) {
				if (!isInfraRetryable(e) || ++attempt >= 3) {
					log.error("rebuildSummonerSearchSnap 실패 seasonId={} attempt={}", seasonId, attempt, e);
					throw e;
				}
				log.warn("rebuildSummonerSearchSnap 재시도 {}/3 seasonId={}: {}",
						attempt, seasonId, e.getMessage());
				sleepQuiet(3_000 * attempt);
			}
		}
	}

	/** 장시간 배치 TX: 세션 lock·idle-in-tx 타임아웃 해제. */
	private static void applyBatchTxSessionGuards(RtaMapper mapper) {
		mapper.disableLocalLockTimeout();
		mapper.disableLocalIdleInTransactionTimeout();
		mapper.disableLocalStatementTimeout();
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
	 * 시즌의 미처리 리플레이 청크를 모두 반영한 직후, 해당 시즌 {@code rta_agg_summoner_opponent_h2h_snap} 을 DELETE 후 INSERT 한다
	 * (잡이 전 시즌 처리 후 H2H 직전에 실패하면 다음 실행에 미처리 rid 가 없어 라이벌만 비는 경우를 방지).
	 * SWEX {@code user_monster_owned_agg} 는 통합 배치 직전 갱신돼 있어야 owned_copy_count 가 채워진다.
	 * <p>
	 * 미처리 매치가 없는 시즌은 스캔하지 않는다({@link RtaMapper#selectSeasonIdsWithPendingSummonerRankingReplays}).
	 */
	public SummonerMonsterSnapRebuildResult rebuildSummonerMonsterSnapAgg(RtaMapper rtaMapper) {
		long tq = System.nanoTime();
		List<Long> targetSeasons = rtaMapper.selectSeasonIdsWithPendingSummonerRankingReplays();
		long pendingQueryMs = msSinceNanos(tq);
		if (targetSeasons == null || targetSeasons.isEmpty()) {
			return new SummonerMonsterSnapRebuildResult(0, 0, 0, 0, 0, List.of(),
					SummonerMonsterSnapPerfAccumulator.formatNoPendingWork(pendingQueryMs));
		}
		SummonerMonsterSnapPerfAccumulator perf = new SummonerMonsterSnapPerfAccumulator(pendingQueryMs,
				targetSeasons.size());
		int fightRows = 0;
		int monRows = 0;
		int pickTurnRows = 0;
		int ownedBoxUpserts = 0;
		int opponentH2hInserts = 0;
		for (long sid : targetSeasons) {
			// A — 쓰로틀: fight snap 행이 이미 있으면 전체 재집계(7.5M 풀스캔) 생략.
			//   행이 없을 때만 초기 seeding 시도 → 실패해도 warn 에 그침(청크가 점진적으로 채움).
			// B — 증분: 청크 트랜잭션 내에서 staging replay 기준 ADD-UPSERT 로 누적.
			long tf = System.nanoTime();
			Long lastFightMs = rtaMapper.selectFightSnapMaxComputedAtForSeason(sid);
			if (lastFightMs != null) {
				log.debug("[fight-snap] seasonId={} 기존 스냅 존재(lastComputed={}ms) — 전체 재집계 생략, 청크 증분으로 갱신", sid, lastFightMs);
			} else {
				// 행이 전혀 없는 경우에만 전체 재집계로 초기 seeding.
				// 실패하면 warn 처리 — 청크 증분이 점진적으로 채워줌.
				try {
					fightRows += insertFightSnapWithRetry(rtaMapper, sid);
				} catch (Exception e) {
					log.warn("[fight-snap] seasonId={} 초기 seeding 실패 — 청크 증분으로 점진적 복구 진행: {}", sid, e.getMessage());
				}
			}
			perf.addFightSnapUpsertMs(msSinceNanos(tf));

			ChunkTotals ct = insertSummonerMonsterAndPickTurnSnapForSeasonChunked(rtaMapper, sid, perf);
			monRows += ct.monsterInserted;
			pickTurnRows += ct.pickTurnInserted;
			ownedBoxUpserts += ct.ownedBoxUpserted;
			fightRows += ct.fightSnapUpserted;

			long th = System.nanoTime();
			opponentH2hInserts += replaceOpponentH2hSnapForSeasons(rtaMapper, List.of(sid));
			perf.addH2hDeleteInsertMs(msSinceNanos(th));
		}
		return new SummonerMonsterSnapRebuildResult(fightRows, monRows, pickTurnRows, ownedBoxUpserts,
				opponentH2hInserts, List.copyOf(targetSeasons), perf.toSummaryBlock());
	}

	/**
	 * 시즌 전원 분모 UPSERT — 풀스캔 쿼리라 커넥션이 끊길 수 있으므로 최대 3회 재시도.
	 * UPSERT(ON CONFLICT DO UPDATE)라 재시도는 멱등하다.
	 */
	private static int insertFightSnapWithRetry(RtaMapper rtaMapper, long seasonId) {
		int maxAttempts = 3;
		Exception last = null;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				return rtaMapper.insertRtaSummonerSeasonFightSnapForSeason(seasonId);
			} catch (Exception e) {
				last = e;
				if (attempt < maxAttempts) {
					try {
						Thread.sleep(5_000L * attempt);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						throw new RuntimeException("fight snap 재시도 중 인터럽트", ie);
					}
					Throwable cause = e.getCause() != null ? e.getCause() : e;
					log.warn("[fight-snap] 재시도 {}/{} (seasonId={}) error={} cause={} sqlState={}",
							attempt, maxAttempts, seasonId, e.getMessage(), cause.getMessage(),
							cause instanceof java.sql.SQLException ? ((java.sql.SQLException) cause).getSQLState() : "N/A");
				}
			}
		}
		throw new RuntimeException(
				"insertRtaSummonerSeasonFightSnapForSeason 재시도 " + maxAttempts + "회 모두 실패 (seasonId=" + seasonId + ")",
				last);
	}

	/** H2H 스냅 DELETE 시 ctid 배치 크기 — 단일 DELETE·장시간 커넥션 I/O 오류 완화. */
	private static final int OPPONENT_H2H_DELETE_ROW_BATCH = 5_000;

	/**
	 * {@code rta_agg_summoner_opponent_h2h_snap} 시즌별 DELETE 후 INSERT 반환 행 합계.
	 * DELETE·INSERT 를 <b>동일 TX</b>에서 실행해 DELETE 커밋 후 INSERT 실패·재시도 시 PK 충돌을 막는다.
	 */
	private int replaceOpponentH2hSnapForSeasons(RtaMapper rtaMapper, Collection<Long> seasonIds) {
		if (seasonIds == null || seasonIds.isEmpty()) {
			return 0;
		}
		int inserted = 0;
		for (long sid : new TreeSet<>(seasonIds)) {
			inserted += replaceOpponentH2hSnapForSeasonWithRetry(rtaMapper, sid);
		}
		return inserted;
	}

	private static int deleteOpponentH2hSnapBySeasonChunked(RtaMapper rtaMapper, long seasonId) {
		int total = 0;
		for (;;) {
			int n = rtaMapper.deleteRtaSummonerOpponentH2hSnapBySeasonWizardBatch(
					seasonId, OPPONENT_H2H_DELETE_ROW_BATCH);
			if (n > 0) {
				total += n;
				continue;
			}
			long remaining = safeCount(rtaMapper.countRtaSummonerOpponentH2hSnapBySeason(seasonId));
			if (remaining <= 0L) {
				break;
			}
			log.warn("h2h snap ctid batch delete returned 0 but seasonId={} remaining={} — season DELETE fallback",
					seasonId, remaining);
			total += rtaMapper.deleteRtaSummonerOpponentH2hSnapBySeason(seasonId);
			break;
		}
		return total;
	}

	private int replaceOpponentH2hSnapForSeasonWithRetry(RtaMapper rtaMapper, long seasonId) {
		int attempt = 0;
		while (true) {
			try {
				final int[] rows = { 0 };
				transactionTemplate.executeWithoutResult(status -> {
					applyBatchTxSessionGuards(rtaMapper);
					long tDel = System.nanoTime();
					int deleted = deleteOpponentH2hSnapBySeasonChunked(rtaMapper, seasonId);
					long tIns = System.nanoTime();
					rows[0] = rtaMapper.insertRtaSummonerOpponentH2hSnapForSeason(seasonId);
					log.debug("h2h snap replace seasonId={} deleted={} inserted={} delMs={} insMs={}",
							seasonId, deleted, rows[0], msSinceNanos(tDel), msSinceNanos(tIns));
				});
				return rows[0];
			} catch (Exception e) {
				if (!isH2hReplaceRetryable(e) || ++attempt >= 3) {
					log.error("replaceOpponentH2hSnap 실패 seasonId={} attempt={}", seasonId, attempt, e);
					throw e;
				}
				log.warn("replaceOpponentH2hSnap 재시도 {}/3 seasonId={}: {}", attempt, seasonId, e.getMessage());
				sleepQuiet(3_000 * attempt);
			}
		}
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
		int inserted = replaceOpponentH2hSnapForSeasons(rtaMapper, seasonIds);
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
		final int fightSnapUpserted;

		ChunkTotals(int monsterInserted, int pickTurnInserted, int ownedBoxUpserted, int fightSnapUpserted) {
			this.monsterInserted = monsterInserted;
			this.pickTurnInserted = pickTurnInserted;
			this.ownedBoxUpserted = ownedBoxUpserted;
			this.fightSnapUpserted = fightSnapUpserted;
		}
	}

	private ChunkTotals insertSummonerMonsterAndPickTurnSnapForSeasonChunked(RtaMapper rtaMapper, long seasonId,
			SummonerMonsterSnapPerfAccumulator perf) {
		if (summonerMonsterSnapReplayChunkSize < 100) {
			log.warn("summonerMonsterSnapReplayChunkSize={} 가 최솟값(100) 미만 — 100 으로 강제 적용",
					summonerMonsterSnapReplayChunkSize);
		}
		int limit = Math.max(100, summonerMonsterSnapReplayChunkSize);
		if (summonerMonsterSnapTxReplaySize < 50) {
			log.warn("summonerMonsterSnapTxReplaySize={} 가 최솟값(50) 미만 — 50 으로 강제 적용",
					summonerMonsterSnapTxReplaySize);
		}
		int txReplayLimit = Math.max(50, summonerMonsterSnapTxReplaySize);
		int monTotal = 0;
		int pickTurnTotal = 0;
		int ownedBoxTotal = 0;
		int fightTotal = 0;
		long afterExclusive = -1L;
		while (true) {
			long tk = System.nanoTime();
			List<Long> rids = rtaMapper.selectReplayIdsForSummonerMonsterSnapKeyset(seasonId, afterExclusive, limit);
			perf.addChunkKeysetSelectMs(msSinceNanos(tk));
			if (rids == null || rids.isEmpty()) {
				break;
			}
			for (List<Long> subRids : partitionReplayIds(rids, txReplayLimit)) {
				int[] mon = { 0 };
				int[] pick = { 0 };
				int[] box = { 0 };
				int[] fight = { 0 };
				executeSummonerSnapSubChunkTransaction(rtaMapper, seasonId, subRids, mon, pick, box, fight, perf);
				perf.incrementChunkIterations();
				monTotal += mon[0];
				pickTurnTotal += pick[0];
				ownedBoxTotal += box[0];
				fightTotal += fight[0];
			}
			if (rids.size() < limit) {
				break;
			}
			afterExclusive = rids.get(rids.size() - 1);
		}
		return new ChunkTotals(monTotal, pickTurnTotal, ownedBoxTotal, fightTotal);
	}

	private void executeSummonerSnapSubChunkTransaction(RtaMapper rtaMapper, long seasonId, List<Long> subRids,
			int[] mon, int[] pick, int[] box, int[] fight, SummonerMonsterSnapPerfAccumulator perf) {
		transactionTemplate.executeWithoutResult(status -> {
			applyBatchTxSessionGuards(rtaMapper);
			long t1 = System.nanoTime();
			rtaMapper.ensureSummonerSnapChunkTempStaging();
			rtaMapper.insertStagingRtaSummonerSnapRidBatch(seasonId, subRids);
			perf.addChunkStagingMs(msSinceNanos(t1));

			t1 = System.nanoTime();
			mon[0] = rtaMapper.insertRtaSummonerMonsterSnapForSeasonReplayChunk(seasonId);
			perf.addChunkMonsterSnapMs(msSinceNanos(t1));

			t1 = System.nanoTime();
			pick[0] = rtaMapper.insertRtaSummonerPickTurnSnapForSeasonReplayChunk(seasonId);
			perf.addChunkPickTurnSnapMs(msSinceNanos(t1));

			t1 = System.nanoTime();
			box[0] = rtaMapper.mergeRtaSummonerOwnedBoxSnapFromStagingReplayChunk(seasonId);
			perf.addChunkOwnedBoxMergeMs(msSinceNanos(t1));

			t1 = System.nanoTime();
			fight[0] = rtaMapper.insertRtaSummonerFightSnapForStagingReplays(seasonId);
			perf.addChunkFightSnapMs(msSinceNanos(t1));

			rtaMapper.insertRtaMatchFlatForStagingReplays(seasonId);

			t1 = System.nanoTime();
			rtaMapper.markSummonerRankingAggDoneForStagingSeason(seasonId);
			perf.addChunkMarkDoneMs(msSinceNanos(t1));
		});
	}

	private static List<List<Long>> partitionReplayIds(List<Long> rids, int maxSize) {
		if (rids == null || rids.isEmpty()) {
			return List.of();
		}
		if (rids.size() <= maxSize) {
			return List.of(rids);
		}
		List<List<Long>> parts = new ArrayList<>((rids.size() + maxSize - 1) / maxSize);
		for (int i = 0; i < rids.size(); i += maxSize) {
			parts.add(rids.subList(i, Math.min(i + maxSize, rids.size())));
		}
		return parts;
	}

	/**
	 * {@code rta_agg_monster_stats_tier_top_snap}: 시즌 전체 합산 솔/듀/트 각 상위 100(티어 컬럼 없음).
	 * 원천 {@code rta_agg_synergy_*} — {@link com.smw.monster.batch.RtaMonsterStatsTierTopSnapJob}(권장 1h)에서 호출;
	 * 시너지 집계가 먼저 반영돼 있어야 함.
	 */
	public MonsterStatsTierTopSnapRebuildResult rebuildMonsterStatsTierTopSnap(RtaMapper rtaMapper) {
		Long seasonId = rtaMapper.selectDefaultSeasonIdForNow();
		if (seasonId == null) {
			log.warn("rebuildMonsterStatsTierTopSnap: 현재 시즌 없음");
			return new MonsterStatsTierTopSnapRebuildResult(0);
		}
		long sid = seasonId.longValue();
		// DELETE 를 별도 짧은 트랜잭션으로 분리 — BATCH executor 에서 커밋 직전 flush 시 락 경합을 최소화.
		// snap 테이블이라 DELETE~INSERT 사이 빈 구간은 허용 가능.
		transactionTemplate.executeWithoutResult(status ->
				rtaMapper.deleteRtaMonsterStatsTierTopSnapBySeason(sid));
		AtomicInteger total = new AtomicInteger(0);
		transactionTemplate.executeWithoutResult(status -> {
			int n = 0;
			n += rtaMapper.insertRtaMonsterStatsTierTopSoloSnapForSeason(sid, monsterStatsMinPickCount);
			n += rtaMapper.insertRtaMonsterStatsTierTopDuoSnapForSeason(sid, monsterStatsMinPickCount);
			n += rtaMapper.insertRtaMonsterStatsTierTopTrioSnapForSeason(sid, monsterStatsMinPickCount);
			total.addAndGet(n);
		});
		return new MonsterStatsTierTopSnapRebuildResult(total.get());
	}

	/**
	 * 원본 스테이징 미적용 건을 정규화 테이블로 반영한다.
	 * {@link summonerswarService#applyPendingArenaReplayRawFromDb()} 는 빈 조회가 나올 때까지
	 * {@code max-rows-per-run} 행 단위 조회를 {@code max-batches-per-job} 회 이내에서 반복한다.
	 * 회당 상한·라운드 상한 후 잔여는 다음 스케줄 또는 수동 재실행에서 처리된다.
	 */
	public RawApplyDrainResult drainReplayRawPending(summonerswarService service) {
		return drainReplayRawPending(service, 0);
	}

	/**
	 * 원본 스테이징 미적용 건을 정규화 테이블로 반영한다.
	 * {@code maxBatchesOverride > 0} 이면 해당 값을 Job 당 라운드 상한으로 사용( backlog catch-up ).
	 */
	public RawApplyDrainResult drainReplayRawPending(summonerswarService service, int maxBatchesOverride) {
		int totalApplied = service.applyPendingArenaReplayRawFromDb(maxBatchesOverride);
		String stopReason = totalApplied == 0 ? "적용할 raw 없음" : "완료";
		return new RawApplyDrainResult(totalApplied, stopReason);
	}

	/**
	 * {@code rta_match.synergy_applied_at IS NULL} 인 rid 를 배치 단위로
	 * {@code rta_agg_synergy_solo/duo/trio} 및 {@code rta_agg_counter_solo/duo/trio}에 반영한다.
	 * 완료 시 {@code synergy_apply_result='S'}.
	 * {@code maxRoundsPerJob > 0} 이면 그 횟수만큼만 라운드 후 종료하고, 잔여 pending 은 다음 실행에서 처리한다.
	 *
	 * @param maxRoundsPerJob {@code <= 0} 이면 pending 소진까지 반복
	 * @param pauseMsBetweenRounds 라운드 사이 대기(ms), 0 이면 생략
	 * @param maxWallClockMsPerJob {@code > 0} 이면 벽시계 상한(ms) 초과 시 종료, {@code <= 0} 이면 제한 없음
	 */
	public SynergyDrainResult drainSynergyPending(
			RtaMapper rtaMapper,
			RtaSynergyAggService synergyAggService,
			RtaCacheEvictor cacheEvictor,
			int batchSize,
			boolean evictCachesEachRound,
			int pauseMsBetweenRounds,
			int maxRoundsPerJob,
			long maxWallClockMsPerJob) {
		int rounds = 0;
		int totalOk = 0;
		int totalFail = 0;
		boolean capped = maxRoundsPerJob > 0;
		final long wallDeadlineNanos = maxWallClockMsPerJob > 0
				? System.nanoTime() + maxWallClockMsPerJob * 1_000_000L
				: 0L;

		// idx_rta_match_synergy_pending(partial index) 강제 사용 — Seq Scan 방지
		rtaMapper.hintBatchDisableSeqScan();
		try {
			String stopReason = "완료";
			while (true) {
				if (wallDeadlineNanos > 0L && System.nanoTime() >= wallDeadlineNanos) {
					stopReason = "실행 시간 상한 도달 — 잔여는 다음 스케줄에서 처리";
					break;
				}
				List<Long> rids = rtaMapper.selectPendingSynergyAggRids(batchSize);
				if (rids == null || rids.isEmpty()) {
					stopReason = rounds == 0 ? "시너지 pending 없음" : "pending 소진";
					break;
				}
				long roundStart = System.nanoTime();
				RtaSynergyAggService.SynergyBatchApplyResult batch = synergyAggService.applySynergyBatch(rids);
				int ok = batch.ok();
				totalOk += ok;
				totalFail += batch.fail();
				rounds++;
				log.debug("[synergy-drain] round={} rids={} ok={} {}ms", rounds, rids.size(), ok, msSinceNanos(roundStart));

				if (evictCachesEachRound && ok > 0) {
					cacheEvictor.evictAllRtaCaches();
				}

				if (pauseMsBetweenRounds > 0) {
					sleepQuiet(pauseMsBetweenRounds);
				}
				if (capped && rounds >= maxRoundsPerJob) {
					stopReason = "라운드 상한 도달 — 잔여는 다음 스케줄에서 처리";
					break;
				}
			}
			return new SynergyDrainResult(rounds, totalOk, totalFail, stopReason);
		} finally {
			// 세션 플래너 설정 복원 — 예외 발생 시에도 반드시 복원
			rtaMapper.hintBatchEnableSeqScan();
		}
	}

	/** no-op — 몬스터 통계는 {@code rta_agg_synergy_solo/duo/trio} 집계 테이블에서 직접 조회. */
	public MonsterStatsRebuildResult rebuildMonsterStatsAgg(RtaMapper rtaMapper) {
		return new MonsterStatsRebuildResult(0, 0);
	}

	/**
	 * 티어 일별 집계 상한: {@code rta_match} 최신 {@code played_at} 정시에서 1시간 전까지.
	 * {@code playedBeforeExclusive} 미만 participant만 집계(진행 중인 최신 정시 버킷 제외).
	 */
	public record TierDailyDataWindow(
			Instant maxPlayedAt,
			Instant tierThroughAt,
			Instant playedBeforeExclusive) {
	}

	public Optional<TierDailyDataWindow> resolveTierDailyDataWindow(RtaMapper rtaMapper, long seasonId) {
		Instant maxPlayed = rtaMapper.selectMaxRtaMatchPlayedAt(seasonId);
		if (maxPlayed == null) {
			return Optional.empty();
		}
		ZonedDateTime latestHour = ZonedDateTime.ofInstant(maxPlayed, KST).truncatedTo(ChronoUnit.HOURS);
		Instant playedBeforeExclusive = latestHour.toInstant();
		Instant tierThroughAt = latestHour.minusHours(1).toInstant();
		return Optional.of(new TierDailyDataWindow(maxPlayed, tierThroughAt, playedBeforeExclusive));
	}

	/**
	 * 시즌별 {@code rta_agg_tier_daily} 전량 재적재.
	 * <p>
	 * 당일만 MERGE하는 증분이 아니라, 시즌마다 해당 {@code season_id} 행을 모두 DELETE 한 뒤
	 * {@code rta_season} 기준(서울) 일자 구간에 대해 누적 집계를 처음부터 다시 넣는다.
	 * 집계 participant 상한은 {@link #resolveTierDailyDataWindow} — 최신 {@code played_at} 정시−1h.
	 */
	public TierDailyAggRebuildResult rebuildTierAggDaily(RtaMapper rtaMapper) {
		Long seasonId = rtaMapper.selectDefaultSeasonIdForNow();
		if (seasonId == null) {
			log.warn("rebuildTierAggDaily: 현재 시즌 없음 — rta_season 에 is_active=true 또는 등록된 시즌이 있는지 확인");
			return new TierDailyAggRebuildResult(0, 0L, 0L, -1L);
		}
		Optional<TierDailyDataWindow> window = resolveTierDailyDataWindow(rtaMapper, seasonId.longValue());
		if (window.isEmpty()) {
			log.warn("rebuildTierAggDaily: rta_match 데이터 없음 seasonId={}", seasonId);
			return new TierDailyAggRebuildResult(0, 0L, 0L, seasonId);
		}
		long t0 = System.nanoTime();
		long sid = seasonId.longValue();
		rtaMapper.deleteRtaAggTierDailyForSeason(sid);
		rtaMapper.insertRtaTierAggDailyForSeason(sid, window.get().playedBeforeExclusive());
		long rows = rtaMapper.countRtaAggTierDailyForSeason(sid);
		long ms = msSinceNanos(t0);
		return new TierDailyAggRebuildResult(rows, ms, ms, sid);
	}

	/**
	 * 시즌×티어 총 경기 수({@code rta_agg_season_rating_match_total}) 재집계 후
	 * 시간별 랭크 컷 스냅({@code rta_agg_rank_cut_hourly_snap}) 적재 및 구 데이터 정리.
	 */
	public RankCutSnapshotRebuildResult rebuildRankCutSnapshots(RtaMapper rtaMapper) {
		Long defaultSid = rtaMapper.selectDefaultSeasonIdForNow();
		if (defaultSid == null) {
			log.warn("rebuildRankCutSnapshots: 현재 시즌 없음");
			return RankCutSnapshotRebuildResult.empty();
		}
		long sid = defaultSid.longValue();

		java.util.Map<Integer, Long> matchTotalsBefore = rankCutSnapValidator.loadMatchTotals(rtaMapper, sid);

		long matchTotalMs;
		if (shouldRebuildSeasonRatingMatchTotal(rtaMapper, sid)) {
			matchTotalMs = rebuildSeasonRatingMatchTotalWithRetry(rtaMapper, sid);
		} else {
			matchTotalMs = 0L;
			log.debug("rebuildRankCutSnapshots: match_total 생략(participant 변경 없음) seasonId={}", sid);
		}

		RtaRankCutSnapValidator.RtaRankCutValidationReport validationReport =
				rankCutSnapValidator.validateMatchTotalRebuild(sid, matchTotalsBefore,
						rankCutSnapValidator.loadMatchTotals(rtaMapper, sid));

		int hourLimit = Math.max(1, rtaBatchProperties.getRankCutMissingHoursPerRun());
		long t0 = System.nanoTime();
		List<Instant> missingHours = rtaMapper.selectMissingRankCutSnapHours(sid, hourLimit);
		log.debug("랭크컷 스냅 누락 시간대 seasonId={} count={} (1회 상한 {}시간)", sid, missingHours.size(), hourLimit);
		for (Instant hour : missingHours) {
			long matchCount = rtaMapper.countRtaMatchForHour(sid, hour);
			if (matchCount == 0) {
				log.debug("랭크컷 스냅 스킵 (rta_match 경기 없음) seasonId={} hour={}", sid, hour);
				continue;
			}
			List<com.smw.rta.model.RtaRankCutSnapRow> rows = rtaMapper.selectRankCutSnapsForHour(sid, hour);
			if (rows.isEmpty()) {
				log.debug("랭크컷 스냅 스킵 (경기 없음) seasonId={} hour={}", sid, hour);
				continue;
			}

			validationReport = validationReport.merge(
					rankCutSnapValidator.validateHourlySnap(rtaMapper, sid, hour, rows));

			int attempt = 0;
			while (true) {
				try {
					final List<com.smw.rta.model.RtaRankCutSnapRow> r = rows;
					int[] result = {0};
					transactionTemplate.executeWithoutResult(tx -> {
						applyBatchTxSessionGuards(rtaMapper);
						result[0] = rtaMapper.insertRtaRankCutHourlySnaps(sid, hour, r);
					});
					log.debug("랭크컷 스냅 적재 완료 seasonId={} hour={} inserted={}", sid, hour, result[0]);
					break;
				} catch (Exception e) {
					if (!isInfraRetryable(e) || ++attempt >= 3) {
						log.error("랭크컷 스냅 적재 실패 seasonId={} hour={} attempt={}", sid, hour, attempt, e);
						throw e;
					}
					log.warn("랭크컷 스냅 적재 인프라 재시도 {}/3 seasonId={} hour={}: {}",
							attempt, sid, hour, e.getMessage());
					sleepQuiet(3_000 * attempt);
				}
			}
		}
		rtaMapper.pruneRtaRankCutHourlySnap();
		long hourlyMs = msSinceNanos(t0);

		rankCutSnapValidator.notifyIfNeeded(validationReport);

		long matchTotalRows = safeCount(rtaMapper.countRtaSeasonRatingMatchTotalRows());
		return new RankCutSnapshotRebuildResult(
				matchTotalRows,
				matchTotalMs,
				hourlyMs,
				validationReport.anomalyCount(),
				validationReport.formatSamples(15));
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

	private boolean shouldRebuildSeasonRatingMatchTotal(RtaMapper rtaMapper, long seasonId) {
		if (!rtaBatchProperties.isSkipSeasonRatingMatchTotalIfFresh()) {
			return true;
		}
		try {
			return rtaMapper.existsParticipantPlayedAfterMatchTotalComputed(seasonId);
		} catch (Exception e) {
			log.warn("match_total 신선도 조회 실패 — 재집계 수행 seasonId={}: {}", seasonId, e.getMessage());
			return true;
		}
	}

	/**
	 * {@code rta_agg_season_rating_match_total} UPSERT — 시너지·몬스터 집계와 락이 겹치면
	 * Hikari {@code lock_timeout}(기본 120s) 초과 시 {@code CannotAcquireLockException} 발생.
	 */
	private long rebuildSeasonRatingMatchTotalWithRetry(RtaMapper rtaMapper, long seasonId) {
		int attempt = 0;
		while (true) {
			long t0 = System.nanoTime();
			try {
				transactionTemplate.executeWithoutResult(tx -> {
					applyBatchTxSessionGuards(rtaMapper);
					rtaMapper.rebuildRtaSeasonRatingMatchTotal(seasonId);
				});
				return msSinceNanos(t0);
			} catch (Exception e) {
				if (!isInfraRetryable(e) || ++attempt >= 3) {
					log.error("rebuildRtaSeasonRatingMatchTotal 실패 seasonId={} attempt={}", seasonId, attempt, e);
					throw e;
				}
				log.warn("rebuildRtaSeasonRatingMatchTotal 인프라 재시도 {}/3 seasonId={}: {}",
						attempt, seasonId, e.getMessage());
				sleepQuiet(3_000 * attempt);
			}
		}
	}

	/** H2H DELETE+INSERT 재시도 — 인프라 오류·PK 중복(잔여 행·INSERT 내 중복 키) 포함. */
	private static boolean isH2hReplaceRetryable(Throwable t) {
		if (isInfraRetryable(t)) {
			return true;
		}
		for (Throwable c = t; c != null; c = c.getCause()) {
			String cn = c.getClass().getName();
			if (cn.contains("DuplicateKeyException")) {
				return true;
			}
			if (c instanceof java.sql.SQLException sql && "23505".equals(sql.getSQLState())) {
				return true;
			}
			String msg = c.getMessage();
			if (msg != null) {
				String lower = msg.toLowerCase();
				if (lower.contains("duplicate key")
						|| lower.contains("unique constraint")
						|| lower.contains("cannot affect row a second time")) {
					return true;
				}
			}
		}
		return false;
	}

	/** 락·커넥션 종료·idle-in-tx 타임아웃 등 인프라 일시 오류(재시도 대상). */
	private static boolean isInfraRetryable(Throwable t) {
		for (Throwable c = t; c != null; c = c.getCause()) {
			String cn = c.getClass().getName();
			if (cn.contains("CannotAcquireLockException")
					|| cn.contains("PessimisticLockingFailureException")
					|| cn.contains("DeadlockLoserDataAccessException")
					|| cn.contains("TransactionSystemException")
					|| cn.contains("DataAccessResourceFailureException")
					|| cn.contains("ConnectionClosed")
					|| cn.contains("ConnectionIsClosedException")) {
				return true;
			}
			if (c instanceof java.sql.SQLException sql) {
				String state = sql.getSQLState();
				if ("55P03".equals(state) || "40P01".equals(state)) {
					return true;
				}
			}
			String msg = c.getMessage();
			if (msg != null) {
				String lower = msg.toLowerCase();
				if (lower.contains("lock timeout")
						|| lower.contains("deadlock")
						|| lower.contains("could not obtain lock")
						|| lower.contains("canceling statement due to lock")
						|| lower.contains("idle_in_transaction_session_timeout")
						|| lower.contains("connection closed")
						|| lower.contains("jdbc rollback failed")
						|| lower.contains("i/o error")
						|| lower.contains("sending to the backend")
						|| lower.contains("socket closed")
						|| lower.contains("broken pipe")) {
					return true;
				}
			}
		}
		return false;
	}

	// ── 몬스터 일별·슬롯 집계 ────────────────────────────────────────────

	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	/**
	 * 시즌 시작일 ~ 오늘까지 {@code rta_agg_monster_daily_snap}에서 누락된 날짜만 적재.
	 * 이미 데이터가 있는 날짜는 건너뛰므로 멱등(idempotent)하고 재실행 부담이 작다.
	 *
	 * @return 새로 적재된 (시즌, 날짜) 쌍의 수
	 */
	public int rebuildMonsterDailySnap(RtaMapper mapper) {
		List<Map<String, Object>> seasons = mapper.selectParticipantSeasonsWithStart();
		if (seasons == null || seasons.isEmpty()) {
			return 0;
		}
		int totalInserted = 0;
		for (Map<String, Object> row : seasons) {
			long seasonId = ((Number) row.get("season_id")).longValue();
			Object startDateObj = row.get("start_date");
			if (startDateObj == null) continue;
			String fromDate = startDateObj.toString();
			List<String> missingDates = mapper.selectMissingMonsterDailySnapDates(seasonId, fromDate);
			if (missingDates == null || missingDates.isEmpty()) continue;
			int filledDates = 0;
			for (String snapDate : missingDates) {
				try {
					transactionTemplate.executeWithoutResult(tx ->
						mapper.insertRtaMonsterDailySnapForDate(seasonId, snapDate)
					);
					filledDates++;
				} catch (Exception e) {
					log.error("rebuildMonsterDailySnap: 날짜 적재 실패 — seasonId={} date={}", seasonId, snapDate, e);
				}
			}
			totalInserted += filledDates;
			log.debug("rebuildMonsterDailySnap: seasonId={}, missingDates={}, filled={}", seasonId, missingDates.size(), filledDates);
		}
		log.debug("rebuildMonsterDailySnap done: totalInserted={}", totalInserted);
		return totalInserted;
	}

	/**
	 * {@code rta_agg_summoner_score_daily_snap} — 시즌 시작~오늘(KST) 중 스냅이 없는 모든 일자를
	 * 소급 적재하고(경기 없는 날 포함), 오늘·어제(KST)는 매 실행마다 UPSERT(당일 점수 갱신).
	 * 당일 경기 없는 소환사는 match_cnt=0, end_of_day_score=직전 점수로 carry-forward.
	 *
	 * @return 새로/재적재한 (시즌, 일자) 처리 횟수
	 */
	public int rebuildSummonerScoreDailySnap(RtaMapper mapper) {
		List<Map<String, Object>> seasons = mapper.selectParticipantSeasonsWithStart();
		if (seasons == null || seasons.isEmpty()) {
			return 0;
		}
		int totalRuns = 0;
		for (Map<String, Object> row : seasons) {
			long seasonId = ((Number) row.get("season_id")).longValue();
			Object startDateObj = row.get("start_date");
			if (startDateObj == null) {
				continue;
			}
			String fromDate = startDateObj.toString();
			// 시즌 시작~어제(KST) 중 스냅 없는 날짜만 순서대로 처리
			List<String> missingDates = mapper.selectMissingSummonerScoreDailySnapDates(seasonId, fromDate);
			java.util.LinkedHashSet<String> datesToFill = new java.util.LinkedHashSet<>();
			if (missingDates != null) {
				datesToFill.addAll(missingDates);
			}
			int filled = 0;
			for (String snapDate : datesToFill) {
				try {
					transactionTemplate.executeWithoutResult(tx ->
							mapper.insertRtaSummonerScoreDailySnapForDate(seasonId, snapDate));
					filled++;
				} catch (Exception e) {
					log.error("rebuildSummonerScoreDailySnap: 적재 실패 seasonId={} date={}", seasonId, snapDate, e);
				}
			}
			totalRuns += filled;
			log.debug("rebuildSummonerScoreDailySnap: seasonId={}, datesProcessed={} (missing={})",
					seasonId, filled, missingDates != null ? missingDates.size() : 0);
		}
		log.debug("rebuildSummonerScoreDailySnap done: totalDateRuns={}", totalRuns);
		return totalRuns;
	}

	/**
	 * 모든 활성 시즌의 {@code rta_agg_monster_pick_slot_snap} 재적재.
	 * rating_id=-1(전 티어) 만 적재.
	 */
	public int rebuildMonsterPickSlotSnap(RtaMapper mapper) {
		List<Long> seasonIds = mapper.selectDistinctParticipantSeasonIds();
		if (seasonIds == null || seasonIds.isEmpty()) {
			return 0;
		}
		for (Long seasonId : seasonIds) {
			transactionTemplate.executeWithoutResult(tx -> {
				mapper.deleteRtaMonsterPickSlotSnapBySeason(seasonId, -1);
				mapper.insertRtaMonsterPickSlotSnapForSeason(seasonId, -1);
			});
		}
		log.debug("rebuildMonsterPickSlotSnap: seasonCount={}", seasonIds.size());
		return seasonIds.size();
	}

	/**
	 * pick_slot_snap incremental drain —
	 * {@code rta_match.pick_slot_snap_applied_at IS NULL} 인 rid 를 청크 단위로 처리.
	 * @return 처리된 총 rid 수
	 */
	public int drainPickSlotSnap(RtaMapper mapper, int chunkSize) {
		return drainPickSlotSnap(mapper, chunkSize, 0);
	}

	/**
	 * pick_slot_snap incremental drain.
	 * @param maxRoundsPerJob {@code <= 0} 이면 pending 소진까지, {@code > 0} 이면 라운드 상한.
	 */
	public int drainPickSlotSnap(RtaMapper mapper, int chunkSize, int maxRoundsPerJob) {
		int total = 0;
		int rounds = 0;
		boolean capped = maxRoundsPerJob > 0;
		while (true) {
			List<Long> rids = mapper.selectPendingPickSlotSnapRids(chunkSize);
			if (rids == null || rids.isEmpty()) break;

			long[] ridArr = rids.stream().mapToLong(Long::longValue).toArray();
			try {
				transactionTemplate.executeWithoutResult(tx -> {
					mapper.insertPickSlotSnapForRids(ridArr);
					mapper.markPickSlotSnapDoneForRids(ridArr);
				});
				total += rids.size();
				rounds++;
				log.debug("drainPickSlotSnap: chunk={}, totalSoFar={}, round={}", rids.size(), total, rounds);
			} catch (Exception e) {
				log.error("drainPickSlotSnap: chunk 처리 실패, 실패 마킹 후 중단. rids[0]={}", ridArr[0], e);
				try {
					mapper.markPickSlotSnapFailedForRids(ridArr);
				} catch (Exception me) {
					log.error("drainPickSlotSnap: 실패 마킹도 실패", me);
				}
				break;
			}

			if (capped && rounds >= maxRoundsPerJob) {
				log.debug("drainPickSlotSnap: 라운드 상한 {} 도달 — 잔여는 다음 실행에서 처리", maxRoundsPerJob);
				break;
			}
			if (rids.size() < chunkSize) break;
		}
		return total;
	}

	/**
	 * 몬스터별 장인 랭킹 스냅({@code rta_agg_monster_top_summoner_snap}) 재적재.
	 * 시즌별 DELETE 후 INSERT — rta_agg_summoner_monster_snap 집계 기반.
	 */
	public int rebuildMonsterTopSummonerSnap(RtaMapper mapper, int minPickCnt, int topN) {
		List<Long> seasonIds = mapper.selectDistinctParticipantSeasonIds();
		if (seasonIds == null || seasonIds.isEmpty()) {
			return 0;
		}
		int total = 0;
		for (Long seasonId : seasonIds) {
			final long sid = seasonId;
			try {
				transactionTemplate.executeWithoutResult(tx -> {
					mapper.deleteRtaMonsterTopSummonerSnapBySeason(sid);
					mapper.insertRtaMonsterTopSummonerSnapForSeason(sid, minPickCnt, topN);
				});
				total++;
			} catch (Exception e) {
				log.error("rebuildMonsterTopSummonerSnap: 실패 — seasonId={}", sid, e);
			}
		}
		log.debug("rebuildMonsterTopSummonerSnap done: seasonCount={}", total);
		return total;
	}

	public record MonsterDailySnapRebuildResult(int seasonCount, int days) {
	}

	/** @param rankingRows {@code rta_agg_summoner_ranking_snap} 합, @param searchRows {@code rta_agg_summoner_search_snap} 합 */
	public record SummonerRankingRebuildResult(int rankingRows, int searchRows, long rankingMs, long searchMs, long wallMs) {
	}

	/**
	 * @param fightRows {@code rta_agg_summoner_season_fight_snap} 합(적재 루프 합이 아닌 전체 count),
	 * @param monsterRows {@code INSERT rta_agg_summoner_monster_snap} 청크 적재가 반환한 행 합(근사),
	 * @param pickTurnRows 픽턴(선후 라인) 소환사 스냅 청크 행 합(근사),
	 * @param ownedBoxUpsertRows {@code mergeRtaSummonerOwnedBoxSnapFromStagingReplayChunk} 의 JDBC 영향 행 합(MERGE 포함),
	 * @param opponentH2hInsertRows {@code insertRtaSummonerOpponentH2hSnapForSeason} 시즌별 합(이번 실행에서 청크 완료 직후 갱신),
	 * @param seasonsWithPendingWork 이번 실행에서 분모·청크·H2H 를 돌린 시즌 ID 목록(미처리 매치가 없으면 빈 목록).
	 * @param perfSummary 운영 검토용 — 구간별 누적 ms 텍스트. {@code sys_batch_run_his.rslt_txt} 에 함께 저장된다.
	 */
	public record SummonerMonsterSnapRebuildResult(
			int fightRows,
			int monsterRows,
			int pickTurnRows,
			int ownedBoxUpsertRows,
			int opponentH2hInsertRows,
			List<Long> seasonsWithPendingWork,
			String perfSummary) {
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

	/**
	 * @param wallMs         전체 소요(ms)
	 * @param maxSeasonMs    시즌 중 가장 오래 걸린 소요(ms)
	 * @param slowestSeasonId 가장 오래 걸린 시즌 ID (-1 이면 처리 시즌 없음)
	 */
	public record TierDailyAggRebuildResult(long totalRows, long wallMs, long maxSeasonMs, long slowestSeasonId) {
	}

	/**
	 * @param matchTotalMs {@code rebuildRtaSeasonRatingMatchTotal} 소요(ms)
	 * @param anchorMs     {@code insertRtaRankCutoffAnchorSnapFromLive} 소요(ms)
	 * @param snapshotMs   {@code insertRtaSnapshotRankCutForAllSeasons} 소요(ms)
	 */
	public record RankCutSnapshotRebuildResult(
			long matchTotalRows,
			long matchTotalMs,
			long hourlyMs,
			int validationAnomalyCount,
			List<String> validationSamples) {

		public static RankCutSnapshotRebuildResult empty() {
			return new RankCutSnapshotRebuildResult(0L, 0L, 0L, 0, List.of());
		}
	}

	private static long msSinceNanos(long startNanos) {
		return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
	}

	/**
	 * {@link #rebuildSummonerMonsterSnapAgg} 단계별 누적 시간(ms). 요약 문자열은 {@code sys_batch_run_his.rslt_txt}(배치 로그)에 남김.
	 */
	public static final class SummonerMonsterSnapPerfAccumulator {
		private final long wallStartNs = System.nanoTime();
		private final long pendingSeasonsQueryMs;
		private final int targetSeasonCount;
		private long fightSnapUpsertSumMs;
		private int chunkIterations;
		private long chunkKeysetSumMs;
		private long chunkStagingSumMs;
		private long chunkMonsterSnapSumMs;
		private long chunkPickTurnSumMs;
		private long chunkOwnedBoxMergeSumMs;
		private long chunkFightSnapSumMs;
		private long chunkMarkDoneSumMs;
		private long h2hDeleteInsertSumMs;

		public SummonerMonsterSnapPerfAccumulator(long pendingSeasonsQueryMs, int targetSeasonCount) {
			this.pendingSeasonsQueryMs = pendingSeasonsQueryMs;
			this.targetSeasonCount = targetSeasonCount;
		}

		public static String formatNoPendingWork(long pendingQueryMs) {
			String nl = System.lineSeparator();
			return "--- PERF rebuildSummonerMonsterSnapAgg (ms) ---" + nl
					+ "pending_seasons_query_ms=" + pendingQueryMs + nl + "status=no_pending" + nl;
		}

		void addFightSnapUpsertMs(long ms) {
			fightSnapUpsertSumMs += ms;
		}

		void addH2hDeleteInsertMs(long ms) {
			h2hDeleteInsertSumMs += ms;
		}

		void addChunkKeysetSelectMs(long ms) {
			chunkKeysetSumMs += ms;
		}

		void addChunkStagingMs(long ms) {
			chunkStagingSumMs += ms;
		}

		void addChunkMonsterSnapMs(long ms) {
			chunkMonsterSnapSumMs += ms;
		}

		void addChunkPickTurnSnapMs(long ms) {
			chunkPickTurnSumMs += ms;
		}

		void addChunkOwnedBoxMergeMs(long ms) {
			chunkOwnedBoxMergeSumMs += ms;
		}

		void addChunkFightSnapMs(long ms) {
			chunkFightSnapSumMs += ms;
		}

		void addChunkMarkDoneMs(long ms) {
			chunkMarkDoneSumMs += ms;
		}

		void incrementChunkIterations() {
			chunkIterations++;
		}

		public String toSummaryBlock() {
			long wallMs = (System.nanoTime() - wallStartNs) / 1_000_000L;
			String nl = System.lineSeparator();
			StringBuilder sb = new StringBuilder(720);
			sb.append("--- PERF rebuildSummonerMonsterSnapAgg (ms, 합계) ---").append(nl);
			sb.append("wall_ms=").append(wallMs).append(nl);
			sb.append("target_seasons=").append(targetSeasonCount).append(nl);
			sb.append("pending_seasons_query_ms=").append(pendingSeasonsQueryMs).append(nl);
			sb.append("fight_snap_upsert_sum_ms=").append(fightSnapUpsertSumMs).append(nl);
			sb.append("h2h_delete_insert_sum_ms=").append(h2hDeleteInsertSumMs).append(nl);
			if (targetSeasonCount > 0) {
				sb.append("h2h_avg_per_season_ms=").append(h2hDeleteInsertSumMs / targetSeasonCount).append(nl);
			}
			sb.append("chunk_iterations=").append(chunkIterations).append(nl);
			sb.append("chunk_keyset_select_sum_ms=").append(chunkKeysetSumMs).append(nl);
			sb.append("chunk_staging_truncate_insert_sum_ms=").append(chunkStagingSumMs).append(nl);
			sb.append("chunk_monster_snap_insert_sum_ms=").append(chunkMonsterSnapSumMs).append(nl);
			sb.append("chunk_pick_turn_snap_insert_sum_ms=").append(chunkPickTurnSumMs).append(nl);
			sb.append("chunk_owned_box_merge_sum_ms=").append(chunkOwnedBoxMergeSumMs).append(nl);
			sb.append("chunk_fight_snap_sum_ms=").append(chunkFightSnapSumMs).append(nl);
			sb.append("chunk_mark_done_sum_ms=").append(chunkMarkDoneSumMs).append(nl);
			if (chunkIterations > 0) {
				sb.append("chunk_avg_keyset_ms=").append(chunkKeysetSumMs / chunkIterations).append(nl);
				sb.append("chunk_avg_monster_snap_ms=").append(chunkMonsterSnapSumMs / chunkIterations).append(nl);
				sb.append("chunk_avg_pick_turn_ms=").append(chunkPickTurnSumMs / chunkIterations).append(nl);
				sb.append("chunk_avg_owned_box_merge_ms=").append(chunkOwnedBoxMergeSumMs / chunkIterations)
						.append(nl);
				sb.append("chunk_avg_fight_snap_ms=").append(chunkFightSnapSumMs / chunkIterations).append(nl);
				sb.append("chunk_avg_mark_done_ms=").append(chunkMarkDoneSumMs / chunkIterations).append(nl);
			}
			sb.append("hint: PG log_min_duration_statement 또는 chunk_* 항목별 EXPLAIN ANALYZE").append(nl);
			return sb.toString();
		}
	}
}
