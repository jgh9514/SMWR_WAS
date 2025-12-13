package com.cf.login.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.sysconf.constants.Constant;
import com.sysconf.util.CookieUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
	private CookieUtil cookieUtil;

	@Operation(summary = "로그인", description = "사용자 로그인을 처리합니다.")
	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody Map<String, Object> param, HttpServletRequest request, HttpSession session, HttpServletResponse response) throws Exception {
		log.info("===== 로그인 요청 시작 =====");
		log.info("요청 파라미터: {}", param);
		try {
			Map<String, Object> result = service.login(param, request, response);
			log.info("로그인 결과: {}", result);
			return ResponseEntity.ok(result);
		} catch (Exception e) {
			log.error("로그인 처리 중 오류 발생", e);
			throw e;
		}
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

		Map<String, Object> userInfo = cookieUtil.getToken(request);

		String errorMessage = service.validateUser(userInfo);
		if (errorMessage != null) {
			result.put("result", errorMessage);
			return new ResponseEntity<>(result, HttpStatus.OK);
		}

		List<Map<String, ?>> userLoginLogs = service.selectLastLoginHst(userInfo);

		result.put("result", "SUCCESS");
		result.put("login_hst", userLoginLogs);
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


}
