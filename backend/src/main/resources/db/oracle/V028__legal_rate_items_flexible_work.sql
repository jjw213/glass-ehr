-- 유연근무제(탄력적·선택적 근로시간제) 법정 한도 legal_rate 항목 등록 (#38). 설계: docs/flexible-work-design.md.
--
-- 규칙
--   - 항목 코드만 등록하고 값(legal_rate 행)은 넣지 않는다(V010·V011·V022·V026 과 같은 원칙).
--     값은 seed/legal_rate_flexible_work.sql(#105, law.go.kr 원문 확인 2026-11-19)로 넣는다.
--   - 재량근로(제58조 제3항)는 수치 한도가 없어 항목이 없다. 대상 업무는 시행령 제31조 목록(코드)으로 둔다.

-- 근로기준법 제51조 제1항: 취업규칙 탄력적 근로시간제 단위기간 한도
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('FLEX_SHORT_UNIT_PERIOD_MAX_WEEKS', '탄력적 근로시간제(취업규칙) 단위기간 한도', 'WEEKS',
     '근로기준법 제51조 제1항');
-- 근로기준법 제51조 제1항 단서: 특정한 주의 근로시간 상한
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('FLEX_SHORT_WEEK_MAX_HOURS', '탄력적 근로시간제(취업규칙) 특정한 주 근로시간 상한', 'HOURS',
     '근로기준법 제51조 제1항 단서');
-- 근로기준법 제51조 제2항 제2호: 서면합의 탄력적 근로시간제 단위기간 한도
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('FLEX_WRITTEN_UNIT_PERIOD_MAX_MONTHS', '탄력적 근로시간제(서면합의) 단위기간 한도', 'MONTHS',
     '근로기준법 제51조 제2항 제2호');
-- 근로기준법 제51조 제2항 단서: 특정한 주·특정한 날 근로시간 상한
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('FLEX_WRITTEN_WEEK_MAX_HOURS', '탄력적 근로시간제(서면합의) 특정한 주 근로시간 상한', 'HOURS',
     '근로기준법 제51조 제2항 단서');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('FLEX_WRITTEN_DAY_MAX_HOURS', '탄력적 근로시간제(서면합의) 특정한 날 근로시간 상한', 'HOURS',
     '근로기준법 제51조 제2항 단서');
-- 근로기준법 제51조의2 제1항: 3개월 초과 탄력적 근로시간제 단위기간 한도, 특정한 주·날 상한
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('FLEX_LONG_UNIT_PERIOD_MAX_MONTHS', '3개월 초과 탄력적 근로시간제 단위기간 한도', 'MONTHS',
     '근로기준법 제51조의2 제1항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('FLEX_LONG_WEEK_MAX_HOURS', '3개월 초과 탄력적 근로시간제 특정한 주 근로시간 상한', 'HOURS',
     '근로기준법 제51조의2 제1항 단서');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('FLEX_LONG_DAY_MAX_HOURS', '3개월 초과 탄력적 근로시간제 특정한 날 근로시간 상한', 'HOURS',
     '근로기준법 제51조의2 제1항 단서');
-- 근로기준법 제51조의2 제2항 제1호: 근로일 종료 후 다음 근로일 개시 전 연속 휴식시간 하한
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('FLEX_LONG_DAILY_REST_MIN_HOURS', '3개월 초과 탄력적 근로시간제 근로일 간 연속 휴식시간 하한', 'HOURS',
     '근로기준법 제51조의2 제2항 제1호');
-- 근로기준법 제52조 제1항: 선택적 근로시간제 정산기간 한도(일반 업무 / 신상품·신기술 연구개발 업무)
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('SELECTIVE_SETTLEMENT_MAX_MONTHS', '선택적 근로시간제 정산기간 한도(일반 업무)', 'MONTHS',
     '근로기준법 제52조 제1항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('SELECTIVE_SETTLEMENT_RND_MAX_MONTHS', '선택적 근로시간제 정산기간 한도(신상품·신기술 연구개발 업무)', 'MONTHS',
     '근로기준법 제52조 제1항');
-- 근로기준법 제52조 제2항 제1호: 1개월 초과 정산기간의 근로일 간 연속 휴식시간 하한
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('SELECTIVE_RND_DAILY_REST_MIN_HOURS', '선택적 근로시간제(1개월 초과 정산기간) 근로일 간 연속 휴식시간 하한', 'HOURS',
     '근로기준법 제52조 제2항 제1호');
