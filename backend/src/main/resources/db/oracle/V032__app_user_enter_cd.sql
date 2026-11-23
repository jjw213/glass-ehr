-- 여러 법인 2단계: 로그인 계정·역할 부여·접속기록·개인정보 조회 로그 (#119). 설계: docs/tenant-design.md 3.2·4장·8.1
--
-- 이 마이그레이션이 하는 일
--   1. app_user PK 를 (enter_cd, login_id) 로 바꾼다. 같은 아이디를 법인마다 따로 쓸 수 있다.
--      ux_app_user_emp·fk_app_user_emp 도 (enter_cd, emp_id) 로 바꿔 다른 법인 사원에 계정을 연결할 수 없게 한다.
--      사원 미연결 계정(emp_id IS NULL)은 여러 개를 허용하도록 함수 기반 UNIQUE 인덱스에서 제외한다.
--   2. app_user_role PK 를 (enter_cd, login_id, role_code), fk_app_user_role_user 를 (enter_cd, login_id) 로 바꾼다.
--      fk_app_user_role_role(→ 공통 app_role)은 그대로 둔다.
--   3. privacy_access_log: enter_cd = 대상 법인(현재 법인), accessor_enter_cd(신규) = 주체 법인(4.4).
--      PK 를 (enter_cd, log_id) 로, 조회 인덱스 앞에 enter_cd 를 넣는다. 기존 행은 둘 다 'C001'.
--   4. login_access_log: enter_cd 는 NULL 허용·FK 없음 그대로(3.2 ※※). DEFAULT 'C001' 을 지워 코드가 시도 법인을 넣게 한다.
--   5. 위 테이블의 enter_cd DEFAULT 'C001' 을 지운다(8.1 2-3). 새 행이 법인을 빠뜨리면 오류가 난다.
--
-- employee 는 아직 PK 가 emp_id 단일이다(후속 이슈). 복합 FK fk_app_user_emp 가 참조할 키로
--   uq_employee_enter_emp (enter_cd, emp_id) 를 둔다. emp_id 가 이미 유일하므로 기존 데이터는 항상 통과한다.
--   employee PK 를 (enter_cd, emp_id) 로 바꾸는 이슈에서 이 UNIQUE 를 지우고 FK 를 PK 로 다시 건다
--   (Oracle 은 같은 열 조합에 PK 와 UNIQUE 를 함께 둘 수 없다).
-- rrn_access_log 의 주체·대상 법인 열은 이 이슈 범위 밖(주민등록번호 이슈와 함께).
-- 법령 수치 계산 없음.
-- JPA 엔티티: com.glassehr.auth.AppUser, PrivacyAccessLog, LoginAccessLog (ddl-auto: validate)

-- 1) 자식 FK 먼저
ALTER TABLE app_user_role DROP CONSTRAINT fk_app_user_role_user;
ALTER TABLE app_user DROP CONSTRAINT fk_app_user_emp;

-- 2) app_user
ALTER TABLE app_user MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE app_user DROP CONSTRAINT uq_app_user_emp DROP INDEX;
ALTER TABLE app_user DROP CONSTRAINT pk_app_user DROP INDEX;
ALTER TABLE app_user ADD CONSTRAINT pk_app_user PRIMARY KEY (enter_cd, login_id);
CREATE UNIQUE INDEX ux_app_user_emp ON app_user (
    CASE WHEN emp_id IS NOT NULL THEN enter_cd END,
    CASE WHEN emp_id IS NOT NULL THEN emp_id END
);

ALTER TABLE employee ADD CONSTRAINT uq_employee_enter_emp UNIQUE (enter_cd, emp_id);
ALTER TABLE app_user ADD CONSTRAINT fk_app_user_emp FOREIGN KEY (enter_cd, emp_id)
    REFERENCES employee (enter_cd, emp_id);

-- 3) app_user_role
ALTER TABLE app_user_role MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE app_user_role DROP CONSTRAINT pk_app_user_role DROP INDEX;
ALTER TABLE app_user_role ADD CONSTRAINT pk_app_user_role PRIMARY KEY (enter_cd, login_id, role_code);
ALTER TABLE app_user_role ADD CONSTRAINT fk_app_user_role_user FOREIGN KEY (enter_cd, login_id)
    REFERENCES app_user (enter_cd, login_id);

-- 4) privacy_access_log: 대상 법인(enter_cd)·주체 법인(accessor_enter_cd)
ALTER TABLE privacy_access_log ADD (accessor_enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL
    CONSTRAINT fk_privacy_log_accessor_enter REFERENCES enter_company (enter_cd)
    CONSTRAINT ck_privacy_log_accessor_enter CHECK (REGEXP_LIKE(accessor_enter_cd, '^[A-Z0-9]+$', 'c')
        AND LENGTH(accessor_enter_cd) >= 2));
ALTER TABLE privacy_access_log MODIFY (enter_cd DEFAULT NULL, accessor_enter_cd DEFAULT NULL);
ALTER TABLE privacy_access_log DROP CONSTRAINT pk_privacy_access_log DROP INDEX;
ALTER TABLE privacy_access_log ADD CONSTRAINT pk_privacy_access_log PRIMARY KEY (enter_cd, log_id);
DROP INDEX ix_privacy_log_at;
DROP INDEX ix_privacy_log_target;
DROP INDEX ix_privacy_log_accessor;
CREATE INDEX ix_privacy_log_at ON privacy_access_log (enter_cd, accessed_at);
CREATE INDEX ix_privacy_log_target ON privacy_access_log (enter_cd, target_emp_id, accessed_at);
CREATE INDEX ix_privacy_log_accessor ON privacy_access_log (enter_cd, accessor_id, accessed_at);

-- 5) login_access_log: 시도 법인(NULL 허용, FK 없음)
ALTER TABLE login_access_log MODIFY (enter_cd DEFAULT NULL);
DROP INDEX ix_login_log_user;
CREATE INDEX ix_login_log_user ON login_access_log (enter_cd, login_id, occurred_at);
