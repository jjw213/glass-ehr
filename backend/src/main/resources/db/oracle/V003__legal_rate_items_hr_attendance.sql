-- 인사·근태 모듈 기준값 항목코드 등록 (#7).
-- 항목 이름·단위·근거 조문만 등록한다. 값(legal_rate 행)은 넣지 않는다 — 사람이 원문을 확인해
-- seed/legal_rate_seed.TODO.sql 로 입력한다(AGENTS.md 법령 규칙).
-- 코드 목록은 com.glassehr.legalrate.LegalRateItem 과 같아야 한다(LegalRateItemRegistrationTest 가 검사).
-- 근거 조문은 항목을 가리키는 위치이며, 값 입력 시 사람이 law.go.kr 현행 조문으로 다시 확인한다.

-- 근로기준법 제53조: 연장 근로의 제한
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('WEEKLY_OVERTIME_LIMIT_HOURS', '1주 연장근로 한도', 'HOURS', '근로기준법 제53조 제1항');

-- 근로기준법 제56조: 연장·야간 및 휴일 근로
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('OVERTIME_PREMIUM_RATE', '연장근로 가산율', 'PERCENT', '근로기준법 제56조 제1항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('HOLIDAY_PREMIUM_RATE', '휴일근로 가산율(기준시간 이내)', 'PERCENT', '근로기준법 제56조 제2항 제1호');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('HOLIDAY_EXCESS_PREMIUM_RATE', '휴일근로 가산율(기준시간 초과)', 'PERCENT', '근로기준법 제56조 제2항 제2호');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('HOLIDAY_PREMIUM_THRESHOLD_HOURS', '휴일근로 가산율 구분 기준시간(1일)', 'HOURS', '근로기준법 제56조 제2항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('NIGHT_PREMIUM_RATE', '야간근로 가산율', 'PERCENT', '근로기준법 제56조 제3항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('NIGHT_WORK_START_HOUR', '야간근로 시작 시각', 'HOUR_OF_DAY', '근로기준법 제56조 제3항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('NIGHT_WORK_END_HOUR', '야간근로 종료 시각', 'HOUR_OF_DAY', '근로기준법 제56조 제3항');

-- 근로기준법 제60조: 연차 유급휴가
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('ANNUAL_LEAVE_BASE_DAYS', '연차 기본 일수', 'DAYS', '근로기준법 제60조 제1항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('ANNUAL_LEAVE_ATTENDANCE_RATE', '연차 발생 출근율 기준', 'PERCENT', '근로기준법 제60조 제1항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('ANNUAL_LEAVE_MONTHLY_DAYS', '1년 미만(또는 출근율 미달) 1개월 개근 시 발생 일수', 'DAYS', '근로기준법 제60조 제2항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('ANNUAL_LEAVE_INCREMENT_BASE_YEARS', '연차 가산 제외 최초 근속 연수', 'YEARS', '근로기준법 제60조 제4항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('ANNUAL_LEAVE_INCREMENT_CYCLE_YEARS', '연차 가산 주기', 'YEARS', '근로기준법 제60조 제4항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('ANNUAL_LEAVE_INCREMENT_DAYS', '연차 가산 주기당 가산 일수', 'DAYS', '근로기준법 제60조 제4항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('ANNUAL_LEAVE_MAX_DAYS', '연차 가산 포함 상한 일수', 'DAYS', '근로기준법 제60조 제4항');

-- 근로기준법 제73조: 생리휴가
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('MENSTRUAL_LEAVE_DAYS_PER_MONTH', '생리휴가 월 일수', 'DAYS', '근로기준법 제73조');

-- 근로기준법 제74조: 임산부의 보호
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('MATERNITY_LEAVE_DAYS', '출산전후휴가 일수', 'DAYS', '근로기준법 제74조 제1항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('MATERNITY_LEAVE_MULTIPLE_DAYS', '출산전후휴가 일수(다태아)', 'DAYS', '근로기준법 제74조 제1항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('MATERNITY_LEAVE_POSTNATAL_MIN_DAYS', '출산전후휴가 중 출산 후 최소 일수', 'DAYS', '근로기준법 제74조 제1항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('MATERNITY_LEAVE_POSTNATAL_MIN_MULTIPLE_DAYS', '출산전후휴가 중 출산 후 최소 일수(다태아)', 'DAYS', '근로기준법 제74조 제1항');

-- 남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의2: 배우자 출산휴가
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('SPOUSE_MATERNITY_LEAVE_DAYS', '배우자 출산휴가 일수', 'DAYS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의2 제1항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('SPOUSE_MATERNITY_LEAVE_CLAIM_DAYS', '배우자 출산휴가 청구 기한(출산일부터)', 'DAYS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의2');

-- 같은 법 제19조: 육아휴직
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('PARENTAL_LEAVE_MAX_MONTHS', '육아휴직 기간', 'MONTHS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('PARENTAL_LEAVE_EXTENSION_MONTHS', '육아휴직 추가 가능 기간(요건 충족 시)', 'MONTHS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조');

-- 같은 법 제19조의2: 육아기 근로시간 단축
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('CHILDCARE_REDUCED_HOURS_MAX_MONTHS', '육아기 근로시간 단축 기간', 'MONTHS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조의2');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('CHILDCARE_REDUCED_WEEKLY_HOURS_MIN', '육아기 단축 후 주 근로시간 하한', 'HOURS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조의2');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('CHILDCARE_REDUCED_WEEKLY_HOURS_MAX', '육아기 단축 후 주 근로시간 상한', 'HOURS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조의2');

-- 같은 법 제22조의2: 근로자의 가족 돌봄 등을 위한 지원
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('FAMILY_CARE_LEAVE_OF_ABSENCE_DAYS_PER_YEAR', '가족돌봄휴직 연간 일수', 'DAYS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제22조의2');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('FAMILY_CARE_LEAVE_DAYS_PER_YEAR', '가족돌봄휴가 연간 일수', 'DAYS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제22조의2');
