package com.glassehr.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API 접근 권한. 로그인한 사용자가 이 중 하나라도 가지면 들어간다(빈 배열이면 로그인만 확인).
 * /api 아래 컨트롤러 메서드는 이 어노테이션이나 {@link PublicApi} 중 하나가 꼭 있어야 한다.
 * 없으면 {@link AuthInterceptor}가 403으로 막는다(빠뜨려도 열리지 않게).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    Permission[] value() default {};
}
