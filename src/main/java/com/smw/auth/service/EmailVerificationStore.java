package com.smw.auth.service;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * 이메일 인증 코드·완료 상태 저장 — Redis(Pod 공유) 우선, 없으면 인메모리 폴백.
 */
@Slf4j
@Component
public class EmailVerificationStore {

	private static final String KEY_CODE = "smw:email:verify:code:";
	private static final String KEY_DONE = "smw:email:verify:done:";
	private static final String KEY_FAIL = "smw:email:verify:fail:";
	private static final String KEY_RATE_EMAIL_LAST = "smw:email:verify:rate:email:last:";
	private static final String KEY_RATE_EMAIL_HOUR = "smw:email:verify:rate:email:hour:";
	private static final String KEY_RATE_IP_HOUR = "smw:email:verify:rate:ip:hour:";

	@Autowired(required = false)
	private StringRedisTemplate redis;

	@PostConstruct
	void logBackend() {
		if (redis != null) {
			log.info("이메일 인증 저장소: Redis (Pod 간 공유)");
		} else {
			log.warn("이메일 인증 저장소: 인메모리 폴백 — Redis 미연결 시 재배포·다중 Pod에서 인증 코드 불일치 가능");
		}
	}

	private final Map<String, CodeEntry> memCodes = new ConcurrentHashMap<>();
	private final Map<String, Long> memVerified = new ConcurrentHashMap<>();
	private final Map<String, Integer> memFailCounts = new ConcurrentHashMap<>();
	private final Map<String, RateState> memRateByEmail = new ConcurrentHashMap<>();
	private final Map<String, RateState> memRateByIp = new ConcurrentHashMap<>();

	private static class CodeEntry {
		final String code;
		final long expiresAtMs;

		CodeEntry(String code, long expiresAtMs) {
			this.code = code;
			this.expiresAtMs = expiresAtMs;
		}
	}

	private static class RateState {
		volatile long windowStartMs = 0;
		volatile int windowCount = 0;
		volatile long lastSentAtMs = 0;
	}

	public static String normalizeEmail(String email) {
		if (email == null) {
			return "";
		}
		return email.trim().toLowerCase(Locale.ROOT);
	}

	public void saveCode(String email, String code, long expiresAtMs) {
		String key = normalizeEmail(email);
		if (key.isEmpty()) {
			return;
		}
		long ttlMs = Math.max(1000L, expiresAtMs - System.currentTimeMillis());
		if (useRedis()) {
			try {
				redis.opsForValue().set(KEY_CODE + key, code, Duration.ofMillis(ttlMs));
				return;
			} catch (Exception e) {
				log.warn("Redis 인증 코드 저장 실패, 인메모리 폴백: email={}", key, e);
			}
		}
		memCodes.put(key, new CodeEntry(code, expiresAtMs));
	}

	public String getCodeIfValid(String email) {
		String key = normalizeEmail(email);
		if (key.isEmpty()) {
			return null;
		}
		if (useRedis()) {
			try {
				String code = redis.opsForValue().get(KEY_CODE + key);
				if (code != null && !code.isBlank()) {
					Long ttl = redis.getExpire(KEY_CODE + key);
					if (ttl != null && ttl <= 0) {
						redis.delete(KEY_CODE + key);
						return null;
					}
					return code;
				}
				return null;
			} catch (Exception e) {
				log.warn("Redis 인증 코드 조회 실패, 인메모리 폴백: email={}", key, e);
			}
		}
		CodeEntry entry = memCodes.get(key);
		if (entry == null) {
			return null;
		}
		if (System.currentTimeMillis() > entry.expiresAtMs) {
			memCodes.remove(key);
			return null;
		}
		return entry.code;
	}

	public void removeCode(String email) {
		String key = normalizeEmail(email);
		if (key.isEmpty()) {
			return;
		}
		if (useRedis()) {
			try {
				redis.delete(KEY_CODE + key);
			} catch (Exception e) {
				log.warn("Redis 인증 코드 삭제 실패: email={}", key, e);
			}
		}
		memCodes.remove(key);
	}

	public void markVerified(String email, long validForMs) {
		String key = normalizeEmail(email);
		if (key.isEmpty()) {
			return;
		}
		if (useRedis()) {
			try {
				redis.opsForValue().set(KEY_DONE + key, String.valueOf(System.currentTimeMillis()),
					Duration.ofMillis(validForMs));
				return;
			} catch (Exception e) {
				log.warn("Redis 인증 완료 저장 실패, 인메모리 폴백: email={}", key, e);
			}
		}
		memVerified.put(key, System.currentTimeMillis());
	}

	public boolean isVerified(String email, long validForMs) {
		String key = normalizeEmail(email);
		if (key.isEmpty()) {
			return false;
		}
		if (useRedis()) {
			try {
				String verifiedAt = redis.opsForValue().get(KEY_DONE + key);
				if (verifiedAt == null || verifiedAt.isBlank()) {
					return false;
				}
				Long ttl = redis.getExpire(KEY_DONE + key);
				return ttl == null || ttl > 0;
			} catch (Exception e) {
				log.warn("Redis 인증 완료 조회 실패, 인메모리 폴백: email={}", key, e);
			}
		}
		Long verifiedAt = memVerified.get(key);
		if (verifiedAt == null) {
			return false;
		}
		if (System.currentTimeMillis() - verifiedAt > validForMs) {
			memVerified.remove(key);
			return false;
		}
		return true;
	}

	public void removeVerified(String email) {
		String key = normalizeEmail(email);
		if (key.isEmpty()) {
			return;
		}
		if (useRedis()) {
			try {
				redis.delete(KEY_DONE + key);
			} catch (Exception e) {
				log.warn("Redis 인증 완료 삭제 실패: email={}", key, e);
			}
		}
		memVerified.remove(key);
	}

	public int incrementFailCount(String email, long ttlMs) {
		String key = normalizeEmail(email);
		if (key.isEmpty()) {
			return 0;
		}
		if (useRedis()) {
			try {
				Long count = redis.opsForValue().increment(KEY_FAIL + key);
				if (count != null && count == 1L) {
					redis.expire(KEY_FAIL + key, Duration.ofMillis(ttlMs));
				}
				return count != null ? count.intValue() : 0;
			} catch (Exception e) {
				log.warn("Redis 인증 실패 카운트 증가 실패, 인메모리 폴백: email={}", key, e);
			}
		}
		return memFailCounts.merge(key, 1, Integer::sum);
	}

	public void clearFailCount(String email) {
		String key = normalizeEmail(email);
		if (key.isEmpty()) {
			return;
		}
		if (useRedis()) {
			try {
				redis.delete(KEY_FAIL + key);
			} catch (Exception e) {
				log.warn("Redis 인증 실패 카운트 삭제 실패: email={}", key, e);
			}
		}
		memFailCounts.remove(key);
	}

	public int getFailCount(String email) {
		String key = normalizeEmail(email);
		if (key.isEmpty()) {
			return 0;
		}
		if (useRedis()) {
			try {
				String val = redis.opsForValue().get(KEY_FAIL + key);
				if (val == null || val.isBlank()) {
					return 0;
				}
				return Integer.parseInt(val);
			} catch (Exception e) {
				log.warn("Redis 인증 실패 카운트 조회 실패, 인메모리 폴백: email={}", key, e);
			}
		}
		return memFailCounts.getOrDefault(key, 0);
	}

	public boolean tryConsumeSendRate(String emailKey, String clientIp, long nowMs,
			int minIntervalSeconds, int maxPerHourPerEmail, int maxPerHourPerIp) {
		if (useRedis()) {
			try {
				if (!consumeRateRedis(KEY_RATE_EMAIL_LAST + emailKey, KEY_RATE_EMAIL_HOUR + emailKey,
					nowMs, minIntervalSeconds, maxPerHourPerEmail)) {
					return false;
				}
				if (clientIp != null && !clientIp.isBlank()) {
					return consumeRateRedis(null, KEY_RATE_IP_HOUR + clientIp.trim(),
						nowMs, 0, maxPerHourPerIp);
				}
				return true;
			} catch (Exception e) {
				log.warn("Redis 발송 rate limit 실패, 인메모리 폴백: email={}", emailKey, e);
			}
		}
		if (!consumeRateMemory(memRateByEmail, emailKey, nowMs, minIntervalSeconds, maxPerHourPerEmail)) {
			return false;
		}
		if (clientIp != null && !clientIp.isBlank()) {
			return consumeRateMemory(memRateByIp, clientIp.trim(), nowMs, 0, maxPerHourPerIp);
		}
		return true;
	}

	private boolean consumeRateRedis(String lastKey, String hourKey, long nowMs,
			int minIntervalSeconds, int maxPerHour) {
		if (lastKey != null && minIntervalSeconds > 0) {
			String last = redis.opsForValue().get(lastKey);
			if (last != null && !last.isBlank()) {
				long lastMs = Long.parseLong(last);
				if (nowMs - lastMs < minIntervalSeconds * 1000L) {
					return false;
				}
			}
		}
		if (maxPerHour > 0) {
			String current = redis.opsForValue().get(hourKey);
			int cnt = 0;
			if (current != null && !current.isBlank()) {
				cnt = Integer.parseInt(current);
			}
			if (cnt >= maxPerHour) {
				return false;
			}
			Long count = redis.opsForValue().increment(hourKey);
			if (count != null && count == 1L) {
				redis.expire(hourKey, Duration.ofHours(1));
			}
		}
		if (lastKey != null) {
			redis.opsForValue().set(lastKey, String.valueOf(nowMs), Duration.ofHours(1));
		}
		return true;
	}

	private boolean consumeRateMemory(Map<String, RateState> store, String key, long nowMs,
			int minIntervalSeconds, int maxPerHour) {
		final RateState state = store.computeIfAbsent(key, (k) -> new RateState());
		synchronized (state) {
			if (minIntervalSeconds > 0 && state.lastSentAtMs > 0) {
				if (nowMs - state.lastSentAtMs < minIntervalSeconds * 1000L) {
					return false;
				}
			}
			if (state.windowStartMs == 0 || nowMs - state.windowStartMs >= 60 * 60 * 1000L) {
				state.windowStartMs = nowMs;
				state.windowCount = 0;
			}
			if (maxPerHour > 0 && state.windowCount >= maxPerHour) {
				return false;
			}
			state.windowCount += 1;
			state.lastSentAtMs = nowMs;
			return true;
		}
	}

	private boolean useRedis() {
		return redis != null;
	}
}
