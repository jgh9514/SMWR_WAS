package com.sysconf.util;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.sysconf.security.JwtTokenProvider;

@Component
public class TokenUtil {
	
	private Map<String, Map<String, Object>> tokenMap;

	@Autowired
	JwtTokenProvider jwtTokenProvider;
	
	@PostConstruct
	public void init() {
		tokenMap = new HashMap<>();
	}
	
	/**
	 * JWT 토큰을 검증하고 사용자 정보를 반환합니다.
	 * 1. JWT 유효성 검증 (만료, 서명 등)
	 * 2. 유효한 경우 tokenMap에서 사용자 정보 조회
	 * 
	 * @param token JWT 토큰
	 * @return 사용자 정보 Map, 토큰이 유효하지 않으면 null
	 */
	public Map<String, Object> getToken(String token) {
		if (token == null || token.isEmpty()) {
			return null;
		}
		
		try {
			// 1. JWT 유효성 검증
			String validationResult = jwtTokenProvider.isValidToken(token);
			if (!"ACCESS".equals(validationResult)) {
				// JWT가 만료되었거나 유효하지 않음
				// 만료된 토큰은 tokenMap에서도 제거
				if (tokenMap != null && tokenMap.containsKey(token)) {
					tokenMap.remove(token);
				}
				return null;
			}
			
			// 2. JWT가 유효하면 tokenMap에서 사용자 정보 조회
			if (tokenMap != null && tokenMap.containsKey(token)) {
				return tokenMap.get(token);
			}
			
			// 3. tokenMap에 없어도 JWT가 유효하면 JWT에서 user_id 추출 가능
			// 하지만 현재 구조상 tokenMap에 userInfo가 저장되어 있으므로 null 반환
			return null;
		} catch (Exception e) {
			// JWT 파싱 오류 등 예외 발생 시
			return null;
		}
	}
	
	public String setToken(Map<String, Object> userInfo) throws Exception {
		String token = jwtTokenProvider.createToken(userInfo.get("user_id").toString());
		tokenMap.put(token, userInfo);

		return token;
	}

	public void deleteToken(String token) {
		tokenMap.remove(token);
	}
}

