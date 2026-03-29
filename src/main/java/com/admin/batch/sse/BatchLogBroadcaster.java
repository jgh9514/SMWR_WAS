package com.admin.batch.sse;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 수동 배치 실행 시 클라이언트(SSE)로 로그 라인을 전달한다.
 */
@Slf4j
@Component
public class BatchLogBroadcaster {

	private static final long SSE_TIMEOUT_MS = 30L * 60 * 1000;

	private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();
	private final ObjectMapper objectMapper = new ObjectMapper();

	public SseEmitter register(String streamId) {
		if (streamId == null || streamId.isBlank()) {
			throw new IllegalArgumentException("streamId required");
		}
		SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
		emitters.put(streamId, emitter);
		Runnable cleanup = () -> emitters.remove(streamId, emitter);
		emitter.onCompletion(cleanup);
		emitter.onTimeout(cleanup);
		emitter.onError(e -> cleanup.run());
		return emitter;
	}

	public void sendLog(String streamId, String line) {
		SseEmitter emitter = emitters.get(streamId);
		if (emitter == null) {
			return;
		}
		try {
			emitter.send(SseEmitter.event().name("log").data(line, MediaType.TEXT_PLAIN));
		} catch (IOException e) {
			log.debug("SSE 로그 전송 실패, 연결 종료 처리 streamId={}", streamId, e);
			emitters.remove(streamId);
		}
	}

	public void complete(String streamId, String status) {
		SseEmitter emitter = emitters.remove(streamId);
		if (emitter == null) {
			return;
		}
		try {
			String payload = objectMapper.writeValueAsString(Map.of("status", status));
			emitter.send(SseEmitter.event().name("done").data(payload, MediaType.APPLICATION_JSON));
			emitter.complete();
		} catch (Exception e) {
			log.debug("SSE 완료 이벤트 전송 실패 streamId={}", streamId, e);
			emitter.completeWithError(e);
		}
	}
}
