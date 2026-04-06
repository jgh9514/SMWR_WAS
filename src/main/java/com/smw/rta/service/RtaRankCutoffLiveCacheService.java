package com.smw.rta.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.smw.rta.mapper.RtaMapper;

/**
 * 랭크 컷 앵커는 {@code rta_rank_cutoff_anchor_snap} 배치 적재 결과를 조회한다. 1시간 캐시.
 * 테이블이 비어 있으면 빈 목록 — {@link com.smw.monster.batch.RtaRankCutSnapshotAggJob} 실행 필요.
 */
@Service
public class RtaRankCutoffLiveCacheService {

	@Autowired
	private RtaMapper rtaMapper;

	@Cacheable(cacheNames = "rtaRankCutoffLive", cacheManager = "rtaOneHourCacheManager", key = "'anchors'")
	public List<Map<String, Object>> getAnchors() {
		List<Map<String, Object>> rows = rtaMapper.getRtaRankCutoffAnchorsFromAgg();
		return rows != null ? rows : Collections.emptyList();
	}
}
