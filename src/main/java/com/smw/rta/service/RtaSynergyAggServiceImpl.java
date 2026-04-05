package com.smw.rta.service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.model.RtaSynergyAggUpsertRow;
import com.smw.rta.model.RtaSynergyComboRow;

@Service
public class RtaSynergyAggServiceImpl implements RtaSynergyAggService {

	private final RtaMapper rtaMapper;

	public RtaSynergyAggServiceImpl(RtaMapper rtaMapper) {
		this.rtaMapper = rtaMapper;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void applyOneRid(long rid) {
		Map<String, Object> replay = rtaMapper.selectSynergyReplayRow(rid);
		if (replay == null || replay.isEmpty()) {
			throw new IllegalStateException("replay_list 없음 rid=" + rid);
		}
		String winner = trimToNull(stringOf(replay.get("winner_wizard_id")));
		Timestamp lastMatchAt = toTimestamp(replay.get("date_add"));

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
		long[] ids1 = toDistinctSorted4(byWizard.get(w1), rid);
		long[] ids2 = toDistinctSorted4(byWizard.get(w2), rid);

		List<RtaSynergyComboRow> factRows = buildComboRows(rid, w1, w2, ids1, ids2, winner);
		if (factRows.size() != 28) {
			throw new IllegalStateException("조합 행 수 != 28 rid=" + rid + " n=" + factRows.size());
		}

		rtaMapper.insertRtaSynergyFacts(factRows);

		List<RtaSynergyAggUpsertRow> aggRows = factRows.stream()
				.map(f -> new RtaSynergyAggUpsertRow(
						f.getArity(),
						f.getM1(),
						f.getM2(),
						f.getM3(),
						f.isWin() ? 1 : 0,
						lastMatchAt))
				.collect(Collectors.toList());
		rtaMapper.upsertRtaSynergyAgg(aggRows);

		int marked = rtaMapper.markSynergyAggDone(rid);
		if (marked == 0) {
			throw new IllegalStateException("synergy_agg_status 갱신 0건 rid=" + rid);
		}
	}

	private static long[] toDistinctSorted4(List<Long> ids, long rid) {
		if (ids == null) {
			throw new IllegalStateException("유닛 리스트 null rid=" + rid);
		}
		long[] arr = ids.stream().mapToLong(Long::longValue).distinct().sorted().toArray();
		if (arr.length != 4) {
			throw new IllegalStateException("필드 유닛 수 != 4 rid=" + rid + " n=" + arr.length);
		}
		return arr;
	}

	private static List<RtaSynergyComboRow> buildComboRows(long rid, String w1, String w2, long[] ids1, long[] ids2,
			String winner) {
		List<RtaSynergyComboRow> out = new ArrayList<>(28);
		appendSide(rid, w1, ids1, winner, out);
		appendSide(rid, w2, ids2, winner, out);
		return out;
	}

	private static void appendSide(long rid, String wizardId, long[] ids, String winner, List<RtaSynergyComboRow> out) {
		boolean win = winner != null && Objects.equals(winner, trimToNull(wizardId));
		for (long id : ids) {
			out.add(new RtaSynergyComboRow(rid, wizardId, 1, id, 0L, 0L, win));
		}
		for (int i = 0; i < 4; i++) {
			for (int j = i + 1; j < 4; j++) {
				out.add(new RtaSynergyComboRow(rid, wizardId, 2, ids[i], ids[j], 0L, win));
			}
		}
		for (int i = 0; i < 4; i++) {
			for (int j = i + 1; j < 4; j++) {
				for (int k = j + 1; k < 4; k++) {
					out.add(new RtaSynergyComboRow(rid, wizardId, 3, ids[i], ids[j], ids[k], win));
				}
			}
		}
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

	private static Timestamp toTimestamp(Object o) {
		if (o == null) {
			return null;
		}
		if (o instanceof Timestamp) {
			return (Timestamp) o;
		}
		if (o instanceof java.util.Date) {
			return new Timestamp(((java.util.Date) o).getTime());
		}
		if (o instanceof java.time.LocalDateTime) {
			return Timestamp.valueOf((java.time.LocalDateTime) o);
		}
		throw new IllegalStateException("date_add 타입 지원 안 함: " + o.getClass());
	}
}
