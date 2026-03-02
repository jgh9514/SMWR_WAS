package com.smw.infra.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.smw.infra.kafka.event.SiegeUploadedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smw.kafka.enabled", havingValue = "true")
public class SiegeEventConsumer {

	private final CacheManager cacheManager;

	@KafkaListener(topics = "${smw.kafka.topics.siege-uploaded:smwr.siege.uploaded}")
	public void onSiegeUploaded(SiegeUploadedEvent event) {
		// 여러 WAS 인스턴스가 떠 있을 때, 업로드 발생 시 캐시를 빠르게 동기화(무효화)
		clear("guildSiegeHistory");
		clear("guildSiegeHistoryCount");

		log.info("SiegeUploadedEvent consumed. affectedMatchIds={}, insertedSiegeCount={}, insertedBattleCount={}",
				event != null ? event.getAffectedMatchIds() : null,
				event != null ? event.getInsertedSiegeCount() : null,
				event != null ? event.getInsertedBattleCount() : null);
	}

	private void clear(String cacheName) {
		try {
			Cache cache = cacheManager.getCache(cacheName);
			if (cache != null) cache.clear();
		} catch (Exception e) {
			log.warn("Cache clear failed: {}", cacheName, e);
		}
	}
}

