package com.cf.login.rest;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.sysconf.constants.Constant;
import com.sysconf.util.CookieUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.admin.user.service.UserService;
import com.cf.login.service.LoginService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "Authentication", description = "인증 관련 API")
@RestController
@RequestMapping("/api/v1/auth")
public class LoginController {

	@Autowired
	LoginService service;

	@Autowired
	UserService userService;
	
	@Autowired
	private CookieUtil cookieUtil;

	@Operation(summary = "로그인", description = "사용자 로그인을 처리합니다.")
	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody Map<String, Object> param, HttpServletRequest request, HttpSession session, HttpServletResponse response) throws Exception {
		log.info("===== 로그인 요청 시작 =====");
		log.info("요청 파라미터: {}", maskSensitive(param));
		try {
			Map<String, Object> result = service.login(param, request, response);
			log.info("로그인 결과: {}", result);
			return ResponseEntity.ok(result);
		} catch (Exception e) {
			log.error("로그인 처리 중 오류 발생", e);
			throw e;
		}
	}

	private Map<String, Object> maskSensitive(Map<String, Object> param) {
		if (param == null) return null;
		Map<String, Object> copy = new HashMap<>(param);
		if (copy.containsKey("password")) {
			Object pw = copy.get("password");
			if (pw != null && !pw.toString().isEmpty()) {
				copy.put("password", "******");
			}
		}
		return copy;
	}

	/**
	 * Logout
	 */
	@Operation(summary = "로그아웃", description = "사용자 로그아웃을 처리합니다.")
	@PostMapping("/logout")
	public ResponseEntity<?> logout(@RequestBody Map<String, Object> param, HttpServletRequest request, HttpServletResponse response, HttpSession session) throws Exception {
		cookieUtil.deleteToken(request, response, Constant.LOGIN_TOKEN_NAME);

		return new ResponseEntity<>(true, HttpStatus.OK);
	}

	/**
	 * Biometric Login
	 */
	@Operation(summary = "생체 인증 로그인", description = "모바일 생체 인증을 통한 로그인을 처리합니다.")
	@PostMapping("/mobile-biometric-login")
	public ResponseEntity<?> biometricLogin(@RequestBody Map<String, Object> param, HttpServletRequest request, HttpServletResponse response) throws Exception {
		return ResponseEntity.ok(service.biometricLogin(param, request, response));
	}

	/**
	 * Auto Login Check
	 */
	@Operation(summary = "자동 로그인 체크", description = "자동 로그인 여부를 확인합니다.")
	@PostMapping("/login-check")
	public ResponseEntity<?> autoLoginCheck(HttpServletRequest request, HttpServletResponse response) throws Exception {
		Map<String, Object> result = new HashMap<>();

		// AuthSessionInterceptor가 이미 만든 userInfoRaw(원본)를 재사용해서 중복 토큰/DB 조회를 피한다
		Map<String, Object> userInfo = null;
		Object raw = request.getAttribute("userInfoRaw");
		if (raw instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> rawMap = (Map<String, Object>) raw;
			// validateUser는 user_id 기반으로 체크하므로 rawMap 사용
			if (rawMap.get("user_id") != null) {
				userInfo = rawMap;
			}
		}
		if (userInfo == null) {
			userInfo = cookieUtil.getToken(request);
		}

		String errorMessage = service.validateUser(userInfo);
		if (errorMessage != null) {
			result.put("result", errorMessage);
			return new ResponseEntity<>(result, HttpStatus.OK);
		}

		userService.enrichUserRolesAndAdminFlag(userInfo);
		cookieUtil.extendToken(request, response, Constant.LOGIN_TOKEN_NAME);

		result.put("result", "SUCCESS");
		result.put("userInfo", userInfo);

		return new ResponseEntity<>(result, HttpStatus.OK);
	}

	/**
	 * 일반 회원가입
	 */
	@Operation(summary = "일반 회원가입", description = "일반 사용자 계정을 생성합니다.")
	@PostMapping("/signup")
	@org.springframework.transaction.annotation.Transactional
	public ResponseEntity<?> signup(@RequestBody Map<String, Object> param, HttpServletRequest request, HttpServletResponse response) throws Exception {
		return ResponseEntity.ok(service.signup(param));
	}

	/**
	 * 아이디 중복 체크
	 */
	@Operation(summary = "아이디 중복 체크", description = "사용자 아이디(user_id)가 이미 사용 중인지 확인합니다.")
	@PostMapping("/user-id/check")
	public ResponseEntity<?> checkUserIdDuplicate(@RequestBody Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();
		try {
			Object userIdObj = param.get("user_id");
			String userId = userIdObj == null ? "" : userIdObj.toString().trim();
			if (userId.isEmpty()) {
				result.put("result", "FAIL");
				result.put("message", "user_id가 필요합니다.");
				result.put("isDuplicate", false);
				return new ResponseEntity<>(result, HttpStatus.OK);
			}

			Map<String, Object> checkParam = new HashMap<>();
			checkParam.put("user_id", userId);
			Map<String, Object> existingUser = userService.selectUserInfo(checkParam);

			boolean isDuplicate = existingUser != null && !"dehs-NOTEXISTS".equals(existingUser.get("user_id"));

			result.put("result", "SUCCESS");
			result.put("isDuplicate", isDuplicate);
			result.put("message", isDuplicate ? "이미 사용 중인 아이디입니다." : "사용 가능한 아이디입니다.");
			return new ResponseEntity<>(result, HttpStatus.OK);
		} catch (Exception e) {
			log.error("아이디 중복 체크 실패", e);
			result.put("result", "FAIL");
			result.put("message", "아이디 중복 체크 처리 중 오류가 발생했습니다.");
			result.put("isDuplicate", false);
			return new ResponseEntity<>(result, HttpStatus.OK);
		}
	}


}
