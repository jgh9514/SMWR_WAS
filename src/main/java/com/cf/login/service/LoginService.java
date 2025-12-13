package com.cf.login.service;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface LoginService {

	public Map<String, Object> selectDvcUserInfo(Map<String, Object> param);

	public void insertUserLoginLog(Map<String, Object> param);

	public List<Map<String, ?>> selectLastLoginHst(Map<String, Object> param);
	
	/**
	 * 일반 회원가입
	 */
	public Map<String, Object> signup(Map<String, Object> param);
	
	/**
	 * 로그인 처리
	 */
	public Map<String, Object> login(Map<String, Object> param, HttpServletRequest request, HttpServletResponse response);
	
	/**
	 * 생체 인증 로그인 처리
	 */
	public Map<String, Object> biometricLogin(Map<String, Object> param, HttpServletRequest request, HttpServletResponse response);
	
	/**
	 * 사용자 검증
	 */
	public String validateUser(Map<String, Object> userInfo);
	
	/**
	 * 사용자 로그인 처리 (IP 추출, 로그 기록, 쿠키 설정)
	 */
	public void processUserLogin(HttpServletRequest request, HttpServletResponse response, Map<String, Object> userInfo) throws Exception;
}
