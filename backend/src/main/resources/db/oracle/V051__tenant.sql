-- 고객사(tenant) 마스터와 법인 소속 (#151). 설계: docs/saas-design.md 2장
--
-- 이 마이그레이션이 하는 일
--   1. 고객사 마스터 tenant(전체 공통, enter_cd 없음)를 만들고 기존 고객사 T001 을 넣는다.
--      T001 이름은 기존 대표 법인(C001) 이름, 상태 ACTIVE(saas-design.md 2.5).
--   2. enter_company.tenant_id 를 NOT NULL + FK(→ tenant)로 더한다. 기존 법인은 모두 T001.
--      DEFAULT 는 두지 않는다(새 법인이 모르는 사이 T001 에 들어가 다른 고객사와 섞이지 않게).
--   3. 로그인 실패 사유에 TENANT_UNAVAILABLE(고객사 정지·해지·체험 종료)을 더한다.
--
-- 상태(saas-design.md 2.2): TRIAL(체험, trial_ends_at 까지 로그인), ACTIVE, SUSPENDED(정지), TERMINATED(해지).
--   TRIAL 은 trial_ends_at 이 있어야 한다. 종료일이 지난 TRIAL 은 애플리케이션이 SUSPENDED 처럼 거부한다.
-- 형식: 정규식 반복 횟수 중괄호는 Oracle JDBC escape 와 겹치므로(V006·V031 주석) 길이는 LENGTH·열 길이로 건다.
--   tenant_id: 영문 대문자·숫자 2~10자. slug(서브도메인): 영문 소문자·숫자·하이픈 3~30자, 전체 유일.
-- 법령 수치 계산 없음.
-- JPA 엔티티: com.glassehr.tenant.Tenant, com.glassehr.tenant.EnterCompany.tenantId (ddl-auto: validate)

CREATE TABLE tenant (
    tenant_id     VARCHAR2(10 CHAR)   NOT NULL,
    tenant_name   VARCHAR2(100 CHAR)  NOT NULL,
    slug          VARCHAR2(30 CHAR)   NOT NULL,
    status        VARCHAR2(20 CHAR)   DEFAULT 'ACTIVE' NOT NULL,
    trial_ends_at TIMESTAMP(6),
    created_at    TIMESTAMP(6)        DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at    TIMESTAMP(6)        DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT pk_tenant PRIMARY KEY (tenant_id),
    CONSTRAINT uq_tenant_slug UNIQUE (slug),
    CONSTRAINT ck_tenant_id CHECK (REGEXP_LIKE(tenant_id, '^[A-Z0-9]+$', 'c') AND LENGTH(tenant_id) >= 2),
    CONSTRAINT ck_tenant_name CHECK (LENGTH(TRIM(tenant_name)) > 0),
    CONSTRAINT ck_tenant_slug CHECK (REGEXP_LIKE(slug, '^[a-z0-9-]+$', 'c') AND LENGTH(slug) >= 3),
    CONSTRAINT ck_tenant_status CHECK (status IN ('TRIAL', 'ACTIVE', 'SUSPENDED', 'TERMINATED')),
    CONSTRAINT ck_tenant_trial CHECK (status <> 'TRIAL' OR trial_ends_at IS NOT NULL)
);

-- 기존 데이터의 고객사. 이름은 기존 대표 법인(C001) 이름.
INSERT INTO tenant (tenant_id, tenant_name, slug, status)
SELECT 'T001', enter_name, 't001', 'ACTIVE' FROM enter_company WHERE enter_cd = 'C001';

ALTER TABLE enter_company ADD (tenant_id VARCHAR2(10 CHAR));
UPDATE enter_company SET tenant_id = 'T001';
ALTER TABLE enter_company MODIFY (tenant_id NOT NULL);
ALTER TABLE enter_company ADD CONSTRAINT fk_enter_company_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id);
CREATE INDEX ix_enter_company_tenant ON enter_company (tenant_id);

ALTER TABLE login_access_log DROP CONSTRAINT ck_login_log_reason;
ALTER TABLE login_access_log ADD CONSTRAINT ck_login_log_reason CHECK (
    (event_code = 'LOGIN_FAILURE'
        AND fail_reason IN ('UNKNOWN_USER', 'BAD_PASSWORD', 'LOCKED', 'INACTIVE', 'NO_PASSWORD', 'TENANT_UNAVAILABLE'))
    OR (event_code <> 'LOGIN_FAILURE' AND fail_reason IS NULL));
