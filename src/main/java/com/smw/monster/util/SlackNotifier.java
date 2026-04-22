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

	public void send(String token, String channelId, String message) {
		if (token == null || token.isBlank() || channelId == null || channelId.isBlank()) {
			log.debug("[slack] token 또는 channelId 미설정 — 알림 생략");
			return;
		}
		Slack slack = Slack.getInstance();
		try {
			var resp = slack.methods(token).chatPostMessage(
					ChatPostMessageRequest.builder()
							.channel(channelId)
							.text(message)
							.build());
			if (!resp.isOk()) {
				log.warn("[slack] 메시지 전송 실패: {}", resp.getError());
			}
		} catch (IOException | SlackApiException e) {
			log.warn("[slack] 메시지 전송 중 예외", e);
		} finally {
			try {
				slack.close();
			} catch (Exception e) {
				log.debug("[slack] Slack 인스턴스 close 실패", e);
			}
		}
	}
}
