package com.smw.rta.service;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.smw.rta.config.RtaDashboardProperties;
import com.smw.rta.mapper.RtaMapper;

/**
 * 랭크 컷 앵커: {@link RtaDashboardProperties#getRankCutAnchorSource()} 에 따라
 * {@code rta_rank_cutoff_anchor_snap}(배치) 또는 {@code rta_match} 라이브.
 * 1시간 캐시.
 */
@Service
public class RtaRankCutoffLiveCacheService {

	@Autowired
	private RtaMapper rtaMapper;

	@Autowired
	private RtaDashboardProperties rtaDashboardProperties;

	@Cacheable(cacheNames = "rtaRankCutoffLive", cacheManager = "rtaOneHourCacheManager",
			key = "'anchors_' + (#seasonCode != null && !#seasonCode.isEmpty() ? #seasonCode : '_all')")
	public List<Map<String, Object>> getAnchors(String seasonCode, Timestamp seasonStart, Timestamp seasonEnd) {
		String src = rtaDashboardProperties.getRankCutAnchorSource();
		if (src == null) {
			src = "snap_then_live";
		}
		src = src.trim().toLowerCase();

		if ("snap".equals(src) || "snap_then_live".equals(src)) {
			List<Map<String, Object>> snap = rtaMapper.getRtaRankCutoffAnchorsFromAgg();
			if (snap != null && !snap.isEmpty()) {
				return snap;
			}
			if ("snap".equals(src)) {
				return Collections.emptyList();
			}
		}

		if ("live".equals(src) || "snap_then_live".equals(src)) {
			List<Map<String, Object>> live = rtaMapper.getRtaRankCutoffAnchorsFromLive(seasonStart, seasonEnd);
			return live != null ? live : Collections.emptyList();
		}

		List<Map<String, Object>> live = rtaMapper.getRtaRankCutoffAnchorsFromLive(seasonStart, seasonEnd);
		return live != null ? live : Collections.emptyList();
	}
}
