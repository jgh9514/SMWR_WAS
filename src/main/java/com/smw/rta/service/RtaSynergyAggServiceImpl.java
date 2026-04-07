package com.smw.rta.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.model.RtaCounterMatchupUpsertRow;
import com.smw.rta.model.RtaSynergyAggUpsertRow;

import lombok.extern.slf4j.Slf4j;

@Service
@Primary
@Slf4j
public class RtaSynergyAggServiceImpl implements RtaSynergyAggService {

	private static final int COUNTER_UPSERT_CHUNK = 500;

	private final RtaMapper rtaMapper;
	private final TransactionTemplate synergyOneRidTx;

	public RtaSynergyAggServiceImpl(RtaMapper rtaMapper,
			@Qualifier("rtaJdbcTransactionManager") PlatformTransactionManager transactionManager) {
		this.rtaMapper = rtaMapper;
		this.synergyOneRidTx = new TransactionTemplate(transactionManager);
		this.synergyOneRidTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Override
	@Transactional(transactionManager = "rtaJdbcTransactionManager", propagation = Propagation.REQUIRES_NEW,
			rollbackFor = Exception.class)
	public void applyOneRid(long rid) {
		applyOneRidInternal(rid);
	}

	@Override
	public SynergyBatchApplyResult applySynergyBatch(List<Long> rids) {
		int ok = 0;
		int fail = 0;
		if (rids == null) {
			return new SynergyBatchApplyResult(0, 0);
		}
		for (Long rid : rids) {
			if (rid == null) {
				continue;
			}
			try {
				// 동일 클래스에서 this.applyOneRid 를 호출하면 @Transactional(REQUIRES_NEW) 가 무력화되므로
				// TransactionTemplate 으로 rid 단위 커밋한다.
				synergyOneRidTx.executeWithoutResult(status -> applyOneRidInternal(rid));
				ok++;
			} catch (Exception e) {
				fail++;
				try {
					markSynergyFailedInNewTx(rid);
				} catch (Exception markEx) {
					log.warn("[rta-synergy] rid={} 실패 표시 중 오류: {}", rid, markEx.getMessage());
				}
				log.warn("[rta-synergy] rid={} 시너지 집계 실패: {}", rid, e.getMessage());
			}
		}
		return new SynergyBatchApplyResult(ok, fail);
	}

	private void markSynergyFailedInNewTx(long rid) {
		synergyOneRidTx.executeWithoutResult(status -> rtaMapper.markSynergyAggFailed(rid));
	}

	private void applyOneRidInternal(long rid) {
		SynergyRidContext ctx = loadSynergyRidContext(rid);
		List<RtaSynergyAggUpsertRow> aggRows = buildAggRows(ctx.seasonId, ctx.w1, ctx.w2, ctx.ids1, ctx.ids2, ctx.winner);
		int expected = expectedSynergyRowCount(ctx.ids1.length);
		if (aggRows.size() != expected) {
			throw new IllegalStateException(
					"조합 행 수 불일치 rid=" + rid + " nUnits=" + ctx.ids1.length + " actual=" + aggRows.size()
							+ " expected=" + expected);
		}
		rtaMapper.upsertRtaSynergyAgg(aggRows);

		int marked = rtaMapper.markSynergyAggDone(rid);
		if (marked == 0) {
			throw new IllegalStateException("synergy_applied_at 갱신 0건 rid=" + rid);
		}

		try {
			List<RtaCounterMatchupUpsertRow> counterRows = buildCounterMatchupRows(ctx);
			flushCounterMatchupInChunks(counterRows);
		} catch (Exception e) {
			log.warn("[rta-synergy] rid={} 카운터 매치업 적재 실패 — 시너지(rta_agg_synergy_combo)는 반영됨: {}", rid,
					e.getMessage());
		}
	}

	@Override
	public List<RtaSynergyAggUpsertRow> buildSynergyRowsForRid(long rid) {
		SynergyRidContext ctx = loadSynergyRidContext(rid);
		List<RtaSynergyAggUpsertRow> rows = buildAggRows(ctx.seasonId, ctx.w1, ctx.w2, ctx.ids1, ctx.ids2, ctx.winner);
		int expected = expectedSynergyRowCount(ctx.ids1.length);
		if (rows.size() != expected) {
			throw new IllegalStateException(
					"조합 행 수 불일치 rid=" + rid + " nUnits=" + ctx.ids1.length + " actual=" + rows.size() + " expected="
							+ expected);
		}
		return rows;
	}

	@Override
	public List<RtaCounterMatchupUpsertRow> buildCounterMatchupRowsForRid(long rid) {
		SynergyRidContext ctx = loadSynergyRidContext(rid);
		return buildCounterMatchupRows(ctx);
	}

	private SynergyRidContext loadSynergyRidContext(long rid) {
		Map<String, Object> replay = rtaMapper.selectSynergyReplayRow(rid);
		if (replay == null || replay.isEmpty()) {
			throw new IllegalStateException("rta_match 없음 rid=" + rid);
		}
		Object sid = replay.get("season_id");
		if (sid == null) {
			throw new IllegalStateException("season_id 없음 rid=" + rid);
		}
		long seasonId = ((Number) sid).longValue();
		String winner = trimToNull(stringOf(replay.get("winner_wizard_id")));

		List<Map<String, Object>> raw = rtaMapper.selectSynergyFieldUnits(rid);
		if (raw == null || raw.isEmpty()) {
			throw new IllegalStateException("필드 유닛 없음 rid=" + rid);
		}

		Map<String, List<Long>> byWizard = new LinkedHashMap<>();
		for (Map<String, Object> row : raw) {
			String w = trimToNull(stringOf(row.get("wizard_id")));
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
		return new SynergyRidContext(seasonId, winner, w1, w2, ids1, ids2);
	}

	/**
	 * 상대 팀의 듀오·트리오 조합별로, 우리 팀 각 필드 유닛이 승/패를 얼마나 냈는지 집계 (몬스터 상세 카운터 매치업).
	 * 승자 미정이면 스킵.
	 */
	private static List<RtaCounterMatchupUpsertRow> buildCounterMatchupRows(SynergyRidContext ctx) {
		if (ctx.winner == null) {
			return Collections.emptyList();
		}
		boolean side1Won = Objects.equals(ctx.winner, trimToNull(ctx.w1));
		List<RtaCounterMatchupUpsertRow> out = new ArrayList<>();
		appendCounterForSide(ctx.seasonId, ctx.ids1, ctx.ids2, side1Won, out);
		appendCounterForSide(ctx.seasonId, ctx.ids2, ctx.ids1, !side1Won, out);
		return out;
	}

	private static void appendCounterForSide(long seasonId, long[] subjectIds, long[] oppIds, boolean subjectWon,
			List<RtaCounterMatchupUpsertRow> out) {
		int winD = subjectWon ? 1 : 0;
		int loseD = subjectWon ? 0 : 1;
		List<String> duoKeys = new ArrayList<>();
		List<String> trioKeys = new ArrayList<>();
		collectOppDuoTrioKeys(oppIds, duoKeys, trioKeys);
		for (long u : subjectIds) {
			for (String key : duoKeys) {
				out.add(new RtaCounterMatchupUpsertRow(seasonId, u, key, 2, winD, loseD));
			}
			for (String key : trioKeys) {
				out.add(new RtaCounterMatchupUpsertRow(seasonId, u, key, 3, winD, loseD));
			}
		}
	}

	private static void collectOppDuoTrioKeys(long[] oppIds, List<String> duoKeys, List<String> trioKeys) {
		int n = oppIds.length;
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				duoKeys.add(comboKeySorted(oppIds[i], oppIds[j]));
			}
		}
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				for (int k = j + 1; k < n; k++) {
					trioKeys.add(comboKeySorted(oppIds[i], oppIds[j], oppIds[k]));
				}
			}
		}
	}

	private void flushCounterMatchupInChunks(List<RtaCounterMatchupUpsertRow> rows) {
		if (rows == null || rows.isEmpty()) {
			return;
		}
		for (int i = 0; i < rows.size(); i += COUNTER_UPSERT_CHUNK) {
			int to = Math.min(i + COUNTER_UPSERT_CHUNK, rows.size());
			rtaMapper.upsertRtaCounterMatchupAgg(rows.subList(i, to));
		}
	}

	private static final class SynergyRidContext {
		final long seasonId;
		final String winner;
		final String w1;
		final String w2;
		final long[] ids1;
		final long[] ids2;

		SynergyRidContext(long seasonId, String winner, String w1, String w2, long[] ids1, long[] ids2) {
			this.seasonId = seasonId;
			this.winner = winner;
			this.w1 = w1;
			this.w2 = w2;
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

	private static List<RtaSynergyAggUpsertRow> buildAggRows(long seasonId, String w1, String w2, long[] ids1, long[] ids2,
			String winner) {
		List<RtaSynergyAggUpsertRow> out = new ArrayList<>(28);
		appendSide(seasonId, w1, ids1, winner, out);
		appendSide(seasonId, w2, ids2, winner, out);
		return out;
	}

	private static void appendSide(long seasonId, String wizardId, long[] ids, String winner,
			List<RtaSynergyAggUpsertRow> out) {
		int n = ids.length;
		if (n < 3 || n > 4) {
			throw new IllegalStateException("필드 유닛 수는 3~4만 지원 n=" + n);
		}
		boolean win = winner != null && Objects.equals(winner, trimToNull(wizardId));
		int wd = win ? 1 : 0;
		for (long id : ids) {
			out.add(new RtaSynergyAggUpsertRow(seasonId, Long.toString(id), 1, wd));
		}
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				out.add(new RtaSynergyAggUpsertRow(seasonId, comboKeySorted(ids[i], ids[j]), 2, wd));
			}
		}
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				for (int k = j + 1; k < n; k++) {
					out.add(new RtaSynergyAggUpsertRow(seasonId, comboKeySorted(ids[i], ids[j], ids[k]), 3, wd));
				}
			}
		}
	}

	private static String comboKeySorted(long... raw) {
		long[] a = Arrays.copyOf(raw, raw.length);
		Arrays.sort(a);
		String[] s = new String[a.length];
		for (int i = 0; i < a.length; i++) {
			s[i] = Long.toString(a[i]);
		}
		return String.join(",", s);
	}

	private static String stringOf(Object o) {
		return o == null ? null : String.valueOf(o);
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
