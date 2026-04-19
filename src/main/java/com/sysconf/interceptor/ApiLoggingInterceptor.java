package com.sysconf.interceptor;

import java.io.BufferedReader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;

import com.admin.log.service.LogService;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ApiLoggingInterceptor implements HandlerInterceptor {

	private static final int MAX_INPUT_PARAM_LENGTH = 2000;

	@Autowired
	LogService logService;

	@Autowired(required = false)
	MeterRegistry meterRegistry;

	@SuppressWarnings("unchecked")
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		if("OPTIONS".equals(request.getMethod())) {
			return true;
		}

		// 처리시간 측정용
		request.setAttribute("__api_start_ms", System.currentTimeMillis());
		if (meterRegistry != null) {
			request.setAttribute("__api_timer_sample", Timer.start(meterRegistry));
		}

		// API 로깅 파라미터 준비
		Map<String, Object> param = new HashMap<>();

		// AuthSessionInterceptor가 주입한 userInfo를 재사용 (쿠키 파싱/세션 주입 중복 제거)
		Object attr = request.getAttribute("userInfo");
		Map<String, Object> userMap = (attr instanceof Map) ? (Map<String, Object>) attr : null;
		
		// 사용자 정보가 있으면 사용자 ID 설정
		if(userMap != null && userMap.get("sess_user_id") != null) {
			param.put("sess_user_id", userMap.get("sess_user_id").toString());
		} else {
			param.put("sess_user_id", "ANONYMOUS"); // 비로그인 사용자
		}

		// URL 추출: 가능한 경우 path template 기준으로 정규화
		String normalizedPath = resolveNormalizedPath(request);
		param.put("url", normalizedPath);
		request.setAttribute("__api_metric_path", normalizedPath);

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
		param.put("trace_id", resolveTraceId(request, response));
		if(userMap != null && userMap.get("sess_lang_cd") != null) {
			param.put("sess_lang_cd", userMap.get("sess_lang_cd").toString());
		} else {
			param.put("sess_lang_cd", "ko"); // 기본값
		}
		param.put("server_ip", request.getRemoteHost());
		param.put("input_param", resolveInputParam(request));

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
		recordApiMetrics(request, response, ex);

		// API 실행 로그 DB 적재는 비활성(LogService.insertApiLogAsync no-op) — Micrometer 메트릭만 유효
		Object p = request.getAttribute("__api_log_param");
		if (p instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> param = (Map<String, Object>) p;
			Object st = request.getAttribute("__api_start_ms");
			if (st instanceof Long) {
				long elapsed = System.currentTimeMillis() - (Long) st;
				param.put("elapsed_ms", elapsed);
			}
			param.put("trace_id", resolveTraceId(request, response));
			param.put("http_status", response.getStatus());
			logService.insertApiLogAsync(param);
		}
	}

	private String resolveTraceId(HttpServletRequest request, HttpServletResponse response) {
		Object traceIdAttr = request.getAttribute("__trace_id");
		if (traceIdAttr != null && !String.valueOf(traceIdAttr).trim().isEmpty()) {
			return String.valueOf(traceIdAttr);
		}

		String traceIdHeader = request.getHeader("X-Request-Id");
		if (traceIdHeader != null && !traceIdHeader.trim().isEmpty()) {
			return traceIdHeader;
		}

		return Objects.toString(response.getHeader("X-Request-Id"), "");
	}

	@SuppressWarnings("unchecked")
	private String resolveNormalizedPath(HttpServletRequest request) {
		Object bestMatchingPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		if (bestMatchingPattern instanceof String && !((String) bestMatchingPattern).trim().isEmpty()) {
			return (String) bestMatchingPattern;
		}

		Map<String, Object> pathVariableAttribute =
				(Map<String, Object>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

		StringBuilder pathVariableString = new StringBuilder();
		if (pathVariableAttribute != null) {
			for (String pathvariable : pathVariableAttribute.keySet()) {
				pathVariableString.append(pathVariableAttribute.get(pathvariable).toString()).append(",");
			}
		}

		String requestUri = request.getRequestURI();
		String lastPart = requestUri.substring(requestUri.lastIndexOf("/") + 1);
		if (pathVariableString.toString().contains(lastPart)) {
			return requestUri.substring(0, requestUri.lastIndexOf("/")) + "/{id}";
		}
		return requestUri;
	}

	private void recordApiMetrics(HttpServletRequest request, HttpServletResponse response, Exception ex) {
		if (meterRegistry == null) {
			return;
		}

		String method = request.getMethod();
		String uri = Objects.toString(request.getAttribute("__api_metric_path"), request.getRequestURI());
		String status = String.valueOf(response.getStatus());
		String outcome = response.getStatus() >= 500 ? "SERVER_ERROR"
				: response.getStatus() >= 400 ? "CLIENT_ERROR"
				: "SUCCESS";
		String exception = ex == null ? "None" : ex.getClass().getSimpleName();

		Tags tags = Tags.of(
				"method", method,
				"uri", uri,
				"status", status,
				"outcome", outcome,
				"exception", exception
		);

		Counter.builder("smw.api.request.count")
				.description("API request count")
				.tags(tags)
				.register(meterRegistry)
				.increment();

		Object timerSample = request.getAttribute("__api_timer_sample");
		if (timerSample instanceof Timer.Sample) {
			((Timer.Sample) timerSample).stop(
					Timer.builder("smw.api.request.duration")
							.description("API request duration")
							.tags(tags)
							.register(meterRegistry)
			);
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

	private String resolveInputParam(HttpServletRequest request) {
		String queryParams = getParameter(request);
		if (queryParams != null && !queryParams.trim().isEmpty()) {
			return truncate(queryParams);
		}

		String contentType = request.getContentType();
		if (contentType == null || !contentType.contains("application/json")) {
			return "";
		}

		try {
			StringBuilder body = new StringBuilder();
			BufferedReader reader = request.getReader();
			String line;
			while ((line = reader.readLine()) != null) {
				body.append(line);
				if (body.length() >= MAX_INPUT_PARAM_LENGTH) {
					break;
				}
			}
			return truncate(body.toString());
		} catch (Exception e) {
			log.debug("API 요청 바디 읽기 실패", e);
			return "";
		}
	}

	private String truncate(String value) {
		if (value == null) {
			return "";
		}
		String normalized = value.replace("\r", "").replace("\n", "");
		if (normalized.length() <= MAX_INPUT_PARAM_LENGTH) {
			return normalized;
		}
		return normalized.substring(0, MAX_INPUT_PARAM_LENGTH) + "...";
	}
}

