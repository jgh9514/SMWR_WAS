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
 * 대시보드 티어: {@code rta_agg_tier_daily} + {@code getRtaTierDistributionDaily} 만 (미적재 시 빈 목록, 라이브 폴백 없음) + 5분 캐시.
 */
@Service
public class RtaDashboardTierCacheService {

	@Autowired
	private RtaMapper rtaMapper;

	@Cacheable(cacheNames = "rtaDashboardTiers", cacheManager = "rtaShortLivedCacheManager",
			key = "'dt_' + #seasonId")
	public Map<String, Object> getTierPart(Long seasonId) {
		List<Map<String, Object>> daily;
		if (seasonId == null) {
			daily = Collections.emptyList();
		} else {
			daily = rtaMapper.getRtaTierDistributionDaily(seasonId);
			if (daily == null) {
				daily = Collections.emptyList();
			}
		}
		Map<String, Object> m = new HashMap<>();
		m.put("daily_tiers", daily);
		return m;
	}
}
