-- 여러 법인 6단계: 인사발령·근로계약·퇴직 (#124). 설계: docs/tenant-design.md 3.2·8.1
--
-- 이 마이그레이션이 하는 일
--   1. hr_appointment PK 를 (enter_cd, appt_id), 발령번호 UNIQUE 를 (enter_cd, appt_no) 로 바꾼다.
--      시퀀스 hr_appointment_no_seq(V014)는 전체 공통 그대로 둔다(3.2: 법인마다 번호가 띄엄띄엄이어도 된다).
--   2. hr_appointment 를 참조하는 FK 4개(취소 연결 2개, 육아휴직 신청의 휴직·복직 발령 2개)를
--      (enter_cd, 발령) 복합 FK 로 바꿔 다른 법인 발령을 가리킬 수 없게 한다.
--   3. hr_appointment_cancel PK (enter_cd, cancel_appt_id), 원 발령 UNIQUE (enter_cd, original_appt_id).
--   4. employment_contract PK (enter_cd, contract_id).
--   5. retirement_case PK (enter_cd, case_id)·사원 UNIQUE (enter_cd, emp_id),
--      retirement_checklist_item PK (enter_cd, case_id, item_code)·FK (enter_cd, case_id),
--      employment_certificate PK (enter_cd, cert_id).
--   6. 6개 테이블의 enter_cd DEFAULT 'C001' 을 지운다(8.1 2-3). 사원 FK(V036)는 이미 (enter_cd, emp_id) 복합이다.
--   IDENTITY 컬럼(appt_id 등)은 그대로 두며 값은 전체에서 유일하다.
--
-- 법령 수치 계산 없음.
-- JPA 엔티티: com.glassehr.appointment·contract·retirement (ddl-auto: validate)

-- 1) hr_appointment·retirement_case 를 참조하는 FK 먼저
ALTER TABLE hr_appointment_cancel DROP CONSTRAINT fk_hr_appt_cancel_self;
ALTER TABLE hr_appointment_cancel DROP CONSTRAINT fk_hr_appt_cancel_original;
ALTER TABLE family_leave_request DROP CONSTRAINT fk_family_leave_leave_appt;
ALTER TABLE family_leave_request DROP CONSTRAINT fk_family_leave_return_appt;
ALTER TABLE retirement_checklist_item DROP CONSTRAINT fk_retirement_checklist_case;

-- 2) hr_appointment
ALTER TABLE hr_appointment MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE hr_appointment DROP CONSTRAINT uq_hr_appointment_no DROP INDEX;
ALTER TABLE hr_appointment DROP CONSTRAINT pk_hr_appointment DROP INDEX;
ALTER TABLE hr_appointment ADD CONSTRAINT pk_hr_appointment PRIMARY KEY (enter_cd, appt_id);
ALTER TABLE hr_appointment ADD CONSTRAINT uq_hr_appointment_no UNIQUE (enter_cd, appt_no);
DROP INDEX ix_hr_appt_emp;
CREATE INDEX ix_hr_appt_emp ON hr_appointment (enter_cd, emp_id, effective_date);
DROP INDEX ix_hr_appt_effective;
CREATE INDEX ix_hr_appt_effective ON hr_appointment (enter_cd, effective_date);
DROP INDEX ix_hr_appt_before_dept;
CREATE INDEX ix_hr_appt_before_dept ON hr_appointment (enter_cd, before_dept_id);
DROP INDEX ix_hr_appt_after_dept;
CREATE INDEX ix_hr_appt_after_dept ON hr_appointment (enter_cd, after_dept_id);

-- 3) hr_appointment_cancel
ALTER TABLE hr_appointment_cancel MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE hr_appointment_cancel DROP CONSTRAINT uq_hr_appt_cancel_original DROP INDEX;
ALTER TABLE hr_appointment_cancel DROP CONSTRAINT pk_hr_appointment_cancel DROP INDEX;
ALTER TABLE hr_appointment_cancel ADD CONSTRAINT pk_hr_appointment_cancel PRIMARY KEY (enter_cd, cancel_appt_id);
ALTER TABLE hr_appointment_cancel ADD CONSTRAINT uq_hr_appt_cancel_original UNIQUE (enter_cd, original_appt_id);
ALTER TABLE hr_appointment_cancel ADD CONSTRAINT fk_hr_appt_cancel_self FOREIGN KEY (enter_cd, cancel_appt_id)
    REFERENCES hr_appointment (enter_cd, appt_id);
ALTER TABLE hr_appointment_cancel ADD CONSTRAINT fk_hr_appt_cancel_original FOREIGN KEY (enter_cd, original_appt_id)
    REFERENCES hr_appointment (enter_cd, appt_id);

-- 4) 육아휴직 신청의 휴직·복직 발령(V026). 신청 테이블 PK 는 근태 법인 이슈에서 바꾼다.
ALTER TABLE family_leave_request ADD CONSTRAINT fk_family_leave_leave_appt FOREIGN KEY (enter_cd, leave_appt_id)
    REFERENCES hr_appointment (enter_cd, appt_id);
ALTER TABLE family_leave_request ADD CONSTRAINT fk_family_leave_return_appt FOREIGN KEY (enter_cd, return_appt_id)
    REFERENCES hr_appointment (enter_cd, appt_id);

-- 5) employment_contract
ALTER TABLE employment_contract MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE employment_contract DROP CONSTRAINT pk_employment_contract DROP INDEX;
ALTER TABLE employment_contract ADD CONSTRAINT pk_employment_contract PRIMARY KEY (enter_cd, contract_id);
DROP INDEX ix_emp_contract_emp;
CREATE INDEX ix_emp_contract_emp ON employment_contract (enter_cd, emp_id, start_date);
DROP INDEX ix_emp_contract_end;
CREATE INDEX ix_emp_contract_end ON employment_contract (enter_cd, end_date);
DROP INDEX ix_emp_contract_undelivered;
CREATE INDEX ix_emp_contract_undelivered ON employment_contract (enter_cd, written_delivered_date);

-- 6) retirement_case
ALTER TABLE retirement_case MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE retirement_case DROP CONSTRAINT uq_retirement_case_emp DROP INDEX;
ALTER TABLE retirement_case DROP CONSTRAINT pk_retirement_case DROP INDEX;
ALTER TABLE retirement_case ADD CONSTRAINT pk_retirement_case PRIMARY KEY (enter_cd, case_id);
ALTER TABLE retirement_case ADD CONSTRAINT uq_retirement_case_emp UNIQUE (enter_cd, emp_id);

-- 7) retirement_checklist_item
ALTER TABLE retirement_checklist_item MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE retirement_checklist_item DROP CONSTRAINT pk_retirement_checklist DROP INDEX;
ALTER TABLE retirement_checklist_item ADD CONSTRAINT pk_retirement_checklist PRIMARY KEY (enter_cd, case_id, item_code);
ALTER TABLE retirement_checklist_item ADD CONSTRAINT fk_retirement_checklist_case FOREIGN KEY (enter_cd, case_id)
    REFERENCES retirement_case (enter_cd, case_id);

-- 8) employment_certificate
ALTER TABLE employment_certificate MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE employment_certificate DROP CONSTRAINT pk_employment_certificate DROP INDEX;
ALTER TABLE employment_certificate ADD CONSTRAINT pk_employment_certificate PRIMARY KEY (enter_cd, cert_id);
DROP INDEX ix_employment_cert_emp;
CREATE INDEX ix_employment_cert_emp ON employment_certificate (enter_cd, emp_id, issued_at);
