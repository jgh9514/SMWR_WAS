package com.smw.rta.service;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.smw.rta.mapper.RtaMapper;

/**
 * 대시보드 티어 일별·기간 범위는 라이브 SQL({@code getRtaTierDistributionDailyFromAgg}) + 5분 캐시 (shortLived).
 */
@Service
public class RtaDashboardTierCacheService {

	@Autowired
	private RtaMapper rtaMapper;

	private static final class ResolvedSeason {
		final Timestamp start;
		final Timestamp end;

		ResolvedSeason(Timestamp start, Timestamp end) {
			this.start = start;
			this.end = end;
		}
	}

	private ResolvedSeason resolveSeason(String seasonCode) {
		String code = seasonCode != null ? seasonCode.trim() : "";
		if (code.isEmpty()) {
			code = rtaMapper.selectDefaultSeasonCodeForNow();
		}
		Map<String, Object> row = code != null && !code.isEmpty() ? rtaMapper.selectRtaSeasonBounds(code) : null;
		if (row == null || row.isEmpty()) {
			code = rtaMapper.selectDefaultSeasonCodeForNow();
			row = rtaMapper.selectRtaSeasonBounds(code);
		}
		if (row == null || row.isEmpty()) {
			return new ResolvedSeason(null, null);
		}
		Object s = row.get("startAt");
		if (s == null) {
			s = row.get("start_at");
		}
		Object e = row.get("endAt");
		if (e == null) {
			e = row.get("end_at");
		}
		Timestamp start = toTimestamp(s);
		Timestamp end = toTimestamp(e);
		return new ResolvedSeason(start, end);
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
		return null;
	}

	@Cacheable(cacheNames = "rtaDashboardTiers", cacheManager = "shortLivedCacheManager", key = "'dt_' + #seasonCode")
	public Map<String, Object> getTierPart(String seasonCode) {
		ResolvedSeason se = resolveSeason(seasonCode);
		List<Map<String, Object>> daily = rtaMapper.getRtaTierDistributionDailyFromAgg(se.start, se.end);
		Map<String, Object> dateRange = rtaMapper.getRtaReplayDateRangeFromAgg(se.start, se.end);
		Map<String, Object> m = new HashMap<>();
		m.put("daily_tiers", daily != null ? daily : Collections.emptyList());
		m.put("date_range", dateRange != null ? dateRange : new HashMap<>());
		return m;
	}
}
