package com.glassehr.appointment;

/** 발령 이전·이후 재직상태. DB CHECK(ck_hr_appt_before_status·ck_hr_appt_after_status, V014)와 같다. */
public enum EmpStatus {
    /** 재직 */
    ACTIVE,
    /** 휴직 */
    ON_LEAVE,
    /** 퇴직 */
    RETIRED
}
