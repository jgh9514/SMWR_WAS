package com.smw.monster.schedule;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.actuate.jdbc.DataSourceHealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.smw.monster.util.SlackNotifier;
import com.smw.rta.config.RtaBatchProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * DB 연결 단절을 감지해 Slack으로 1회 알림.
 * DOWN → 알림 전송 후 플래그 세팅(중복 방지). UP 복구 시 플래그 초기화.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbHealthMonitorScheduler {

    private final DataSourceHealthIndicator dataSourceHealthIndicator;
    private final SlackNotifier slackNotifier;
    private final RtaBatchProperties rtaBatchProperties;

    private final AtomicBoolean downNotified = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${smw.db-monitor.check-interval-ms:30000}")
    public void checkDbHealth() {
        Health health;
        try {
            health = dataSourceHealthIndicator.health();
        } catch (Exception e) {
            log.warn("[db-monitor] health 체크 중 예외 발생: {}", e.getMessage());
            notifyIfFirst("DB health 체크 중 예외: " + e.getMessage());
            return;
        }

        if (Status.DOWN.equals(health.getStatus())) {
            notifyIfFirst("DB 연결 단절 감지: " + health.getDetails());
        } else {
            if (downNotified.compareAndSet(true, false)) {
                log.info("[db-monitor] DB 연결 복구됨. 알림 플래그 초기화.");
            }
        }
    }

    private void notifyIfFirst(String reason) {
        if (!downNotified.compareAndSet(false, true)) {
            return;
        }
        log.error("[db-monitor] {}", reason);
        String token = rtaBatchProperties.getSlackToken();
        String channelId = rtaBatchProperties.getSlackChannelId();
        slackNotifier.send(token, channelId, ":red_circle: *[SMWR] DB 연결 단절*\n" + reason);
    }
}
