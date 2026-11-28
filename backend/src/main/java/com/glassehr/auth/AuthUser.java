package com.glassehr.auth;

import com.glassehr.employee.RequestActor;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * 요청한 로그인 사용자. 역할·권한은 DB(app_user_role·app_role_permission)에서 읽는다(요청 헤더의 역할은 믿지 않음).
 *
 * @param tenantId       현재 고객사 = 주체 법인이 속한 고객사(#151, docs/saas-design.md 2.3). 현재 법인도 같은 고객사다
 * @param enterCd        현재 법인. 모든 업무 조회·수정의 법인 조건은 이 값만 쓴다(docs/tenant-design.md 4.2)
 * @param homeEnterCd    주체 법인 = 로그인한 법인. loginId·empId·역할은 이 법인의 계정 기준
 * @param loginId        로그인 ID(주체 법인 안에서 유일)
 * @param empId          연결된 사원(없으면 null — 시스템 관리자 계정 등)
 * @param roles          역할 코드(부여 역할 + 자동 역할 DEPT_HEAD·EMPLOYEE)
 * @param permissions    역할 권한의 합
 * @param managedDeptIds 부서장으로 맡은 부서와 모든 하위부서(오늘 기준). 부서장이 아니면 빈 집합
 * @param clientIp       접속 IP(로그용)
 */
public record AuthUser(String tenantId, String enterCd, String homeEnterCd, String loginId, Long empId,
        Set<String> roles, Set<Permission> permissions, Set<Long> managedDeptIds, String clientIp) {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_HR = "HR";
    public static final String ROLE_PAYROLL = "PAYROLL";
    public static final String ROLE_DEPT_HEAD = "DEPT_HEAD";
    public static final String ROLE_EMPLOYEE = "EMPLOYEE";
    /** 그룹 관리자: 법인을 전환해 다른 법인 데이터를 본다(V033). */
    public static final String ROLE_GROUP_ADMIN = "GROUP_ADMIN";

    public AuthUser {
        roles = Collections.unmodifiableSet(new TreeSet<>(roles));
        permissions = permissions.isEmpty() ? Collections.unmodifiableSet(EnumSet.noneOf(Permission.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(permissions));
        managedDeptIds = Collections.unmodifiableSet(new LinkedHashSet<>(managedDeptIds));
    }

    public boolean has(Permission p) {
        return permissions.contains(p);
    }

    public boolean hasAny(Permission... ps) {
        for (Permission p : ps) {
            if (has(p)) {
                return true;
            }
        }
        return false;
    }

    public boolean isSelf(long otherEmpId) {
        return empId != null && empId == otherEmpId;
    }

    /**
     * 사원 데이터 범위 안인가. 전체 조회 권한, 부서장 범위(현재 소속 부서가 맡은 부서·하위부서), 본인 중 하나.
     */
    public boolean canSeeEmployee(long targetEmpId, Long targetDeptId) {
        if (has(Permission.EMP_READ_ALL)) {
            return true;
        }
        if (has(Permission.EMP_READ_DEPT) && targetDeptId != null && managedDeptIds.contains(targetDeptId)) {
            return true;
        }
        return has(Permission.EMP_READ_SELF) && isSelf(targetEmpId);
    }

    /** 인적사항 원문을 볼 수 있는가. 권한이 있거나 본인이면 볼 수 있다. */
    public boolean canSeePersonal(long targetEmpId) {
        return has(Permission.EMP_PERSONAL_VIEW) || isSelf(targetEmpId);
    }

    /** 로그에 남길 역할 목록(쉼표 구분, 200자 이내). */
    public String rolesText() {
        String s = String.join(",", roles);
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    /**
     * 기존 사원 서비스가 받는 요청자 형식으로 바꾼다. 역할은 하나만 들어가므로 인사 &gt; 급여 &gt; 관리자 &gt; 일반 순으로 고른다
     * (주민등록번호 원문 권한이 있는 역할을 앞에 둬 RrnService 의 역할 검사와 RRN_REVEAL 권한이 어긋나지 않게 함).
     */
    public RequestActor toActor() {
        RequestActor.Role r;
        if (roles.contains(ROLE_HR)) {
            r = RequestActor.Role.HR;
        } else if (roles.contains(ROLE_PAYROLL)) {
            r = RequestActor.Role.PAYROLL;
        } else if (roles.contains(ROLE_ADMIN)) {
            r = RequestActor.Role.ADMIN;
        } else {
            r = RequestActor.Role.EMPLOYEE;
        }
        return new RequestActor(loginId, r, clientIp, homeEnterCd);
    }
}
