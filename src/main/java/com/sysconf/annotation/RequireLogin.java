package com.sysconf.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 클래스 또는 메서드에 적용하여 로그인 필수 여부를 표시합니다.
 * - 클래스 레벨: 해당 컨트롤러의 모든 메서드에 로그인 필요
 * - 메서드 레벨: 해당 메서드만 로그인 필요 (클래스에 없을 때)
 * - 메서드 어노테이션이 클래스 어노테이션보다 우선
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireLogin {
}
