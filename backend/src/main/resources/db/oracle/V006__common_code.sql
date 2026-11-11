-- 공통코드 (#11). 직급·직책·직군·고용형태·근무지·퇴직사유 같은 코드를 한 곳에서 관리한다.
-- 코드그룹(common_code_group) 1 : N 코드(common_code).
--
-- 규칙
--   - 코드값은 영문 대문자·숫자·밑줄(첫 글자는 영문), 30자 이내. 화면에는 code_name 을 보인다.
--   - 사용 여부(use_yn)는 'Y'/'N'. 사원·발령 이력이 코드를 참조하므로 행을 지우지 않고 'N'으로 바꾼다.
--   - 정렬 순서(sort_order)는 0 이상 정수. 같은 값이면 코드값 순으로 보인다.
--   - 코드그룹은 이 마이그레이션·시드로만 추가한다(로직이 그룹코드를 참조하므로). 화면에서는 코드만 추가·수정한다.
-- JPA 엔티티: com.glassehr.code.CodeGroup, CommonCode (ddl-auto: validate)

CREATE TABLE common_code_group (
    group_code   VARCHAR2(30 CHAR)  NOT NULL,
    group_name   VARCHAR2(100 CHAR) NOT NULL,
    description  VARCHAR2(500 CHAR),
    sort_order   NUMBER(5)          DEFAULT 0   NOT NULL,
    use_yn       VARCHAR2(1 CHAR)   DEFAULT 'Y' NOT NULL,
    created_at   TIMESTAMP(6)       DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at   TIMESTAMP(6)       DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT pk_common_code_group PRIMARY KEY (group_code),
    CONSTRAINT ck_common_code_group_code
        CHECK (REGEXP_LIKE(group_code, '^[A-Z][A-Z0-9_]{0,29}$', 'c')),
    CONSTRAINT ck_common_code_group_name
        CHECK (LENGTH(TRIM(group_name)) > 0),
    CONSTRAINT ck_common_code_group_sort
        CHECK (sort_order >= 0),
    CONSTRAINT ck_common_code_group_use
        CHECK (use_yn IN ('Y', 'N'))
);

CREATE TABLE common_code (
    group_code   VARCHAR2(30 CHAR)  NOT NULL,
    code         VARCHAR2(30 CHAR)  NOT NULL,
    code_name    VARCHAR2(100 CHAR) NOT NULL,
    description  VARCHAR2(500 CHAR),
    sort_order   NUMBER(5)          DEFAULT 0   NOT NULL,
    use_yn       VARCHAR2(1 CHAR)   DEFAULT 'Y' NOT NULL,
    created_at   TIMESTAMP(6)       DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at   TIMESTAMP(6)       DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT pk_common_code PRIMARY KEY (group_code, code),
    CONSTRAINT fk_common_code_group
        FOREIGN KEY (group_code) REFERENCES common_code_group (group_code),
    CONSTRAINT ck_common_code_code
        CHECK (REGEXP_LIKE(code, '^[A-Z][A-Z0-9_]{0,29}$', 'c')),
    CONSTRAINT ck_common_code_name
        CHECK (LENGTH(TRIM(code_name)) > 0),
    CONSTRAINT ck_common_code_sort
        CHECK (sort_order >= 0),
    CONSTRAINT ck_common_code_use
        CHECK (use_yn IN ('Y', 'N'))
);

CREATE INDEX ix_common_code_group_sort ON common_code (group_code, sort_order, code);
