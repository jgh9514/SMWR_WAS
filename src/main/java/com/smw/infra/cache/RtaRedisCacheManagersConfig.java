package com.smw.infra.cache;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * RTA 조회 캐시를 Redis 에 두어 API Pod 간 공유. 배치 Pod 가 무효화 후 워밍업하면 WAS 는 DB 대신 Redis 를 탄다.
 */
@Configuration
@ConditionalOnProperty(prefix = "smw.cache.rta", name = "use-redis", havingValue = "true")
public class RtaRedisCacheManagersConfig {

	@Value("${smw.cache.short-lived.expire-after-write-minutes:5}")
	private long shortLivedExpireAfterWriteMinutes;

	@Value("${smw.cache.rta-list-read.expire-after-write-minutes:15}")
	private long rtaListReadExpireAfterWriteMinutes;

	@Value("${smw.cache.rta.monster.expire-after-write-minutes:60}")
	private long rtaMonsterExpireAfterWriteMinutes;

	/**
	 * 티어·랭킹·시즌 등 5분 TTL. {@code rtaMonster} 는 {@link #rtaMonsterCacheManager} 로 분리(1h).
	 */
	@Bean("rtaShortLivedCacheManager")
	public CacheManager rtaShortLivedCacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
		return buildManager(connectionFactory, objectMapper,
				Arrays.asList("rtaDashboardRankCut", "rtaDashboardTiers", "rtaRanking", "rtaSeasons"),
				Duration.ofMinutes(shortLivedExpireAfterWriteMinutes));
	}

	@Bean("rtaMonsterCacheManager")
	public CacheManager rtaMonsterCacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
		return buildManager(connectionFactory, objectMapper,
				Arrays.asList("rtaMonster"),
				Duration.ofMinutes(rtaMonsterExpireAfterWriteMinutes));
	}

	@Bean("rtaListReadCacheManager")
	public CacheManager rtaListReadCacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
		return buildManager(connectionFactory, objectMapper,
				Arrays.asList("rtaMatchList"),
				Duration.ofMinutes(rtaListReadExpireAfterWriteMinutes));
	}

	@Bean("rtaOneHourCacheManager")
	public CacheManager rtaOneHourCacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
		return buildManager(connectionFactory, objectMapper,
				Arrays.asList("rtaRankCutoffLive"),
				Duration.ofHours(1));
	}

	private static RedisCacheManager buildManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper,
			Iterable<String> cacheNames, Duration ttl) {
		GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
		RedisCacheConfiguration cfg = RedisCacheConfiguration.defaultCacheConfig()
				.entryTtl(ttl)
				.serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
				.serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));
		Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
		for (String name : cacheNames) {
			perCache.put(name, cfg);
		}
		return RedisCacheManager.builder(connectionFactory)
				.cacheDefaults(cfg)
				.withInitialCacheConfigurations(perCache)
				.build();
	}
}
