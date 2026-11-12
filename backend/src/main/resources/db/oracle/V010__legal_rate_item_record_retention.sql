-- 근로자 명부 보존 기간 항목코드 등록 (#14). V003·V005 와 같은 원칙: 항목만 등록하고 값(legal_rate 행)은 넣지 않는다.
-- 값은 사람이 law.go.kr 원문을 확인해 legal_rate_api 로 입력한다. 값이 없으면 퇴직 처리(보존기한 계산)가 거부된다.
-- 코드 목록은 com.glassehr.legalrate.LegalRateItem 과 같아야 한다(LegalRateItemRegistrationTest).

INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('EMPLOYEE_RECORD_RETENTION_YEARS', '근로자 명부 보존 기간(퇴직일부터)', 'YEARS', '근로기준법 제42조');
