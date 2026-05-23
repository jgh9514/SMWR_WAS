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
			"rtaRankCutDetail",
			"rtaDashboardTiers",
			"rtaRanking",
			"rtaSeasons",
	};

	private static final String RTA_MONSTER_CACHE_NAME = "rtaMonster";
	private static final String RTA_PLAYER_DATA_CACHE_NAME = "rtaPlayerData";

	private final CacheManager rtaShortLivedCacheManager;
	/** {@code rtaMonster} — 목록/상세/link-preview, TTL 1h ({@code rtaMonsterCacheManager}) */
	private final CacheManager rtaMonsterCacheManager;
	private final CacheManager rtaListReadCacheManager;
	/** {@code rtaPlayerData} — 플레이어 summary/snap, TTL 30분 ({@code rtaPlayerCacheManager}) */
	private final CacheManager rtaPlayerCacheManager;
	private final ObjectProvider<RtaRedisCacheWarmup> rtaRedisCacheWarmup;

	public RtaCacheEvictor(
			@Qualifier("rtaShortLivedCacheManager") CacheManager rtaShortLivedCacheManager,
			@Qualifier("rtaMonsterCacheManager") CacheManager rtaMonsterCacheManager,
			@Qualifier("rtaListReadCacheManager") CacheManager rtaListReadCacheManager,
			@Qualifier("rtaPlayerCacheManager") CacheManager rtaPlayerCacheManager,
			ObjectProvider<RtaRedisCacheWarmup> rtaRedisCacheWarmup) {
		this.rtaShortLivedCacheManager = rtaShortLivedCacheManager;
		this.rtaMonsterCacheManager = rtaMonsterCacheManager;
		this.rtaListReadCacheManager = rtaListReadCacheManager;
		this.rtaPlayerCacheManager = rtaPlayerCacheManager;
		this.rtaRedisCacheWarmup = rtaRedisCacheWarmup;
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
		Cache playerData = rtaPlayerCacheManager.getCache(RTA_PLAYER_DATA_CACHE_NAME);
		if (playerData != null) {
			playerData.clear();
			log.debug("[rta-cache] cleared: {} (rtaPlayerCacheManager)", RTA_PLAYER_DATA_CACHE_NAME);
		}
		log.debug("[rta-cache] evictAllRtaCaches done");
		rtaRedisCacheWarmup.ifAvailable(RtaRedisCacheWarmup::warmAfterEviction);
	}
}
