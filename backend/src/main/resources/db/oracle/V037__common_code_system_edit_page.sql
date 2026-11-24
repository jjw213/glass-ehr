-- #140 코드그룹 추가·수정·삭제와 코드 삭제 (docs/common-code-design.md, DECISIONS 2026-11-24)
--
-- 1. common_code_group.system_yn: 로직이 그룹코드를 참조하는 시스템 그룹('Y'). 삭제·사용 여부 변경 불가, 이름·설명·순서만 수정.
--    화면에서 만든 사용자 그룹은 'N'.
-- 2. common_code_group.edit_page: 다른 모듈 화면에서 편집하는 그룹의 화면 키. NULL = 공통코드 화면에서 편집.
-- 3. common_code.system_yn: 로직이 코드값을 직접 참조하는 기본 코드('Y'). 삭제 불가, 사용 안 함만 가능.
--    - APPOINTMENT_TYPE 7개: com.glassehr.appointment.AppointmentType
--    - RETIREMENT_REASON: com.glassehr.retirement.RetirementChecklist 가 case 로 참조하는 7개(OTHER 는 default 라 제외)
--    - EMPLOYMENT_TYPE FIXED_TERM·PART_TIME: EmployeeService·ContractTermsChecker
-- 이미 있는 모든 법인 행에 같은 규칙을 적용한다(코드그룹은 법인별, #121).

ALTER TABLE common_code_group ADD (
    system_yn  VARCHAR2(1 CHAR)  DEFAULT 'N' NOT NULL,
    edit_page  VARCHAR2(30 CHAR),
    CONSTRAINT ck_common_code_group_system CHECK (system_yn IN ('Y', 'N'))
);

ALTER TABLE common_code ADD (
    system_yn  VARCHAR2(1 CHAR)  DEFAULT 'N' NOT NULL,
    CONSTRAINT ck_common_code_system CHECK (system_yn IN ('Y', 'N'))
);

UPDATE common_code_group SET system_yn = 'Y'
 WHERE group_code IN ('JOB_GRADE', 'JOB_TITLE', 'JOB_FAMILY', 'EMPLOYMENT_TYPE', 'WORK_LOCATION',
                      'RETIREMENT_REASON', 'APPOINTMENT_TYPE');

UPDATE common_code SET system_yn = 'Y'
 WHERE group_code = 'APPOINTMENT_TYPE'
    OR (group_code = 'RETIREMENT_REASON'
        AND code IN ('VOLUNTARY', 'RECOMMENDED_RESIGNATION', 'CONTRACT_EXPIRED', 'RETIREMENT_AGE', 'DISMISSAL',
                     'TRANSFER_AFFILIATE', 'DEATH'))
    OR (group_code = 'EMPLOYMENT_TYPE' AND code IN ('FIXED_TERM', 'PART_TIME'));
