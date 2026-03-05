package com.sysconf.security;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

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

	// IP별 요청 기록
	private final Map<String, RequestRecord> requestMap = new ConcurrentHashMap<>();

	private static class RequestRecord {
		private int count;
		private long windowStart;

		public RequestRecord() {
			this.count = 1;
			this.windowStart = System.currentTimeMillis();
		}

		public boolean isAllowed(int maxRequests, long windowMs) {
			long now = System.currentTimeMillis();
			
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
		
		// /api/v1/** 경로는 Rate Limit 제외 (프론트엔드 API 요청)
		if (requestUri != null && requestUri.startsWith("/api/v1/")) {
			chain.doFilter(request, response);
			return;
		}
		
		// Actuator 엔드포인트도 제외
		if (requestUri != null && requestUri.startsWith("/actuator/")) {
			chain.doFilter(request, response);
			return;
		}

		// 로그인 API는 더 엄격한 제한 적용
		boolean isLoginEndpoint = requestUri != null && requestUri.contains("/login");
		int effectiveMaxRequests = isLoginEndpoint ? 10 : maxRequests; // 로그인은 1분에 10회
		int effectiveWindowSeconds = isLoginEndpoint ? 60 : windowSeconds;

		String clientIp = getClientIp(httpRequest);
		RequestRecord record = requestMap.computeIfAbsent(clientIp, k -> new RequestRecord());

		long windowMs = effectiveWindowSeconds * 1000L;
		if (!record.isAllowed(effectiveMaxRequests, windowMs)) {
			httpResponse.setStatus(429); // Too Many Requests
			httpResponse.setContentType("application/json");
			httpResponse.getWriter().write("{\"result\":\"FAIL\",\"message\":\"너무 많은 요청이 발생했습니다. 잠시 후 다시 시도해주세요.\"}");
			return;
		}

		chain.doFilter(request, response);
	}
}

