package com.smw.rta.cache;

import org.springframework.beans.factory.ObjectProvider;
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

	/** {@link com.smw.infra.cache.RtaCaffeineCacheManagersConfig#rtaShortLivedCacheManager} RTA 5분 대 캐시(몬스터 제외) */
	private static final String[] SHORT_LIVED_RTA_CACHE_NAMES = {
			"rtaDashboardRankCut",
			"rtaDashboardTiers",
			"rtaRanking",
			"rtaSeasons",
	};

	private static final String RTA_MONSTER_CACHE_NAME = "rtaMonster";

	private final CacheManager rtaShortLivedCacheManager;
	/** {@code rtaMonster} — 목록/상세/link-preview, TTL 1h ({@code rtaMonsterCacheManager}) */
	private final CacheManager rtaMonsterCacheManager;
	private final CacheManager rtaListReadCacheManager;
	private final CacheManager rtaOneHourCacheManager;
	private final ObjectProvider<RtaRedisCacheWarmup> rtaRedisCacheWarmup;

	public RtaCacheEvictor(
			@Qualifier("rtaShortLivedCacheManager") CacheManager rtaShortLivedCacheManager,
			@Qualifier("rtaMonsterCacheManager") CacheManager rtaMonsterCacheManager,
			@Qualifier("rtaListReadCacheManager") CacheManager rtaListReadCacheManager,
			@Qualifier("rtaOneHourCacheManager") CacheManager rtaOneHourCacheManager,
			ObjectProvider<RtaRedisCacheWarmup> rtaRedisCacheWarmup) {
		this.rtaShortLivedCacheManager = rtaShortLivedCacheManager;
		this.rtaMonsterCacheManager = rtaMonsterCacheManager;
		this.rtaListReadCacheManager = rtaListReadCacheManager;
		this.rtaOneHourCacheManager = rtaOneHourCacheManager;
		this.rtaRedisCacheWarmup = rtaRedisCacheWarmup;
	}

	/** 랭크 컷 앵커 캐시({@code rtaRankCutoffLive}) — 배치 재적재 후 호출 */
	public void evictRtaRankCutoffLiveCache() {
		Cache cache = rtaOneHourCacheManager.getCache("rtaRankCutoffLive");
		if (cache != null) {
			cache.clear();
			log.debug("[rta-cache] cleared: rtaRankCutoffLive");
		}
	}

	/**
	 * 몬스터 통계/상세/link-preview 캐시만 비움. {@link com.smw.monster.batch.RtaMonsterStatsTierTopSnapJob} 티어 top 스냅
	 * 적재 후·시너지와 무관하게 1h 주기로 호출.
	 */
	public void evictRtaMonsterReadCache() {
		Cache c = rtaMonsterCacheManager.getCache(RTA_MONSTER_CACHE_NAME);
		if (c != null) {
			c.clear();
			log.debug("[rta-cache] cleared: {} (rtaMonsterCacheManager)", RTA_MONSTER_CACHE_NAME);
		}
	}

	public void evictAllRtaCaches() {
		for (String name : SHORT_LIVED_RTA_CACHE_NAMES) {
			Cache cache = rtaShortLivedCacheManager.getCache(name);
			if (cache != null) {
				cache.clear();
				log.debug("[rta-cache] cleared: {}", name);
			}
		}
		Cache monster = rtaMonsterCacheManager.getCache(RTA_MONSTER_CACHE_NAME);
		if (monster != null) {
			monster.clear();
			log.debug("[rta-cache] cleared: {} (rtaMonsterCacheManager)", RTA_MONSTER_CACHE_NAME);
		}
		Cache matchList = rtaListReadCacheManager.getCache("rtaMatchList");
		if (matchList != null) {
			matchList.clear();
			log.debug("[rta-cache] cleared: rtaMatchList (rtaListReadCacheManager)");
		}
		evictRtaRankCutoffLiveCache();
		log.debug("[rta-cache] short-lived {} + rtaMonster + rtaMatchList + rtaRankCutoffLive 무효화", SHORT_LIVED_RTA_CACHE_NAMES.length);
		rtaRedisCacheWarmup.ifAvailable(RtaRedisCacheWarmup::warmAfterEviction);
	}
}
