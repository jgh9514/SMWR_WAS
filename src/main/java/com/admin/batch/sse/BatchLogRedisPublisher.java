package com.admin.batch.sse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smw.rta.config.BatchLogProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 배치 SSE 로그를 Redis pub/sub으로 발행 — smw-batch(실행) → smw-app(SSE) 중계.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(name = "smw.batch.log.redis-bridge-enabled", havingValue = "true", matchIfMissing = true)
public class BatchLogRedisPublisher {

	public static final String CHANNEL = "smw:batch:log:stream";

	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;
	private final BatchLogProperties batchLogProperties;

	public void publishLog(String streamId, String line) {
		if (!batchLogProperties.getLog().isRedisBridgeEnabled()) {
			return;
		}
		publish(BatchLogRedisMessage.log(streamId, line));
	}

	public void publishDone(String streamId, String status) {
		if (!batchLogProperties.getLog().isRedisBridgeEnabled()) {
			return;
		}
		publish(BatchLogRedisMessage.done(streamId, status));
	}

	private void publish(BatchLogRedisMessage message) {
		if (message.streamId() == null || message.streamId().isBlank()) {
			return;
		}
		try {
			redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(message));
		} catch (JsonProcessingException e) {
			log.warn("[batch-log-redis] publish serialize failed streamId={}", message.streamId(), e);
		} catch (Exception e) {
			log.debug("[batch-log-redis] publish failed streamId={}", message.streamId(), e);
		}
	}
}
