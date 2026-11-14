package com.glassehr.code;

import java.util.List;

/**
 * 로직이 참조하는 공통코드 그룹코드. 시드(V007__common_code_seed.sql, V014__hr_appointment.sql)와 같아야 한다(CommonCodeSeedScriptTest).
 * 그룹은 마이그레이션으로만 추가한다. 화면에서는 그룹 안의 코드만 추가·수정한다.
 */
public final class CodeGroups {

    /** 직급 */
    public static final String JOB_GRADE = "JOB_GRADE";
    /** 직책 */
    public static final String JOB_TITLE = "JOB_TITLE";
    /** 직군 */
    public static final String JOB_FAMILY = "JOB_FAMILY";
    /** 고용형태. 기간제 및 단시간근로자 보호 등에 관한 법률 제2조 제1호·제2호의 구분을 따른다. */
    public static final String EMPLOYMENT_TYPE = "EMPLOYMENT_TYPE";
    /** 근무지 */
    public static final String WORK_LOCATION = "WORK_LOCATION";
    /** 퇴직사유 */
    public static final String RETIREMENT_REASON = "RETIREMENT_REASON";

    /** 인사발령 유형(V014__hr_appointment.sql). 코드값은 com.glassehr.appointment.AppointmentType 과 같다. */
    public static final String APPOINTMENT_TYPE = "APPOINTMENT_TYPE";

    /** 시드 파일 순서(V007 → V014)대로. */
    public static final List<String> ALL = List.of(
            JOB_GRADE, JOB_TITLE, JOB_FAMILY, EMPLOYMENT_TYPE, WORK_LOCATION, RETIREMENT_REASON, APPOINTMENT_TYPE);

    /** 그룹코드·코드값 형식. DB CHECK(ck_common_code_code)와 같다. */
    public static final String CODE_PATTERN = "^[A-Z][A-Z0-9_]{0,29}$";

    private CodeGroups() {
    }
}
