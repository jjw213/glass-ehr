package com.glassehr.org;

import java.time.LocalDate;

/**
 * 부서 폐지 전 재직자 확인. 폐지되는 부서에 소속 사원이 남아 있으면 폐지를 막는다(docs/org-history-design.md 3장).
 *
 * <p>구현은 {@code com.glassehr.employee.EmployeeDeptMemberChecker}(#14, 사원의 현재 소속 기준).
 * 인사발령 이슈에서 발령 이력(미래 발령 포함) 기준으로 바꾼다.
 */
public interface DeptMemberChecker {

    /**
     * {@code date} 당일이나 그 뒤에 이 부서에 소속된(미래 발령 포함) 재직자 수.
     * 폐지일 당일 0시부터 부서가 없어지므로, 폐지일 이후 소속이 하나라도 있으면 폐지할 수 없다.
     */
    long countMembersOnOrAfter(long deptId, LocalDate date);
}
