package com.glassehr.auth;

import java.util.Optional;

/**
 * 로그인 ID로 사용자·역할·권한·데이터 범위를 찾는다. 없거나 사용 중지면 빈 값.
 * 실제 구현은 {@link DbAuthUserResolver}. WebMvc 테스트는 가짜 구현을 넣는다.
 */
public interface AuthUserResolver {

    Optional<AuthUser> resolve(String loginId, String clientIp);
}
