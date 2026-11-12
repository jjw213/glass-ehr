package com.glassehr.org;

import com.glassehr.org.OrgDeptHist.DeptAttrs;

/**
 * 부서 이력 행의 변경 유형(org_dept_hist.change_type). docs/org-history-design.md 3장.
 * 폐지는 이력 행을 추가하지 않고 org_dept.valid_to 와 마지막 행의 valid_to 로 나타낸다.
 */
public enum DeptChangeType {
    /** 신설(첫 행). */
    CREATE,
    /** 이름만 바뀜(부서장·정렬순서가 함께 바뀌어도 이름이 바뀌면 RENAME). */
    RENAME,
    /** 상위부서가 바뀜(다른 속성이 함께 바뀌어도 MOVE). */
    MOVE,
    /** 부서장·정렬순서 등 그 밖의 변경. */
    UPDATE;

    /** 이전 속성과 새 속성을 비교해 대표 유형을 정한다. 우선순위: 상위부서 > 이름 > 그 밖. */
    static DeptChangeType of(DeptAttrs before, DeptAttrs after) {
        if (!before.sameParent(after)) {
            return MOVE;
        }
        if (!before.name().equals(after.name())) {
            return RENAME;
        }
        return UPDATE;
    }
}
