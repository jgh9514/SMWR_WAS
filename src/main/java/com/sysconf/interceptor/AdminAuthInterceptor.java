package com.sysconf.interceptor;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import com.sysconf.annotation.RequireAdmin;
import com.sysconf.security.AdminPrivilegeResolver;

import org.springframework.beans.factory.annotation.Autowired;

import lombok.extern.slf4j.Slf4j;

/**
 * 관리자 전용 API 인증 인터셉터.
 * - @RequireAdmin 어노테이션이 있는 핸들러에서만 동작
 * - 미인증: 401, 관리자 권한 없음: 403
 * - AuthSessionInterceptor가 세션을 주입한 후 실행되므로, 세션 주입은 하지 않음
 */
@Slf4j
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

	@Autowired
	private AdminPrivilegeResolver adminPrivilegeResolver;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		if ("OPTIONS".equals(request.getMethod())) {
			return true;
		}

		if (!requiresAdmin(handler)) {
			return true;
		}

		Object attr = request.getAttribute("userInfo");
		if (!(attr instanceof Map)) {
			log.warn("세션 정보 없음 (RequireAdmin) - URI: {}", request.getRequestURI());
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType("application/json;charset=UTF-8");
			response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"로그인이 필요합니다.\"}");
			response.getWriter().flush();
			return false;
		}

		@SuppressWarnings("unchecked")
		Map<String, Object> userInfo = (Map<String, Object>) attr;
		if (userInfo.get("sess_user_id") == null) {
			log.warn("세션 정보 없음 (RequireAdmin) - URI: {}", request.getRequestURI());
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType("application/json;charset=UTF-8");
			response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"로그인이 필요합니다.\"}");
			response.getWriter().flush();
			return false;
		}

		if (!isAdminUser(userInfo)) {
			log.warn("관리자 권한 없음 - URI: {}, user: {}", request.getRequestURI(), userInfo.get("sess_user_id"));
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.setContentType("application/json;charset=UTF-8");
			response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"관리자 권한이 필요합니다.\"}");
			response.getWriter().flush();
			return false;
		}

		return true;
	}

	private boolean isAdminUser(Map<String, Object> userInfo) {
		return adminPrivilegeResolver.isAdminUser(userInfo);
	}

	private boolean requiresAdmin(Object handler) {
		if (!(handler instanceof HandlerMethod)) {
			return false;
		}
		HandlerMethod hm = (HandlerMethod) handler;
		if (hm.getMethodAnnotation(RequireAdmin.class) != null) {
			return true;
		}
		if (hm.getBeanType().getAnnotation(RequireAdmin.class) != null) {
			return true;
		}
		return false;
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView model) throws Exception {
		// no-op
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
		// no-op (SessionThread 정리는 AuthSessionInterceptor에서 수행)
	}
}
