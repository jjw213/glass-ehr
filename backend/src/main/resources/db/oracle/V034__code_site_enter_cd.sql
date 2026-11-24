-- 여러 법인 3단계: 공통코드·사업장·사번 채번 (#121). 설계: docs/tenant-design.md 3.2·8.1
--
-- 이 마이그레이션이 하는 일
--   1. common_code_group PK 를 (enter_cd, group_code), common_code PK 를 (enter_cd, group_code, code) 로 바꾼다.
--      fk_common_code_group 을 (enter_cd, group_code) 복합 FK 로 바꿔 다른 법인 그룹에 코드를 달 수 없게 한다.
--      조회 인덱스 ix_common_code_group_sort 앞에 enter_cd 를 넣는다.
--   2. biz_site PK 를 (enter_cd, site_code), emp_no_seq PK 를 (enter_cd, site_code, hire_year) 로 바꾼다.
--      사번 순번은 법인·사업장·입사연도마다 따로 센다(같은 S01-2026-001 이 다른 법인에 있어도 된다).
--   3. biz_site 를 참조하는 FK 를 복합 FK 로 바꾼다:
--      fk_emp_no_seq_site, fk_employee_site, fk_wra_site(worker_rep_agreement.scope_site_code) → (enter_cd, site_code).
--   4. 위 네 테이블의 enter_cd DEFAULT 'C001' 을 지운다(8.1 2-3). 새 행이 법인을 빠뜨리면 오류가 난다.
--
-- employee 의 UNIQUE(uq_employee_emp_no) 는 사원 이슈에서 (enter_cd, emp_no) 로 바꾼다(이 이슈 범위 밖).
-- 그때까지는 사번이 전체에서 유일해야 하므로 실제로 다른 법인에 같은 사번을 넣을 수는 없다.
-- 법령 수치 계산 없음.
-- JPA 엔티티: com.glassehr.code.CodeGroup·CommonCode, com.glassehr.employee.BizSite·EmpNoSeq (ddl-auto: validate)

-- 1) 자식 FK 먼저
ALTER TABLE common_code DROP CONSTRAINT fk_common_code_group;
ALTER TABLE emp_no_seq DROP CONSTRAINT fk_emp_no_seq_site;
ALTER TABLE employee DROP CONSTRAINT fk_employee_site;
ALTER TABLE worker_rep_agreement DROP CONSTRAINT fk_wra_site;

-- 2) 공통코드
ALTER TABLE common_code_group MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE common_code_group DROP CONSTRAINT pk_common_code_group DROP INDEX;
ALTER TABLE common_code_group ADD CONSTRAINT pk_common_code_group PRIMARY KEY (enter_cd, group_code);

ALTER TABLE common_code MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE common_code DROP CONSTRAINT pk_common_code DROP INDEX;
ALTER TABLE common_code ADD CONSTRAINT pk_common_code PRIMARY KEY (enter_cd, group_code, code);
ALTER TABLE common_code ADD CONSTRAINT fk_common_code_group FOREIGN KEY (enter_cd, group_code)
    REFERENCES common_code_group (enter_cd, group_code);
DROP INDEX ix_common_code_group_sort;
CREATE INDEX ix_common_code_group_sort ON common_code (enter_cd, group_code, sort_order, code);

-- 3) 사업장·채번
ALTER TABLE biz_site MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE biz_site DROP CONSTRAINT pk_biz_site DROP INDEX;
ALTER TABLE biz_site ADD CONSTRAINT pk_biz_site PRIMARY KEY (enter_cd, site_code);

ALTER TABLE emp_no_seq MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE emp_no_seq DROP CONSTRAINT pk_emp_no_seq DROP INDEX;
ALTER TABLE emp_no_seq ADD CONSTRAINT pk_emp_no_seq PRIMARY KEY (enter_cd, site_code, hire_year);

-- 4) biz_site 를 참조하는 복합 FK
ALTER TABLE emp_no_seq ADD CONSTRAINT fk_emp_no_seq_site FOREIGN KEY (enter_cd, site_code)
    REFERENCES biz_site (enter_cd, site_code);
ALTER TABLE employee ADD CONSTRAINT fk_employee_site FOREIGN KEY (enter_cd, site_code)
    REFERENCES biz_site (enter_cd, site_code);
ALTER TABLE worker_rep_agreement ADD CONSTRAINT fk_wra_site FOREIGN KEY (enter_cd, scope_site_code)
    REFERENCES biz_site (enter_cd, site_code);
