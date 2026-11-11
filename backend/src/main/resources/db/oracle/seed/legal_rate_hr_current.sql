-- #7: 공식 원문 확인 2026-11-11. 사용자 요청으로 조사·입력.
-- 확인한 현행 법령 버전부터만 제공하는 시드. 최초 법 제정일을 뜻하지 않는다.
-- 과거 미지원 날짜는 미등록 예외로 처리한다. 상세 조건: docs/legal-rate-seed-sources.md
-- V001~V004 실행 후 SQL*Plus/SQLcl에서 실행. 기존 행과 겹치면 전체 롤백.
-- 자동 마이그레이션 대상 아님. 재실행 시 중복을 덮어쓰지 않는다.
SET DEFINE OFF
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
BEGIN
    legal_rate_api.insert_period('ANNUAL_LEAVE_ATTENDANCE_RATE', DATE '2026-10-08', NULL, 80, 'PERCENT', '근로기준법 제60조 제1항', 'https://law.go.kr/LSW/lsLinkCommonInfo.do?lsJoLnkSeq=1012828043', 'codex-source-check-20261111');
    legal_rate_api.insert_period('ANNUAL_LEAVE_BASE_DAYS', DATE '2026-10-08', NULL, 15, 'DAYS', '근로기준법 제60조 제1항', 'https://law.go.kr/LSW/lsLinkCommonInfo.do?lsJoLnkSeq=1012828043', 'codex-source-check-20261111');
    legal_rate_api.insert_period('ANNUAL_LEAVE_INCREMENT_BASE_YEARS', DATE '2026-10-08', NULL, 1, 'YEARS', '근로기준법 제60조 제4항', 'https://law.go.kr/LSW/lsLinkCommonInfo.do?lsJoLnkSeq=1012828043', 'codex-source-check-20261111');
    legal_rate_api.insert_period('ANNUAL_LEAVE_INCREMENT_CYCLE_YEARS', DATE '2026-10-08', NULL, 2, 'YEARS', '근로기준법 제60조 제4항', 'https://law.go.kr/LSW/lsLinkCommonInfo.do?lsJoLnkSeq=1012828043', 'codex-source-check-20261111');
    legal_rate_api.insert_period('ANNUAL_LEAVE_INCREMENT_DAYS', DATE '2026-10-08', NULL, 1, 'DAYS', '근로기준법 제60조 제4항', 'https://law.go.kr/LSW/lsLinkCommonInfo.do?lsJoLnkSeq=1012828043', 'codex-source-check-20261111');
    legal_rate_api.insert_period('ANNUAL_LEAVE_MAX_DAYS', DATE '2026-10-08', NULL, 25, 'DAYS', '근로기준법 제60조 제4항', 'https://law.go.kr/LSW/lsLinkCommonInfo.do?lsJoLnkSeq=1012828043', 'codex-source-check-20261111');
    legal_rate_api.insert_period('ANNUAL_LEAVE_MONTHLY_DAYS', DATE '2026-10-08', NULL, 1, 'DAYS', '근로기준법 제60조 제2항', 'https://law.go.kr/LSW/lsLinkCommonInfo.do?lsJoLnkSeq=1012828043', 'codex-source-check-20261111');
    legal_rate_api.insert_period('CHILDCARE_REDUCED_HOURS_MAX_MONTHS', DATE '2026-09-18', NULL, 12, 'MONTHS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조의2', 'https://law.go.kr/LSW/lsLawLinkInfo.do?chrClsCd=010202&lsJoLnkSeq=1010788173', 'codex-source-check-20261111');
    legal_rate_api.insert_period('CHILDCARE_REDUCED_WEEKLY_HOURS_MAX', DATE '2026-09-18', NULL, 35, 'HOURS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조의2', 'https://law.go.kr/LSW/lsLawLinkInfo.do?chrClsCd=010202&lsJoLnkSeq=1010788173', 'codex-source-check-20261111');
    legal_rate_api.insert_period('CHILDCARE_REDUCED_WEEKLY_HOURS_MIN', DATE '2026-09-18', NULL, 15, 'HOURS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조의2', 'https://law.go.kr/LSW/lsLawLinkInfo.do?chrClsCd=010202&lsJoLnkSeq=1010788173', 'codex-source-check-20261111');
    legal_rate_api.insert_period('FAMILY_CARE_LEAVE_DAYS_PER_YEAR', DATE '2026-09-18', NULL, 10, 'DAYS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제22조의2', 'https://law.go.kr/LSW/lsLawLinkInfo.do?chrClsCd=010202&lsJoLnkSeq=1000446262', 'codex-source-check-20261111');
    legal_rate_api.insert_period('FAMILY_CARE_LEAVE_OF_ABSENCE_DAYS_PER_YEAR', DATE '2026-09-18', NULL, 90, 'DAYS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제22조의2', 'https://law.go.kr/LSW/lsLawLinkInfo.do?chrClsCd=010202&lsJoLnkSeq=1000446262', 'codex-source-check-20261111');
    legal_rate_api.insert_period('HOLIDAY_EXCESS_PREMIUM_RATE', DATE '2026-10-08', NULL, 100, 'PERCENT', '근로기준법 제56조 제2항 제2호', 'https://www.law.go.kr/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1034322805', 'codex-source-check-20261111');
    legal_rate_api.insert_period('HOLIDAY_PREMIUM_RATE', DATE '2026-10-08', NULL, 50, 'PERCENT', '근로기준법 제56조 제2항 제1호', 'https://www.law.go.kr/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1034322805', 'codex-source-check-20261111');
    legal_rate_api.insert_period('HOLIDAY_PREMIUM_THRESHOLD_HOURS', DATE '2026-10-08', NULL, 8, 'HOURS', '근로기준법 제56조 제2항', 'https://www.law.go.kr/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1034322805', 'codex-source-check-20261111');
    legal_rate_api.insert_period('MATERNITY_LEAVE_DAYS', DATE '2026-10-08', NULL, 90, 'DAYS', '근로기준법 제74조 제1항', 'https://www.law.go.kr/LSW/lsInfoP.do?lsiSeq=285279', 'codex-source-check-20261111');
    legal_rate_api.insert_period('MATERNITY_LEAVE_MULTIPLE_DAYS', DATE '2026-10-08', NULL, 120, 'DAYS', '근로기준법 제74조 제1항', 'https://www.law.go.kr/LSW/lsInfoP.do?lsiSeq=285279', 'codex-source-check-20261111');
    legal_rate_api.insert_period('MATERNITY_LEAVE_POSTNATAL_MIN_DAYS', DATE '2026-10-08', NULL, 45, 'DAYS', '근로기준법 제74조 제1항', 'https://www.law.go.kr/LSW/lsInfoP.do?lsiSeq=285279', 'codex-source-check-20261111');
    legal_rate_api.insert_period('MATERNITY_LEAVE_POSTNATAL_MIN_MULTIPLE_DAYS', DATE '2026-10-08', NULL, 60, 'DAYS', '근로기준법 제74조 제1항', 'https://www.law.go.kr/LSW/lsInfoP.do?lsiSeq=285279', 'codex-source-check-20261111');
    legal_rate_api.insert_period('MENSTRUAL_LEAVE_DAYS_PER_MONTH', DATE '2026-10-08', NULL, 1, 'DAYS', '근로기준법 제73조', 'https://law.go.kr/lsLinkCommonInfo.do?lsJoLnkSeq=1007688443', 'codex-source-check-20261111');
    legal_rate_api.insert_period('NIGHT_PREMIUM_RATE', DATE '2026-10-08', NULL, 50, 'PERCENT', '근로기준법 제56조 제3항', 'https://www.law.go.kr/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1034322805', 'codex-source-check-20261111');
    legal_rate_api.insert_period('NIGHT_WORK_END_HOUR', DATE '2026-10-08', NULL, 6, 'HOUR_OF_DAY', '근로기준법 제56조 제3항', 'https://www.law.go.kr/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1034322805', 'codex-source-check-20261111');
    legal_rate_api.insert_period('NIGHT_WORK_START_HOUR', DATE '2026-10-08', NULL, 22, 'HOUR_OF_DAY', '근로기준법 제56조 제3항', 'https://www.law.go.kr/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1034322805', 'codex-source-check-20261111');
    legal_rate_api.insert_period('OVERTIME_PREMIUM_RATE', DATE '2026-10-08', NULL, 50, 'PERCENT', '근로기준법 제56조 제1항', 'https://www.law.go.kr/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1034322805', 'codex-source-check-20261111');
    legal_rate_api.insert_period('PARENTAL_LEAVE_EXTENSION_MONTHS', DATE '2026-09-18', NULL, 6, 'MONTHS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조', 'https://www.law.go.kr/lsLinkCommonInfo.do?lsJoLnkSeq=1034314827', 'codex-source-check-20261111');
    legal_rate_api.insert_period('PARENTAL_LEAVE_MAX_MONTHS', DATE '2026-09-18', NULL, 12, 'MONTHS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조', 'https://www.law.go.kr/lsLinkCommonInfo.do?lsJoLnkSeq=1034314827', 'codex-source-check-20261111');
    legal_rate_api.insert_period('SPOUSE_MATERNITY_LEAVE_CLAIM_DAYS', DATE '2026-09-18', NULL, 120, 'DAYS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의2', 'https://law.go.kr/lsInfoP.do?ancNo=21065&ancYd=20251001&ancYnChk=0&chrClsCd=010202&efGubun=Y&efYd=20251001&lsiSeq=276851&nwJoYnInfo=Y', 'codex-source-check-20261111');
    legal_rate_api.insert_period('SPOUSE_MATERNITY_LEAVE_DAYS', DATE '2026-09-18', NULL, 20, 'DAYS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의2 제1항', 'https://law.go.kr/lsInfoP.do?ancNo=21065&ancYd=20251001&ancYnChk=0&chrClsCd=010202&efGubun=Y&efYd=20251001&lsiSeq=276851&nwJoYnInfo=Y', 'codex-source-check-20261111');
    legal_rate_api.insert_period('WEEKLY_OVERTIME_LIMIT_HOURS', DATE '2026-10-08', NULL, 12, 'HOURS', '근로기준법 제53조 제1항', 'https://law.go.kr/LSW/lsInfoP.do?lsiSeq=285279', 'codex-source-check-20261111');
END;
/
COMMIT;
