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
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ApiLoggingInterceptor extends HandlerInterceptorAdapter {

	@Autowired
	LogService logService;

	@SuppressWarnings("unchecked")
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		if("OPTIONS".equals(request.getMethod())) {
			return true;
		}

		// 처리시간 측정용
		request.setAttribute("__api_start_ms", System.currentTimeMillis());

		// API 로깅 파라미터 준비
		Map<String, Object> param = new HashMap<>();

		// SessionInterceptor가 주입한 userInfo를 재사용 (쿠키 파싱/세션 주입 중복 제거)
		Object attr = request.getAttribute("userInfo");
		Map<String, Object> userMap = (attr instanceof Map) ? (Map<String, Object>) attr : null;
		
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

		// 요청 thread에서 DB insert를 하지 않음 (afterCompletion에서 비동기로 처리)
		request.setAttribute("__api_log_param", param);

		return true;
	}
	
	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView model) throws Exception {
		// DEBUG 로그 제거
	}
	
	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
		// 비동기 API 로그 적재 (등록된 API만 저장됨)
		Object p = request.getAttribute("__api_log_param");
		if (p instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> param = (Map<String, Object>) p;
			Object st = request.getAttribute("__api_start_ms");
			if (st instanceof Long) {
				long elapsed = System.currentTimeMillis() - (Long) st;
				param.put("elapsed_ms", elapsed); // (현재 DB 컬럼 없음) 추후 확장용
			}
			param.put("http_status", Objects.toString(response.getStatus(), "")); // (현재 DB 컬럼 없음) 추후 확장용
			logService.insertApiLogAsync(param);
		}
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

