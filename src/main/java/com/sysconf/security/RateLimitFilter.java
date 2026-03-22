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
 * Rate Limiting 필터
 * IP 기반 요청 제한
 */
@Component
@Order(2)
public class RateLimitFilter implements Filter {

	@Value("${smw.rate-limit.max-requests:100}")
	private int maxRequests;

	@Value("${smw.rate-limit.window-seconds:60}")
	private int windowSeconds;

	@Autowired(required = false)
	private MeterRegistry meterRegistry;

	// IP별 요청 기록
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
			
			// 윈도우가 지났으면 리셋
			if (now - windowStart > windowMs) {
				count = 1;
				windowStart = now;
				return true;
			}

			// 요청 수 체크
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

	/**
	 * 클라이언트 IP 주소 추출
	 */
	private String getClientIp(HttpServletRequest request) {
		String xForwardedFor = request.getHeader("X-Forwarded-For");
		if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
			return xForwardedFor.split(",")[0].trim();
		}

		String xRealIp = request.getHeader("X-Real-IP");
		if (xRealIp != null && !xRealIp.isEmpty()) {
			return xRealIp;
		}

		return request.getRemoteAddr();
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;

		String requestUri = httpRequest.getRequestURI();
		boolean isLoginEndpoint = requestUri != null && requestUri.contains("/login");

		// Actuator 엔드포인트도 제외
		if (requestUri != null && requestUri.startsWith("/actuator/")) {
			chain.doFilter(request, response);
			return;
		}

		// 일반 /api/v1/** 경로는 제외하되, 로그인 API는 예외적으로 Rate Limit 적용
		if (requestUri != null && requestUri.startsWith("/api/v1/") && !isLoginEndpoint) {
			chain.doFilter(request, response);
			return;
		}

		maybeCleanupRequestMap();

		// 로그인 API는 더 엄격한 제한 적용
		int effectiveMaxRequests = isLoginEndpoint ? 10 : maxRequests; // 로그인은 1분에 10회
		int effectiveWindowSeconds = isLoginEndpoint ? 60 : windowSeconds;

		String clientIp = getClientIp(httpRequest);
		RequestRecord record = requestMap.computeIfAbsent(clientIp, k -> new RequestRecord());

		long windowMs = effectiveWindowSeconds * 1000L;
		if (!record.isAllowed(effectiveMaxRequests, windowMs)) {
			recordRateLimitMetric(requestUri, isLoginEndpoint, "blocked");
			httpResponse.setStatus(429); // Too Many Requests
			httpResponse.setContentType("application/json");
			httpResponse.setHeader("Retry-After", String.valueOf(effectiveWindowSeconds));
			httpResponse.setHeader("X-RateLimit-Limit", String.valueOf(effectiveMaxRequests));
			httpResponse.setHeader("X-RateLimit-Remaining", "0");
			httpResponse.setHeader("X-RateLimit-Window-Seconds", String.valueOf(effectiveWindowSeconds));
			httpResponse.getWriter().write("{\"result\":\"FAIL\",\"message\":\"너무 많은 요청이 발생했습니다. 잠시 후 다시 시도해주세요.\"}");
			return;
		}

		recordRateLimitMetric(requestUri, isLoginEndpoint, "allowed");
		httpResponse.setHeader("X-RateLimit-Limit", String.valueOf(effectiveMaxRequests));
		httpResponse.setHeader("X-RateLimit-Remaining", String.valueOf(record.getRemainingRequests(effectiveMaxRequests)));
		httpResponse.setHeader("X-RateLimit-Window-Seconds", String.valueOf(effectiveWindowSeconds));

		chain.doFilter(request, response);
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
		snapshot.put("tracked_ips", requestMap.size());
		snapshot.put("default_max_requests", maxRequests);
		snapshot.put("default_window_seconds", windowSeconds);
		snapshot.put("login_max_requests", 10);
		snapshot.put("login_window_seconds", 60);

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

	private void recordRateLimitMetric(String requestUri, boolean isLoginEndpoint, String outcome) {
		if (meterRegistry == null) {
			return;
		}

		String endpointType = isLoginEndpoint ? "login" : "general";
		String normalizedUri = normalizeUri(requestUri, isLoginEndpoint);
		Counter.builder("smw.rate_limit.requests")
				.description("Rate limit decision count")
				.tag("endpoint_type", endpointType)
				.tag("outcome", outcome)
				.tag("uri", normalizedUri)
				.register(meterRegistry)
				.increment();
	}

	private String normalizeUri(String requestUri, boolean isLoginEndpoint) {
		if (requestUri == null || requestUri.trim().isEmpty()) {
			return "unknown";
		}
		if (isLoginEndpoint) {
			return "/login";
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

