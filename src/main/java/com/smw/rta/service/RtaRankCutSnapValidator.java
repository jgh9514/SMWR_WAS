package com.smw.rta.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.smw.monster.util.SlackNotifier;
import com.smw.rta.config.RtaBatchProperties;
import com.smw.rta.config.RtaRankCutValidationProperties;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.model.RtaRankCutSnapRow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@code rta_agg_rank_cut_hourly_snap} 적재 직전·시즌 경기 수 재집계 후
 * 급격한 증감·티어 역전 등 집계 오류 징후를 검출한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RtaRankCutSnapValidator {

	private final RtaRankCutValidationProperties props;
	private final RtaBatchProperties batchProperties;
	private final SlackNotifier slackNotifier;

	public RtaRankCutValidationReport validateHourlySnap(
			RtaMapper mapper,
			long seasonId,
			Instant snapHour,
			List<RtaRankCutSnapRow> newRows) {
		if (!props.isEnabled() || newRows == null || newRows.isEmpty()) {
			return RtaRankCutValidationReport.empty();
		}

		List<RtaRankCutAnomaly> anomalies = new ArrayList<>();
		anomalies.addAll(checkBounds(snapHour, newRows));
		anomalies.addAll(checkMonotonicity(snapHour, newRows));

		Map<String, RtaRankCutSnapRow> previousByGrade = loadPreviousByGrade(mapper, seasonId, snapHour);
		for (RtaRankCutSnapRow row : newRows) {
			String grade = normalizeGrade(row.getGradeSlot());
			if (grade == null) {
				continue;
			}
			RtaRankCutSnapRow prev = previousByGrade.get(grade);
			if (prev == null) {
				continue;
			}
			RtaRankCutAnomaly delta = checkHourOverHourDelta(snapHour, row, prev);
			if (delta != null) {
				anomalies.add(delta);
			}
		}

		if (!anomalies.isEmpty()) {
			log.warn("[rank-cut-validation] seasonId={} hour={} anomalies={}", seasonId, snapHour, anomalies.size());
		}
		return new RtaRankCutValidationReport(anomalies);
	}

	public Map<Integer, Long> loadMatchTotals(RtaMapper mapper, long seasonId) {
		List<Map<String, Object>> rows = mapper.selectSeasonRatingMatchTotals(seasonId);
		Map<Integer, Long> out = new HashMap<>();
		if (rows == null) {
			return out;
		}
		for (Map<String, Object> row : rows) {
			Integer ratingId = toInt(row.get("ratingId"));
			Long total = toLong(row.get("totalMatches"));
			if (ratingId != null && total != null) {
				out.put(ratingId, total);
			}
		}
		return out;
	}

	public RtaRankCutValidationReport validateMatchTotalRebuild(
			long seasonId,
			Map<Integer, Long> before,
			Map<Integer, Long> after) {
		if (!props.isEnabled() || !props.isMatchTotalDropCheckEnabled()) {
			return RtaRankCutValidationReport.empty();
		}
		List<RtaRankCutAnomaly> anomalies = new ArrayList<>();
		for (Map.Entry<Integer, Long> e : before.entrySet()) {
			Integer ratingId = e.getKey();
			long prev = e.getValue() != null ? e.getValue() : 0L;
			long next = after.getOrDefault(ratingId, 0L);
			if (next >= prev) {
				continue;
			}
			long drop = prev - next;
			double dropPct = prev > 0 ? (double) drop / prev : 1.0d;
			if (drop >= props.getMatchTotalDropAbsThreshold()
					&& dropPct >= props.getMatchTotalDropPctThreshold()) {
				anomalies.add(new RtaRankCutAnomaly(
						"MATCH_TOTAL_DROP",
						null,
						null,
						String.format(
								"seasonId=%d ratingId=%d total_matches %,d → %,d (Δ-%,d, -%.1f%%)",
								seasonId, ratingId, prev, next, drop, dropPct * 100)));
			}
		}
		if (!anomalies.isEmpty()) {
			log.warn("[rank-cut-validation] seasonId={} match_total_drop anomalies={}", seasonId, anomalies.size());
		}
		return new RtaRankCutValidationReport(anomalies);
	}

	public void notifyIfNeeded(RtaRankCutValidationReport report) {
		if (report == null || report.anomalyCount() < props.getSlackAlertMinAnomalies()) {
			return;
		}
		String token = batchProperties.getSlackToken();
		String channel = batchProperties.getSlackChannelId();
		if (token == null || token.isBlank() || channel == null || channel.isBlank()) {
			return;
		}
		StringBuilder sb = new StringBuilder();
		sb.append("[랭크컷 검증] 집계 이상 징후 ").append(report.anomalyCount()).append("건\n");
		for (String line : report.formatSamples(props.getMaxSamplesInLog())) {
			sb.append("• ").append(line).append('\n');
		}
		slackNotifier.send(token, channel, sb.toString());
	}

	private Map<String, RtaRankCutSnapRow> loadPreviousByGrade(RtaMapper mapper, long seasonId, Instant beforeHour) {
		List<Map<String, Object>> rows = mapper.selectPreviousRankCutSnapsByGrade(seasonId, beforeHour);
		Map<String, RtaRankCutSnapRow> out = new LinkedHashMap<>();
		if (rows == null) {
			return out;
		}
		for (Map<String, Object> row : rows) {
			String grade = normalizeGrade(Objects.toString(row.get("gradeSlot"), null));
			if (grade == null) {
				continue;
			}
			RtaRankCutSnapRow snap = new RtaRankCutSnapRow(
					grade,
					toShort(row.get("sortOrder")),
					toLong(row.get("cutoffScore")));
			out.putIfAbsent(grade, snap);
		}
		return out;
	}

	private List<RtaRankCutAnomaly> checkBounds(Instant snapHour, List<RtaRankCutSnapRow> rows) {
		List<RtaRankCutAnomaly> out = new ArrayList<>();
		for (RtaRankCutSnapRow row : rows) {
			long score = row.getCutoffScore();
			if (score < 0 || score > props.getMaxReasonableScore()) {
				out.add(new RtaRankCutAnomaly(
						"BOUNDS",
						row.getGradeSlot(),
						snapHour,
						String.format("grade=%s cutoff=%d (허용 0~%,d)", row.getGradeSlot(), score, props.getMaxReasonableScore())));
			}
		}
		return out;
	}

	private List<RtaRankCutAnomaly> checkMonotonicity(Instant snapHour, List<RtaRankCutSnapRow> rows) {
		List<RtaRankCutAnomaly> out = new ArrayList<>();
		List<RtaRankCutSnapRow> sorted = new ArrayList<>(rows);
		sorted.sort(Comparator.comparingInt(RtaRankCutSnapRow::getSortOrder));
		long tol = Math.max(0L, props.getMonotonicityTolerance());
		for (int i = 1; i < sorted.size(); i++) {
			RtaRankCutSnapRow lower = sorted.get(i - 1);
			RtaRankCutSnapRow higher = sorted.get(i);
			if (higher.getCutoffScore() + tol < lower.getCutoffScore()) {
				out.add(new RtaRankCutAnomaly(
						"MONOTONICITY",
						higher.getGradeSlot(),
						snapHour,
						String.format(
								"티어 역전 sort %d(%s)=%d > sort %d(%s)=%d",
								lower.getSortOrder(), lower.getGradeSlot(), lower.getCutoffScore(),
								higher.getSortOrder(), higher.getGradeSlot(), higher.getCutoffScore())));
			}
		}
		return out;
	}

	private RtaRankCutAnomaly checkHourOverHourDelta(Instant snapHour, RtaRankCutSnapRow current, RtaRankCutSnapRow previous) {
		long prev = previous.getCutoffScore();
		long cur = current.getCutoffScore();
		long absDelta = Math.abs(cur - prev);
		if (absDelta == 0) {
			return null;
		}
		boolean absExceeded = absDelta > props.getMaxAbsScoreDelta();
		if (prev < props.getMinBaselineScore()) {
			return absExceeded
					? new RtaRankCutAnomaly(
							"HOUR_DELTA",
							current.getGradeSlot(),
							snapHour,
							String.format("grade=%s %,d → %,d (Δ%,d, 시즌 초반)", current.getGradeSlot(), prev, cur, cur - prev))
					: null;
		}
		double pct = (double) absDelta / prev;
		boolean pctExceeded = pct > props.getMaxPctScoreDelta();
		if (!absExceeded && !pctExceeded) {
			return null;
		}
		return new RtaRankCutAnomaly(
				"HOUR_DELTA",
				current.getGradeSlot(),
				snapHour,
				String.format(
						"grade=%s %,d → %,d (Δ%,d, %.1f%%)",
						current.getGradeSlot(),
						prev,
						cur,
						cur - prev,
						pct * 100));
	}

	private static String normalizeGrade(String gradeSlot) {
		if (gradeSlot == null) {
			return null;
		}
		String g = gradeSlot.trim();
		return g.isEmpty() ? null : g;
	}

	private static Integer toInt(Object o) {
		if (o instanceof Number n) {
			return n.intValue();
		}
		if (o != null) {
			try {
				return Integer.parseInt(String.valueOf(o));
			} catch (NumberFormatException ignored) {
				return null;
			}
		}
		return null;
	}

	private static long toLong(Object o) {
		if (o instanceof Number n) {
			return n.longValue();
		}
		if (o != null) {
			try {
				return Long.parseLong(String.valueOf(o));
			} catch (NumberFormatException ignored) {
				return 0L;
			}
		}
		return 0L;
	}

	private static short toShort(Object o) {
		if (o instanceof Number n) {
			return n.shortValue();
		}
		return 0;
	}

	public record RtaRankCutAnomaly(String kind, String gradeSlot, Instant snapHour, String detail) {
	}

	public record RtaRankCutValidationReport(List<RtaRankCutAnomaly> anomalies) {

		public static RtaRankCutValidationReport empty() {
			return new RtaRankCutValidationReport(List.of());
		}

		public int anomalyCount() {
			return anomalies != null ? anomalies.size() : 0;
		}

		public RtaRankCutValidationReport merge(RtaRankCutValidationReport other) {
			if (other == null || other.anomalyCount() == 0) {
				return this;
			}
			List<RtaRankCutAnomaly> merged = new ArrayList<>(this.anomalies.size() + other.anomalies.size());
			merged.addAll(this.anomalies);
			merged.addAll(other.anomalies);
			return new RtaRankCutValidationReport(merged);
		}

		public List<String> formatSamples(int limit) {
			int cap = Math.max(1, limit);
			List<String> lines = new ArrayList<>();
			if (anomalies == null) {
				return lines;
			}
			for (int i = 0; i < anomalies.size() && lines.size() < cap; i++) {
				RtaRankCutAnomaly a = anomalies.get(i);
				String hour = a.snapHour() != null ? a.snapHour().toString() : "-";
				lines.add(String.format("[%s] %s hour=%s %s", a.kind(), a.gradeSlot() != null ? a.gradeSlot() : "-", hour, a.detail()));
			}
			if (anomalies.size() > cap) {
				lines.add(String.format("… 외 %d건", anomalies.size() - cap));
			}
			return lines;
		}
	}
}
