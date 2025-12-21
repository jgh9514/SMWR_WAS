package com.sysconf.util;

import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.sysconf.constants.Constant;

@Component
public class CookieUtil {

	@Value("${smw.cookie-domain}")
    private String cookieDomain;

	@Value("${smw.cookie-live-time}")
	private int cookieLiveTime;

	@Autowired
	private TokenUtil tokenUtil;

    public void refreshtoken(HttpServletRequest request, HttpServletResponse response, Map<String, Object> userInfo, String tokenName) throws Exception {
    	deleteToken(request, response, tokenName);
    	createToken(request, response, userInfo, tokenName);
    }

    public void createToken(HttpServletRequest request, HttpServletResponse response, Map<String, Object> userInfo, String tokenName) throws Exception {
		String token = tokenUtil.setToken(userInfo);

		String autoLogin = "false";
		
		if (userInfo.get("auto_login") != null) {
			autoLogin = userInfo.get("auto_login").toString();
		}

		// HTTPS 환경 감지
		boolean isSecure = request.isSecure() || 
			(request.getHeader("X-Forwarded-Proto") != null && "https".equals(request.getHeader("X-Forwarded-Proto")));
		
		ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from(tokenName, token)
												.path("/")
												.maxAge("true".equals(autoLogin) ? -1 : cookieLiveTime)
												.httpOnly(true) // XSS 방지를 위한 HttpOnly 설정
												.secure(isSecure) // HTTPS 환경에서는 true
												.sameSite("Lax"); // CSRF 방지를 위한 SameSite 설정
		
		// 도메인 설정: 로컬 환경에서는 도메인을 설정하지 않음 (로컬에서 쿠키가 작동하도록)
		String requestHost = request.getHeader("Host");
		boolean isLocalhost = requestHost != null && (
			requestHost.contains("localhost") || 
			requestHost.contains("127.0.0.1") ||
			requestHost.contains("localhost.com") ||
			requestHost.startsWith("localhost:") ||
			requestHost.startsWith("127.0.0.1:") ||
			requestHost.startsWith("localhost.com:")
		);
		
		// 로컬 환경이 아니고, 도메인이 설정되어 있을 때만 도메인 설정
		if (!isLocalhost && cookieDomain != null && !cookieDomain.isEmpty()) {
			cookieBuilder.domain(cookieDomain);
		}
		// 로컬 환경에서는 도메인을 설정하지 않음 (브라우저가 자동으로 localhost로 인식)
		
		ResponseCookie cookie = cookieBuilder.build();
		
		String userAgent = request.getHeader("USER-AGENT");
		if (userAgent != null && userAgent.contains("Safari") && !userAgent.contains("Chrome") && !userAgent.contains("Edg")) {
			response.setHeader("Set-Cookie", cookie.toString() + "; Partitioned");
		} else {
			response.addHeader("Set-Cookie", cookie.toString());
		}
    }

	public void extendToken(HttpServletRequest request, HttpServletResponse response, String tokenName) throws Exception {
//		String token = tokenUtil.setToken(userInfo);
		String cookieValue = getCookieValue(request, tokenName);

		// HTTPS 환경 감지
		boolean isSecure = request.isSecure() || 
			(request.getHeader("X-Forwarded-Proto") != null && "https".equals(request.getHeader("X-Forwarded-Proto")));
		
		ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from(tokenName, cookieValue)
												.path("/")
												.maxAge(cookieLiveTime)
												.httpOnly(true) // XSS 방지를 위한 HttpOnly 설정
												.secure(isSecure) // HTTPS 환경에서는 true
												.sameSite("Lax"); // CSRF 방지를 위한 SameSite 설정
		
		// 도메인 설정: 로컬 환경에서는 도메인을 설정하지 않음 (로컬에서 쿠키가 작동하도록)
		String requestHost = request.getHeader("Host");
		boolean isLocalhost = requestHost != null && (
			requestHost.contains("localhost") || 
			requestHost.contains("127.0.0.1") ||
			requestHost.contains("localhost.com") ||
			requestHost.startsWith("localhost:") ||
			requestHost.startsWith("127.0.0.1:") ||
			requestHost.startsWith("localhost.com:")
		);
		
		// 로컬 환경이 아니고, 도메인이 설정되어 있을 때만 도메인 설정
		if (!isLocalhost && cookieDomain != null && !cookieDomain.isEmpty()) {
			cookieBuilder.domain(cookieDomain);
		}
		// 로컬 환경에서는 도메인을 설정하지 않음 (브라우저가 자동으로 localhost로 인식)
		
		ResponseCookie cookie = cookieBuilder.build();

		String userAgent = request.getHeader("USER-AGENT");
		if (userAgent != null && userAgent.contains("Safari") && !userAgent.contains("Chrome") && !userAgent.contains("Edg")) {
			response.setHeader("Set-Cookie", cookie.toString() + "; Partitioned");
		} else {
			response.addHeader("Set-Cookie", cookie.toString());
		}
	}

    public void deleteToken(HttpServletRequest request, HttpServletResponse response, String tokenName) throws Exception {
		// 1. 토큰 삭제 (메모리에서)
		Cookie[] cookies = request.getCookies();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if (cookie.getName().equals(tokenName)) {
					tokenUtil.deleteToken(cookie.getValue());
					break;
				}
			}
		}
		
		String userAgent = request.getHeader("USER-AGENT");
		boolean isSafari = userAgent != null && userAgent.contains("Safari") && !userAgent.contains("Chrome") && !userAgent.contains("Edg");
		
		// 2. 도메인 설정: 로컬 환경에서는 도메인을 설정하지 않음 (쿠키 생성 시와 동일하게)
		String requestHost = request.getHeader("Host");
		boolean isLocalhost = requestHost != null && (
			requestHost.contains("localhost") || 
			requestHost.contains("127.0.0.1") ||
			requestHost.contains("localhost.com") ||
			requestHost.startsWith("localhost:") ||
			requestHost.startsWith("127.0.0.1:") ||
			requestHost.startsWith("localhost.com:")
		);
		
		// 3. 쿠키 삭제: 로컬 환경이 아니고 도메인이 설정된 경우에만 도메인 쿠키 삭제 시도
		if (!isLocalhost && cookieDomain != null && !cookieDomain.isEmpty()) {
			ResponseCookie deleteCookieWithDomain = ResponseCookie.from(tokenName, "")
													.path("/")
													.domain(cookieDomain)
													.maxAge(0)
													.build();
			if (isSafari) {
				response.addHeader("Set-Cookie", deleteCookieWithDomain.toString() + "; Partitioned");
			} else {
				response.addHeader("Set-Cookie", deleteCookieWithDomain.toString());
			}
		}
		
		// 4. 도메인 없이 쿠키 삭제 시도 (로컬 개발 환경 또는 도메인이 설정되지 않은 경우)
		// 로컬 환경에서는 쿠키 생성 시 도메인을 설정하지 않았으므로, 삭제할 때도 도메인 없이 삭제해야 함
		ResponseCookie deleteCookieNoDomain = ResponseCookie.from(tokenName, "")
												.path("/")
												.maxAge(0)
												.build();
		if (isSafari) {
			response.addHeader("Set-Cookie", deleteCookieNoDomain.toString() + "; Partitioned");
		} else {
			response.addHeader("Set-Cookie", deleteCookieNoDomain.toString());
		}
    }

    public Map<String, Object> getToken(HttpServletRequest request) throws Exception {
    	Cookie[] cookies = request.getCookies();
    	String token = "";
    	if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (Constant.LOGIN_TOKEN_NAME.equals(cookie.getName())) {
                	token = cookie.getValue();
                	break;
                }
            }
        }
    	
    	// 토큰이 없으면 null 반환
    	if (token == null || token.isEmpty()) {
    		return null;
    	}
    	
		Map<String, Object> userInfo = tokenUtil.getToken(token);
		
		return userInfo;
    }

	public String getCookieValue(HttpServletRequest request, String name) {
	    if (request.getCookies() != null) {
	        for (Cookie cookie : request.getCookies()) {
	            if (cookie.getName().equals(name)) {
	                return cookie.getValue();
	            }
	        }
	    }
	    return null;
	}
}

