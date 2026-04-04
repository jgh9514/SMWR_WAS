package com.smw.rta.cache;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * RtaServiceImpl 등에 붙은 RTA 조회 캐시(shortLived) 일괄 무효화.
 * raw 정규화 배치 등으로 DB가 바뀐 뒤 호출한다.
 */
@Slf4j
@Component
public class RtaCacheEvictor {

	private static final String[] CACHE_NAMES = {
			"rtaMatchesCount",
			"rtaStats",
			"rtaMonsterStatsV2",
			"rtaMonsterDetail",
			"rtaDashboardV2",
			"rtaSummonerRanking",
	};

	private final CacheManager shortLivedCacheManager;

	public RtaCacheEvictor(@Qualifier("shortLivedCacheManager") CacheManager shortLivedCacheManager) {
		this.shortLivedCacheManager = shortLivedCacheManager;
	}

	public void evictAllRtaCaches() {
		for (String name : CACHE_NAMES) {
			Cache cache = shortLivedCacheManager.getCache(name);
			if (cache != null) {
				cache.clear();
				log.debug("[rta-cache] cleared: {}", name);
			}
		}
		log.info("[rta-cache] RTA 관련 캐시 {}개 무효화 완료", CACHE_NAMES.length);
	}
}
