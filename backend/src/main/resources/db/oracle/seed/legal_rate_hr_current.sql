-- #7 법령 연혁 시드. 확인일 2026-11-12.
-- 2026년 법령 전체 시행일을 항목 시행일로 사용한 초안을 교정한다.
-- 범위/규모별 시행/적용례: docs/legal-rate-seed-sources.md 필독.
-- 값이 없는 최초 구간은 미지원이며 0 또는 최신값으로 대체하지 않는다.
-- V001~V005 이후, 대상 코드에 기존 값이 없는 DB에서만 실행. 기존값은 덮어쓰지 않는다.
-- TIER 번호는 시행령 호 번호. TIER2는 2025-02-23부터 폐지(0으로 대체 금지).
SET DEFINE OFF
WHENEVER OSERROR EXIT FAILURE ROLLBACK
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
DECLARE
    v_count NUMBER;
BEGIN
    -- 잘못된 이전 시드나 수동 입력값이 있으면 쓰기 전에 중단한다.
    SELECT COUNT(*) INTO v_count FROM legal_rate WHERE item_code IN (
        'ANNUAL_LEAVE_ATTENDANCE_RATE',
        'ANNUAL_LEAVE_BASE_DAYS',
        'ANNUAL_LEAVE_INCREMENT_BASE_YEARS',
        'ANNUAL_LEAVE_INCREMENT_CYCLE_YEARS',
        'ANNUAL_LEAVE_INCREMENT_DAYS',
        'ANNUAL_LEAVE_MAX_DAYS',
        'ANNUAL_LEAVE_MONTHLY_DAYS',
        'CHILDCARE_REDUCED_HOURS_MAX_MONTHS',
        'CHILDCARE_REDUCED_WEEKLY_HOURS_MAX',
        'CHILDCARE_REDUCED_WEEKLY_HOURS_MIN',
        'FAMILY_CARE_LEAVE_DAYS_PER_YEAR',
        'FAMILY_CARE_LEAVE_OF_ABSENCE_DAYS_PER_YEAR',
        'HOLIDAY_EXCESS_PREMIUM_RATE',
        'HOLIDAY_PREMIUM_RATE',
        'HOLIDAY_PREMIUM_THRESHOLD_HOURS',
        'MATERNITY_LEAVE_DAYS',
        'MATERNITY_LEAVE_MULTIPLE_DAYS',
        'MATERNITY_LEAVE_POSTNATAL_MIN_DAYS',
        'MATERNITY_LEAVE_POSTNATAL_MIN_MULTIPLE_DAYS',
        'MENSTRUAL_LEAVE_DAYS_PER_MONTH',
        'NIGHT_PREMIUM_RATE',
        'NIGHT_WORK_END_HOUR',
        'NIGHT_WORK_START_HOUR',
        'OVERTIME_PREMIUM_RATE',
        'PARENTAL_LEAVE_EXTENSION_MONTHS',
        'PARENTAL_LEAVE_MAX_MONTHS',
        'SPOUSE_MATERNITY_LEAVE_CLAIM_DAYS',
        'SPOUSE_MATERNITY_LEAVE_DAYS',
        'WEEKLY_OVERTIME_LIMIT_HOURS',
        'MATERNITY_LEAVE_PREMATURE_DAYS',
        'MISCARRIAGE_LEAVE_TIER1_DAYS',
        'MISCARRIAGE_LEAVE_TIER1_FROM_WEEKS',
        'MISCARRIAGE_LEAVE_TIER2_DAYS',
        'MISCARRIAGE_LEAVE_TIER2_FROM_WEEKS',
        'MISCARRIAGE_LEAVE_TIER3_DAYS',
        'MISCARRIAGE_LEAVE_TIER3_FROM_WEEKS',
        'MISCARRIAGE_LEAVE_TIER4_DAYS',
        'MISCARRIAGE_LEAVE_TIER4_FROM_WEEKS',
        'MISCARRIAGE_LEAVE_TIER5_DAYS',
        'MISCARRIAGE_LEAVE_TIER5_FROM_WEEKS',
        'PARENTAL_LEAVE_SPLIT_COUNT',
        'PREGNANCY_REDUCED_HOURS_EARLY_WEEKS',
        'PREGNANCY_REDUCED_HOURS_LATE_FROM_WEEKS',
        'PREGNANCY_REDUCED_HOURS_PER_DAY',
        'PREGNANCY_REDUCED_SHORT_DAY_TARGET_HOURS',
        'PREGNANCY_REDUCED_SHORT_DAY_THRESHOLD_HOURS',
        'SPOUSE_MATERNITY_LEAVE_SPLIT_COUNT');
    IF v_count <> 0 THEN
        RAISE_APPLICATION_ERROR(-20201, 'Target legal_rate rows already exist; inspect before importing');
    END IF;
    legal_rate_api.insert_period('ANNUAL_LEAVE_ATTENDANCE_RATE', DATE '2008-07-01', NULL, 80, 'PERCENT', '근로기준법 제60조 제1항', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('ANNUAL_LEAVE_BASE_DAYS', DATE '2008-07-01', NULL, 15, 'DAYS', '근로기준법 제60조 제1항', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('ANNUAL_LEAVE_INCREMENT_BASE_YEARS', DATE '2008-07-01', NULL, 1, 'YEARS', '근로기준법 제60조 제4항', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('ANNUAL_LEAVE_INCREMENT_CYCLE_YEARS', DATE '2008-07-01', NULL, 2, 'YEARS', '근로기준법 제60조 제4항', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('ANNUAL_LEAVE_INCREMENT_DAYS', DATE '2008-07-01', NULL, 1, 'DAYS', '근로기준법 제60조 제4항', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('ANNUAL_LEAVE_MAX_DAYS', DATE '2008-07-01', NULL, 25, 'DAYS', '근로기준법 제60조 제4항', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('ANNUAL_LEAVE_MONTHLY_DAYS', DATE '2008-07-01', NULL, 1, 'DAYS', '근로기준법 제60조 제2항', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('CHILDCARE_REDUCED_HOURS_MAX_MONTHS', DATE '2008-06-22', NULL, 12, 'MONTHS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조의2', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010202&lsId=000130&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('CHILDCARE_REDUCED_WEEKLY_HOURS_MAX', DATE '2008-06-22', DATE '2019-10-01', 30, 'HOURS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조의2', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010202&lsId=000130&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('CHILDCARE_REDUCED_WEEKLY_HOURS_MAX', DATE '2019-10-01', NULL, 35, 'HOURS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조의2', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010202&lsId=000130&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('CHILDCARE_REDUCED_WEEKLY_HOURS_MIN', DATE '2008-06-22', NULL, 15, 'HOURS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조의2', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010202&lsId=000130&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('FAMILY_CARE_LEAVE_DAYS_PER_YEAR', DATE '2020-01-01', NULL, 10, 'DAYS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제22조의2', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010202&lsId=000130&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('FAMILY_CARE_LEAVE_OF_ABSENCE_DAYS_PER_YEAR', DATE '2013-02-02', NULL, 90, 'DAYS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제22조의2', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010202&lsId=000130&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('HOLIDAY_EXCESS_PREMIUM_RATE', DATE '2018-03-20', NULL, 100, 'PERCENT', '근로기준법 제56조 제2항 제2호', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('HOLIDAY_PREMIUM_RATE', DATE '2018-03-20', NULL, 50, 'PERCENT', '근로기준법 제56조 제2항 제1호', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('HOLIDAY_PREMIUM_THRESHOLD_HOURS', DATE '2018-03-20', NULL, 8, 'HOURS', '근로기준법 제56조 제2항', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('MATERNITY_LEAVE_DAYS', DATE '2007-04-11', NULL, 90, 'DAYS', '근로기준법 제74조 제1항', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('MATERNITY_LEAVE_MULTIPLE_DAYS', DATE '2007-04-11', DATE '2014-07-01', 90, 'DAYS', '근로기준법 제74조 제1항', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('MATERNITY_LEAVE_MULTIPLE_DAYS', DATE '2014-07-01', NULL, 120, 'DAYS', '근로기준법 제74조 제1항', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('MATERNITY_LEAVE_POSTNATAL_MIN_DAYS', DATE '2007-04-11', NULL, 45, 'DAYS', '근로기준법 제74조 제1항', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('MATERNITY_LEAVE_POSTNATAL_MIN_MULTIPLE_DAYS', DATE '2007-04-11', DATE '2014-07-01', 45, 'DAYS', '근로기준법 제74조 제1항', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('MATERNITY_LEAVE_POSTNATAL_MIN_MULTIPLE_DAYS', DATE '2014-07-01', NULL, 60, 'DAYS', '근로기준법 제74조 제1항', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('MATERNITY_LEAVE_PREMATURE_DAYS', DATE '2025-02-23', NULL, 100, 'DAYS', '근로기준법 제74조 제1항, 법률 제20520호 부칙 제1조 제2호·제7조', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261112');
    legal_rate_api.insert_period('MENSTRUAL_LEAVE_DAYS_PER_MONTH', DATE '2008-07-01', NULL, 1, 'DAYS', '근로기준법 제73조', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('MISCARRIAGE_LEAVE_TIER1_DAYS', DATE '2012-08-02', DATE '2025-02-23', 5, 'DAYS', '근로기준법 시행령 제43조 제3항 제1호, 대통령령 제23868호 부칙', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010102&lsId=003058', 'codex-history-check-20261112');
    legal_rate_api.insert_period('MISCARRIAGE_LEAVE_TIER1_DAYS', DATE '2025-02-23', NULL, 10, 'DAYS', '근로기준법 시행령 제43조 제3항 제1호, 대통령령 제35276호 부칙 제1·2조', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010102&lsId=003058', 'codex-history-check-20261112');
    legal_rate_api.insert_period('MISCARRIAGE_LEAVE_TIER1_FROM_WEEKS', DATE '2012-08-02', NULL, 0, 'WEEKS', '근로기준법 시행령 제43조 제3항 제1호, 대통령령 제23868호 부칙', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010102&lsId=003058', 'codex-history-check-20261112');
    legal_rate_api.insert_period('MISCARRIAGE_LEAVE_TIER2_DAYS', DATE '2012-08-02', DATE '2025-02-23', 10, 'DAYS', '근로기준법 시행령 제43조 제3항 제2호, 대통령령 제23868호 부칙', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010102&lsId=003058', 'codex-history-check-20261112');
    legal_rate_api.insert_period('MISCARRIAGE_LEAVE_TIER2_FROM_WEEKS', DATE '2012-08-02', DATE '2025-02-23', 12, 'WEEKS', '근로기준법 시행령 제43조 제3항 제2호, 대통령령 제23868호 부칙', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010102&lsId=003058', 'codex-history-check-20261112');
    legal_rate_api.insert_period('MISCARRIAGE_LEAVE_TIER3_DAYS', DATE '2012-08-02', NULL, 30, 'DAYS', '근로기준법 시행령 제43조 제3항 제3호, 대통령령 제23868호 부칙', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010102&lsId=003058', 'codex-history-check-20261112');
    legal_rate_api.insert_period('MISCARRIAGE_LEAVE_TIER3_FROM_WEEKS', DATE '2012-08-02', NULL, 16, 'WEEKS', '근로기준법 시행령 제43조 제3항 제3호, 대통령령 제23868호 부칙', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010102&lsId=003058', 'codex-history-check-20261112');
    legal_rate_api.insert_period('MISCARRIAGE_LEAVE_TIER4_DAYS', DATE '2012-08-02', NULL, 60, 'DAYS', '근로기준법 시행령 제43조 제3항 제4호, 대통령령 제23868호 부칙', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010102&lsId=003058', 'codex-history-check-20261112');
    legal_rate_api.insert_period('MISCARRIAGE_LEAVE_TIER4_FROM_WEEKS', DATE '2012-08-02', NULL, 22, 'WEEKS', '근로기준법 시행령 제43조 제3항 제4호, 대통령령 제23868호 부칙', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010102&lsId=003058', 'codex-history-check-20261112');
    legal_rate_api.insert_period('MISCARRIAGE_LEAVE_TIER5_DAYS', DATE '2012-08-02', NULL, 90, 'DAYS', '근로기준법 시행령 제43조 제3항 제5호, 대통령령 제23868호 부칙', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010102&lsId=003058', 'codex-history-check-20261112');
    legal_rate_api.insert_period('MISCARRIAGE_LEAVE_TIER5_FROM_WEEKS', DATE '2012-08-02', NULL, 28, 'WEEKS', '근로기준법 시행령 제43조 제3항 제5호, 대통령령 제23868호 부칙', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010102&lsId=003058', 'codex-history-check-20261112');
    legal_rate_api.insert_period('NIGHT_PREMIUM_RATE', DATE '2018-03-20', NULL, 50, 'PERCENT', '근로기준법 제56조 제3항', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('NIGHT_WORK_END_HOUR', DATE '2018-03-20', NULL, 6, 'HOUR_OF_DAY', '근로기준법 제56조 제3항', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('NIGHT_WORK_START_HOUR', DATE '2018-03-20', NULL, 22, 'HOUR_OF_DAY', '근로기준법 제56조 제3항', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('OVERTIME_PREMIUM_RATE', DATE '2018-03-20', NULL, 50, 'PERCENT', '근로기준법 제56조 제1항', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('PARENTAL_LEAVE_EXTENSION_MONTHS', DATE '2008-06-22', DATE '2025-02-23', 0, 'MONTHS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010202&lsId=000130&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('PARENTAL_LEAVE_EXTENSION_MONTHS', DATE '2025-02-23', NULL, 6, 'MONTHS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010202&lsId=000130&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('PARENTAL_LEAVE_MAX_MONTHS', DATE '2008-06-22', NULL, 12, 'MONTHS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010202&lsId=000130&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('PARENTAL_LEAVE_SPLIT_COUNT', DATE '2019-10-01', DATE '2020-12-08', 1, 'COUNT', '남녀고용평등법 제19조의4 제1항, 법률 제16558호·제17602호·제20521호 부칙', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=000130', 'codex-history-check-20261112');
    legal_rate_api.insert_period('PARENTAL_LEAVE_SPLIT_COUNT', DATE '2020-12-08', DATE '2025-02-23', 2, 'COUNT', '남녀고용평등법 제19조의4 제1항, 법률 제16558호·제17602호·제20521호 부칙', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=000130', 'codex-history-check-20261112');
    legal_rate_api.insert_period('PARENTAL_LEAVE_SPLIT_COUNT', DATE '2025-02-23', NULL, 3, 'COUNT', '남녀고용평등법 제19조의4 제1항, 법률 제16558호·제17602호·제20521호 부칙', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=000130', 'codex-history-check-20261112');
    legal_rate_api.insert_period('PREGNANCY_REDUCED_HOURS_EARLY_WEEKS', DATE '2016-03-25', NULL, 12, 'WEEKS', '근로기준법 제74조 제7항, 법률 제12527호 부칙 제1조 제2호', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261112');
    legal_rate_api.insert_period('PREGNANCY_REDUCED_HOURS_LATE_FROM_WEEKS', DATE '2016-03-25', DATE '2025-02-23', 36, 'WEEKS', '근로기준법 제74조 제7항, 법률 제12527호·제20520호 부칙', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261112');
    legal_rate_api.insert_period('PREGNANCY_REDUCED_HOURS_LATE_FROM_WEEKS', DATE '2025-02-23', NULL, 32, 'WEEKS', '근로기준법 제74조 제7항, 법률 제12527호·제20520호 부칙', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261112');
    legal_rate_api.insert_period('PREGNANCY_REDUCED_HOURS_PER_DAY', DATE '2016-03-25', NULL, 2, 'HOURS', '근로기준법 제74조 제7항, 법률 제12527호 부칙 제1조 제2호', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261112');
    legal_rate_api.insert_period('PREGNANCY_REDUCED_SHORT_DAY_TARGET_HOURS', DATE '2016-03-25', NULL, 6, 'HOURS', '근로기준법 제74조 제7항 단서, 법률 제12527호 부칙 제1조 제2호', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261112');
    legal_rate_api.insert_period('PREGNANCY_REDUCED_SHORT_DAY_THRESHOLD_HOURS', DATE '2016-03-25', NULL, 8, 'HOURS', '근로기준법 제74조 제7항 단서, 법률 제12527호 부칙 제1조 제2호', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261112');
    legal_rate_api.insert_period('SPOUSE_MATERNITY_LEAVE_CLAIM_DAYS', DATE '2008-06-22', DATE '2019-10-01', 30, 'DAYS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의2', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010202&lsId=000130&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('SPOUSE_MATERNITY_LEAVE_CLAIM_DAYS', DATE '2019-10-01', DATE '2025-02-23', 90, 'DAYS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의2', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010202&lsId=000130&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('SPOUSE_MATERNITY_LEAVE_CLAIM_DAYS', DATE '2025-02-23', NULL, 120, 'DAYS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의2', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010202&lsId=000130&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('SPOUSE_MATERNITY_LEAVE_DAYS', DATE '2019-10-01', DATE '2025-02-23', 10, 'DAYS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의2 제1항', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010202&lsId=000130&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('SPOUSE_MATERNITY_LEAVE_DAYS', DATE '2025-02-23', NULL, 20, 'DAYS', '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의2 제1항', 'https://law.go.kr/LSW/lsRvsDocListP.do?chrClsCd=010202&lsId=000130&lsRvsGubun=all', 'codex-history-check-20261111');
    legal_rate_api.insert_period('SPOUSE_MATERNITY_LEAVE_SPLIT_COUNT', DATE '2019-10-01', DATE '2025-02-23', 1, 'COUNT', '남녀고용평등법 제18조의2 제4항, 법률 제16558호·제20521호 부칙', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=000130', 'codex-history-check-20261112');
    legal_rate_api.insert_period('SPOUSE_MATERNITY_LEAVE_SPLIT_COUNT', DATE '2025-02-23', NULL, 3, 'COUNT', '남녀고용평등법 제18조의2 제4항, 법률 제16558호·제20521호 부칙', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=000130', 'codex-history-check-20261112');
    legal_rate_api.insert_period('WEEKLY_OVERTIME_LIMIT_HOURS', DATE '2021-07-01', NULL, 12, 'HOURS', '근로기준법 제53조 제1항', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-history-check-20261111');
END;
/
COMMIT;
