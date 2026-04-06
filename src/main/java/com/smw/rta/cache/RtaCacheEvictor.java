package com.smw.rta.cache;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * RTA 관련 Spring Cache 무효화 훅.
 * <p>
 * {@code CACHE_NAMES} 에 캐시 이름을 넣으면 배치(스냅샷·raw·시너지·티어/컷 등) 후 자동으로 비운다.
 * 현재 비어 있으면 호출은 no-op. {@code RtaServiceImpl} 등에 {@code @Cacheable} 을 붙일 때 이름을 여기에 등록한다.
 */
@Slf4j
@Component
public class RtaCacheEvictor {

	/** {@link com.smw.infra.cache.CacheManagerConfig#shortLivedCacheManager()} 에 등록한 RTA 캐시 이름과 동일 */
	private static final String[] CACHE_NAMES = {
			"rtaDashboardTiers",
			"rtaMatchList",
			"rtaMonster",
			"rtaRanking",
			"rtaSeasons",
	};

	private final CacheManager shortLivedCacheManager;
	private final CacheManager rtaOneHourCacheManager;

	public RtaCacheEvictor(
			@Qualifier("shortLivedCacheManager") CacheManager shortLivedCacheManager,
			@Qualifier("rtaOneHourCacheManager") CacheManager rtaOneHourCacheManager) {
		this.shortLivedCacheManager = shortLivedCacheManager;
		this.rtaOneHourCacheManager = rtaOneHourCacheManager;
	}

	/** 랭크 컷 앵커 캐시({@code rtaRankCutoffLive}) — 배치 재적재 후 호출 */
	public void evictRtaRankCutoffLiveCache() {
		Cache cache = rtaOneHourCacheManager.getCache("rtaRankCutoffLive");
		if (cache != null) {
			cache.clear();
			log.debug("[rta-cache] cleared: rtaRankCutoffLive");
		}
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
