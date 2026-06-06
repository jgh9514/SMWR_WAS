package com.sysconf.util;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.admin.user.service.UserService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smw.guild.service.GuildService;
import com.sysconf.security.JwtTokenProvider;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TokenUtil {
	
	@Autowired
	JwtTokenProvider jwtTokenProvider;
	
	@Autowired
	UserService userService;
	
	@Autowired
	GuildService guildService;
	
	// 토큰 기반 유저 정보 조회는 매 요청마다 DB를 칠 수 있어 비용이 큼
	// - 짧은 TTL(30초) 캐시로 체감 성능을 개선하고, 길드 변경 등도 빠르게 반영되도록 함
	private final Cache<String, Map<String, Object>> tokenUserInfoCache = Caffeine.newBuilder()
			.maximumSize(10_000)
			.expireAfterWrite(Duration.ofSeconds(30))
			.build();
	
	/**
	 * JWT 토큰으로 사용자 정보를 조회합니다.
	 * JWT에서 user_id를 추출한 후 DB에서 사용자 정보를 조회합니다.
	 * 
	 * @param token JWT 토큰
	 * @return 사용자 정보 Map, 조회 실패 시 null
	 */
	public Map<String, Object> getToken(String token) {
		if (token == null || token.isEmpty()) {
			return null;
		}
		
		Map<String, Object> cached = tokenUserInfoCache.getIfPresent(token);
		if (cached != null) {
			// 호출측에서 put/remove 등을 할 수 있으므로 방어적으로 복사본 반환
			return new HashMap<>(cached);
		}
		
		try {
			// JWT에서 user_id 추출 (만료된 자동 로그인 토큰은 grace 허용 후 재발급 대상)
			String userId;
			try {
				userId = jwtTokenProvider.getUserIdByToken(token);
			} catch (io.jsonwebtoken.ExpiredJwtException expired) {
				if (!jwtTokenProvider.isAutoLoginToken(token)) {
					log.debug("JWT 만료(자동 로그인 아님): {}", expired.getMessage());
					return null;
				}
				userId = jwtTokenProvider.getUserIdAllowExpired(token);
			}
			
			// DB에서 사용자 정보 조회
			Map<String, Object> param = new HashMap<>();
			param.put("user_id", userId);
			Map<String, Object> userInfo = userService.selectUserInfo(param);
			
			if (userInfo == null || "dehs-NOTEXISTS".equals(userInfo.get("user_id"))) {
				log.warn("DB에서 사용자 정보를 찾을 수 없음: user_id={}", userId);
				return null;
			}
			
			// 사용자 정보에서 비밀번호 제거
			userInfo.remove("user_pw");
			
			// 길드 정보 조회
			Map<String, Object> guildParam = new HashMap<>();
			guildParam.put("user_id", userId);
			Map<String, ?> userGuild = guildService.selectUserGuild(guildParam);
			if (userGuild != null) {
				userInfo.put("guild_id", userGuild.get("guild_id"));
				userInfo.put("guild_name", userGuild.get("guild_name"));
				userInfo.put("guild_role", userGuild.get("role"));
			}
			
			log.debug("JWT 기반 사용자 정보 조회 성공: user_id={}", userId);

			// 캐시에 보관 (복사본을 저장해서 외부 변경에 영향 없게)
			tokenUserInfoCache.put(token, new HashMap<>(userInfo));
			return userInfo;
		} catch (Exception e) {
			// JWT 파싱 실패 또는 DB 조회 실패
			log.debug("토큰에서 사용자 정보 조회 실패: {}", e.getMessage());
			return null;
		}
	}
	
	/**
	 * 사용자 정보로 JWT 토큰을 생성합니다.
	 * 
	 * @param userInfo 사용자 정보
	 * @return JWT 토큰
	 */
	public boolean isAutoLoginToken(String token) {
		return jwtTokenProvider.isAutoLoginToken(token);
	}

	public String setToken(Map<String, Object> userInfo) throws Exception {
		boolean autoLogin = "true".equalsIgnoreCase(String.valueOf(userInfo.get("auto_login")));
		String token = jwtTokenProvider.createToken(userInfo.get("user_id").toString(), autoLogin);
		
		log.debug("JWT 토큰 생성 완료 - user_id: {}, 토큰 길이: {}", 
			userInfo.get("user_id"), token.length());

		return token;
	}

	/**
	 * DB 갱신 후 JWT userInfo 캐시 무효화 (siege_view_scope 등 설정 변경 시)
	 */
	public void evictTokenUserInfoCache(String token) {
		if (token != null && !token.isEmpty()) {
			tokenUserInfoCache.invalidate(token);
		}
	}

	/**
	 * 토큰 삭제 — 쿠키 제거와 함께 userInfo 캐시도 무효화
	 */
	public void deleteToken(String token) {
		evictTokenUserInfoCache(token);
		log.debug("토큰 삭제 요청 (JWT 기반이므로 쿠키에서만 삭제됨)");
	}
}

