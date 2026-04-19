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
 * 대시보드 랭크 컷: 앵커 스냅 + 시즌별 스냅샷 테이블 — 시즌 PK 단위 캐시.
 */
@Service
public class RtaDashboardRankCutoffCacheService {

	@Autowired
	private RtaMapper rtaMapper;

	@Autowired
	private RtaRankCutoffLiveCacheService rtaRankCutoffLiveCacheService;

	@Cacheable(cacheNames = "rtaDashboardRankCut", cacheManager = "rtaShortLivedCacheManager",
			key = "'rc_' + (#seasonId != null ? #seasonId : 'dflt')")
	public Map<String, Object> getRankCutoffPart(Long seasonId) {
		Long sid = seasonId;
		List<Map<String, Object>> anchors = rtaRankCutoffLiveCacheService.getAnchors();
		List<Map<String, Object>> snap;
		if (sid != null) {
			snap = rtaMapper.getRtaSnapshotRankCutLatest(sid);
			if (snap == null) {
				snap = Collections.emptyList();
			}
		} else {
			snap = Collections.emptyList();
		}
		Map<String, Object> m = new HashMap<>();
		m.put("rank_cutoff_anchors", anchors);
		m.put("snapshot_rank_cut", snap);
		m.put("seasonId", sid);
		return m;
	}
}
