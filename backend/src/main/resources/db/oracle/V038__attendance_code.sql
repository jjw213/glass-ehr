-- #143 근태코드: 휴가 종류(LEAVE_TYPE)·근태 구분(ATTENDANCE_TYPE). 설계: docs/attendance-code-design.md
--
-- 1. common_code.process_type: 근태코드의 처리방식. 로직은 코드값이 아니라 처리방식을 본다.
--    LEAVE_TYPE      ANNUAL(연차)·STATUTORY(법정휴가)·COMPANY(회사 약정휴가)·UNPAID(무급휴가)
--    ATTENDANCE_TYPE BUSINESS_TRIP(출장)·OUTSIDE_WORK(외근)·REMOTE(재택)·EDUCATION(교육) — 근무로 인정되는 구분
--    다른 그룹은 NULL. 목록은 com.glassehr.code.AttendanceCodeService.PROCESS_TYPES 와 같아야 한다.
-- 2. 두 그룹은 시스템 그룹(system_yn='Y'), edit_page='attendanceCodes'(근태관리 > 근태코드 관리에서 편집).
--    이미 있는 모든 법인에 넣는다(공통코드는 법인별, #121).
-- 3. 연차·법정휴가 6개는 기본 코드(system_yn='Y', 삭제 불가). 일수·유급 기간은 legal_rate 에서 읽고 화면에서 고치지 않는다.
--    회사 약정휴가 예시(경조사·병가·창립기념일)와 근태 구분 4개는 삭제할 수 있다.
-- 4. attendance_leave_type: 휴가 종류별 상세 설정(사용 단위·유급·증빙·연간 한도·성별 제한·법정 근거 문구).
-- 5. 법정휴가 유급 일수·난임치료휴가 일수 legal_rate_item 은 V039 에서 등록한다. 값은 법령 데이터 작업으로 넣는다
--    (AGENTS.md 법령 규칙). 코드 목록은 com.glassehr.legalrate.LegalRateItem 과 같아야 한다.

ALTER TABLE common_code ADD (
    process_type  VARCHAR2(30 CHAR)
);

INSERT INTO common_code_group (enter_cd, group_code, group_name, description, sort_order, use_yn, system_yn, edit_page)
    SELECT enter_cd, 'LEAVE_TYPE', '휴가 종류', '연차·법정휴가·회사 약정휴가·무급휴가. 근태관리 > 근태코드 관리에서 편집', 70, 'Y', 'Y', 'attendanceCodes'
      FROM enter_company;
INSERT INTO common_code_group (enter_cd, group_code, group_name, description, sort_order, use_yn, system_yn, edit_page)
    SELECT enter_cd, 'ATTENDANCE_TYPE', '근태 구분', '출장·외근·재택·교육 등 근무로 인정되는 구분. 근태관리 > 근태코드 관리에서 편집', 80, 'Y', 'Y', 'attendanceCodes'
      FROM enter_company;

-- 휴가 종류: 연차·법정휴가(기본 코드)
INSERT INTO common_code (enter_cd, group_code, code, code_name, description, sort_order, use_yn, system_yn, process_type)
    SELECT enter_cd, 'LEAVE_TYPE', 'ANNUAL', '연차', '근로기준법 제60조 연차 유급휴가', 10, 'Y', 'Y', 'ANNUAL' FROM enter_company;
INSERT INTO common_code (enter_cd, group_code, code, code_name, description, sort_order, use_yn, system_yn, process_type)
    SELECT enter_cd, 'LEAVE_TYPE', 'MATERNITY', '출산전후휴가', '근로기준법 제74조', 20, 'Y', 'Y', 'STATUTORY' FROM enter_company;
INSERT INTO common_code (enter_cd, group_code, code, code_name, description, sort_order, use_yn, system_yn, process_type)
    SELECT enter_cd, 'LEAVE_TYPE', 'SPOUSE_MATERNITY', '배우자 출산휴가', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의2', 30, 'Y', 'Y', 'STATUTORY' FROM enter_company;
INSERT INTO common_code (enter_cd, group_code, code, code_name, description, sort_order, use_yn, system_yn, process_type)
    SELECT enter_cd, 'LEAVE_TYPE', 'MENSTRUAL', '생리휴가', '근로기준법 제73조', 40, 'Y', 'Y', 'STATUTORY' FROM enter_company;
INSERT INTO common_code (enter_cd, group_code, code, code_name, description, sort_order, use_yn, system_yn, process_type)
    SELECT enter_cd, 'LEAVE_TYPE', 'FAMILY_CARE', '가족돌봄휴가', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제22조의2 제2항', 50, 'Y', 'Y', 'STATUTORY' FROM enter_company;
INSERT INTO common_code (enter_cd, group_code, code, code_name, description, sort_order, use_yn, system_yn, process_type)
    SELECT enter_cd, 'LEAVE_TYPE', 'INFERTILITY_TREATMENT', '난임치료휴가', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의3', 60, 'Y', 'Y', 'STATUTORY' FROM enter_company;
-- 휴가 종류: 회사 약정휴가 예시(삭제 가능)
INSERT INTO common_code (enter_cd, group_code, code, code_name, description, sort_order, use_yn, system_yn, process_type)
    SELECT enter_cd, 'LEAVE_TYPE', 'CONDOLENCE', '경조사', '회사 약정휴가 예시', 110, 'Y', 'N', 'COMPANY' FROM enter_company;
INSERT INTO common_code (enter_cd, group_code, code, code_name, description, sort_order, use_yn, system_yn, process_type)
    SELECT enter_cd, 'LEAVE_TYPE', 'SICK', '병가', '회사 약정휴가 예시(무급·증빙 필요)', 120, 'Y', 'N', 'COMPANY' FROM enter_company;
INSERT INTO common_code (enter_cd, group_code, code, code_name, description, sort_order, use_yn, system_yn, process_type)
    SELECT enter_cd, 'LEAVE_TYPE', 'FOUNDATION_DAY', '창립기념일', '회사 약정휴가 예시', 130, 'Y', 'N', 'COMPANY' FROM enter_company;
-- 근태 구분
INSERT INTO common_code (enter_cd, group_code, code, code_name, description, sort_order, use_yn, system_yn, process_type)
    SELECT enter_cd, 'ATTENDANCE_TYPE', 'BUSINESS_TRIP', '출장', NULL, 10, 'Y', 'N', 'BUSINESS_TRIP' FROM enter_company;
INSERT INTO common_code (enter_cd, group_code, code, code_name, description, sort_order, use_yn, system_yn, process_type)
    SELECT enter_cd, 'ATTENDANCE_TYPE', 'OUTSIDE_WORK', '외근', NULL, 20, 'Y', 'N', 'OUTSIDE_WORK' FROM enter_company;
INSERT INTO common_code (enter_cd, group_code, code, code_name, description, sort_order, use_yn, system_yn, process_type)
    SELECT enter_cd, 'ATTENDANCE_TYPE', 'REMOTE', '재택근무', NULL, 30, 'Y', 'N', 'REMOTE' FROM enter_company;
INSERT INTO common_code (enter_cd, group_code, code, code_name, description, sort_order, use_yn, system_yn, process_type)
    SELECT enter_cd, 'ATTENDANCE_TYPE', 'EDUCATION', '교육', NULL, 40, 'Y', 'N', 'EDUCATION' FROM enter_company;

-- 근태코드 그룹은 처리방식이 꼭 있고, 다른 그룹은 처리방식을 두지 않는다.
ALTER TABLE common_code ADD CONSTRAINT ck_common_code_process CHECK (
    (group_code = 'LEAVE_TYPE' AND process_type IN ('ANNUAL', 'STATUTORY', 'COMPANY', 'UNPAID'))
    OR (group_code = 'ATTENDANCE_TYPE' AND process_type IN ('BUSINESS_TRIP', 'OUTSIDE_WORK', 'REMOTE', 'EDUCATION'))
    OR (group_code NOT IN ('LEAVE_TYPE', 'ATTENDANCE_TYPE') AND process_type IS NULL));

-- 휴가 종류 상세 설정. 코드(common_code)와 1:1, 코드를 지울 때 함께 지운다(AttendanceCodeService).
--   unit_*_yn         사용 단위(종일·오전/오후 반차·시간 단위). 하나 이상.
--   paid_yn           유급 여부. 법정휴가는 NULL(유급 기간은 legal_rate 에서 읽는다). 생리휴가는 무급(근로기준법 제73조).
--   annual_limit_days 연간 한도 일수. NULL = 한도 없음(연차·법정휴가는 legal_rate·연차 계산에서 정한다).
--   gender_limit      성별 제한(F/M). 법정휴가 중 해당 휴가만(출산전후휴가 제74조·생리휴가 제73조: 여성 근로자).
CREATE TABLE attendance_leave_type (
    enter_cd           VARCHAR2(10 CHAR)  NOT NULL,
    group_code         VARCHAR2(30 CHAR)  DEFAULT 'LEAVE_TYPE' NOT NULL,
    code               VARCHAR2(30 CHAR)  NOT NULL,
    unit_day_yn        VARCHAR2(1 CHAR)   DEFAULT 'Y' NOT NULL,
    unit_half_day_yn   VARCHAR2(1 CHAR)   DEFAULT 'N' NOT NULL,
    unit_hour_yn       VARCHAR2(1 CHAR)   DEFAULT 'N' NOT NULL,
    paid_yn            VARCHAR2(1 CHAR),
    proof_required_yn  VARCHAR2(1 CHAR)   DEFAULT 'N' NOT NULL,
    annual_limit_days  NUMBER(4, 1),
    gender_limit       VARCHAR2(1 CHAR),
    legal_basis        VARCHAR2(200 CHAR),
    created_at         TIMESTAMP(6)       DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at         TIMESTAMP(6)       DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT pk_attendance_leave_type PRIMARY KEY (enter_cd, code),
    CONSTRAINT fk_att_leave_type_enter FOREIGN KEY (enter_cd) REFERENCES enter_company (enter_cd),
    CONSTRAINT fk_att_leave_type_code FOREIGN KEY (enter_cd, group_code, code)
        REFERENCES common_code (enter_cd, group_code, code),
    CONSTRAINT ck_att_leave_type_enter
        CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2),
    CONSTRAINT ck_att_leave_type_group CHECK (group_code = 'LEAVE_TYPE'),
    CONSTRAINT ck_att_leave_type_yn CHECK (unit_day_yn IN ('Y', 'N') AND unit_half_day_yn IN ('Y', 'N')
        AND unit_hour_yn IN ('Y', 'N') AND proof_required_yn IN ('Y', 'N')
        AND (paid_yn IS NULL OR paid_yn IN ('Y', 'N'))),
    CONSTRAINT ck_att_leave_type_unit
        CHECK (unit_day_yn = 'Y' OR unit_half_day_yn = 'Y' OR unit_hour_yn = 'Y'),
    CONSTRAINT ck_att_leave_type_limit CHECK (annual_limit_days IS NULL OR annual_limit_days > 0),
    CONSTRAINT ck_att_leave_type_gender CHECK (gender_limit IS NULL OR gender_limit IN ('F', 'M'))
);

INSERT INTO attendance_leave_type (enter_cd, code, unit_day_yn, unit_half_day_yn, unit_hour_yn, paid_yn, proof_required_yn, annual_limit_days, gender_limit, legal_basis)
    SELECT enter_cd, 'ANNUAL', 'Y', 'Y', 'N', 'Y', 'N', NULL, NULL, '근로기준법 제60조' FROM enter_company;
INSERT INTO attendance_leave_type (enter_cd, code, unit_day_yn, unit_half_day_yn, unit_hour_yn, paid_yn, proof_required_yn, annual_limit_days, gender_limit, legal_basis)
    SELECT enter_cd, 'MATERNITY', 'Y', 'N', 'N', NULL, 'Y', NULL, 'F', '근로기준법 제74조' FROM enter_company;
INSERT INTO attendance_leave_type (enter_cd, code, unit_day_yn, unit_half_day_yn, unit_hour_yn, paid_yn, proof_required_yn, annual_limit_days, gender_limit, legal_basis)
    SELECT enter_cd, 'SPOUSE_MATERNITY', 'Y', 'N', 'N', NULL, 'Y', NULL, NULL, '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의2' FROM enter_company;
INSERT INTO attendance_leave_type (enter_cd, code, unit_day_yn, unit_half_day_yn, unit_hour_yn, paid_yn, proof_required_yn, annual_limit_days, gender_limit, legal_basis)
    SELECT enter_cd, 'MENSTRUAL', 'Y', 'N', 'N', 'N', 'N', NULL, 'F', '근로기준법 제73조(무급)' FROM enter_company;
INSERT INTO attendance_leave_type (enter_cd, code, unit_day_yn, unit_half_day_yn, unit_hour_yn, paid_yn, proof_required_yn, annual_limit_days, gender_limit, legal_basis)
    SELECT enter_cd, 'FAMILY_CARE', 'Y', 'N', 'N', NULL, 'N', NULL, NULL, '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제22조의2 제2항' FROM enter_company;
INSERT INTO attendance_leave_type (enter_cd, code, unit_day_yn, unit_half_day_yn, unit_hour_yn, paid_yn, proof_required_yn, annual_limit_days, gender_limit, legal_basis)
    SELECT enter_cd, 'INFERTILITY_TREATMENT', 'Y', 'N', 'N', NULL, 'Y', NULL, NULL, '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의3' FROM enter_company;
INSERT INTO attendance_leave_type (enter_cd, code, unit_day_yn, unit_half_day_yn, unit_hour_yn, paid_yn, proof_required_yn, annual_limit_days, gender_limit, legal_basis)
    SELECT enter_cd, 'CONDOLENCE', 'Y', 'N', 'N', 'Y', 'Y', NULL, NULL, NULL FROM enter_company;
INSERT INTO attendance_leave_type (enter_cd, code, unit_day_yn, unit_half_day_yn, unit_hour_yn, paid_yn, proof_required_yn, annual_limit_days, gender_limit, legal_basis)
    SELECT enter_cd, 'SICK', 'Y', 'N', 'N', 'N', 'Y', NULL, NULL, NULL FROM enter_company;
INSERT INTO attendance_leave_type (enter_cd, code, unit_day_yn, unit_half_day_yn, unit_hour_yn, paid_yn, proof_required_yn, annual_limit_days, gender_limit, legal_basis)
    SELECT enter_cd, 'FOUNDATION_DAY', 'Y', 'N', 'N', 'Y', 'N', NULL, NULL, NULL FROM enter_company;
