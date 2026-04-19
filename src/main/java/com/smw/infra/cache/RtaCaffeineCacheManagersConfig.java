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

	@Bean("rtaShortLivedCacheManager")
	public CacheManager rtaShortLivedCacheManager() {
		CaffeineCacheManager cacheManager = new CaffeineCacheManager();
		cacheManager.setCacheNames(Arrays.asList(
				"rtaDashboardRankCut",
				"rtaDashboardTiers",
				"rtaMonster",
				"rtaRanking",
				"rtaSeasons"));
		cacheManager.setCaffeine(Caffeine.newBuilder()
				.maximumSize(shortLivedMaximumSize)
				.expireAfterWrite(Duration.ofMinutes(shortLivedExpireAfterWriteMinutes))
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

	@Bean("rtaOneHourCacheManager")
	public CacheManager rtaOneHourCacheManager() {
		CaffeineCacheManager cacheManager = new CaffeineCacheManager();
		cacheManager.setCacheNames(Arrays.asList("rtaRankCutoffLive"));
		cacheManager.setCaffeine(Caffeine.newBuilder()
				.maximumSize(20)
				.expireAfterWrite(Duration.ofHours(1))
				.recordStats());
		return cacheManager;
	}
}
