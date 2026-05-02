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
 * 랭크 컷 상세: {@code rta_agg_rank_cut_hourly_snap} 에서 시즌 전체 일별 히스토리 조회 — 시즌 PK 단위 캐시.
 */
@Service
public class RtaRankCutDetailCacheService {

	@Autowired
	private RtaMapper rtaMapper;

	@Cacheable(cacheNames = "rtaRankCutDetail", cacheManager = "rtaShortLivedCacheManager",
			key = "'rcd_' + (#seasonId != null ? #seasonId : 'dflt')")
	public Map<String, Object> getRankCutDetail(Long seasonId) {
		List<Map<String, Object>> daily = seasonId != null
				? rtaMapper.getRtaRankCutHourlyDaily(seasonId)
				: Collections.emptyList();
		if (daily == null) {
			daily = Collections.emptyList();
		}
		Map<String, Object> m = new HashMap<>();
		m.put("daily", daily);
		m.put("seasonId", seasonId);
		return m;
	}
}
