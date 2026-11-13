-- 기간제근로자 사용기간 상한 항목코드 등록 (#17). V003·V005·V010 과 같은 원칙: 항목만 등록하고 값(legal_rate 행)은 넣지 않는다.
-- 값은 사람이 law.go.kr 원문(기간제 및 단시간근로자 보호 등에 관한 법률 제4조 제1항)을 확인해 legal_rate_api 로 입력한다.
-- 값이 없으면 기간제 사용기간 도래 알림은 계산하지 않고 "기준값 없음"으로 표시한다(기본값 없음).
-- 코드 목록은 com.glassehr.legalrate.LegalRateItem 과 같아야 한다(LegalRateItemRegistrationTest).

INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('FIXED_TERM_MAX_USE_YEARS', '기간제근로자 사용기간 상한(계속근로 총기간)', 'YEARS', '기간제 및 단시간근로자 보호 등에 관한 법률 제4조 제1항');
