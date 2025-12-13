package com.sysconf.interceptor;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import com.admin.log.service.LogService;
import com.sysconf.util.CookieUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ApiLoggingInterceptor extends HandlerInterceptorAdapter {

	@Autowired
	LogService logService;
	
	@Autowired
	private CookieUtil cookieUtil;

	@SuppressWarnings("unchecked")
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		// DEBUG 로그 제거 - 필요시 TRACE 레벨로 변경 가능

		if("OPTIONS".equals(request.getMethod())) {
			return true;
		}

		// 사용자 정보 가져오기 (있으면 사용, 없으면 null)
		Map<String, Object> userInfo = cookieUtil.getToken(request);
		Map<String, Object> userMap = null;
		
		if(userInfo != null) {
			userMap = new HashMap<>(); 
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
		}

		// API 로깅 파라미터 준비
		Map<String, Object> param = new HashMap<>();
		
		// 사용자 정보가 있으면 사용자 ID 설정
		if(userMap != null && userMap.get("sess_user_id") != null) {
			param.put("sess_user_id", userMap.get("sess_user_id").toString());
		} else {
			param.put("sess_user_id", "ANONYMOUS"); // 비로그인 사용자
		}

		// URL 추출 (Path Variable 처리)
		Map<String, Object> pathVariableAttribute = (Map<String, Object>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
		
		StringBuilder pathVariableString = new StringBuilder();
		if (pathVariableAttribute != null) {
			for (String pathvariable : pathVariableAttribute.keySet()) {
				pathVariableString.append(pathVariableAttribute.get(pathvariable).toString()).append(",");
			}
		}
		
		String lastPart = request.getRequestURI().substring(request.getRequestURI().lastIndexOf("/") + 1);
		if(pathVariableString.toString().contains(lastPart)) {
			param.put("url", request.getRequestURI().substring(0, request.getRequestURI().lastIndexOf("/")));
		} else {
			param.put("url", request.getRequestURI());
		}

		// IP 정보 추출
		String userIp = request.getHeader("X-Forwarded-For");
		if (userIp == null) {
			userIp = request.getRemoteAddr();
		} else {
			userIp = userIp.split(",")[0].trim();
		}

		// API 로깅
		param.put("method", request.getMethod());
		param.put("ip", userIp);
		if(userMap != null && userMap.get("sess_lang_cd") != null) {
			param.put("sess_lang_cd", userMap.get("sess_lang_cd").toString());
		} else {
			param.put("sess_lang_cd", "ko"); // 기본값
		}
		param.put("server_ip", request.getRemoteHost());
		param.put("input_param", getParameter(request));
		
		logService.insertApiLog(param);

		return true;
	}
	
	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView model) throws Exception {
		// DEBUG 로그 제거
	}
	
	@SuppressWarnings("rawtypes")
	public static String getParameter(HttpServletRequest request) {
		Enumeration params = request.getParameterNames();
		String strParam = "";
		while(params.hasMoreElements()) {
			String name = (String)params.nextElement();
			String value = request.getParameter(name);
			strParam += name + "=" + value + "&";
		}
		
		return strParam;
	}
}

