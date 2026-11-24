-- 여러 법인 4단계: 조직(부서·조직 이력) (#122). 설계: docs/tenant-design.md 3.2·8.1
--
-- 이 마이그레이션이 하는 일
--   1. org_dept PK 를 (enter_cd, dept_id), 부서코드 UNIQUE 를 (enter_cd, dept_code) 로 바꾼다.
--      같은 부서코드가 법인마다 따로 있을 수 있다. dept_id(IDENTITY)는 그대로 전체에서 유일하다.
--   2. org_dept_hist PK 를 (enter_cd, hist_id), 시작일 UNIQUE 를 (enter_cd, dept_id, valid_from) 로 바꾼다.
--   3. org_dept 를 참조하는 FK 를 (enter_cd, 부서) 복합 FK 로 바꿔 다른 법인 부서를 가리킬 수 없게 한다:
--      fk_org_dept_hist_dept·_parent(조직 이력), fk_employee_dept(사원 현재 부서),
--      fk_hr_appt_before_dept·_after_dept(인사발령 이전·이후 부서).
--   4. 부서장 FK fk_org_dept_hist_head 를 (enter_cd, head_emp_id) → employee (enter_cd, emp_id)(V032 uq_employee_enter_emp)로 바꾼다.
--      다른 법인 사원을 부서장으로 둘 수 없다.
--   5. 조직 이력 조회 인덱스 앞에 enter_cd 를 넣는다.
--   6. org_dept·org_dept_hist 의 enter_cd DEFAULT 'C001' 을 지운다(8.1 2-3). 새 행이 법인을 빠뜨리면 오류가 난다.
--
-- employee·hr_appointment 의 PK 와 enter_cd DEFAULT 는 사원·인사발령 이슈에서 바꾼다(이 이슈 범위 밖).
-- 법령 수치 계산 없음.
-- JPA 엔티티: com.glassehr.org.OrgDept·OrgDeptHist (ddl-auto: validate)

-- 1) org_dept 를 참조하는 FK 먼저
ALTER TABLE org_dept_hist DROP CONSTRAINT fk_org_dept_hist_dept;
ALTER TABLE org_dept_hist DROP CONSTRAINT fk_org_dept_hist_parent;
ALTER TABLE org_dept_hist DROP CONSTRAINT fk_org_dept_hist_head;
ALTER TABLE employee DROP CONSTRAINT fk_employee_dept;
ALTER TABLE hr_appointment DROP CONSTRAINT fk_hr_appt_before_dept;
ALTER TABLE hr_appointment DROP CONSTRAINT fk_hr_appt_after_dept;

-- 2) 부서
ALTER TABLE org_dept MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE org_dept DROP CONSTRAINT uq_org_dept_code DROP INDEX;
ALTER TABLE org_dept DROP CONSTRAINT pk_org_dept DROP INDEX;
ALTER TABLE org_dept ADD CONSTRAINT pk_org_dept PRIMARY KEY (enter_cd, dept_id);
ALTER TABLE org_dept ADD CONSTRAINT uq_org_dept_code UNIQUE (enter_cd, dept_code);

-- 3) 조직 이력
ALTER TABLE org_dept_hist MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE org_dept_hist DROP CONSTRAINT uq_org_dept_hist_start DROP INDEX;
ALTER TABLE org_dept_hist DROP CONSTRAINT pk_org_dept_hist DROP INDEX;
ALTER TABLE org_dept_hist ADD CONSTRAINT pk_org_dept_hist PRIMARY KEY (enter_cd, hist_id);
ALTER TABLE org_dept_hist ADD CONSTRAINT uq_org_dept_hist_start UNIQUE (enter_cd, dept_id, valid_from);
DROP INDEX ix_org_dept_hist_period;
CREATE INDEX ix_org_dept_hist_period ON org_dept_hist (enter_cd, valid_from, valid_to);
DROP INDEX ix_org_dept_hist_parent;
CREATE INDEX ix_org_dept_hist_parent ON org_dept_hist (enter_cd, parent_dept_id);

-- 4) 복합 FK
ALTER TABLE org_dept_hist ADD CONSTRAINT fk_org_dept_hist_dept FOREIGN KEY (enter_cd, dept_id)
    REFERENCES org_dept (enter_cd, dept_id);
ALTER TABLE org_dept_hist ADD CONSTRAINT fk_org_dept_hist_parent FOREIGN KEY (enter_cd, parent_dept_id)
    REFERENCES org_dept (enter_cd, dept_id);
ALTER TABLE org_dept_hist ADD CONSTRAINT fk_org_dept_hist_head FOREIGN KEY (enter_cd, head_emp_id)
    REFERENCES employee (enter_cd, emp_id);
ALTER TABLE employee ADD CONSTRAINT fk_employee_dept FOREIGN KEY (enter_cd, current_dept_id)
    REFERENCES org_dept (enter_cd, dept_id);
ALTER TABLE hr_appointment ADD CONSTRAINT fk_hr_appt_before_dept FOREIGN KEY (enter_cd, before_dept_id)
    REFERENCES org_dept (enter_cd, dept_id);
ALTER TABLE hr_appointment ADD CONSTRAINT fk_hr_appt_after_dept FOREIGN KEY (enter_cd, after_dept_id)
    REFERENCES org_dept (enter_cd, dept_id);
