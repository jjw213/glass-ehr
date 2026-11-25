package com.glassehr.employee;

import java.util.Locale;

/**
 * 요청한 사람. 역할은 SPEC §0(관리자·인사담당·급여담당·일반 직원).
 *
 * <p>백엔드 인증·권한 이슈 전까지는 컨트롤러가 요청 헤더 {@code X-Actor-Id}, {@code X-Actor-Role}에서 만든다.
 * 이 헤더는 위조할 수 있으므로 운영 전에 반드시 인증 정보(세션·토큰)로 바꿔야 한다(docs/employee-design.md 5장).
 *
 * @param homeEnterCd 요청한 사람의 주체 법인(로그인한 법인, AuthUser.homeEnterCd). 주민등록번호 조회 로그의
 *                    accessor_enter_cd 에 남긴다(docs/tenant-design.md 4.4). 대상 법인은 서비스 인자 enterCd 로 따로 받는다.
 */
public record RequestActor(String actorId, Role role, String clientIp, String homeEnterCd) {

    public enum Role {
        ADMIN,
        HR,
        PAYROLL,
        EMPLOYEE
    }

    static final String ANONYMOUS = "anonymous";

    /** 헤더 값으로 만든다. 모르는 역할은 null(권한 없음). */
    static RequestActor of(String actorId, String role, String clientIp, String homeEnterCd) {
        String id = actorId == null || actorId.isBlank() ? ANONYMOUS : actorId.strip();
        if (id.length() > 50) {
            id = id.substring(0, 50);
        }
        Role r = null;
        if (role != null && !role.isBlank()) {
            try {
                r = Role.valueOf(role.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                r = null;
            }
        }
        String ip = clientIp != null && clientIp.length() > 45 ? clientIp.substring(0, 45) : clientIp;
        return new RequestActor(id, r, ip, homeEnterCd);
    }

    /** 주민등록번호 원문 조회 가능 역할: 인사담당·급여담당(docs/rrn-design.md 3장). 관리자는 불가. */
    boolean canRevealRrn() {
        return role == Role.HR || role == Role.PAYROLL;
    }
}
