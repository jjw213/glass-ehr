package com.glassehr.employee;

import com.glassehr.org.DeptMemberChecker;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * 부서 폐지 전 재직자 확인을 사원 테이블의 현재 소속으로 한다(#14, 기존 NoEmployeeDeptMemberChecker 대체).
 * 인사발령 이슈에서 발령 이력(미래 발령 포함) 기준으로 다시 바꾼다.
 */
@Component
public class EmployeeDeptMemberChecker implements DeptMemberChecker {

    private final EmployeeRepository employees;

    public EmployeeDeptMemberChecker(EmployeeRepository employees) {
        this.employees = employees;
    }

    @Override
    public long countMembersOnOrAfter(long deptId, LocalDate date) {
        return employees.countInDeptOnOrAfter(deptId, date);
    }
}
