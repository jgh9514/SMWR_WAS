package com.smw.rta.service;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.smw.rta.config.RtaBatchProperties;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.model.RtaCounterMatchupUpsertRow;
import com.smw.rta.model.RtaSynergyBanDeltaRow;
import com.smw.rta.model.RtaSynergyAggUpsertRow;
import com.smw.rta.util.PgJdbcUpdateCount;

import lombok.extern.slf4j.Slf4j;

@Service
@Primary
@Slf4j
public class RtaSynergyAggServiceImpl implements RtaSynergyAggService {

	/**
	 * PostgreSQL PreparedStatement 바인드 상한 65,535 — VALUES 행당 컬럼 수(시너지 6·카운터 7) 중 큰 쪽에 맞춤.
	 * (카운터 7열 → 65535/7 ≈ 9362)
	 */
	private static final int AGG_UPSERT_FLUSH_CHUNK = 9300;

	/** 이보다 많으면 완료 UPDATE 를 MyBatis {@code unnest} 대신 COPY→tmp_bulk_rids→JOIN (동일 트랜잭션 커넥션). */
	private static final int MARK_DONE_JDBC_MIN = 2048;

	/** 이 개수 이하이면 MyBatis 다건 UPSERT(소량·단건에 유리). 초과 시 COPY+스테이징(대량). */
	@Value("${smw.rta.counter-agg.legacy-upsert-max-rows:8000}")
	private int counterLegacyUpsertMaxRows;

	/** COPY+merge 를 이 행 수마다 끊어 실행 — 한 번에 수백만 행 merge 시 DB 가 멈춘 것처럼 보일 수 있음 */
	@Value("${smw.rta.counter-agg.copy-staging-chunk-rows:400000}")
	private int counterCopyStagingChunkRows;

	@Value("${smw.rta.counter-agg.use-copy-staging:true}")
	private boolean counterUseCopyStaging;

	@Value("${smw.rta.synergy-agg.legacy-upsert-max-rows:8000}")
	private int synergyLegacyUpsertMaxRows;

	/** COPY+merge 를 이 행 수마다 끊어 실행 — 600만 행을 단일 merge 하면 수십 분 걸릴 수 있음 */
	@Value("${smw.rta.synergy-agg.copy-staging-chunk-rows:600000}")
	private int synergyChunkRows;

	@Value("${smw.rta.synergy-agg.use-copy-staging:true}")
	private boolean synergyUseCopyStaging;

	private final RtaMapper rtaMapper;
	private final TransactionTemplate synergyOneRidTx;
	private final RtaCounterMatchupCopyStagingService counterCopyStagingService;
	private final RtaSynergyAggCopyStagingService synergyCopyStagingService;
	private final RtaBulkRidLookupService bulkRidLookupService;
	private final RtaSynergyBanCntBulkService synergyBanCntBulkService;
	private final DataSource dataSource;
	private final RtaBatchProperties rtaBatchProperties;

	public RtaSynergyAggServiceImpl(RtaMapper rtaMapper,
			@Qualifier("rtaJdbcTransactionManager") PlatformTransactionManager transactionManager,
			RtaCounterMatchupCopyStagingService counterCopyStagingService,
			RtaSynergyAggCopyStagingService synergyCopyStagingService,
			RtaBulkRidLookupService bulkRidLookupService,
			RtaSynergyBanCntBulkService synergyBanCntBulkService,
			DataSource dataSource,
			RtaBatchProperties rtaBatchProperties) {
		this.rtaMapper = rtaMapper;
		this.synergyOneRidTx = new TransactionTemplate(transactionManager);
		this.synergyOneRidTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.counterCopyStagingService = counterCopyStagingService;
		this.synergyCopyStagingService = synergyCopyStagingService;
		this.bulkRidLookupService = bulkRidLookupService;
		this.synergyBanCntBulkService = synergyBanCntBulkService;
		this.dataSource = dataSource;
		this.rtaBatchProperties = rtaBatchProperties;
	}

	@Override
	@Transactional(transactionManager = "rtaJdbcTransactionManager", propagation = Propagation.REQUIRES_NEW,
			rollbackFor = Exception.class)
	public void applyOneRid(long rid) {
		applyOneRidInternal(rid);
	}

	@Override
	@Transactional(transactionManager = "rtaJdbcTransactionManager", rollbackFor = Exception.class)
	public SynergyBatchApplyResult applySynergyBatch(List<Long> rids) {
		int ok = 0;
		if (rids == null || rids.isEmpty()) {
			return new SynergyBatchApplyResult(0, 0);
		}
		List<Long> ordered = new ArrayList<>();
		for (Long rid : rids) {
			if (rid != null) {
				ordered.add(rid);
			}
		}
		int n = ordered.size();
		// 초기 용량 사전 설정 → 리해시 제거 (기본 16에서 수백만 항목 → 20회+ 리해시 발생)
		Map<Long, Map<String, Object>> replayByRid = new HashMap<>(n * 4 / 3 + 1);
		Map<Long, List<Map<String, Object>>> unitsByRid = new HashMap<>(n * 4 / 3 + 1);
		Map<Long, List<Map<String, Object>>> ratingsByRid = new HashMap<>(n * 4 / 3 + 1);
		prefetchSynergyLookup(ordered, replayByRid, unitsByRid, ratingsByRid);

		// 경기당 고유 시너지 키 ~15개, 카운터 키 ~50개 추정 (4/3 = load factor 역수)
		Map<SynergyMergeKey, long[]> synAcc = new HashMap<>(n * 15 * 4 / 3 + 1);
		Map<SynergyMergeKey, Integer> synComboSizes = new HashMap<>(n * 15 * 4 / 3 + 1);
		Map<CounterMergeKey, long[]> cntAcc = new HashMap<>(n * 50 * 4 / 3 + 1);
		Map<CounterMergeKey, Integer> cntOppSizes = new HashMap<>(n * 50 * 4 / 3 + 1);

		List<Long> processed = new ArrayList<>(ordered.size());
		for (Long rid : ordered) {
			try {
				Map<String, Object> replay = replayByRid.get(rid);
				List<Map<String, Object>> raw = unitsByRid.get(rid);
				List<Map<String, Object>> ratingRows = ratingsByRid.getOrDefault(rid, Collections.emptyList());
				SynergyRidContext ctx = toSynergyRidContext(rid, replay, raw, ratingRows);
				BuiltRows built = buildAllRowsForContext(rid, ctx);
				accumulateSynergyAgg(synAcc, synComboSizes, built.synergy());
				accumulateCounterMatchup(cntAcc, cntOppSizes, built.counter());
				processed.add(rid);
				ok++;
			} catch (Exception e) {
				try {
					markSynergyFailedInNewTx(rid);
				} catch (Exception markEx) {
					log.warn("[rta-synergy] rid={} 실패 표시 중 오류: {}", rid, markEx.getMessage());
				}
				log.error("[rta-synergy] rid={} 시너지 집계 실패 — 통합 배치 중단", rid, e);
				throw new IllegalStateException("시너지 집계 실패 rid=" + rid + ": " + e.getMessage(), e);
			}
		}
		List<RtaSynergyAggUpsertRow> mergedSyn = synergyMapsToList(synAcc, synComboSizes);
		List<RtaCounterMatchupUpsertRow> mergedCnt = counterMapsToList(cntAcc, cntOppSizes);
		if (!processed.isEmpty() && mergedSyn.isEmpty()) {
			throw new IllegalStateException(
					"시너지 집계 행이 비어 있는데 처리된 rid 가 있음(버그·데이터 불일치) — 완료 표시 금지, processed=" + processed.size());
		}
		if (!processed.isEmpty() && mergedCnt.isEmpty()) {
			throw new IllegalStateException(
					"카운터 집계 행이 비어 있는데 처리된 rid 가 있음(버그·데이터 불일치) — 완료 표시 금지, processed=" + processed.size());
		}
		// COPY 전 인덱스 키 순 정렬 → B-tree 순차 프로브 → 버퍼 캐시 히트율 향상
		mergedSyn.sort(Comparator.comparingLong(RtaSynergyAggUpsertRow::getSeasonId)
				.thenComparingInt(RtaSynergyAggUpsertRow::getRatingId)
				.thenComparing(RtaSynergyAggUpsertRow::getComboKey));
		mergedCnt.sort(Comparator.comparingLong(RtaCounterMatchupUpsertRow::getSeasonId)
				.thenComparingInt(RtaCounterMatchupUpsertRow::getRatingId)
				.thenComparingLong(RtaCounterMatchupUpsertRow::getSubjectUnitId)
				.thenComparing(RtaCounterMatchupUpsertRow::getOpponentComboKey));
		// 병렬: staging 테이블은 분리됐으나, 동시 MERGE+ANALYZE+다른 세션(다른 Pod/로컬)과 rta_agg_* 락이 겹치면
		// lock_timeout 이 날 수 있음 — smw.rta.batch.parallel-synergy-counter-staging-flush=false 로 순차 flush.
		if (rtaBatchProperties.isParallelSynergyCounterStagingFlush()) {
			CompletableFuture<Void> synFlush = CompletableFuture.runAsync(() -> flushSynergyInChunks(mergedSyn));
			CompletableFuture<Void> cntFlush = CompletableFuture.runAsync(() -> flushCounterMatchupInChunks(mergedCnt));
			try {
				CompletableFuture.allOf(synFlush, cntFlush).join();
			} catch (CompletionException ce) {
				Throwable cause = ce.getCause();
				throw new IllegalStateException("병렬 flush 실패: " + cause.getMessage(), cause);
			}
		} else {
			flushSynergyInChunks(mergedSyn);
			flushCounterMatchupInChunks(mergedCnt);
		}
		flushSynergyBanDeltas(bulkRidLookupService.aggregateSynergyBanIncrements(ordered));
		int marked = markSynergyAggDoneForRidsAll(processed);
		long markedRows = PgJdbcUpdateCount.toLong(marked);
		if (marked >= 0 && markedRows != processed.size()) {
			log.warn("[rta-synergy] mark done expected={} actual={}", processed.size(), markedRows);
		} else if (marked < 0) {
			// PG JDBC 영향 행 수가 int 범위를 넘으면 음수로 보일 수 있음 — 실제 UPDATE 는 정상일 수 있음
			log.debug("[rta-synergy] mark done affected raw int overflow (logged as unsigned={}), rid count={}",
					markedRows, processed.size());
		}
		return new SynergyBatchApplyResult(ok, 0);
	}

	@Override
	public int markSynergyAggDoneForRidsBatched(List<Long> rids) {
		return markSynergyAggDoneForRidsAll(rids);
	}

	@Override
	@Transactional(transactionManager = "rtaJdbcTransactionManager", rollbackFor = Exception.class)
	public int rebuildPickTurnAgg() {
		List<Long> seasonIds = rtaMapper.selectDistinctParticipantSeasonIds();
		if (seasonIds == null || seasonIds.isEmpty()) {
			log.info("[rta-pick-turn] 집계 대상 시즌 없음");
			return 0;
		}
		for (Long seasonId : seasonIds) {
			long t0 = System.currentTimeMillis();
			rtaMapper.deleteRtaPickTurnAggBySeason(seasonId.longValue());
			int rows = rtaMapper.insertRtaPickTurnAggForSeason(seasonId.longValue());
			log.info("[rta-pick-turn] seasonId={} upserted={} {}ms", seasonId, rows, System.currentTimeMillis() - t0);
		}
		return seasonIds.size();
	}

	/**
	 * 소량은 ANY(bigint[]), 대량은 COPY→tmp_bulk_rids→UPDATE … FROM (동일 트랜잭션 커넥션).
	 */
	private int markSynergyAggDoneForRidsAll(List<Long> rids) {
		if (rids == null || rids.isEmpty()) {
			return 0;
		}
		if (rids.size() <= MARK_DONE_JDBC_MIN) {
			return rtaMapper.markSynergyAggDoneForRids(toLongArray(rids, 0, rids.size()));
		}
		Connection conn = DataSourceUtils.getConnection(dataSource);
		try {
			long n = RtaBulkRidTempTable.markRtaMatchSynergyAppliedSuccess(conn, rids);
			return n > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) n;
		} catch (SQLException | IOException e) {
			throw new IllegalStateException("markSynergyAggDone JDBC 실패: " + e.getMessage(), e);
		}
	}

	private static long[] toLongArray(List<Long> rids, int from, int to) {
		long[] a = new long[to - from];
		for (int i = from; i < to; i++) {
			a[i - from] = rids.get(i).longValue();
		}
		return a;
	}

	@Override
	public List<RtaSynergyAggUpsertRow> mergeSynergyAggRows(List<RtaSynergyAggUpsertRow> rows) {
		if (rows == null || rows.isEmpty()) {
			return Collections.emptyList();
		}
		Map<SynergyMergeKey, long[]> acc = new HashMap<>();
		Map<SynergyMergeKey, Integer> comboSizes = new HashMap<>();
		accumulateSynergyAgg(acc, comboSizes, rows);
		return synergyMapsToList(acc, comboSizes);
	}

	@Override
	public List<RtaCounterMatchupUpsertRow> mergeCounterMatchupRows(List<RtaCounterMatchupUpsertRow> rows) {
		if (rows == null || rows.isEmpty()) {
			return Collections.emptyList();
		}
		Map<CounterMergeKey, long[]> acc = new HashMap<>();
		Map<CounterMergeKey, Integer> oppSizes = new HashMap<>();
		accumulateCounterMatchup(acc, oppSizes, rows);
		return counterMapsToList(acc, oppSizes);
	}

	private static void accumulateSynergyAgg(Map<SynergyMergeKey, long[]> acc, Map<SynergyMergeKey, Integer> comboSizes,
			List<RtaSynergyAggUpsertRow> rows) {
		for (RtaSynergyAggUpsertRow r : rows) {
			if (r == null) {
				continue;
			}
			SynergyMergeKey k = new SynergyMergeKey(r.getSeasonId(), r.getRatingId(), r.getComboKey());
			long[] mw = acc.computeIfAbsent(k, x -> new long[2]);
			mw[0] += r.getMatchDelta();
			mw[1] += r.getWinDelta();
			comboSizes.putIfAbsent(k, r.getComboSize());
		}
	}

	private static List<RtaSynergyAggUpsertRow> synergyMapsToList(Map<SynergyMergeKey, long[]> acc,
			Map<SynergyMergeKey, Integer> comboSizes) {
		if (acc.isEmpty()) {
			return Collections.emptyList();
		}
		List<RtaSynergyAggUpsertRow> out = new ArrayList<>(acc.size());
		for (Map.Entry<SynergyMergeKey, long[]> e : acc.entrySet()) {
			SynergyMergeKey k = e.getKey();
			long[] mw = e.getValue();
			int cs = comboSizes.getOrDefault(k, 1);
			out.add(new RtaSynergyAggUpsertRow(k.seasonId(), k.ratingId(), k.comboKey(), cs, saturatingInt(mw[0]),
					saturatingInt(mw[1])));
		}
		return out;
	}

	private static void accumulateCounterMatchup(Map<CounterMergeKey, long[]> acc, Map<CounterMergeKey, Integer> oppSizes,
			List<RtaCounterMatchupUpsertRow> rows) {
		for (RtaCounterMatchupUpsertRow r : rows) {
			if (r == null) {
				continue;
			}
			CounterMergeKey k = new CounterMergeKey(r.getSeasonId(), r.getRatingId(), r.getSubjectUnitId(),
					r.getOpponentComboKey());
			long[] wl = acc.computeIfAbsent(k, x -> new long[2]);
			wl[0] += r.getWinDelta();
			wl[1] += r.getLoseDelta();
			oppSizes.putIfAbsent(k, r.getOpponentComboSize());
		}
	}

	private static List<RtaCounterMatchupUpsertRow> counterMapsToList(Map<CounterMergeKey, long[]> acc,
			Map<CounterMergeKey, Integer> oppSizes) {
		if (acc.isEmpty()) {
			return Collections.emptyList();
		}
		List<RtaCounterMatchupUpsertRow> out = new ArrayList<>(acc.size());
		for (Map.Entry<CounterMergeKey, long[]> e : acc.entrySet()) {
			CounterMergeKey k = e.getKey();
			long[] wl = e.getValue();
			int os = oppSizes.getOrDefault(k, 2);
			out.add(new RtaCounterMatchupUpsertRow(k.seasonId(), k.ratingId(), k.subjectUnitId(), k.opponentComboKey(), os,
					saturatingInt(wl[0]), saturatingInt(wl[1])));
		}
		return out;
	}

	private static int saturatingInt(long v) {
		if (v > Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		if (v < Integer.MIN_VALUE) {
			return Integer.MIN_VALUE;
		}
		return (int) v;
	}

	private record SynergyMergeKey(long seasonId, int ratingId, String comboKey) {
	}

	private record CounterMergeKey(long seasonId, int ratingId, long subjectUnitId, String opponentComboKey) {
	}

	private void markSynergyFailedInNewTx(long rid) {
		synergyOneRidTx.executeWithoutResult(status -> rtaMapper.markSynergyAggFailed(rid));
	}

	@Override
	public void prefetchSynergyLookup(List<Long> rids, Map<Long, Map<String, Object>> replayByRid,
			Map<Long, List<Map<String, Object>>> unitsByRid,
			Map<Long, List<Map<String, Object>>> ratingsByRid) {
		if (rids == null || rids.isEmpty()) {
			return;
		}
		/* COPY→tmp_bulk_rids→JOIN — ANY(bigint[]) 대량 전달 대비 인덱스 친화·플래너 유리 */
		bulkRidLookupService.prefetchSynergyLookupMaps(rids, replayByRid, unitsByRid, ratingsByRid);
	}

	@Override
	public RtaSynergyAggService.SynergyRidBuildResult buildRowsFromLookup(long rid,
			Map<Long, Map<String, Object>> replayByRid, Map<Long, List<Map<String, Object>>> unitsByRid,
			Map<Long, List<Map<String, Object>>> ratingsByRid) {
		Objects.requireNonNull(ratingsByRid, "ratingsByRid");
		List<Map<String, Object>> ratingRows = ratingsByRid.getOrDefault(rid, Collections.emptyList());
		BuiltRows b = buildAllRowsForContext(rid,
				toSynergyRidContext(rid, replayByRid.get(rid), unitsByRid.get(rid), ratingRows));
		return new RtaSynergyAggService.SynergyRidBuildResult(b.synergy(), b.counter());
	}

	private void applyOneRidInternal(long rid) {
		BuiltRows built = buildAllRowsForRid(rid);
		flushSynergyDirectUpserts(built.synergy());
		flushSynergyBanDeltas(bulkRidLookupService.aggregateSynergyBanIncrements(Collections.singletonList(rid)));
		int marked = rtaMapper.markSynergyAggDone(rid);
		if (marked == 0) {
			throw new IllegalStateException("synergy 완료 표시 갱신 0건 rid=" + rid);
		}
		flushCounterMatchupInChunks(built.counter());
	}

	private BuiltRows buildAllRowsForRid(long rid) {
		return buildAllRowsForContext(rid, loadSynergyRidContext(rid));
	}

	private BuiltRows buildAllRowsForContext(long rid, SynergyRidContext ctx) {
		List<RtaSynergyAggUpsertRow> aggRows = buildAggRows(ctx.seasonId, ctx.w1, ctx.w2, ctx.rating1, ctx.rating2,
				ctx.ids1, ctx.ids2, ctx.winner);
		int expected = expectedSynergyRowCount(ctx.ids1.length);
		if (aggRows.size() != expected) {
			throw new IllegalStateException(
					"조합 행 수 불일치 rid=" + rid + " nUnits=" + ctx.ids1.length + " actual=" + aggRows.size()
							+ " expected=" + expected);
		}
		return new BuiltRows(aggRows, buildCounterMatchupRows(ctx));
	}

	private record BuiltRows(List<RtaSynergyAggUpsertRow> synergy, List<RtaCounterMatchupUpsertRow> counter) {
	}

	@Override
	public List<RtaSynergyAggUpsertRow> buildSynergyRowsForRid(long rid) {
		return buildAllRowsForRid(rid).synergy();
	}

	@Override
	public List<RtaCounterMatchupUpsertRow> buildCounterMatchupRowsForRid(long rid) {
		return buildAllRowsForRid(rid).counter();
	}

	private SynergyRidContext loadSynergyRidContext(long rid) {
		return toSynergyRidContext(rid, rtaMapper.selectSynergyReplayRow(rid), rtaMapper.selectSynergyFieldUnits(rid), null);
	}

	/**
	 * 단건/배치 조회 공통 — replay·raw 는 동일 컬럼(rid·wizard_id·unit_master_id)을 쓴다.
	 *
	 * @param participantRatingRowsOrNull 배치에서 미리 채운 participant 행이면 DB 조회 생략. {@code null}이면 rid 단건 조회.
	 */
	private SynergyRidContext toSynergyRidContext(long rid, Map<String, Object> replay, List<Map<String, Object>> raw,
			List<Map<String, Object>> participantRatingRowsOrNull) {
		if (replay == null || replay.isEmpty()) {
			throw new IllegalStateException("rta_match 없음 rid=" + rid);
		}
		Object sid = replay.get("season_id");
		if (sid == null) {
			throw new IllegalStateException("season_id 없음 rid=" + rid);
		}
		long seasonId = ((Number) sid).longValue();
		String winner = normalizeWizardId(replay.get("winner_wizard_id"));

		if (raw == null || raw.isEmpty()) {
			throw new IllegalStateException("필드 유닛 없음 rid=" + rid);
		}

		Map<String, List<Long>> byWizard = new LinkedHashMap<>();
		for (Map<String, Object> row : raw) {
			String w = normalizeWizardId(row.get("wizard_id"));
			if (w == null) {
				continue;
			}
			long uid = toLong(row.get("unit_master_id"));
			byWizard.computeIfAbsent(w, k -> new ArrayList<>()).add(uid);
		}
		for (List<Long> ids : byWizard.values()) {
			Collections.sort(ids);
		}

		if (byWizard.size() != 2) {
			throw new IllegalStateException("wizard 수 != 2 rid=" + rid + " actual=" + byWizard.size());
		}

		List<String> wizards = new ArrayList<>(byWizard.keySet());
		Collections.sort(wizards);
		String w1 = wizards.get(0);
		String w2 = wizards.get(1);
		long[] ids1 = toDistinctSortedFieldUnits(byWizard.get(w1), rid);
		long[] ids2 = toDistinctSortedFieldUnits(byWizard.get(w2), rid);
		if (ids1.length != ids2.length) {
			throw new IllegalStateException(
					"양측 필드 유닛 수 불일치 rid=" + rid + " " + ids1.length + " vs " + ids2.length);
		}
		// replay_id 는 위 rid 로 이미 한 경기로 한정됨. 키는 그 안에서의 wizard_id 만이면 충분.
		Map<String, Integer> ratingsByWizard = new HashMap<>();
		List<Map<String, Object>> pr = participantRatingRowsOrNull != null ? participantRatingRowsOrNull
				: rtaMapper.selectSynergyWizardRatings(rid);
		if (pr == null || pr.isEmpty()) {
			throw new IllegalStateException("rta_match_participant 없음 rid=" + rid);
		}
		for (Map<String, Object> row : pr) {
			String w = normalizeWizardId(row.get("wizard_id"));
			if (w == null) {
				continue;
			}
			Object ro = row.get("rating_id");
			if (ro == null) {
				throw new IllegalStateException("rating_id null rid=" + rid + " wizard_id=" + w);
			}
			int ridVal;
			if (ro instanceof Number) {
				ridVal = ((Number) ro).intValue();
			} else {
				try {
					ridVal = Integer.parseInt(String.valueOf(ro).trim());
				} catch (NumberFormatException e) {
					throw new IllegalStateException(
							"rating_id 파싱 실패 rid=" + rid + " wizard_id=" + w + " value=" + ro, e);
				}
			}
			if (ridVal <= 0) {
				throw new IllegalStateException(
						"rating_id 는 1 이상이어야 함 rid=" + rid + " wizard_id=" + w + " rating_id=" + ridVal);
			}
			ratingsByWizard.put(w, Integer.valueOf(ridVal));
		}
		if (!ratingsByWizard.containsKey(w1) || !ratingsByWizard.containsKey(w2)) {
			throw new IllegalStateException("participant rating 과 unit_pick wizard 불일치 rid=" + rid + " w1=" + w1
					+ " w2=" + w2 + " ratingKeys=" + ratingsByWizard.keySet());
		}
		int r1 = ratingsByWizard.get(w1).intValue();
		int r2 = ratingsByWizard.get(w2).intValue();
		if (r1 <= 0 || r2 <= 0) {
			throw new IllegalStateException("rating_id 유효성 실패 rid=" + rid + " r1=" + r1 + " r2=" + r2);
		}
		return new SynergyRidContext(seasonId, winner, w1, w2, r1, r2, ids1, ids2);
	}

	/**
	 * 상대 팀의 솔로·듀오·트리오 조합별로, 우리 팀 각 필드 유닛이 승/패를 얼마나 냈는지 집계 (몬스터 상세 카운터 매치업).
	 * 승자 미정이면 스킵.
	 */
	private static List<RtaCounterMatchupUpsertRow> buildCounterMatchupRows(SynergyRidContext ctx) {
		if (ctx.winner == null) {
			return Collections.emptyList();
		}
		boolean side1Won = Objects.equals(ctx.winner, ctx.w1);
		List<RtaCounterMatchupUpsertRow> out = new ArrayList<>();
		appendCounterForSide(ctx.seasonId, ctx.rating1, ctx.ids1, ctx.ids2, side1Won, out);
		appendCounterForSide(ctx.seasonId, ctx.rating2, ctx.ids2, ctx.ids1, !side1Won, out);
		return out;
	}

	private static void appendCounterForSide(long seasonId, int ratingId, long[] subjectIds, long[] oppIds,
			boolean subjectWon, List<RtaCounterMatchupUpsertRow> out) {
		int winD = subjectWon ? 1 : 0;
		int loseD = subjectWon ? 0 : 1;
		List<String> soloKeys = new ArrayList<>();
		List<String> duoKeys = new ArrayList<>();
		List<String> trioKeys = new ArrayList<>();
		collectOppSoloDuoTrioKeys(oppIds, soloKeys, duoKeys, trioKeys);
		for (long u : subjectIds) {
			for (String key : soloKeys) {
				out.add(new RtaCounterMatchupUpsertRow(seasonId, ratingId, u, key, 1, winD, loseD));
			}
			for (String key : duoKeys) {
				out.add(new RtaCounterMatchupUpsertRow(seasonId, ratingId, u, key, 2, winD, loseD));
			}
			for (String key : trioKeys) {
				out.add(new RtaCounterMatchupUpsertRow(seasonId, ratingId, u, key, 3, winD, loseD));
			}
		}
	}

	private static void collectOppSoloDuoTrioKeys(long[] oppIds, List<String> soloKeys, List<String> duoKeys,
			List<String> trioKeys) {
		// oppIds 는 toDistinctSortedFieldUnits() 로 이미 정렬됨 → comboKey() 오버로드 사용
		int n = oppIds.length;
		for (int i = 0; i < n; i++) {
			soloKeys.add(comboKey(oppIds[i]));
		}
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				duoKeys.add(comboKey(oppIds[i], oppIds[j]));
			}
		}
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				for (int k = j + 1; k < n; k++) {
					trioKeys.add(comboKey(oppIds[i], oppIds[j], oppIds[k]));
				}
			}
		}
	}

	private void flushSynergyBanDeltas(List<RtaSynergyBanDeltaRow> rows) {
		synergyBanCntBulkService.applyBanCntDeltas(rows);
	}

	private void flushSynergyInChunks(List<RtaSynergyAggUpsertRow> rows) {
		if (rows == null || rows.isEmpty()) {
			return;
		}
		if (synergyUseCopyStaging && rows.size() > synergyLegacyUpsertMaxRows) {
			int chunk = Math.max(1, synergyChunkRows);
			int total = rows.size();
			if (total > chunk) {
				int rounds = (total + chunk - 1) / chunk;
				log.info("[rta-synergy] 시너지 staging: 총 {}행 → COPY·merge {}회 분할 (청크당 최대 {}행)", total, rounds, chunk);
			}
			for (int from = 0; from < total; from += chunk) {
				int to = Math.min(from + chunk, total);
				synergyCopyStagingService.flushSynergyAggViaCopyStaging(rows.subList(from, to));
			}
			return;
		}
		for (int i = 0; i < rows.size(); i += AGG_UPSERT_FLUSH_CHUNK) {
			int to = Math.min(i + AGG_UPSERT_FLUSH_CHUNK, rows.size());
			flushSynergyDirectUpserts(rows.subList(i, to));
		}
	}

	private void flushSynergyDirectUpserts(List<RtaSynergyAggUpsertRow> rows) {
		if (rows == null || rows.isEmpty()) {
			return;
		}
		List<RtaSynergyAggUpsertRow> soloRows = new ArrayList<>();
		List<RtaSynergyAggUpsertRow> duoRows = new ArrayList<>();
		List<RtaSynergyAggUpsertRow> trioRows = new ArrayList<>();
		for (RtaSynergyAggUpsertRow row : rows) {
			if (row == null) {
				continue;
			}
			switch (row.getComboSize()) {
			case 1:
				soloRows.add(row);
				break;
			case 2:
				duoRows.add(row);
				break;
			case 3:
				trioRows.add(row);
				break;
			default:
				throw new IllegalArgumentException("지원하지 않는 comboSize=" + row.getComboSize());
			}
		}
		if (!soloRows.isEmpty()) {
			rtaMapper.upsertRtaSynergySoloAgg(soloRows);
		}
		if (!duoRows.isEmpty()) {
			rtaMapper.upsertRtaSynergyDuoAgg(duoRows);
		}
		if (!trioRows.isEmpty()) {
			rtaMapper.upsertRtaSynergyTrioAgg(trioRows);
		}
	}

	private void flushCounterMatchupInChunks(List<RtaCounterMatchupUpsertRow> rows) {
		if (rows == null || rows.isEmpty()) {
			return;
		}
		if (counterUseCopyStaging && rows.size() > counterLegacyUpsertMaxRows) {
			int chunk = Math.max(1, counterCopyStagingChunkRows);
			int total = rows.size();
			if (total > chunk) {
				int rounds = (total + chunk - 1) / chunk;
				log.info("[rta-synergy] 카운터 matchup staging: 총 {}행 → COPY·merge {}회 분할 (청크당 최대 {}행)",
						total, rounds, chunk);
				for (int from = 0; from < total; from += chunk) {
					int to = Math.min(from + chunk, total);
					boolean analyzeAfterMerge = to == total;
					counterCopyStagingService.flushCounterMatchupViaCopyStaging(rows.subList(from, to), analyzeAfterMerge);
				}
				return;
			}
			log.info("[rta-synergy] 카운터 matchup staging: 총 {}행 → 단일 COPY·merge", total);
			counterCopyStagingService.flushCounterMatchupViaCopyStaging(rows);
			return;
		}
		// 소량 직접 upsert: solo(1)/duo(2)/trio(3) 테이블로 분리 후 청크 단위 적재
		List<RtaCounterMatchupUpsertRow> soloRows  = new ArrayList<>();
		List<RtaCounterMatchupUpsertRow> duoRows   = new ArrayList<>();
		List<RtaCounterMatchupUpsertRow> trioRows  = new ArrayList<>();
		for (RtaCounterMatchupUpsertRow r : rows) {
			if (r == null) {
				continue;
			}
			switch (r.getOpponentComboSize()) {
			case 1:
				soloRows.add(r);
				break;
			case 2:
				duoRows.add(r);
				break;
			case 3:
				trioRows.add(r);
				break;
			default:
				throw new IllegalArgumentException("지원하지 않는 opponentComboSize=" + r.getOpponentComboSize());
			}
		}
		for (int i = 0; i < soloRows.size(); i += AGG_UPSERT_FLUSH_CHUNK) {
			rtaMapper.upsertRtaCounterSoloAgg(soloRows.subList(i, Math.min(i + AGG_UPSERT_FLUSH_CHUNK, soloRows.size())));
		}
		for (int i = 0; i < duoRows.size(); i += AGG_UPSERT_FLUSH_CHUNK) {
			rtaMapper.upsertRtaCounterDuoAgg(duoRows.subList(i, Math.min(i + AGG_UPSERT_FLUSH_CHUNK, duoRows.size())));
		}
		for (int i = 0; i < trioRows.size(); i += AGG_UPSERT_FLUSH_CHUNK) {
			rtaMapper.upsertRtaCounterTrioAgg(trioRows.subList(i, Math.min(i + AGG_UPSERT_FLUSH_CHUNK, trioRows.size())));
		}
	}

	private static final class SynergyRidContext {
		final long seasonId;
		final String winner;
		final String w1;
		final String w2;
		final int rating1;
		final int rating2;
		final long[] ids1;
		final long[] ids2;

		SynergyRidContext(long seasonId, String winner, String w1, String w2, int rating1, int rating2, long[] ids1,
				long[] ids2) {
			this.seasonId = seasonId;
			this.winner = winner;
			this.w1 = w1;
			this.w2 = w2;
			this.rating1 = rating1;
			this.rating2 = rating2;
			this.ids1 = ids1;
			this.ids2 = ids2;
		}
	}

	/**
	 * RTA는 픽 4 + 밴 1 → {@code is_banned=false} 인 행은 보통 3개. (밴 플래그 없는 특수 케이스는 4개)
	 */
	private static long[] toDistinctSortedFieldUnits(List<Long> ids, long rid) {
		if (ids == null) {
			throw new IllegalStateException("유닛 리스트 null rid=" + rid);
		}
		long[] arr = ids.stream().mapToLong(Long::longValue).distinct().sorted().toArray();
		if (arr.length < 3 || arr.length > 4) {
			throw new IllegalStateException("필드 유닛 수는 3~4만 지원 rid=" + rid + " n=" + arr.length);
		}
		return arr;
	}

	/** 한 진영당 솔로 n + 듀오 C(n,2) + 트리오 C(n,3), 양 진영 x2 */
	private static int expectedSynergyRowCount(int n) {
		if (n < 3) {
			return 0;
		}
		int pairs = n * (n - 1) / 2;
		int trios = n * (n - 1) * (n - 2) / 6;
		return 2 * (n + pairs + trios);
	}

	private static List<RtaSynergyAggUpsertRow> buildAggRows(long seasonId, String w1, String w2, int rating1, int rating2,
			long[] ids1, long[] ids2, String winner) {
		List<RtaSynergyAggUpsertRow> out = new ArrayList<>(28);
		appendSide(seasonId, rating1, w1, ids1, winner, out);
		appendSide(seasonId, rating2, w2, ids2, winner, out);
		return out;
	}

	private static void appendSide(long seasonId, int ratingId, String wizardId, long[] ids, String winner,
			List<RtaSynergyAggUpsertRow> out) {
		int n = ids.length;
		if (n < 3 || n > 4) {
			throw new IllegalStateException("필드 유닛 수는 3~4만 지원 n=" + n);
		}
		boolean win = winner != null && Objects.equals(winner, wizardId);
		int wd = win ? 1 : 0;
		if (ratingId <= 0) {
			throw new IllegalStateException("집계 rating_id 는 1 이상이어야 함 got=" + ratingId);
		}
		int rid = ratingId;
		// ids 는 toDistinctSortedFieldUnits() 로 이미 정렬됨 → comboKey() 오버로드 사용 (copy·sort 생략)
		for (long id : ids) {
			out.add(new RtaSynergyAggUpsertRow(seasonId, rid, comboKey(id), 1, 1, wd));
		}
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				out.add(new RtaSynergyAggUpsertRow(seasonId, rid, comboKey(ids[i], ids[j]), 2, 1, wd));
			}
		}
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				for (int k = j + 1; k < n; k++) {
					out.add(new RtaSynergyAggUpsertRow(seasonId, rid, comboKey(ids[i], ids[j], ids[k]), 3, 1, wd));
				}
			}
		}
	}

	/**
	 * ids 배열이 이미 정렬된 경우 전용 — copy·sort·String[] 없이 직접 직렬화.
	 * {@link #appendSide}, {@link #collectOppSoloDuoTrioKeys} 는 정렬된 배열을 오름차순으로 순회하므로 사용 가능.
	 */
	private static String comboKey(long a) {
		return Long.toString(a);
	}

	private static String comboKey(long a, long b) {
		// a < b 보장 (정렬된 ids 오름차순 순회)
		return Long.toString(a) + ',' + Long.toString(b);
	}

	private static String comboKey(long a, long b, long c) {
		// a < b < c 보장
		return Long.toString(a) + ',' + Long.toString(b) + ',' + Long.toString(c);
	}


	/**
	 * unit_pick / participant / replay 의 wizard_id 가 Number·문자열 등으로 달라도 동일 키로 맞춘다.
	 * (예: {@code "5916878"} vs {@code "5916878.0"} → 둘 다 {@code "5916878"})
	 */
	private static String normalizeWizardId(Object o) {
		if (o == null) {
			return null;
		}
		if (o instanceof Number) {
			return Long.toString(((Number) o).longValue());
		}
		String s = String.valueOf(o).trim();
		if (s.isEmpty()) {
			return null;
		}
		if (s.indexOf('.') >= 0 || s.indexOf('e') >= 0 || s.indexOf('E') >= 0) {
			try {
				return Long.toString((long) Double.parseDouble(s));
			} catch (NumberFormatException e) {
				return trimToNull(s);
			}
		}
		try {
			return Long.toString(Long.parseLong(s));
		} catch (NumberFormatException e) {
			return trimToNull(s);
		}
	}

	private static String trimToNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}

	private static long toLong(Object o) {
		if (o == null) {
			return 0L;
		}
		if (o instanceof Number) {
			return ((Number) o).longValue();
		}
		return Long.parseLong(String.valueOf(o).trim());
	}
}
