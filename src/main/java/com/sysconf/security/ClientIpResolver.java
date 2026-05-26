package com.sysconf.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 프록시(LB/Ingress) 뒤에서도 일관된 클라이언트 IP를 추출한다.
 */
public final class ClientIpResolver {

	private ClientIpResolver() {
	}

	public static String resolve(HttpServletRequest request) {
		if (request == null) {
			return "unknown";
		}
		String xForwardedFor = request.getHeader("X-Forwarded-For");
		if (xForwardedFor != null && !xForwardedFor.isBlank()) {
			return xForwardedFor.split(",")[0].trim();
		}
		String xRealIp = request.getHeader("X-Real-IP");
		if (xRealIp != null && !xRealIp.isBlank()) {
			return xRealIp.trim();
		}
		String remote = request.getRemoteAddr();
		return remote != null ? remote : "unknown";
	}
}
