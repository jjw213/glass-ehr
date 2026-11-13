-- 근무유형 저장 검사용 legal_rate 항목코드 등록 (#25). V003·V005·V010 과 같은 원칙: 항목만 등록하고 값(legal_rate 행)은 넣지 않는다.
-- 값은 사람이 law.go.kr 원문을 확인해 legal_rate_api 로 입력한다. 값이 없으면 근무유형 저장·배정이 거부된다(기본값 없음).
-- 코드 목록은 com.glassehr.legalrate.LegalRateItem 과 같아야 한다(LegalRateItemRegistrationTest).
-- 휴게 최소 시간을 분으로 두기 위해 단위 MINUTES 를 추가한다. 기존 단위는 모두 유지한다.

ALTER TABLE legal_rate_item DROP CONSTRAINT ck_legal_rate_item_unit;
ALTER TABLE legal_rate_item ADD CONSTRAINT ck_legal_rate_item_unit
    CHECK (unit IN ('PERCENT', 'HOURS', 'DAYS', 'MONTHS', 'YEARS', 'HOUR_OF_DAY', 'WEEKS', 'COUNT', 'MINUTES'));

-- 근로기준법 제50조: 근로시간
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('WEEKLY_STANDARD_WORK_HOURS', '1주 근로시간 한도(휴게시간 제외)', 'HOURS', '근로기준법 제50조 제1항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('DAILY_STANDARD_WORK_HOURS', '1일 근로시간 한도(휴게시간 제외)', 'HOURS', '근로기준법 제50조 제2항');

-- 근로기준법 제54조 제1항: 휴게 — 근로시간 구간별 최소 휴게시간(짧은 구간·긴 구간)
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('BREAK_SHORT_THRESHOLD_HOURS', '휴게 부여 기준 근로시간(짧은 구간)', 'HOURS', '근로기준법 제54조 제1항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('BREAK_SHORT_MIN_MINUTES', '짧은 구간 최소 휴게시간', 'MINUTES', '근로기준법 제54조 제1항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('BREAK_LONG_THRESHOLD_HOURS', '휴게 부여 기준 근로시간(긴 구간)', 'HOURS', '근로기준법 제54조 제1항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('BREAK_LONG_MIN_MINUTES', '긴 구간 최소 휴게시간', 'MINUTES', '근로기준법 제54조 제1항');

-- 근로기준법 제55조 제1항: 1주 유급휴일(주휴일) 최소 횟수
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('WEEKLY_PAID_HOLIDAY_MIN_COUNT', '1주 유급휴일(주휴일) 최소 횟수', 'COUNT', '근로기준법 제55조 제1항');
