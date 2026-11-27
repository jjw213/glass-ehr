-- 여러 법인 8단계: 결재·연장근로·가족돌봄휴직 (#127). 설계: docs/tenant-design.md 3.2·8.1
--
-- 이 마이그레이션이 하는 일
--   1. 결재 6개 테이블(approval_template·_template_step·_doc·_doc_step·_doc_step_approver·_action_log),
--      special_overtime_auth·special_overtime_auth_emp·overtime_request·family_leave_request 의 PK 앞에 enter_cd 를 넣는다.
--   2. UNIQUE(uq_approval_template_from·uq_approval_doc_ref·uq_approval_doc_step·uq_special_overtime_auth_no)와
--      열린 템플릿 함수 인덱스(ux_approval_template_open)를 법인 안에서만 유일하게 바꾼다.
--   3. 내부 FK 6개(fk_approval_tstep_template·fk_approval_doc_template·fk_approval_dstep_doc·fk_approval_dsa_step·
--      fk_approval_log_doc·fk_sot_emp_auth)를 (enter_cd, 부모키) 복합 FK 로 바꿔 다른 법인 행을 가리킬 수 없게 한다.
--   4. 조회 인덱스 앞에 enter_cd 를 넣고, 10개 테이블의 enter_cd DEFAULT 'C001' 을 지운다(8.1 2-3).
--   사원 FK(fk_approval_doc_requester·fk_approval_dstep_actor·fk_approval_dsa_emp·fk_approval_log_actor·fk_sot_emp_emp·
--   fk_overtime_request_emp·fk_family_leave_emp)는 V036, 발령 FK 는 V038, 근무유형·배정 FK 는 V039 에서 이미 복합 FK 다.
--   그래서 결재선·결재 이력·특별연장근로 대상에 다른 법인 사원을 넣을 수 없다. IDENTITY 열은 그대로 전체에서 유일하다.
--
-- 법령 수치 계산 없음.
-- JPA 엔티티: com.glassehr.approval.ApprovalTemplate·ApprovalDoc·ApprovalDocStep·ApprovalActionLog,
--   com.glassehr.overtime.OvertimeRequest·SpecialOvertimeAuth, com.glassehr.familyleave.FamilyLeaveRequest (ddl-auto: validate)

-- 1) 자식 FK 먼저
ALTER TABLE approval_template_step DROP CONSTRAINT fk_approval_tstep_template;
ALTER TABLE approval_doc DROP CONSTRAINT fk_approval_doc_template;
ALTER TABLE approval_doc_step DROP CONSTRAINT fk_approval_dstep_doc;
ALTER TABLE approval_doc_step_approver DROP CONSTRAINT fk_approval_dsa_step;
ALTER TABLE approval_action_log DROP CONSTRAINT fk_approval_log_doc;
ALTER TABLE special_overtime_auth_emp DROP CONSTRAINT fk_sot_emp_auth;

-- 2) 결재선 템플릿
ALTER TABLE approval_template MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE approval_template DROP CONSTRAINT pk_approval_template DROP INDEX;
ALTER TABLE approval_template ADD CONSTRAINT pk_approval_template PRIMARY KEY (enter_cd, template_id);
ALTER TABLE approval_template DROP CONSTRAINT uq_approval_template_from DROP INDEX;
ALTER TABLE approval_template ADD CONSTRAINT uq_approval_template_from UNIQUE (enter_cd, request_type, valid_from);
DROP INDEX ux_approval_template_open;
-- 종료일 없는(열린) 템플릿은 법인·신청 유형마다 하나. 닫힌 행은 두 식이 모두 NULL 이라 인덱스에 들어가지 않는다.
CREATE UNIQUE INDEX ux_approval_template_open ON approval_template (
    CASE WHEN valid_to IS NULL THEN enter_cd END,
    CASE WHEN valid_to IS NULL THEN request_type END);

ALTER TABLE approval_template_step MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE approval_template_step DROP CONSTRAINT pk_approval_template_step DROP INDEX;
ALTER TABLE approval_template_step ADD CONSTRAINT pk_approval_template_step
    PRIMARY KEY (enter_cd, template_id, step_no);

-- 3) 결재 문서
ALTER TABLE approval_doc MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE approval_doc DROP CONSTRAINT pk_approval_doc DROP INDEX;
ALTER TABLE approval_doc ADD CONSTRAINT pk_approval_doc PRIMARY KEY (enter_cd, approval_id);
ALTER TABLE approval_doc DROP CONSTRAINT uq_approval_doc_ref DROP INDEX;
ALTER TABLE approval_doc ADD CONSTRAINT uq_approval_doc_ref UNIQUE (enter_cd, request_type, request_ref);
DROP INDEX ix_approval_doc_requester;
CREATE INDEX ix_approval_doc_requester ON approval_doc (enter_cd, requester_emp_id, submitted_at);

ALTER TABLE approval_doc_step MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE approval_doc_step DROP CONSTRAINT pk_approval_doc_step DROP INDEX;
ALTER TABLE approval_doc_step ADD CONSTRAINT pk_approval_doc_step PRIMARY KEY (enter_cd, step_id);
ALTER TABLE approval_doc_step DROP CONSTRAINT uq_approval_doc_step DROP INDEX;
ALTER TABLE approval_doc_step ADD CONSTRAINT uq_approval_doc_step UNIQUE (enter_cd, approval_id, step_no);

ALTER TABLE approval_doc_step_approver MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE approval_doc_step_approver DROP CONSTRAINT pk_approval_dstep_approver DROP INDEX;
ALTER TABLE approval_doc_step_approver ADD CONSTRAINT pk_approval_dstep_approver PRIMARY KEY (enter_cd, step_id, emp_id);
DROP INDEX ix_approval_dsa_emp;
CREATE INDEX ix_approval_dsa_emp ON approval_doc_step_approver (enter_cd, emp_id);

ALTER TABLE approval_action_log MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE approval_action_log DROP CONSTRAINT pk_approval_action_log DROP INDEX;
ALTER TABLE approval_action_log ADD CONSTRAINT pk_approval_action_log PRIMARY KEY (enter_cd, action_id);
DROP INDEX ix_approval_log_doc;
CREATE INDEX ix_approval_log_doc ON approval_action_log (enter_cd, approval_id, action_id);

-- 4) 특별연장근로 인가·대상자
ALTER TABLE special_overtime_auth MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE special_overtime_auth DROP CONSTRAINT pk_special_overtime_auth DROP INDEX;
ALTER TABLE special_overtime_auth ADD CONSTRAINT pk_special_overtime_auth PRIMARY KEY (enter_cd, auth_id);
ALTER TABLE special_overtime_auth DROP CONSTRAINT uq_special_overtime_auth_no DROP INDEX;
ALTER TABLE special_overtime_auth ADD CONSTRAINT uq_special_overtime_auth_no UNIQUE (enter_cd, auth_no);

ALTER TABLE special_overtime_auth_emp MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE special_overtime_auth_emp DROP CONSTRAINT pk_special_overtime_auth_emp DROP INDEX;
ALTER TABLE special_overtime_auth_emp ADD CONSTRAINT pk_special_overtime_auth_emp PRIMARY KEY (enter_cd, auth_id, emp_id);

-- 5) 연장·휴일근로 신청
ALTER TABLE overtime_request MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE overtime_request DROP CONSTRAINT pk_overtime_request DROP INDEX;
ALTER TABLE overtime_request ADD CONSTRAINT pk_overtime_request PRIMARY KEY (enter_cd, request_id);
DROP INDEX ix_overtime_request_emp_date;
CREATE INDEX ix_overtime_request_emp_date ON overtime_request (enter_cd, emp_id, work_date);

-- 6) 육아휴직·육아기 단축·가족돌봄휴직 신청
ALTER TABLE family_leave_request MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE family_leave_request DROP CONSTRAINT pk_family_leave_request DROP INDEX;
ALTER TABLE family_leave_request ADD CONSTRAINT pk_family_leave_request PRIMARY KEY (enter_cd, req_id);
DROP INDEX ix_family_leave_emp;
CREATE INDEX ix_family_leave_emp ON family_leave_request (enter_cd, emp_id, leave_kind, start_date);

-- 7) 복합 FK
ALTER TABLE approval_template_step ADD CONSTRAINT fk_approval_tstep_template FOREIGN KEY (enter_cd, template_id)
    REFERENCES approval_template (enter_cd, template_id);
ALTER TABLE approval_doc ADD CONSTRAINT fk_approval_doc_template FOREIGN KEY (enter_cd, template_id)
    REFERENCES approval_template (enter_cd, template_id);
ALTER TABLE approval_doc_step ADD CONSTRAINT fk_approval_dstep_doc FOREIGN KEY (enter_cd, approval_id)
    REFERENCES approval_doc (enter_cd, approval_id);
ALTER TABLE approval_doc_step_approver ADD CONSTRAINT fk_approval_dsa_step FOREIGN KEY (enter_cd, step_id)
    REFERENCES approval_doc_step (enter_cd, step_id);
ALTER TABLE approval_action_log ADD CONSTRAINT fk_approval_log_doc FOREIGN KEY (enter_cd, approval_id)
    REFERENCES approval_doc (enter_cd, approval_id);
ALTER TABLE special_overtime_auth_emp ADD CONSTRAINT fk_sot_emp_auth FOREIGN KEY (enter_cd, auth_id)
    REFERENCES special_overtime_auth (enter_cd, auth_id);
