package com.smw.rta.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.smw.rta.mapper.RtaMapper;

/**
 * 랭크 컷 앵커는 원장 기준 라이브 조회. 1시간 캐시로 부하 완화 (스냅샷 테이블 없음).
 */
@Service
public class RtaRankCutoffLiveCacheService {

	@Autowired
	private RtaMapper rtaMapper;

	@Cacheable(cacheNames = "rtaRankCutoffLive", cacheManager = "rtaOneHourCacheManager", key = "'anchors'")
	public List<Map<String, Object>> getAnchors() {
		List<Map<String, Object>> rows = rtaMapper.getRtaRankCutoffAnchorsFromLive();
		return rows != null ? rows : Collections.emptyList();
	}
}
