package com.smw.rta.service;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.smw.rta.mapper.RtaMapper;

/**
 * 랭크 컷 앵커는 티어 분포와 동일한 시즌 구간({@code rta_match.played_at})으로 라이브 집계한다. 1시간 캐시.
 * 배치 {@code rta_rank_cutoff_anchor_snap} 은 별도 스냅 용도이며 대시보드 API는 라이브 경로를 사용한다.
 */
@Service
public class RtaRankCutoffLiveCacheService {

	@Autowired
	private RtaMapper rtaMapper;

	@Cacheable(cacheNames = "rtaRankCutoffLive", cacheManager = "rtaOneHourCacheManager",
			key = "'anchors_' + (#seasonCode != null && !#seasonCode.isEmpty() ? #seasonCode : '_all')")
	public List<Map<String, Object>> getAnchors(String seasonCode, Timestamp seasonStart, Timestamp seasonEnd) {
		List<Map<String, Object>> rows = rtaMapper.getRtaRankCutoffAnchorsFromLive(seasonStart, seasonEnd);
		return rows != null ? rows : Collections.emptyList();
	}
}
