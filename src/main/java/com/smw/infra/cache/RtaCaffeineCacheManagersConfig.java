package com.smw.infra.cache;

import java.time.Duration;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * RTA 조회 전용 캐시 — 기본은 Caffeine. {@code smw.cache.rta.use-redis=true} 이면
 * {@link RtaRedisCacheManagersConfig} 가 동일 빈 이름으로 Redis 를 등록한다.
 */
@Configuration
@ConditionalOnProperty(prefix = "smw.cache.rta", name = "use-redis", havingValue = "false", matchIfMissing = true)
public class RtaCaffeineCacheManagersConfig {

	@Value("${smw.cache.short-lived.maximum-size:500}")
	private long shortLivedMaximumSize;

	@Value("${smw.cache.short-lived.expire-after-write-minutes:5}")
	private long shortLivedExpireAfterWriteMinutes;

	@Value("${smw.cache.rta-list-read.expire-after-write-minutes:15}")
	private long rtaListReadExpireAfterWriteMinutes;

	@Value("${smw.cache.rta-list-read.maximum-size:3000}")
	private long rtaListReadMaximumSize;

	@Value("${smw.cache.rta.monster.expire-after-write-minutes:60}")
	private long rtaMonsterExpireAfterWriteMinutes;

	@Value("${smw.cache.rta.monster.maximum-size:2000}")
	private long rtaMonsterMaximumSize;

	@Value("${smw.cache.rta-player.maximum-size:5000}")
	private long rtaPlayerMaximumSize;

	@Value("${smw.cache.rta-player.expire-after-write-minutes:30}")
	private long rtaPlayerExpireAfterWriteMinutes;

	@Bean("rtaShortLivedCacheManager")
	public CacheManager rtaShortLivedCacheManager() {
		CaffeineCacheManager cacheManager = new CaffeineCacheManager();
		cacheManager.setCacheNames(Arrays.asList(
				"rtaDashboardRankCut",
				"rtaRankCutDetail",
				"rtaDashboardTiers",
				"rtaRanking",
				"rtaSeasons"));
		cacheManager.setCaffeine(Caffeine.newBuilder()
				.maximumSize(shortLivedMaximumSize)
				.expireAfterWrite(Duration.ofMinutes(shortLivedExpireAfterWriteMinutes))
				.recordStats());
		return cacheManager;
	}

	@Bean("rtaPlayerCacheManager")
	public CacheManager rtaPlayerCacheManager() {
		CaffeineCacheManager cacheManager = new CaffeineCacheManager();
		cacheManager.setCacheNames(Arrays.asList("rtaPlayerData"));
		cacheManager.setCaffeine(Caffeine.newBuilder()
				.maximumSize(rtaPlayerMaximumSize)
				.expireAfterWrite(Duration.ofMinutes(rtaPlayerExpireAfterWriteMinutes))
				.recordStats());
		return cacheManager;
	}

	@Bean("rtaMonsterCacheManager")
	public CacheManager rtaMonsterCacheManager() {
		CaffeineCacheManager cacheManager = new CaffeineCacheManager();
		cacheManager.setCacheNames(Arrays.asList("rtaMonster"));
		cacheManager.setCaffeine(Caffeine.newBuilder()
				.maximumSize(rtaMonsterMaximumSize)
				.expireAfterWrite(Duration.ofMinutes(rtaMonsterExpireAfterWriteMinutes))
				.recordStats());
		return cacheManager;
	}

	@Bean("rtaListReadCacheManager")
	public CacheManager rtaListReadCacheManager() {
		CaffeineCacheManager cacheManager = new CaffeineCacheManager();
		cacheManager.setCacheNames(Arrays.asList("rtaMatchList"));
		cacheManager.setCaffeine(Caffeine.newBuilder()
				.maximumSize(rtaListReadMaximumSize)
				.expireAfterWrite(Duration.ofMinutes(rtaListReadExpireAfterWriteMinutes))
				.recordStats());
		return cacheManager;
	}

}
