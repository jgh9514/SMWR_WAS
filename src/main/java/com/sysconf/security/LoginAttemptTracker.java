package com.sysconf.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 로그인 실패 횟수(IP+user_id) 기반 일시 차단 — DB 스키마 변경 없이 브루트포스 완화.
 */
@Component
public class LoginAttemptTracker {

	@Value("${smw.security.auth.login-max-attempts:5}")
	private int maxAttempts;

	@Value("${smw.security.auth.login-lock-minutes:15}")
	private int lockMinutes;

	@Value("${smw.security.auth.login-failure-delay-ms-min:300}")
	private int failureDelayMsMin;

	@Value("${smw.security.auth.login-failure-delay-ms-max:800}")
	private int failureDelayMsMax;

	private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();

	public boolean isBlocked(String clientIp, String userId) {
		AttemptState state = attempts.get(key(clientIp, userId));
		if (state == null) {
			return false;
		}
		long now = System.currentTimeMillis();
		if (state.lockedUntilMs > now) {
			return true;
		}
		if (state.lockedUntilMs > 0 && state.lockedUntilMs <= now) {
			attempts.remove(key(clientIp, userId));
		}
		return false;
	}

	public void onSuccess(String clientIp, String userId) {
		attempts.remove(key(clientIp, userId));
	}

	public void onFailure(String clientIp, String userId) {
		String k = key(clientIp, userId);
		AttemptState state = attempts.computeIfAbsent(k, ignored -> new AttemptState());
		long now = System.currentTimeMillis();
		synchronized (state) {
			state.failCount++;
			if (state.failCount >= maxAttempts) {
				state.lockedUntilMs = now + lockMinutes * 60_000L;
			}
		}
		applyFailureDelay();
	}

	public void applyFailureDelay() {
		int min = Math.max(0, failureDelayMsMin);
		int max = Math.max(min, failureDelayMsMax);
		if (max == 0) {
			return;
		}
		int delay = min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
		try {
			Thread.sleep(delay);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static String key(String clientIp, String userId) {
		String ip = clientIp != null ? clientIp : "unknown";
		String uid = userId != null ? userId.trim().toLowerCase() : "";
		return ip + "|" + uid;
	}

	private static final class AttemptState {
		private int failCount;
		private long lockedUntilMs;
	}
}
