package com.smw.monster.util;

import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class SlackNotifier {

	/**
	 * 관리자 Slack 테스트 API 등에서 사용. {@link #send(String, String, String)} 는 결과를 로그만 남긴다.
	 */
	public SendOutcome sendWithOutcome(String token, String channelId, String message) {
		if (token == null || token.isBlank() || channelId == null || channelId.isBlank()) {
			return SendOutcome.notConfigured();
		}
		Slack slack = Slack.getInstance();
		try {
			var resp = slack.methods(token).chatPostMessage(
					ChatPostMessageRequest.builder()
							.channel(channelId)
							.text(message)
							.build());
			if (!resp.isOk()) {
				return SendOutcome.failure("Slack API: " + resp.getError());
			}
			return SendOutcome.ok();
		} catch (IOException | SlackApiException e) {
			return SendOutcome.failure(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
		} finally {
			try {
				slack.close();
			} catch (Exception e) {
				log.debug("[slack] Slack 인스턴스 close 실패", e);
			}
		}
	}

	public void send(String token, String channelId, String message) {
		SendOutcome o = sendWithOutcome(token, channelId, message);
		if (!o.isConfigured()) {
			log.debug("[slack] token 또는 channelId 미설정 — 알림 생략");
		} else if (!o.isSuccess()) {
			log.warn("[slack] 메시지 전송 실패: {}", o.getDetail());
		}
	}

	/** {@link #sendWithOutcome} 결과 — JSON API 응답에 실을 수 있음(토큰 미포함). */
	public static final class SendOutcome {
		private final boolean configured;
		private final boolean success;
		private final String detail;

		private SendOutcome(boolean configured, boolean success, String detail) {
			this.configured = configured;
			this.success = success;
			this.detail = detail;
		}

		public static SendOutcome notConfigured() {
			return new SendOutcome(false, false, "smw.rta.batch.slack-token / slack-channel-id 가 비어 있습니다.");
		}

		public static SendOutcome ok() {
			return new SendOutcome(true, true, "chat.postMessage 성공");
		}

		public static SendOutcome failure(String detail) {
			return new SendOutcome(true, false, detail);
		}

		public boolean isConfigured() {
			return configured;
		}

		public boolean isSuccess() {
			return success;
		}

		public String getDetail() {
			return detail;
		}
	}
}
