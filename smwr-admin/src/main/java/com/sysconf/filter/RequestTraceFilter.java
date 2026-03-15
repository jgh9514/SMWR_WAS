package com.sysconf.filter;

import java.io.IOException;
import java.util.UUID;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.ThreadContext;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestTraceFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String traceId = request.getHeader("X-Request-Id");
		if (traceId == null || traceId.trim().isEmpty()) {
			traceId = UUID.randomUUID().toString().replace("-", "");
		}

		ThreadContext.put("traceId", traceId);
		response.setHeader("X-Request-Id", traceId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			ThreadContext.remove("traceId");
		}
	}
}

