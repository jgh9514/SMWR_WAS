package com.smw.auth.rest;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smw.auth.service.EmailService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Email Verification", description = "이메일 인증 API")
@RestController
@RequestMapping("/api/v1/auth/email")
public class EmailVerificationController {

	@Autowired
	private EmailService emailService;

	/**
	 * 이메일 인증 코드 발송
	 */
	@Operation(summary = "이메일 인증 코드 발송", description = "회원가입 시 이메일 인증 코드를 발송합니다.")
	@PostMapping("/send")
	public ResponseEntity<?> sendVerificationCode(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		String email = param.get("email") != null ? param.get("email").toString() : null;
		
		if (email == null || email.trim().isEmpty()) {
			Map<String, Object> result = new java.util.HashMap<>();
			result.put("result", "FAIL");
			result.put("message", "이메일을 입력해주세요.");
			return ResponseEntity.ok(result);
		}
		
		return ResponseEntity.ok(emailService.sendVerificationCode(email.trim()));
	}

	/**
	 * 이메일 인증 코드 확인
	 */
	@Operation(summary = "이메일 인증 코드 확인", description = "발송된 이메일 인증 코드를 확인합니다.")
	@PostMapping("/verify")
	public ResponseEntity<?> verifyCode(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		String email = param.get("email") != null ? param.get("email").toString() : null;
		String code = param.get("code") != null ? param.get("code").toString() : null;
		
		if (email == null || email.trim().isEmpty()) {
			Map<String, Object> result = new java.util.HashMap<>();
			result.put("result", "FAIL");
			result.put("message", "이메일을 입력해주세요.");
			return ResponseEntity.ok(result);
		}
		
		if (code == null || code.trim().isEmpty()) {
			Map<String, Object> result = new java.util.HashMap<>();
			result.put("result", "FAIL");
			result.put("message", "인증 코드를 입력해주세요.");
			return ResponseEntity.ok(result);
		}
		
		return ResponseEntity.ok(emailService.verifyCode(email.trim(), code.trim()));
	}
}

