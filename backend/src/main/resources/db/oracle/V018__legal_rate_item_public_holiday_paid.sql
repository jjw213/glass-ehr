-- 공휴일 유급휴일 적용 사업장 규모 항목코드 등록 (#26). V011·V015 와 같은 원칙: 항목만 등록하고 값(legal_rate 행)은 넣지 않는다.
-- 근로기준법 제55조 제2항(관공서 공휴일의 유급휴일 보장)은 부칙에 따라 상시근로자 수별로 시행일이 다르다.
-- 값 = 그 날짜에 제55조 제2항이 적용되는 상시근로자 수 하한(명). 이 사업장 상시근로자 수가 값 이상이면 적용한다.
-- 값은 law.go.kr 원문(근로기준법 부칙)을 확인해 legal_rate_api 로 입력한다. 값이 없으면 공휴일 자료 불러오기가 거부된다(기본값 없음).
-- 코드 목록은 com.glassehr.legalrate.LegalRateItem 과 같아야 한다(LegalRateItemRegistrationTest).

INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('PUBLIC_HOLIDAY_PAID_MIN_HEADCOUNT', '공휴일 유급휴일 적용 상시근로자 수 하한(명)', 'COUNT', '근로기준법 제55조 제2항, 같은 법 부칙(규모별 시행일)');
