package com.sysconf.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 관리자 전용 API를 표시합니다.
 * - 클래스/메서드 레벨 적용
 * - 로그인 + ROLE_ADMIN(RL0001) 권한 필요
 * - 미인증: 401, 권한 없음: 403
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAdmin {
}
