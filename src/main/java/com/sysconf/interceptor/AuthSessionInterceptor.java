package com.sysconf.interceptor;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import com.sysconf.annotation.RequireLogin;
import com.sysconf.constants.Constant;
import com.sysconf.util.CookieUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * 일반용 인증 인터셉터 (세션 주입 + 로그인 필수).
 * - 토큰 파싱 후 SessionThread, request에 사용자 정보 주입
 * - @RequireLogin 어노테이션이 있는 핸들러에서만 미인증 시 401 반환
 */
@Slf4j
@Component
public class AuthSessionInterceptor implements HandlerInterceptor {

	@Autowired
	private CookieUtil cookieUtil;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		if ("OPTIONS".equals(request.getMethod())) {
			return true;
		}

		// 1. 토큰 파싱 및 사용자 정보 주입 (SessionInterceptor 로직)
		Map<String, Object> userInfo = cookieUtil.getToken(request);
		if (userInfo != null) {
			request.setAttribute("userInfoRaw", userInfo);

			Map<String, Object> userMap = buildUserMap(userInfo);
			SessionThread.SESSION_USER_INFO.set(userMap);
			request.setAttribute("userInfo", userMap);

			log.debug("AuthSessionInterceptor - sess_user_id={}, uri={}", userMap.get("sess_user_id"), request.getRequestURI());

			// JWT·쿠키 슬라이딩 갱신 (기존 extendToken 은 쿠키 maxAge 만 늘리고 JWT 만료는 유지됨)
			cookieUtil.refreshtoken(request, response, userInfo, Constant.LOGIN_TOKEN_NAME);
		}

		// 2. @RequireLogin 분기: 어노테이션이 있고 사용자 없으면 401
		if (requiresLogin(handler)) {
			Object attr = request.getAttribute("userInfo");
			if (!(attr instanceof Map) || ((Map<?, ?>) attr).get("sess_user_id") == null) {
				log.warn("세션 정보 없음 (RequireLogin) - URI: {}", request.getRequestURI());
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				response.setContentType("application/json;charset=UTF-8");
				response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"로그인이 필요합니다.\"}");
				response.getWriter().flush();
				return false;
			}
		}

		return true;
	}

	private Map<String, Object> buildUserMap(Map<String, Object> userInfo) {
		Map<String, Object> userMap = new HashMap<>();
		userMap.put("sess_user_id", userInfo.get("user_id"));
		userMap.put("sess_lang_cd", userInfo.get("lang_cd"));
		userMap.put("sess_corg_no", userInfo.get("corg_no"));
		userMap.put("sess_role", userInfo.get("roles"));
		Object siegeViewScope = userInfo.get("siege_view_scope");
		userMap.put("siege_view_scope", siegeViewScope != null ? siegeViewScope : "C");
		if (userInfo.get("guild_id") != null) {
			userMap.put("sess_guild_id", userInfo.get("guild_id"));
			userMap.put("sess_guild_name", userInfo.get("guild_name"));
			userMap.put("sess_guild_role", userInfo.get("guild_role"));
		}
		return userMap;
	}

	private boolean requiresLogin(Object handler) {
		if (!(handler instanceof HandlerMethod)) {
			return false;
		}
		HandlerMethod hm = (HandlerMethod) handler;
		if (hm.getMethodAnnotation(RequireLogin.class) != null) {
			return true;
		}
		if (hm.getBeanType().getAnnotation(RequireLogin.class) != null) {
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
		SessionThread.SESSION_USER_INFO.remove();
	}
}
