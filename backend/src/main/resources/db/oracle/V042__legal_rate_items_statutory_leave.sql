-- #143 근태코드: 법정휴가 유급 일수·난임치료휴가 일수 항목코드만 등록한다(값 없음).
-- 값은 법령 데이터 작업(Codex, law.go.kr 원문 확인)으로 seed/legal_rate_*.sql 에 넣는다(AGENTS.md 법령 규칙).
-- 근태관리 > 근태코드 관리 화면이 조회일 기준으로 읽어 보여 준다(com.glassehr.code.AttendanceCodeService).
-- 코드 목록은 com.glassehr.legalrate.LegalRateItem 과 같아야 한다(LegalRateItemRegistrationTest).

INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('MATERNITY_LEAVE_PAID_DAYS', '출산전후휴가 중 유급 일수', 'DAYS', '근로기준법 제74조');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('MATERNITY_LEAVE_MULTIPLE_PAID_DAYS', '출산전후휴가 중 유급 일수(다태아)', 'DAYS', '근로기준법 제74조');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('SPOUSE_MATERNITY_LEAVE_PAID_DAYS', '배우자 출산휴가 중 유급 일수', 'DAYS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의2');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('FAMILY_CARE_LEAVE_PAID_DAYS', '가족돌봄휴가 중 유급 일수', 'DAYS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제22조의2');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('INFERTILITY_TREATMENT_LEAVE_DAYS_PER_YEAR', '난임치료휴가 연간 일수', 'DAYS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의3');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('INFERTILITY_TREATMENT_LEAVE_PAID_DAYS', '난임치료휴가 중 유급 일수', 'DAYS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의3');
