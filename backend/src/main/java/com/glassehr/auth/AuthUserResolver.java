package com.glassehr.auth;

import java.util.Optional;

/**
 * 법인·로그인 ID로 사용자·역할·권한·데이터 범위를 찾는다. 없거나 사용 중지면(계정·법인 모두) 빈 값.
 * 실제 구현은 {@link DbAuthUserResolver}. WebMvc 테스트는 가짜 구현을 넣는다.
 */
public interface AuthUserResolver {

    /**
     * @param homeEnterCd    주체 법인(로그인한 법인). 계정·역할은 이 법인 기준
     * @param currentEnterCd 세션의 현재 법인. 주체 법인과 다르면 법인 전환(ENTER_SWITCH) 권한이 있고 그 법인이
     *                       사용 가능할 때만 사용자를 돌려준다(docs/tenant-design.md 4.3)
     */
    Optional<AuthUser> resolve(String homeEnterCd, String loginId, String currentEnterCd, String clientIp);

    /** 현재 법인 = 주체 법인(전환하지 않은 요청). */
    default Optional<AuthUser> resolve(String enterCd, String loginId, String clientIp) {
        return resolve(enterCd, loginId, enterCd, clientIp);
    }
}
