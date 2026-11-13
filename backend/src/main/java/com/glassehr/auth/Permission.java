package com.glassehr.auth;

/**
 * API 권한. V011 app_permission 코드와 같아야 한다(AuthSchemaScriptTest). 역할별 부여는 app_role_permission.
 */
public enum Permission {
    /** 전체 사원 조회 */
    EMP_READ_ALL,
    /** 자기 부서(하위 포함) 사원 조회 — 부서장 */
    EMP_READ_DEPT,
    /** 본인 정보 조회 — 모든 사용자 */
    EMP_READ_SELF,
    /** 다른 사원 인적사항(생년월일·주소·이력·메모) 원문 보기 */
    EMP_PERSONAL_VIEW,
    /** 사원 등록·수정·퇴직 처리·주민등록번호 등록 */
    EMP_WRITE,
    /** 사원 목록 내려받기 */
    EMP_EXPORT,
    /** 주민등록번호 원문 조회 */
    RRN_REVEAL,
    /** 부서 신설·변경·폐지 */
    ORG_WRITE,
    /** 공통코드 추가·수정 */
    CODE_WRITE,
    /** 개인정보 조회 로그 보기 */
    PRIVACY_LOG_READ
}
