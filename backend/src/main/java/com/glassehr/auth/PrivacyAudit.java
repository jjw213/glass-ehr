package com.glassehr.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 개인정보를 돌려주는 API 표시. 로그인 안 함·권한 없음으로 {@link AuthInterceptor}에서 막히면 그 시도를
 * 개인정보 조회 로그(DENIED)로 남긴다. 성공·범위 밖·대상 없음 기록은 컨트롤러가 남긴다.
 * 경로 변수 {@code empId}가 있으면 대상 사원으로 기록한다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PrivacyAudit {

    PrivacyAccessLog.Item item();

    PrivacyAccessLog.Action action();
}
