package com.sysconf.interceptor;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import com.sysconf.constants.Constant;
import com.sysconf.util.CookieUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AuthInterceptor extends HandlerInterceptorAdapter {
	
	@Autowired
	private CookieUtil cookieUtil;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		if("OPTIONS".equals(request.getMethod())) {
			return true;
		}

		Map<String, Object> userInfo = cookieUtil.getToken(request);

		if(userInfo == null) {
			log.warn("세션 정보 없음 - URI: {}", request.getRequestURI());
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType("application/json;charset=UTF-8");
			response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"로그인이 필요합니다.\"}");
			response.getWriter().flush();
			return false;
		}
		
		cookieUtil.extendToken(request, response, Constant.LOGIN_TOKEN_NAME);

		Map<String, Object> userMap = new HashMap<>(); 
		userMap.put("sess_user_id", userInfo.get("user_id"));
		userMap.put("sess_lang_cd", userInfo.get("lang_cd"));
		userMap.put("sess_corg_no", userInfo.get("corg_no"));
		userMap.put("sess_role", userInfo.get("roles"));
		// siege_view_scope가 없으면 기본값 'C' (현재 시즌만)
		Object siegeViewScope = userInfo.get("siege_view_scope");
		userMap.put("siege_view_scope", siegeViewScope != null ? siegeViewScope : "C");
		// 길드 정보 추가
		if (userInfo.get("guild_id") != null) {
			userMap.put("sess_guild_id", userInfo.get("guild_id"));
			userMap.put("sess_guild_name", userInfo.get("guild_name"));
			userMap.put("sess_guild_role", userInfo.get("guild_role"));
		}

		SessionThread.SESSION_USER_INFO.set(userMap);

		request.setAttribute("userInfo", userMap);
		
		// TODO: API 권한 체크 로직 일시 중단
		// 권한 체크가 필요한 경우 여기서 처리
//		Map<String, Object> param = new HashMap<>();
//		param.put("sess_user_id", userMap.get("sess_user_id").toString());
//		param.put("url", request.getRequestURI());
//		List<Map<String, ?>> roleList = apiRoleRepository.selectApiRole(param);
//		if(roleList.size() == 0) {
//			log.info("=============== API 권한 없음... Forbidden!! ===============");
//			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
//			response.setContentType("application/json;charset=UTF-8");
//			response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"접근 권한이 없습니다.\"}");
//			response.getWriter().flush();
//			return false;
//		}

		return true;
	}
	
	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView model) throws Exception {
		// DEBUG 로그 제거
	}
	
	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
		SessionThread.SESSION_USER_INFO.remove();
	}
}

