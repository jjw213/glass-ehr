package com.glassehr.approval;

/** 결재자 지정 방식. V024 ck_approval_tstep_kind 와 같다. 결재자는 신청일 기준으로 찾는다(ApproverResolver). */
public enum ApproverKind {
    /** 신청자 소속 부서장 */
    DEPT_HEAD(false),
    /** 신청자 소속 부서의 상위 부서장 */
    UPPER_DEPT_HEAD(false),
    /** 직책(approver_ref = 직책 코드)을 가진 재직자 중 한 명 */
    POSITION(true),
    /** 역할(approver_ref = ADMIN·HR·PAYROLL)을 가진 재직자 중 한 명 */
    ROLE(true),
    /** 특정 사원(approver_ref = emp_id) */
    EMPLOYEE(true);

    private final boolean needsRef;

    ApproverKind(boolean needsRef) {
        this.needsRef = needsRef;
    }

    public boolean needsRef() {
        return needsRef;
    }
}
