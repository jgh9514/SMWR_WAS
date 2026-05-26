package com.sysconf.security;

import java.util.regex.Pattern;

/**
 * 회원가입·로그인 입력 검증(길이·문자셋).
 */
public final class AuthCredentialsValidator {

	private static final Pattern USER_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,32}$");

	private AuthCredentialsValidator() {
	}

	public static String validateUserId(String userId) {
		if (userId == null || userId.isBlank()) {
			return "아이디를 입력해주세요.";
		}
		String trimmed = userId.trim();
		if (!USER_ID_PATTERN.matcher(trimmed).matches()) {
			return "아이디는 3~32자의 영문, 숫자, 밑줄(_)만 사용할 수 있습니다.";
		}
		return null;
	}

	public static String validatePassword(String password, int minLength, int maxLength) {
		if (password == null || password.isBlank()) {
			return "비밀번호를 입력해주세요.";
		}
		if (password.length() < minLength) {
			return "비밀번호는 최소 " + minLength + "자 이상이어야 합니다.";
		}
		if (password.length() > maxLength) {
			return "비밀번호는 최대 " + maxLength + "자까지 입력할 수 있습니다.";
		}
		return null;
	}
}
