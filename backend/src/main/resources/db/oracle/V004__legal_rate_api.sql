-- legal_rate 겹침 방지 쓰기 패키지 (#7). 설계: docs/legal-rate-design.md 3절
-- Oracle은 기간 겹침 제약(exclusion constraint)이 없으므로 쓰기를 이 패키지로만 허용한다.
--   1. legal_rate_item 행을 SELECT ... FOR UPDATE 로 잠가 같은 항목의 쓰기를 직렬화한다.
--   2. 같은 항목의 기존 기간과 반개구간 [from, to) 겹침을 검사한다(맞닿는 기간은 허용).
--   3. 겹침이 없을 때만 INSERT/UPDATE 한다.
-- COMMIT/ROLLBACK 은 호출자가 한다(여러 호출을 한 트랜잭션으로 묶을 수 있게). 잠금은 커밋까지 유지된다.
-- 여러 항목을 한 트랜잭션에서 쓸 때는 item_code 오름차순으로 호출해 데드락을 줄인다.
-- AUTHID DEFINER: 앱 계정은 legal_rate DML 권한 없이 EXECUTE 권한만으로 쓴다(grants/legal_rate_grants.sql).
--
-- 오류 코드
--   -20101 미등록 item_code
--   -20102 단위가 항목 단위와 다름
--   -20103 기존 기간과 겹침
--   -20104 대상 기간 행 없음(set_end)
--   -20105 입력값 오류(NULL, 시각 성분, 시작일 >= 종료일)
--
-- 이 파일은 PL/SQL 이라 문장 구분자로 '/' 단독 줄을 쓴다(SQL*Plus/SQLcl 실행 기준).

CREATE OR REPLACE PACKAGE legal_rate_api AUTHID DEFINER AS

    -- 새 기간 [p_from, p_to) 의 값을 추가한다. p_to NULL = 종료 미정.
    PROCEDURE insert_period(
        p_item_code   IN VARCHAR2,
        p_from        IN DATE,
        p_to          IN DATE,
        p_value       IN NUMBER,
        p_unit        IN VARCHAR2,
        p_legal_basis IN VARCHAR2,
        p_source_url  IN VARCHAR2,
        p_created_by  IN VARCHAR2);

    -- 시작일이 p_from 인 기존 기간의 종료일을 p_to 로 바꾼다(NULL = 종료 미정).
    -- 새 값이 고시되면 기존 열린 기간을 set_end 로 닫고 insert_period 로 다음 기간을 넣는다.
    PROCEDURE set_end(
        p_item_code IN VARCHAR2,
        p_from      IN DATE,
        p_to        IN DATE);

END legal_rate_api;
/

CREATE OR REPLACE PACKAGE BODY legal_rate_api AS

    c_open_end CONSTANT DATE := DATE '9999-12-31';

    -- 항목 행을 잠그고 단위를 돌려준다.
    FUNCTION lock_item(p_item_code IN VARCHAR2) RETURN VARCHAR2 IS
        v_unit legal_rate_item.unit%TYPE;
    BEGIN
        SELECT unit INTO v_unit
          FROM legal_rate_item
         WHERE item_code = p_item_code
           FOR UPDATE;
        RETURN v_unit;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20101, 'legal_rate: unregistered item_code=' || p_item_code);
    END lock_item;

    PROCEDURE check_period(p_from IN DATE, p_to IN DATE) IS
    BEGIN
        IF p_from IS NULL THEN
            RAISE_APPLICATION_ERROR(-20105, 'legal_rate: effective_from is null');
        END IF;
        IF p_from <> TRUNC(p_from) OR (p_to IS NOT NULL AND p_to <> TRUNC(p_to)) THEN
            RAISE_APPLICATION_ERROR(-20105, 'legal_rate: date has time component');
        END IF;
        IF p_to IS NOT NULL AND p_from >= p_to THEN
            RAISE_APPLICATION_ERROR(-20105, 'legal_rate: effective_from must be before effective_to');
        END IF;
    END check_period;

    -- 반개구간 겹침: existing.from < new.to AND new.from < existing.to (NULL 종료 = 무한)
    PROCEDURE check_overlap(
        p_item_code    IN VARCHAR2,
        p_from         IN DATE,
        p_to           IN DATE,
        p_exclude_from IN DATE) IS
        v_cnt NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_cnt
          FROM legal_rate
         WHERE item_code = p_item_code
           AND (p_exclude_from IS NULL OR effective_from <> p_exclude_from)
           AND effective_from < NVL(p_to, c_open_end)
           AND (effective_to IS NULL OR p_from < effective_to);
        IF v_cnt > 0 THEN
            RAISE_APPLICATION_ERROR(-20103,
                'legal_rate: period overlaps existing rows, item_code=' || p_item_code
                || ', from=' || TO_CHAR(p_from, 'YYYY-MM-DD'));
        END IF;
    END check_overlap;

    PROCEDURE insert_period(
        p_item_code   IN VARCHAR2,
        p_from        IN DATE,
        p_to          IN DATE,
        p_value       IN NUMBER,
        p_unit        IN VARCHAR2,
        p_legal_basis IN VARCHAR2,
        p_source_url  IN VARCHAR2,
        p_created_by  IN VARCHAR2) IS
        v_unit legal_rate_item.unit%TYPE;
    BEGIN
        check_period(p_from, p_to);
        v_unit := lock_item(p_item_code);
        IF p_unit IS NULL OR p_unit <> v_unit THEN
            RAISE_APPLICATION_ERROR(-20102,
                'legal_rate: unit mismatch, item_code=' || p_item_code || ', expected=' || v_unit);
        END IF;
        check_overlap(p_item_code, p_from, p_to, NULL);

        INSERT INTO legal_rate
            (item_code, effective_from, effective_to, rate_value, unit, legal_basis, source_url, created_by)
        VALUES
            (p_item_code, p_from, p_to, p_value, p_unit, p_legal_basis, p_source_url, p_created_by);
    END insert_period;

    PROCEDURE set_end(
        p_item_code IN VARCHAR2,
        p_from      IN DATE,
        p_to        IN DATE) IS
        v_unit legal_rate_item.unit%TYPE;
    BEGIN
        check_period(p_from, p_to);
        v_unit := lock_item(p_item_code);
        check_overlap(p_item_code, p_from, p_to, p_from);

        UPDATE legal_rate
           SET effective_to = p_to
         WHERE item_code = p_item_code
           AND effective_from = p_from;
        IF SQL%ROWCOUNT = 0 THEN
            RAISE_APPLICATION_ERROR(-20104,
                'legal_rate: no period, item_code=' || p_item_code
                || ', from=' || TO_CHAR(p_from, 'YYYY-MM-DD'));
        END IF;
    END set_end;

END legal_rate_api;
/
