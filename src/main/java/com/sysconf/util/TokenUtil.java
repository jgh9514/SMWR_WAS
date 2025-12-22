package com.sysconf.util;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.admin.user.service.UserService;
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
		
		try {
			// JWT에서 user_id 추출 (JWT 파싱 실패 시 예외 발생)
			String userId = jwtTokenProvider.getUserIdByToken(token);
			
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
	public String setToken(Map<String, Object> userInfo) throws Exception {
		String token = jwtTokenProvider.createToken(userInfo.get("user_id").toString());
		
		log.debug("JWT 토큰 생성 완료 - user_id: {}, 토큰 길이: {}", 
			userInfo.get("user_id"), token.length());

		return token;
	}

	/**
	 * 토큰 삭제 (JWT 기반이므로 별도 처리 불필요)
	 * 실제 토큰 삭제는 쿠키에서 처리됩니다.
	 */
	public void deleteToken(String token) {
		// JWT 기반이므로 메모리에서 삭제할 필요 없음
		log.debug("토큰 삭제 요청 (JWT 기반이므로 쿠키에서만 삭제됨)");
	}
}

