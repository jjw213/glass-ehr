package com.glassehr.auth;

import java.util.Optional;

/**
 * 법인·로그인 ID로 사용자·역할·권한·데이터 범위를 찾는다. 없거나 사용 중지면(계정·법인 모두) 빈 값.
 * 실제 구현은 {@link DbAuthUserResolver}. WebMvc 테스트는 가짜 구현을 넣는다.
 */
public interface AuthUserResolver {

    /** @param enterCd 주체 법인(로그인한 법인). 지금은 법인 전환이 없어 현재 법인도 같은 값이다. */
    Optional<AuthUser> resolve(String enterCd, String loginId, String clientIp);
}
