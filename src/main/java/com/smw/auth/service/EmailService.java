package com.smw.auth.service;

import java.util.Map;

public interface EmailService {
	
	/**
	 * 이메일 인증 코드 발송
	 */
	public Map<String, Object> sendVerificationCode(String email);
	
	/**
	 * 이메일 인증 코드 확인
	 */
	public Map<String, Object> verifyCode(String email, String code);
	
	/**
	 * 이메일 인증 완료 여부 확인
	 */
	public boolean isEmailVerified(String email);
	
	/**
	 * 이메일 인증 정보 삭제 (회원가입 완료 후 호출)
	 */
	public void removeVerifiedEmail(String email);
}

