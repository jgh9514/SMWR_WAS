package com.smw.rta.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.smw.rta.mapper.RtaMapper;

/**
 * 랭크 컷 앵커: {@code rta_rank_cutoff_anchor_snap} 배치 적재분만 조회 (API에서 라이브 집계 없음).
 * 1시간 캐시. 앵커는 시즌 무관 단일 집계이므로 seasonId 불필요 — 캐시 키 고정.
 */
@Service
public class RtaRankCutoffLiveCacheService {

	@Autowired
	private RtaMapper rtaMapper;

	@Cacheable(cacheNames = "rtaRankCutoffLive", cacheManager = "rtaOneHourCacheManager", key = "'anchors'")
	public List<Map<String, Object>> getAnchors() {
		List<Map<String, Object>> snap = rtaMapper.getRtaRankCutoffAnchorsFromAgg();
		return snap != null && !snap.isEmpty() ? snap : Collections.emptyList();
	}
}
