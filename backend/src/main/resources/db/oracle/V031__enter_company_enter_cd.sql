-- 여러 법인 1단계 (#118). 설계: docs/tenant-design.md 2장·3장·8.1
--
-- 이 마이그레이션이 하는 일
--   1. 법인 마스터 enter_company(전체 공통)를 만들고 기존 데이터의 법인 C001 행을 넣는다.
--   2. 법인별 테이블 42개에 enter_cd 를 더한다. 기존 행은 DEFAULT 로 모두 'C001'이 된다.
--      각 열에 fk_<테이블>_enter(→ enter_company)와 형식 CHECK ck_<테이블>_enter 를 건다.
--      30자 제한을 넘는 테이블 이름은 기존 제약 이름과 같은 약어를 쓴다
--      (daily_summary, approval_tstep, approval_dsa, retirement_checklist, employment_cert, wra_file, sot_emp).
--
-- 이 단계에서 하지 않는 것
--   - PK·UNIQUE·FK 는 아직 바꾸지 않는다(모듈별 후속 이슈, tenant-design.md 8.2).
--   - DEFAULT 'C001'은 남겨 둔다. 엔티티·쿼리가 아직 enter_cd 를 넣지 않으므로, 각 테이블의 PK 를 바꾸는
--     후속 이슈에서 DEFAULT 를 지운다(8.1 2-3).
--   - 엔티티에는 아직 enter_cd 필드를 넣지 않는다(ddl-auto: validate 는 남는 열을 허용한다).
--
-- 형식: 영문 대문자·숫자 2~10자. 정규식 반복 횟수 중괄호는 Oracle JDBC escape 와 겹치므로(V006 주석)
--   REGEXP_LIKE(..., '^[A-Z0-9]+$') + LENGTH >= 2 로 쓰고, 10자 상한은 열 길이 VARCHAR2(10 CHAR)로 건다.
--
-- login_access_log 는 예외(tenant-design.md 3.2 ※※·4.4): 없는 법인으로 시도한 로그인 실패도 남겨야 하므로
--   NULL 을 허용하고 FK 를 걸지 않는다. 기존 행과 지금(회사 선택 전) 로그인 기록은 DEFAULT 로 'C001'.
-- 법령 수치 계산 없음.
-- JPA 엔티티: com.glassehr.tenant.EnterCompany (ddl-auto: validate)

CREATE TABLE enter_company (
    enter_cd      VARCHAR2(10 CHAR)   NOT NULL,
    enter_name    VARCHAR2(100 CHAR)  NOT NULL,
    biz_reg_no    VARCHAR2(10 CHAR)   NOT NULL,
    use_yn        VARCHAR2(1 CHAR)    DEFAULT 'Y' NOT NULL,
    sort_order    NUMBER(5)           DEFAULT 0 NOT NULL,
    created_at    TIMESTAMP(6)        DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at    TIMESTAMP(6)        DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT pk_enter_company PRIMARY KEY (enter_cd),
    CONSTRAINT uq_enter_company_biz_reg_no UNIQUE (biz_reg_no),
    CONSTRAINT ck_enter_company_cd
        CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2),
    CONSTRAINT ck_enter_company_name CHECK (LENGTH(TRIM(enter_name)) > 0),
    -- 사업자등록번호 숫자 10자리(하이픈 없이). 공개 정보라 암호화하지 않는다. 체크섬 검사는 근거 미확인이라 넣지 않는다.
    CONSTRAINT ck_enter_company_biz_reg_no
        CHECK (REGEXP_LIKE(biz_reg_no, '^[0-9]+$', 'c') AND LENGTH(biz_reg_no) = 10),
    CONSTRAINT ck_enter_company_use_yn CHECK (use_yn IN ('Y', 'N')),
    CONSTRAINT ck_enter_company_sort CHECK (sort_order >= 0)
);

-- 기존 데이터의 법인. 사업자등록번호는 명백한 가짜 값이다(공개 저장소).
INSERT INTO enter_company (enter_cd, enter_name, biz_reg_no, use_yn, sort_order)
VALUES ('C001', '기본 법인(예시)', '0000000000', 'Y', 1);

-- 인사·조직 기반
ALTER TABLE common_code_group ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_common_code_group_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_common_code_group_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE common_code ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_common_code_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_common_code_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE org_dept ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_org_dept_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_org_dept_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE org_dept_hist ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_org_dept_hist_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_org_dept_hist_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE biz_site ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_biz_site_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_biz_site_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE emp_no_seq ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_emp_no_seq_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_emp_no_seq_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE employee ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_employee_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_employee_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE emp_rrn ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_emp_rrn_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_emp_rrn_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE rrn_access_log ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_rrn_access_log_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_rrn_access_log_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE employment_contract ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_employment_contract_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_employment_contract_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));

-- 로그인·권한·로그
ALTER TABLE app_user ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_app_user_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_app_user_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE app_user_role ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_app_user_role_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_app_user_role_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE privacy_access_log ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_privacy_access_log_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_privacy_access_log_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE login_access_log ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001'
    CONSTRAINT ck_login_access_log_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));

-- 인사 업무
ALTER TABLE hr_appointment ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_hr_appointment_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_hr_appointment_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE hr_appointment_cancel ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_hr_appointment_cancel_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_hr_appointment_cancel_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE retirement_case ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_retirement_case_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_retirement_case_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE retirement_checklist_item ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_retirement_checklist_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_retirement_checklist_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE employment_certificate ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_employment_cert_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_employment_cert_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));

-- 근태 기준
ALTER TABLE work_pattern ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_work_pattern_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_work_pattern_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE emp_work_pattern ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_emp_work_pattern_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_emp_work_pattern_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE worker_rep_agreement ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_worker_rep_agreement_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_worker_rep_agreement_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE worker_rep_agreement_file ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_wra_file_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_wra_file_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE holiday ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_holiday_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_holiday_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE flex_work_assign ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_flex_work_assign_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_flex_work_assign_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE flex_work_plan ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_flex_work_plan_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_flex_work_plan_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));

-- 근태 기록·신청
ALTER TABLE attendance_punch ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_attendance_punch_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_attendance_punch_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE attendance_daily_summary ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_daily_summary_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_daily_summary_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE overtime_request ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_overtime_request_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_overtime_request_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE special_overtime_auth ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_special_overtime_auth_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_special_overtime_auth_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE special_overtime_auth_emp ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_sot_emp_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_sot_emp_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE family_leave_request ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_family_leave_request_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_family_leave_request_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));

-- 결재
ALTER TABLE approval_template ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_approval_template_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_approval_template_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE approval_template_step ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_approval_tstep_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_approval_tstep_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE approval_doc ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_approval_doc_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_approval_doc_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE approval_doc_step ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_approval_doc_step_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_approval_doc_step_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE approval_doc_step_approver ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_approval_dsa_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_approval_dsa_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE approval_action_log ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_approval_action_log_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_approval_action_log_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));

-- 메시지
ALTER TABLE msg_thread ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_msg_thread_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_msg_thread_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE msg_thread_member ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_msg_thread_member_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_msg_thread_member_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE msg_message ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_msg_message_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_msg_message_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
ALTER TABLE msg_read ADD (enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_msg_read_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_msg_read_enter CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2));
