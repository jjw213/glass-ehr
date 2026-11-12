package com.glassehr.org;

import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * 사원 테이블이 아직 없는 동안의 {@link DeptMemberChecker}. 사원이 한 명도 없으므로 0을 돌려준다.
 * 사원 등록·인사발령 이슈에서 발령 이력을 조회하는 구현으로 바꾸고 이 클래스는 지운다.
 * (폐지 거부 동작 자체는 OrgDeptServiceTest 가 검증한다.)
 */
@Component
public class NoEmployeeDeptMemberChecker implements DeptMemberChecker {

    @Override
    public long countMembersOnOrAfter(long deptId, LocalDate date) {
        return 0;
    }
}
