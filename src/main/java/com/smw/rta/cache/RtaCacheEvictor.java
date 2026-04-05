package com.smw.rta.cache;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 과거 RTA 조회 캐시 무효화 훅. 현재 RtaServiceImpl은 캐시 미사용 — 배치 호출은 noop.
 */
@Slf4j
@Component
public class RtaCacheEvictor {

	private static final String[] CACHE_NAMES = {};

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
		log.debug("[rta-cache] 무효화 대상 {}개 (RTA 서비스 캐시 미사용)", CACHE_NAMES.length);
	}
}
