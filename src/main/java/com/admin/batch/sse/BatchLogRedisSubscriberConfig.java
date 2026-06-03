package com.admin.batch.sse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smw.rta.config.BatchLogProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(RedisConnectionFactory.class)
@ConditionalOnProperty(name = "smw.batch.log.redis-bridge-enabled", havingValue = "true", matchIfMissing = true)
public class BatchLogRedisSubscriberConfig {

	private final BatchLogBroadcaster batchLogBroadcaster;
	private final ObjectMapper objectMapper;
	private final BatchLogProperties batchLogProperties;

	@Bean
	RedisMessageListenerContainer batchLogRedisListenerContainer(RedisConnectionFactory connectionFactory) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		MessageListenerAdapter adapter = new MessageListenerAdapter(this, "onRedisMessage");
		container.addMessageListener(adapter, new ChannelTopic(BatchLogRedisPublisher.CHANNEL));
		log.info("[batch-log-redis] subscribed channel={}", BatchLogRedisPublisher.CHANNEL);
		return container;
	}

	@SuppressWarnings("unused")
	public void onRedisMessage(String body) {
		if (!batchLogProperties.getLog().isRedisBridgeEnabled() || body == null || body.isBlank()) {
			return;
		}
		try {
			BatchLogRedisMessage msg = objectMapper.readValue(body, BatchLogRedisMessage.class);
			if (msg.streamId() == null || msg.streamId().isBlank()) {
				return;
			}
			if ("log".equals(msg.type())) {
				batchLogBroadcaster.deliverLogFromRemote(msg.streamId(), msg.payload());
			} else if ("done".equals(msg.type())) {
				batchLogBroadcaster.deliverDoneFromRemote(msg.streamId(), msg.payload());
			}
		} catch (Exception e) {
			log.debug("[batch-log-redis] message handle failed", e);
		}
	}
}
