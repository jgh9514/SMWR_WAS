package com.sysconf.filter;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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

		request.setAttribute("__trace_id", traceId);
		ThreadContext.put("traceId", traceId);
		response.setHeader("X-Request-Id", traceId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			ThreadContext.remove("traceId");
		}
	}
}

