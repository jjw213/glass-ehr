-- 여러 법인 6단계: 근무유형·휴일·근로자대표 서면합의 (#125). 설계: docs/tenant-design.md 3.2·8.1
--
-- 이 마이그레이션이 하는 일
--   1. work_pattern PK 를 (enter_cd, pattern_id), 코드 UNIQUE 를 (enter_cd, pattern_code) 로 바꾼다.
--      같은 근무유형 코드를 법인마다 따로 쓸 수 있다. IDENTITY 열(pattern_id 등)은 그대로 전체에서 유일하다.
--   2. emp_work_pattern PK 를 (enter_cd, assign_id), 시작일 UNIQUE 를 (enter_cd, emp_id, valid_from) 로 바꾼다.
--   3. worker_rep_agreement PK 를 (enter_cd, agreement_id), worker_rep_agreement_file PK 를 (enter_cd, file_id) 로.
--   4. holiday PK 를 (enter_cd, holiday_id), 날짜·이름 UNIQUE 를 (enter_cd, holiday_date, holiday_name) 로,
--      회사휴일·대체된 휴일 날짜 유일 인덱스 ux_holiday_reserved_date 에 법인을 넣는다(법인마다 그 날 하나).
--      ※ uq_holiday_replaces 는 단일 열(replaces_holiday_id) 그대로 둔다. Oracle 복합 UNIQUE 는 일부 열만 NULL 인 행끼리도
--        같은 값으로 보아(('C001', NULL) 두 행 충돌) 대체가 아닌 휴일을 두 개 이상 둘 수 없게 된다. holiday_id 는 전체에서
--        유일하고 아래 fk_holiday_replaces 가 같은 법인 휴일만 가리키게 하므로 법인별 유일과 같다.
--   5. 이 테이블들을 참조하는 FK 를 (enter_cd, 부모키) 복합 FK 로 바꿔 다른 법인 행을 가리킬 수 없게 한다:
--      work_pattern ← fk_emp_work_pattern_pattern, fk_daily_summary_pattern, fk_family_leave_pattern
--      worker_rep_agreement ← fk_holiday_agreement, fk_wra_file_agreement, fk_flex_work_assign_agreement
--      emp_work_pattern ← fk_family_leave_reduced, fk_family_leave_restore
--      holiday ← fk_holiday_replaces(자기 참조)
--   6. 조회 인덱스 앞에 enter_cd 를 넣고, 다섯 테이블의 enter_cd DEFAULT 'C001' 을 지운다(8.1 2-3).
--
-- 법령 수치 계산 없음.
-- JPA 엔티티: com.glassehr.attendance.WorkPattern·EmpWorkPattern·Holiday·LaborAgreement·LaborAgreementFile (ddl-auto: validate)

-- 1) 참조 FK 먼저
ALTER TABLE emp_work_pattern DROP CONSTRAINT fk_emp_work_pattern_pattern;
ALTER TABLE attendance_daily_summary DROP CONSTRAINT fk_daily_summary_pattern;
ALTER TABLE family_leave_request DROP CONSTRAINT fk_family_leave_pattern;
ALTER TABLE family_leave_request DROP CONSTRAINT fk_family_leave_reduced;
ALTER TABLE family_leave_request DROP CONSTRAINT fk_family_leave_restore;
ALTER TABLE holiday DROP CONSTRAINT fk_holiday_replaces;
ALTER TABLE holiday DROP CONSTRAINT fk_holiday_agreement;
ALTER TABLE worker_rep_agreement_file DROP CONSTRAINT fk_wra_file_agreement;
ALTER TABLE flex_work_assign DROP CONSTRAINT fk_flex_work_assign_agreement;

-- 2) 근무유형
ALTER TABLE work_pattern MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE work_pattern DROP CONSTRAINT uq_work_pattern_code DROP INDEX;
ALTER TABLE work_pattern DROP CONSTRAINT pk_work_pattern DROP INDEX;
ALTER TABLE work_pattern ADD CONSTRAINT pk_work_pattern PRIMARY KEY (enter_cd, pattern_id);
ALTER TABLE work_pattern ADD CONSTRAINT uq_work_pattern_code UNIQUE (enter_cd, pattern_code);

-- 3) 사원 근무유형 배정
ALTER TABLE emp_work_pattern MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE emp_work_pattern DROP CONSTRAINT uq_emp_work_pattern_start DROP INDEX;
ALTER TABLE emp_work_pattern DROP CONSTRAINT pk_emp_work_pattern DROP INDEX;
ALTER TABLE emp_work_pattern ADD CONSTRAINT pk_emp_work_pattern PRIMARY KEY (enter_cd, assign_id);
ALTER TABLE emp_work_pattern ADD CONSTRAINT uq_emp_work_pattern_start UNIQUE (enter_cd, emp_id, valid_from);
DROP INDEX ix_emp_work_pattern_pattern;
CREATE INDEX ix_emp_work_pattern_pattern ON emp_work_pattern (enter_cd, pattern_id);

-- 4) 근로자대표 서면합의·첨부
ALTER TABLE worker_rep_agreement MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE worker_rep_agreement DROP CONSTRAINT pk_worker_rep_agreement DROP INDEX;
ALTER TABLE worker_rep_agreement ADD CONSTRAINT pk_worker_rep_agreement PRIMARY KEY (enter_cd, agreement_id);
DROP INDEX ix_wra_kind;
DROP INDEX ix_wra_effective_to;
DROP INDEX ix_wra_rep_emp;
CREATE INDEX ix_wra_kind ON worker_rep_agreement (enter_cd, agreement_kind, effective_from);
CREATE INDEX ix_wra_effective_to ON worker_rep_agreement (enter_cd, effective_to);
CREATE INDEX ix_wra_rep_emp ON worker_rep_agreement (enter_cd, rep_emp_id);

ALTER TABLE worker_rep_agreement_file MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE worker_rep_agreement_file DROP CONSTRAINT pk_wra_file DROP INDEX;
ALTER TABLE worker_rep_agreement_file ADD CONSTRAINT pk_wra_file PRIMARY KEY (enter_cd, file_id);
DROP INDEX ix_wra_file_agreement;
CREATE INDEX ix_wra_file_agreement ON worker_rep_agreement_file (enter_cd, agreement_id);

-- 5) 휴일
ALTER TABLE holiday MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE holiday DROP CONSTRAINT uq_holiday_date_name DROP INDEX;
ALTER TABLE holiday DROP CONSTRAINT pk_holiday DROP INDEX;
ALTER TABLE holiday ADD CONSTRAINT pk_holiday PRIMARY KEY (enter_cd, holiday_id);
ALTER TABLE holiday ADD CONSTRAINT uq_holiday_date_name UNIQUE (enter_cd, holiday_date, holiday_name);
DROP INDEX ix_holiday_agreement;
CREATE INDEX ix_holiday_agreement ON holiday (enter_cd, agreement_id);
-- 다른 종류 행은 두 CASE 가 모두 NULL 이라 인덱스에 들어가지 않는다(V017 과 같은 방식).
DROP INDEX ux_holiday_reserved_date;
CREATE UNIQUE INDEX ux_holiday_reserved_date ON holiday (
    CASE WHEN holiday_kind IN ('COMPANY', 'REPLACEMENT') THEN enter_cd END,
    CASE WHEN holiday_kind IN ('COMPANY', 'REPLACEMENT') THEN holiday_date END);

-- 6) 복합 FK
ALTER TABLE emp_work_pattern ADD CONSTRAINT fk_emp_work_pattern_pattern FOREIGN KEY (enter_cd, pattern_id)
    REFERENCES work_pattern (enter_cd, pattern_id);
ALTER TABLE attendance_daily_summary ADD CONSTRAINT fk_daily_summary_pattern FOREIGN KEY (enter_cd, pattern_id)
    REFERENCES work_pattern (enter_cd, pattern_id);
ALTER TABLE family_leave_request ADD CONSTRAINT fk_family_leave_pattern FOREIGN KEY (enter_cd, work_pattern_id)
    REFERENCES work_pattern (enter_cd, pattern_id);
ALTER TABLE family_leave_request ADD CONSTRAINT fk_family_leave_reduced FOREIGN KEY (enter_cd, reduced_assign_id)
    REFERENCES emp_work_pattern (enter_cd, assign_id);
ALTER TABLE family_leave_request ADD CONSTRAINT fk_family_leave_restore FOREIGN KEY (enter_cd, restore_assign_id)
    REFERENCES emp_work_pattern (enter_cd, assign_id);
ALTER TABLE holiday ADD CONSTRAINT fk_holiday_replaces FOREIGN KEY (enter_cd, replaces_holiday_id)
    REFERENCES holiday (enter_cd, holiday_id);
ALTER TABLE holiday ADD CONSTRAINT fk_holiday_agreement FOREIGN KEY (enter_cd, agreement_id)
    REFERENCES worker_rep_agreement (enter_cd, agreement_id);
ALTER TABLE worker_rep_agreement_file ADD CONSTRAINT fk_wra_file_agreement FOREIGN KEY (enter_cd, agreement_id)
    REFERENCES worker_rep_agreement (enter_cd, agreement_id);
ALTER TABLE flex_work_assign ADD CONSTRAINT fk_flex_work_assign_agreement FOREIGN KEY (enter_cd, agreement_id)
    REFERENCES worker_rep_agreement (enter_cd, agreement_id);
