-- 여러 법인 5단계: 사원·주민등록번호 (#123). 설계: docs/tenant-design.md 3.2·4.4·8.1
--
-- 이 마이그레이션이 하는 일
--   1. employee PK 를 (enter_cd, emp_id), 사번 UNIQUE 를 (enter_cd, emp_no) 로 바꾼다.
--      사번은 법인마다 따로 매긴다(V034 emp_no_seq). emp_id(IDENTITY)는 그대로 전체에서 유일하다.
--      V032 의 임시 UNIQUE uq_employee_enter_emp 는 PK 와 열 조합이 같아 지운다(Oracle 은 둘을 함께 둘 수 없다).
--   2. employee 를 참조하는 FK 를 모두 (enter_cd, 사원) 복합 FK 로 바꿔 다른 법인 사원을 가리킬 수 없게 한다.
--      자식 테이블에는 enter_cd 열이 이미 있다(V031). 자식 테이블 PK 는 각 모듈 이슈에서 바꾼다.
--   3. emp_rrn PK 를 (enter_cd, emp_id), HMAC UNIQUE 를 (enter_cd, rrn_hash) 로 바꾼다.
--      전적은 "퇴직 + 다른 법인 신규 입사"(6장)라 같은 사람의 주민등록번호가 두 법인에 하나씩 있을 수 있다(3.2 ※).
--   4. rrn_access_log: enter_cd = 대상 법인, accessor_enter_cd(신규) = 주체 법인(4.4). PK 를 (enter_cd, log_id) 로.
--      기존 행은 둘 다 'C001'. 개인정보 보호법 제29조(안전조치의무)·접속기록 — 주체·대상 법인을 함께 남긴다.
--   5. employee·emp_rrn·rrn_access_log 의 enter_cd DEFAULT 'C001' 을 지운다(8.1 2-3).
--
-- 법령 수치 계산 없음.
-- JPA 엔티티: com.glassehr.employee (ddl-auto: validate)

-- 1) employee 를 참조하는 FK 먼저
ALTER TABLE app_user DROP CONSTRAINT fk_app_user_emp;
ALTER TABLE org_dept_hist DROP CONSTRAINT fk_org_dept_hist_head;
ALTER TABLE emp_rrn DROP CONSTRAINT fk_emp_rrn_emp;
ALTER TABLE employment_contract DROP CONSTRAINT fk_employment_contract_emp;
ALTER TABLE hr_appointment DROP CONSTRAINT fk_hr_appt_emp;
ALTER TABLE emp_work_pattern DROP CONSTRAINT fk_emp_work_pattern_emp;
ALTER TABLE worker_rep_agreement DROP CONSTRAINT fk_wra_rep_emp;
ALTER TABLE attendance_punch DROP CONSTRAINT fk_punch_emp;
ALTER TABLE retirement_case DROP CONSTRAINT fk_retirement_case_emp;
ALTER TABLE employment_certificate DROP CONSTRAINT fk_employment_cert_emp;
ALTER TABLE attendance_daily_summary DROP CONSTRAINT fk_daily_summary_emp;
ALTER TABLE approval_doc DROP CONSTRAINT fk_approval_doc_requester;
ALTER TABLE approval_doc_step DROP CONSTRAINT fk_approval_dstep_actor;
ALTER TABLE approval_doc_step_approver DROP CONSTRAINT fk_approval_dsa_emp;
ALTER TABLE approval_action_log DROP CONSTRAINT fk_approval_log_actor;
ALTER TABLE special_overtime_auth_emp DROP CONSTRAINT fk_sot_emp_emp;
ALTER TABLE overtime_request DROP CONSTRAINT fk_overtime_request_emp;
ALTER TABLE family_leave_request DROP CONSTRAINT fk_family_leave_emp;
ALTER TABLE flex_work_assign DROP CONSTRAINT fk_flex_work_assign_emp;
ALTER TABLE msg_thread_member DROP CONSTRAINT fk_msg_member_emp;

-- 2) employee
ALTER TABLE employee MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE employee DROP CONSTRAINT uq_employee_enter_emp DROP INDEX;
ALTER TABLE employee DROP CONSTRAINT uq_employee_emp_no DROP INDEX;
ALTER TABLE employee DROP CONSTRAINT pk_employee DROP INDEX;
ALTER TABLE employee ADD CONSTRAINT pk_employee PRIMARY KEY (enter_cd, emp_id);
ALTER TABLE employee ADD CONSTRAINT uq_employee_emp_no UNIQUE (enter_cd, emp_no);
DROP INDEX ix_employee_dept;
CREATE INDEX ix_employee_dept ON employee (enter_cd, current_dept_id);

-- 3) emp_rrn
ALTER TABLE emp_rrn MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE emp_rrn DROP CONSTRAINT uq_emp_rrn_hash DROP INDEX;
ALTER TABLE emp_rrn DROP CONSTRAINT pk_emp_rrn DROP INDEX;
ALTER TABLE emp_rrn ADD CONSTRAINT pk_emp_rrn PRIMARY KEY (enter_cd, emp_id);
ALTER TABLE emp_rrn ADD CONSTRAINT uq_emp_rrn_hash UNIQUE (enter_cd, rrn_hash);

-- 4) rrn_access_log: 대상 법인(enter_cd)·주체 법인(accessor_enter_cd)
ALTER TABLE rrn_access_log ADD (accessor_enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_rrn_log_accessor_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_rrn_log_accessor_enter CHECK (REGEXP_LIKE(accessor_enter_cd, '^[A-Z0-9]+$', 'c')
        AND LENGTH(accessor_enter_cd) >= 2));
ALTER TABLE rrn_access_log MODIFY (enter_cd DEFAULT NULL, accessor_enter_cd DEFAULT NULL);
ALTER TABLE rrn_access_log DROP CONSTRAINT pk_rrn_access_log DROP INDEX;
ALTER TABLE rrn_access_log ADD CONSTRAINT pk_rrn_access_log PRIMARY KEY (enter_cd, log_id);
DROP INDEX ix_rrn_access_log_emp;
DROP INDEX ix_rrn_access_log_at;
CREATE INDEX ix_rrn_access_log_emp ON rrn_access_log (enter_cd, emp_id, accessed_at);
CREATE INDEX ix_rrn_access_log_at ON rrn_access_log (enter_cd, accessed_at);

-- 5) 복합 FK (→ employee (enter_cd, emp_id))
ALTER TABLE app_user ADD CONSTRAINT fk_app_user_emp FOREIGN KEY (enter_cd, emp_id)
    REFERENCES employee (enter_cd, emp_id);
ALTER TABLE org_dept_hist ADD CONSTRAINT fk_org_dept_hist_head FOREIGN KEY (enter_cd, head_emp_id)
    REFERENCES employee (enter_cd, emp_id);
ALTER TABLE emp_rrn ADD CONSTRAINT fk_emp_rrn_emp FOREIGN KEY (enter_cd, emp_id)
    REFERENCES employee (enter_cd, emp_id);
ALTER TABLE employment_contract ADD CONSTRAINT fk_employment_contract_emp FOREIGN KEY (enter_cd, emp_id)
    REFERENCES employee (enter_cd, emp_id);
ALTER TABLE hr_appointment ADD CONSTRAINT fk_hr_appt_emp FOREIGN KEY (enter_cd, emp_id)
    REFERENCES employee (enter_cd, emp_id);
ALTER TABLE emp_work_pattern ADD CONSTRAINT fk_emp_work_pattern_emp FOREIGN KEY (enter_cd, emp_id)
    REFERENCES employee (enter_cd, emp_id);
ALTER TABLE worker_rep_agreement ADD CONSTRAINT fk_wra_rep_emp FOREIGN KEY (enter_cd, rep_emp_id)
    REFERENCES employee (enter_cd, emp_id);
ALTER TABLE attendance_punch ADD CONSTRAINT fk_punch_emp FOREIGN KEY (enter_cd, emp_id)
    REFERENCES employee (enter_cd, emp_id);
ALTER TABLE retirement_case ADD CONSTRAINT fk_retirement_case_emp FOREIGN KEY (enter_cd, emp_id)
    REFERENCES employee (enter_cd, emp_id);
ALTER TABLE employment_certificate ADD CONSTRAINT fk_employment_cert_emp FOREIGN KEY (enter_cd, emp_id)
    REFERENCES employee (enter_cd, emp_id);
ALTER TABLE attendance_daily_summary ADD CONSTRAINT fk_daily_summary_emp FOREIGN KEY (enter_cd, emp_id)
    REFERENCES employee (enter_cd, emp_id);
ALTER TABLE approval_doc ADD CONSTRAINT fk_approval_doc_requester FOREIGN KEY (enter_cd, requester_emp_id)
    REFERENCES employee (enter_cd, emp_id);
ALTER TABLE approval_doc_step ADD CONSTRAINT fk_approval_dstep_actor FOREIGN KEY (enter_cd, acted_emp_id)
    REFERENCES employee (enter_cd, emp_id);
ALTER TABLE approval_doc_step_approver ADD CONSTRAINT fk_approval_dsa_emp FOREIGN KEY (enter_cd, emp_id)
    REFERENCES employee (enter_cd, emp_id);
ALTER TABLE approval_action_log ADD CONSTRAINT fk_approval_log_actor FOREIGN KEY (enter_cd, actor_emp_id)
    REFERENCES employee (enter_cd, emp_id);
ALTER TABLE special_overtime_auth_emp ADD CONSTRAINT fk_sot_emp_emp FOREIGN KEY (enter_cd, emp_id)
    REFERENCES employee (enter_cd, emp_id);
ALTER TABLE overtime_request ADD CONSTRAINT fk_overtime_request_emp FOREIGN KEY (enter_cd, emp_id)
    REFERENCES employee (enter_cd, emp_id);
ALTER TABLE family_leave_request ADD CONSTRAINT fk_family_leave_emp FOREIGN KEY (enter_cd, emp_id)
    REFERENCES employee (enter_cd, emp_id);
ALTER TABLE flex_work_assign ADD CONSTRAINT fk_flex_work_assign_emp FOREIGN KEY (enter_cd, emp_id)
    REFERENCES employee (enter_cd, emp_id);
ALTER TABLE msg_thread_member ADD CONSTRAINT fk_msg_member_emp FOREIGN KEY (enter_cd, emp_id)
    REFERENCES employee (enter_cd, emp_id);
