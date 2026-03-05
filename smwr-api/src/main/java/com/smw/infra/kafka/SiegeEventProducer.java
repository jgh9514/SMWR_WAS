package com.smw.infra.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.smw.common.event.SiegeUploadedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SiegeEventProducer {

	private final KafkaTemplate<Object, Object> kafkaTemplate;

	@Value("${smw.kafka.enabled:false}")
	private boolean enabled;

	@Value("${smw.kafka.topics.siege-uploaded:smwr.siege.uploaded}")
	private String siegeUploadedTopic;

	public void publishSiegeUploaded(SiegeUploadedEvent event) {
		if (!enabled) return;

		try {
			kafkaTemplate.send(siegeUploadedTopic, event);
		} catch (Exception e) {
			// 업로드 자체는 DB 트랜잭션이 우선이라 이벤트 실패는 로그만 남김
			log.warn("Kafka publish failed: topic={}, event={}", siegeUploadedTopic, event, e);
		}
	}
}

