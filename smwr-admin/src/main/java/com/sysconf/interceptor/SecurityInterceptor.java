package com.sysconf.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class SecurityInterceptor extends HandlerInterceptorAdapter {
	Logger logger  = LoggerFactory.getLogger(this.getClass());

    @SuppressWarnings("unchecked")
	@Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		if("OPTIONS".equals(request.getMethod())) {
	        return true;
	    }

		Map<String, Object> pathVariableAttribute = (Map<String, Object>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
		StringBuilder pathVariableString = new StringBuilder();
		if (pathVariableAttribute != null) {
			for (String pathvariable : pathVariableAttribute.keySet()) {
				pathVariableString.append(pathVariableAttribute.get(pathvariable).toString()).append(",");
			}
		}
		String lastPart = request.getRequestURI().substring(request.getRequestURI().lastIndexOf("/") + 1);

		if (request.getContentType() != null && request.getContentType().contains("application/json")) {
			String method = request.getMethod();
			if ("POST".equals(method) && pathVariableString.toString().contains(lastPart)) {
//				if (!sha256.encrypt(request.getRequestURI()).equals(request.getHeader("url-enc-data"))) {
//					logger.error("POST PathVariable Request Parameter 위변조 감지 프로세스 작동!!!!");
//					response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
//					return false;
//				}
			} else if ("POST".equals(method)) {
//				final CachedBodyHttpServletWrapper cachingRequest = (CachedBodyHttpServletWrapper) request;
//				String requestBody;
//				if ((requestBody = cachingRequest.getReader().readLine()) != null) {
//					// S3로 업로드!
//					String encBody = sha256.encrypt(requestBody);
//					if (!encBody.equals(request.getHeader("enc-data"))) {
//						logger.error("POST Request Parameter 위변조 감지 프로세스 작동!!!!");
//						response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
//						return false;
//					}
//				}
			} else if ("GET".equals(method) && pathVariableString.toString().contains(lastPart)) {
//				if (!sha256.encrypt(request.getRequestURI()).equals(request.getHeader("url-enc-data"))) {
//					logger.error("GET PathVariable Request Parameter 위변조 감지 프로세스 작동!!!!");
//					response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
//					return false;
//				}
			} else if ("GET".equals(method) && request.getQueryString() != null && !"".equals(request.getQueryString())) {
//				Map<String, Object> requestParam = convertStringToMap(request.getQueryString());
//				ObjectMapper om = new ObjectMapper();
//				String requestParamStr = om.writeValueAsString(requestParam);
//
//				if (!sha256.encrypt(requestParamStr).equals(request.getHeader("enc-data"))) {
//					logger.error("GET Request Parameter 위변조 감지 프로세스 작동!!!!");
//					response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
//					return false;
//				}
			}
		}

        return true;
    }
    
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView model) throws Exception {

    	logger.debug("===================== " + request.getRequestURI() + " END! =======================");
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
    	SessionThread.SESSION_USER_INFO.remove();
    }

	public static Map<String, Object> convertStringToMap(String input) throws UnsupportedEncodingException {

		Map<String, Object> resultMap = new LinkedHashMap<>();

		if (input == null || input.isEmpty()) {
			return resultMap;
		}

		String[] pairs = input.split("&");

		for (String pair : pairs) {
			String[] parts = pair.split("=", 2); // key=value 쌍
			String key = URLDecoder.decode(parts[0], String.valueOf(StandardCharsets.UTF_8));
			String value = parts.length > 1 ? URLDecoder.decode(parts[1], String.valueOf(StandardCharsets.UTF_8)) : "";

			// 값이 ","를 포함하면 배열로 간주
			if (value.contains(",")) {
				String[] splitValues = value.split(",");
				resultMap.put(key, Arrays.asList(splitValues));
			} else {
				resultMap.put(key, value);
			}
		}

		return resultMap;
	}

}