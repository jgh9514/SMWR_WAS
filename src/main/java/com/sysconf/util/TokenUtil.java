package com.sysconf.util;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.sysconf.security.JwtTokenProvider;

import lombok.extern.slf4j.Slf4j;

@Slf4j
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
			log.debug("토큰이 null이거나 비어있음");
			return null;
		}
		
		try {
			// 1. JWT 유효성 검증
			String validationResult = jwtTokenProvider.isValidToken(token);
			log.debug("JWT 검증 결과: {}", validationResult);
			
			if (!"ACCESS".equals(validationResult)) {
				// JWT가 만료되었거나 유효하지 않음
				log.warn("JWT 검증 실패: {} - 토큰 길이: {}", validationResult, token.length());
				// 만료된 토큰은 tokenMap에서도 제거
				if (tokenMap != null && tokenMap.containsKey(token)) {
					tokenMap.remove(token);
				}
				return null;
			}
			
			// 2. JWT가 유효하면 tokenMap에서 사용자 정보 조회
			log.debug("tokenMap 크기: {}, 토큰 존재 여부: {}", tokenMap != null ? tokenMap.size() : 0, tokenMap != null && tokenMap.containsKey(token));
			
			if (tokenMap != null && tokenMap.containsKey(token)) {
				Map<String, Object> userInfo = tokenMap.get(token);
				log.debug("사용자 정보 조회 성공: user_id={}", userInfo.get("user_id"));
				return userInfo;
			}
			
			// 3. tokenMap에 없어도 JWT가 유효하면 JWT에서 user_id 추출 가능
			// 하지만 현재 구조상 tokenMap에 userInfo가 저장되어 있으므로 null 반환
			log.warn("JWT는 유효하지만 tokenMap에 사용자 정보가 없음 - 토큰 길이: {}", token.length());
			return null;
		} catch (Exception e) {
			// JWT 파싱 오류 등 예외 발생 시
			log.error("토큰 처리 중 예외 발생", e);
			return null;
		}
	}
	
	public String setToken(Map<String, Object> userInfo) throws Exception {
		String token = jwtTokenProvider.createToken(userInfo.get("user_id").toString());
		tokenMap.put(token, userInfo);
		
		log.debug("토큰 생성 및 저장 완료 - user_id: {}, 토큰 길이: {}, tokenMap 크기: {}", 
			userInfo.get("user_id"), token.length(), tokenMap.size());

		return token;
	}

	public void deleteToken(String token) {
		tokenMap.remove(token);
	}
}

