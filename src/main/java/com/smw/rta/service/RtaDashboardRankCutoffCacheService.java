package com.smw.rta.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.smw.rta.mapper.RtaMapper;

/**
 * 대시보드 랭크 컷: {@code rta_agg_rank_cut_hourly_snap} 에서 6개 시점(현재·3h·6h·12h·3d·7d) 조회 — 시즌 PK 단위 캐시.
 */
@Service
public class RtaDashboardRankCutoffCacheService {

	@Autowired
	private RtaMapper rtaMapper;

	@Cacheable(cacheNames = "rtaDashboardRankCut", cacheManager = "rtaShortLivedCacheManager",
			key = "'rc_' + (#seasonId != null ? #seasonId : 'dflt')")
	public Map<String, Object> getRankCutoffPart(Long seasonId) {
		List<Map<String, Object>> anchors = seasonId != null
				? rtaMapper.getRtaRankCutHourlyForDashboard(seasonId)
				: Collections.emptyList();
		if (anchors == null) {
			anchors = Collections.emptyList();
		}
		Map<String, Object> m = new HashMap<>();
		m.put("rank_cutoff_anchors", anchors);
		m.put("seasonId", seasonId);
		return m;
	}
}
