package com.sysconf.security;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * CSRF 보호 필터
 * X-Requested-With 헤더 검증
 */
@Component
@Order(1)
public class CsrfTokenFilter implements Filter {

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;

		// GET, HEAD, OPTIONS 요청은 CSRF 검증 제외
		String method = httpRequest.getMethod();
		if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
			chain.doFilter(request, response);
			return;
		}

		// /api/v1/** 경로는 CSRF 검증 제외 (Spring Security CSRF 설정과 일치)
		String requestURI = httpRequest.getRequestURI();
		if (requestURI != null && requestURI.startsWith("/api/v1/")) {
			chain.doFilter(request, response);
			return;
		}

		// X-Requested-With 헤더 검증
		String requestedWith = httpRequest.getHeader("X-Requested-With");
		if (requestedWith == null || !"XMLHttpRequest".equals(requestedWith)) {
			httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
			httpResponse.setContentType("application/json");
			httpResponse.getWriter().write("{\"result\":\"FAIL\",\"message\":\"CSRF 검증에 실패했습니다.\"}");
			return;
		}

		chain.doFilter(request, response);
	}
}

