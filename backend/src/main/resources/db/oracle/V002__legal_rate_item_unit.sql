-- legal_rate_item 항목 사전 확장 (#7). 설계: docs/legal-rate-design.md
-- 항목마다 단위와 근거 조문을 고정하고, legal_rate 값 행의 단위가 항목 단위와 다르면 DB가 거부한다.
-- 이 스크립트는 legal_rate_item 이 비어 있을 때 적용한다(V001 직후, 시드 입력 전).

-- 단위 코드: LegalRateUnit 과 같은 목록을 유지한다.
--   PERCENT     요율. 고시·조문 표기 그대로의 퍼센트 숫자(2026-11-11 결정)
--   HOURS       시간 수(조문 표기 그대로). 근태 계산 시 분으로 변환
--   DAYS        일수
--   MONTHS      개월 수
--   YEARS       연 수
--   HOUR_OF_DAY 하루 중 시각(0~24시, 조문 표기 그대로)
ALTER TABLE legal_rate_item ADD (
    unit        VARCHAR2(32 CHAR)   NOT NULL,
    legal_basis VARCHAR2(1000 CHAR) NOT NULL,
    CONSTRAINT ck_legal_rate_item_unit
        CHECK (unit IN ('PERCENT', 'HOURS', 'DAYS', 'MONTHS', 'YEARS', 'HOUR_OF_DAY')),
    CONSTRAINT ck_legal_rate_item_basis
        CHECK (LENGTH(TRIM(legal_basis)) > 0),
    CONSTRAINT uq_legal_rate_item_unit UNIQUE (item_code, unit)
);

-- 값 행의 단위는 항목 단위와 같아야 한다.
ALTER TABLE legal_rate ADD CONSTRAINT fk_legal_rate_item_unit
    FOREIGN KEY (item_code, unit) REFERENCES legal_rate_item (item_code, unit);
