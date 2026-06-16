package com.sysconf.security;

import java.io.IOException;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Rate Limiting 필터 — 인증·로그인 API는 엄격, 그 외 일부 경로는 기본 제한.
 */
@Component
@Order(2)
public class RateLimitFilter implements Filter {

	@Value("${smw.rate-limit.max-requests:100}")
	private int maxRequests;

	@Value("${smw.rate-limit.window-seconds:60}")
	private int windowSeconds;

	@Value("${smw.rate-limit.auth-login-max-requests:10}")
	private int authLoginMaxRequests;

	@Value("${smw.rate-limit.auth-login-window-seconds:60}")
	private int authLoginWindowSeconds;

	@Value("${smw.rate-limit.auth-signup-max-requests:10}")
	private int authSignupMaxRequests;

	@Value("${smw.rate-limit.auth-signup-window-seconds:300}")
	private int authSignupWindowSeconds;

	@Value("${smw.rate-limit.auth-user-id-check-max-requests:30}")
	private int authUserIdCheckMaxRequests;

	@Value("${smw.rate-limit.auth-user-id-check-window-seconds:300}")
	private int authUserIdCheckWindowSeconds;

	@Value("${smw.rate-limit.auth-email-max-requests:8}")
	private int authEmailMaxRequests;

	@Value("${smw.rate-limit.auth-email-window-seconds:3600}")
	private int authEmailWindowSeconds;

	@Autowired(required = false)
	private MeterRegistry meterRegistry;

	private final Map<String, RequestRecord> requestMap = new ConcurrentHashMap<>();
	private final AtomicLong requestSequence = new AtomicLong();
	private static final long CLEANUP_INTERVAL = 1000L;

	private static class RequestRecord {
		private int count;
		private long windowStart;
		private long lastSeen;

		public RequestRecord() {
			this.count = 1;
			this.windowStart = System.currentTimeMillis();
			this.lastSeen = this.windowStart;
		}

		public synchronized boolean isAllowed(int maxRequests, long windowMs) {
			long now = System.currentTimeMillis();
			lastSeen = now;

			if (now - windowStart > windowMs) {
				count = 1;
				windowStart = now;
				return true;
			}

			if (count >= maxRequests) {
				return false;
			}

			count++;
			return true;
		}

		public long getLastSeen() {
			return lastSeen;
		}

		public synchronized int getRemainingRequests(int maxRequests) {
			return Math.max(0, maxRequests - count);
		}
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;

		String requestUri = httpRequest.getRequestURI();

		if (requestUri != null && requestUri.startsWith("/actuator/")) {
			chain.doFilter(request, response);
			return;
		}

		AuthEndpointKind authKind = classifyAuthEndpoint(requestUri);
		boolean isLoginEndpoint = authKind == AuthEndpointKind.LOGIN;

		// /api/v1/** 중 인증 민감 경로만 rate limit (나머지 API는 WAS 부하·정상 트래픽 고려해 제외)
		if (requestUri != null && requestUri.startsWith("/api/v1/") && authKind == AuthEndpointKind.NONE && !isLoginEndpoint) {
			chain.doFilter(request, response);
			return;
		}

		maybeCleanupRequestMap();

		int effectiveMaxRequests;
		int effectiveWindowSeconds;
		if (authKind == AuthEndpointKind.LOGIN) {
			effectiveMaxRequests = authLoginMaxRequests;
			effectiveWindowSeconds = authLoginWindowSeconds;
		} else if (authKind == AuthEndpointKind.SIGNUP) {
			effectiveMaxRequests = authSignupMaxRequests;
			effectiveWindowSeconds = authSignupWindowSeconds;
		} else if (authKind == AuthEndpointKind.USER_ID_CHECK) {
			effectiveMaxRequests = authUserIdCheckMaxRequests;
			effectiveWindowSeconds = authUserIdCheckWindowSeconds;
		} else if (authKind == AuthEndpointKind.EMAIL) {
			effectiveMaxRequests = authEmailMaxRequests;
			effectiveWindowSeconds = authEmailWindowSeconds;
		} else if (isLoginEndpoint) {
			effectiveMaxRequests = authLoginMaxRequests;
			effectiveWindowSeconds = authLoginWindowSeconds;
		} else {
			effectiveMaxRequests = maxRequests;
			effectiveWindowSeconds = windowSeconds;
		}

		String clientIp = ClientIpResolver.resolve(httpRequest);
		String rateKey = clientIp + "|" + authKind.name();
		RequestRecord record = requestMap.computeIfAbsent(rateKey, k -> new RequestRecord());

		long windowMs = effectiveWindowSeconds * 1000L;
		if (!record.isAllowed(effectiveMaxRequests, windowMs)) {
			recordRateLimitMetric(requestUri, authKind, "blocked");
			httpResponse.setStatus(429);
			httpResponse.setContentType("application/json;charset=UTF-8");
			httpResponse.setHeader("Retry-After", String.valueOf(effectiveWindowSeconds));
			httpResponse.setHeader("X-RateLimit-Limit", String.valueOf(effectiveMaxRequests));
			httpResponse.setHeader("X-RateLimit-Remaining", "0");
			httpResponse.setHeader("X-RateLimit-Window-Seconds", String.valueOf(effectiveWindowSeconds));
			httpResponse.getWriter().write("{\"result\":\"FAIL\",\"message\":\"너무 많은 요청이 발생했습니다. 잠시 후 다시 시도해주세요.\"}");
			return;
		}

		recordRateLimitMetric(requestUri, authKind, "allowed");
		httpResponse.setHeader("X-RateLimit-Limit", String.valueOf(effectiveMaxRequests));
		httpResponse.setHeader("X-RateLimit-Remaining", String.valueOf(record.getRemainingRequests(effectiveMaxRequests)));
		httpResponse.setHeader("X-RateLimit-Window-Seconds", String.valueOf(effectiveWindowSeconds));

		chain.doFilter(request, response);
	}

	private enum AuthEndpointKind {
		NONE, LOGIN, SIGNUP, USER_ID_CHECK, EMAIL
	}

	private static AuthEndpointKind classifyAuthEndpoint(String uri) {
		if (uri == null || uri.isBlank()) {
			return AuthEndpointKind.NONE;
		}
		if (!uri.startsWith("/api/v1/auth")) {
			if (uri.contains("/login")) {
				return AuthEndpointKind.LOGIN;
			}
			return AuthEndpointKind.NONE;
		}
		if (uri.contains("/email/")) {
			return AuthEndpointKind.EMAIL;
		}
		if (uri.contains("/user-id/check")) {
			return AuthEndpointKind.USER_ID_CHECK;
		}
		if (uri.contains("/signup") || uri.contains("/mobile-biometric-login")) {
			return AuthEndpointKind.SIGNUP;
		}
		if (uri.contains("/login")) {
			return AuthEndpointKind.LOGIN;
		}
		return AuthEndpointKind.NONE;
	}

	private void maybeCleanupRequestMap() {
		long currentSequence = requestSequence.incrementAndGet();
		if (currentSequence % CLEANUP_INTERVAL != 0) {
			return;
		}

		long now = System.currentTimeMillis();
		long staleThresholdMs = Math.max(windowSeconds * 1000L * 2, 300_000L);
		requestMap.entrySet().removeIf(entry -> now - entry.getValue().getLastSeen() > staleThresholdMs);
	}

	public Map<String, Object> getSnapshot() {
		maybeCleanupRequestMap();
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("enabled", true);
		snapshot.put("tracked_keys", requestMap.size());
		snapshot.put("default_max_requests", maxRequests);
		snapshot.put("default_window_seconds", windowSeconds);
		snapshot.put("auth_login_max_requests", authLoginMaxRequests);
		snapshot.put("auth_login_window_seconds", authLoginWindowSeconds);
		snapshot.put("auth_signup_max_requests", authSignupMaxRequests);
		snapshot.put("auth_signup_window_seconds", authSignupWindowSeconds);
		snapshot.put("auth_user_id_check_max_requests", authUserIdCheckMaxRequests);
		snapshot.put("auth_user_id_check_window_seconds", authUserIdCheckWindowSeconds);
		snapshot.put("auth_email_max_requests", authEmailMaxRequests);
		snapshot.put("auth_email_window_seconds", authEmailWindowSeconds);

		long now = System.currentTimeMillis();
		int activeWindows = 0;
		for (RequestRecord record : requestMap.values()) {
			if (now - record.getLastSeen() <= Math.max(windowSeconds * 1000L, 60_000L)) {
				activeWindows++;
			}
		}
		snapshot.put("active_windows", activeWindows);
		return snapshot;
	}

	private void recordRateLimitMetric(String requestUri, AuthEndpointKind kind, String outcome) {
		if (meterRegistry == null) {
			return;
		}

		String endpointType = kind == AuthEndpointKind.NONE ? "general" : kind.name().toLowerCase();
		String normalizedUri = normalizeUri(requestUri, kind);
		Counter.builder("smw.rate_limit.requests")
				.description("Rate limit decision count")
				.tag("endpoint_type", endpointType)
				.tag("outcome", outcome)
				.tag("uri", normalizedUri)
				.register(meterRegistry)
				.increment();
	}

	private String normalizeUri(String requestUri, AuthEndpointKind kind) {
		if (requestUri == null || requestUri.trim().isEmpty()) {
			return "unknown";
		}
		if (kind == AuthEndpointKind.LOGIN) {
			return "/api/v1/auth/login";
		}
		if (kind == AuthEndpointKind.SIGNUP) {
			return "/api/v1/auth/signup";
		}
		if (kind == AuthEndpointKind.USER_ID_CHECK) {
			return "/api/v1/auth/user-id/check";
		}
		if (kind == AuthEndpointKind.EMAIL) {
			return "/api/v1/auth/email";
		}

		String[] parts = requestUri.split("/");
		StringBuilder normalized = new StringBuilder();
		for (String part : parts) {
			if (part == null || part.isEmpty()) {
				continue;
			}
			normalized.append("/");
			normalized.append(isVariableSegment(part) ? "{id}" : part);
		}
		return normalized.length() > 0 ? normalized.toString() : "/";
	}

	private boolean isVariableSegment(String segment) {
		if (segment == null || segment.isEmpty()) {
			return false;
		}
		if (segment.matches("\\d+")) {
			return true;
		}
		return segment.matches("[0-9a-fA-F\\-]{16,}");
	}
}
