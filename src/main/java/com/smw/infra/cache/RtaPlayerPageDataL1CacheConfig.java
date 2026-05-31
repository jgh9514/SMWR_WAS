package com.smw.infra.cache;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * page-data 전용 프로세스 로컬 L1 — Redis RTT·역직렬화를 건너뛰어 p99를 줄인다.
 * Redis(L2)는 Pod 간 공유·배치 워밍용.
 */
@Configuration
public class RtaPlayerPageDataL1CacheConfig {

	public static final String CACHE_NAME = "rtaPlayerPageDataL1";

	@Value("${smw.cache.rta-player.page-data-l1.maximum-size:4000}")
	private long maximumSize;

	@Value("${smw.cache.rta-player.page-data-l1.expire-after-write-minutes:5}")
	private long expireAfterWriteMinutes;

	@Bean(name = "rtaPlayerPageDataL1Cache")
	public Cache rtaPlayerPageDataL1Cache() {
		CaffeineCacheManager manager = new CaffeineCacheManager(CACHE_NAME);
		manager.setCaffeine(Caffeine.newBuilder()
				.maximumSize(maximumSize)
				.expireAfterWrite(Duration.ofMinutes(expireAfterWriteMinutes))
				.recordStats());
		return manager.getCache(CACHE_NAME);
	}
}
