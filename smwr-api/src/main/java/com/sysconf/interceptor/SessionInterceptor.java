package com.sysconf.interceptor;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import com.sysconf.util.CookieUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * 세션(토큰) 정보를 가능한 경우 항상 SessionThread에 주입합니다.
 * - 인증 강제(차단)는 하지 않음
 * - AuthInterceptor는 보호 경로에서 별도로 인증을 강제함
 */
@Slf4j
@Component
public class SessionInterceptor extends HandlerInterceptorAdapter {

	@Autowired
	private CookieUtil cookieUtil;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		if ("OPTIONS".equals(request.getMethod())) {
			return true;
		}

		Map<String, Object> userInfo = cookieUtil.getToken(request);
		if (userInfo == null) {
			return true;
		}

		// 원본 사용자 정보도 보관 (login-check 등에서 재사용하여 중복 토큰/DB 조회 방지)
		request.setAttribute("userInfoRaw", userInfo);

		Map<String, Object> userMap = new HashMap<>();
		userMap.put("sess_user_id", userInfo.get("user_id"));
		userMap.put("sess_lang_cd", userInfo.get("lang_cd"));
		userMap.put("sess_corg_no", userInfo.get("corg_no"));
		userMap.put("sess_role", userInfo.get("roles"));
		userMap.put("siege_view_scope", userInfo.get("siege_view_scope"));
		if (userInfo.get("guild_id") != null) {
			userMap.put("sess_guild_id", userInfo.get("guild_id"));
			userMap.put("sess_guild_name", userInfo.get("guild_name"));
			userMap.put("sess_guild_role", userInfo.get("guild_role"));
		}

		// 기본값 보정 (일부 쿼리에서 필수)
		if (userMap.get("siege_view_scope") == null) {
			userMap.put("siege_view_scope", "C");
		}

		SessionThread.SESSION_USER_INFO.set(userMap);
		request.setAttribute("userInfo", userMap);

		log.debug("SessionInterceptor - sess_user_id={}, uri={}", userMap.get("sess_user_id"), request.getRequestURI());
		return true;
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView model)
			throws Exception {
		// no-op
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
			throws Exception {
		// 정리는 ApiLoggingInterceptor/AuthInterceptor에서도 수행하지만, 이중 제거는 문제 없음
		SessionThread.SESSION_USER_INFO.remove();
	}
}


