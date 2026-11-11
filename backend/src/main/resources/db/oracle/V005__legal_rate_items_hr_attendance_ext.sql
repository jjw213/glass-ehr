-- 인사·근태 기준값 항목코드 추가 등록 (#7, 2026-11-11 사람 답변 "①~⑤ 모두 추가").
-- V003 과 같은 원칙: 항목 이름·단위·근거 조문만 등록하고 값(legal_rate 행)은 넣지 않는다.
-- 코드 목록은 com.glassehr.legalrate.LegalRateItem 과 같아야 한다(LegalRateItemRegistrationTest 가 V003+V005 를 합쳐 검사).
-- 근거 조문은 항목을 가리키는 위치이며, 값 입력 전에 law.go.kr 현행 조문·시행령으로 다시 확인한다.
-- 개수·주수 기준을 조문 표기 그대로 저장하기 위해 단위 WEEKS·COUNT 를 추가한다.

-- 단위 코드 확장: LegalRateUnit 과 같은 목록을 유지한다(V002 목록 + 아래 두 개).
--   WEEKS  주 수(임신 주수 등, 조문 표기 그대로)
--   COUNT  횟수(조문 표기 그대로. 예: "n회에 한정하여 나누어 사용"의 n)
ALTER TABLE legal_rate_item DROP CONSTRAINT ck_legal_rate_item_unit;
ALTER TABLE legal_rate_item ADD CONSTRAINT ck_legal_rate_item_unit
    CHECK (unit IN ('PERCENT', 'HOURS', 'DAYS', 'MONTHS', 'YEARS', 'HOUR_OF_DAY', 'WEEKS', 'COUNT'));

-- ① 근로기준법 제74조 제1항: 미숙아 출산 시 출산전후휴가 일수
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('MATERNITY_LEAVE_PREMATURE_DAYS', '출산전후휴가 일수(미숙아 출산)', 'DAYS', '근로기준법 제74조 제1항');

-- ② 근로기준법 제74조 제3항, 같은 법 시행령 제43조: 유산·사산휴가
-- 임신기간 구간별 휴가 일수. 구간 n 은 "임신기간 TIERn_FROM_WEEKS 주 이상"부터 다음 구간 시작 전까지이며,
-- 구간 경계 주수도 값으로 저장한다(하드코딩 금지). 구간 수(5)는 등록 시점 시행령 구조 기준이며,
-- 값 입력 시 시행령 원문으로 구간 수가 다르면 항목을 고친다.
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('MISCARRIAGE_LEAVE_TIER1_FROM_WEEKS', '유산·사산휴가 구간1 시작 임신 주수', 'WEEKS', '근로기준법 제74조 제3항, 같은 법 시행령 제43조');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('MISCARRIAGE_LEAVE_TIER1_DAYS', '유산·사산휴가 구간1 휴가 일수', 'DAYS', '근로기준법 제74조 제3항, 같은 법 시행령 제43조');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('MISCARRIAGE_LEAVE_TIER2_FROM_WEEKS', '유산·사산휴가 구간2 시작 임신 주수', 'WEEKS', '근로기준법 제74조 제3항, 같은 법 시행령 제43조');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('MISCARRIAGE_LEAVE_TIER2_DAYS', '유산·사산휴가 구간2 휴가 일수', 'DAYS', '근로기준법 제74조 제3항, 같은 법 시행령 제43조');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('MISCARRIAGE_LEAVE_TIER3_FROM_WEEKS', '유산·사산휴가 구간3 시작 임신 주수', 'WEEKS', '근로기준법 제74조 제3항, 같은 법 시행령 제43조');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('MISCARRIAGE_LEAVE_TIER3_DAYS', '유산·사산휴가 구간3 휴가 일수', 'DAYS', '근로기준법 제74조 제3항, 같은 법 시행령 제43조');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('MISCARRIAGE_LEAVE_TIER4_FROM_WEEKS', '유산·사산휴가 구간4 시작 임신 주수', 'WEEKS', '근로기준법 제74조 제3항, 같은 법 시행령 제43조');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('MISCARRIAGE_LEAVE_TIER4_DAYS', '유산·사산휴가 구간4 휴가 일수', 'DAYS', '근로기준법 제74조 제3항, 같은 법 시행령 제43조');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('MISCARRIAGE_LEAVE_TIER5_FROM_WEEKS', '유산·사산휴가 구간5 시작 임신 주수', 'WEEKS', '근로기준법 제74조 제3항, 같은 법 시행령 제43조');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('MISCARRIAGE_LEAVE_TIER5_DAYS', '유산·사산휴가 구간5 휴가 일수', 'DAYS', '근로기준법 제74조 제3항, 같은 법 시행령 제43조');

-- ③ 근로기준법 제74조 제7항: 임신기 근로시간 단축
-- 대상 기간(임신 초기 "n주 이내", 후기 "n주 이후")과 단축 시간, 1일 근로시간이 짧은 근로자의 기준·단축 후 시간.
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('PREGNANCY_REDUCED_HOURS_EARLY_WEEKS', '임신기 근로시간 단축 대상: 임신 후 이내 주수', 'WEEKS', '근로기준법 제74조 제7항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('PREGNANCY_REDUCED_HOURS_LATE_FROM_WEEKS', '임신기 근로시간 단축 대상: 이후 주수', 'WEEKS', '근로기준법 제74조 제7항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('PREGNANCY_REDUCED_HOURS_PER_DAY', '임신기 근로시간 단축 1일 단축 시간', 'HOURS', '근로기준법 제74조 제7항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('PREGNANCY_REDUCED_SHORT_DAY_THRESHOLD_HOURS', '임신기 근로시간 단축: 1일 근로시간 미만 기준(이 시간 미만 근로자)', 'HOURS', '근로기준법 제74조 제7항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('PREGNANCY_REDUCED_SHORT_DAY_TARGET_HOURS', '임신기 근로시간 단축: 기준 미만 근로자의 단축 후 1일 근로시간', 'HOURS', '근로기준법 제74조 제7항');

-- ④ 남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의2: 배우자 출산휴가 분할 사용
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('SPOUSE_MATERNITY_LEAVE_SPLIT_COUNT', '배우자 출산휴가 나누어 사용할 수 있는 횟수(조문 표기 그대로)', 'COUNT', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의2');

-- ⑤ 같은 법 제19조, 제19조의4 제1항: 육아휴직 분할 사용
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('PARENTAL_LEAVE_SPLIT_COUNT', '육아휴직 나누어 사용할 수 있는 횟수(조문 표기 그대로)', 'COUNT', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조, 제19조의4 제1항');
