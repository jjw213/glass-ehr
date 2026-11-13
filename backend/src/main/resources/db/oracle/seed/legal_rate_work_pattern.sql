-- #25 근무유형 기준값. 원문 확인: 2026-11-13.
-- 30인 이상에 공통 적용 가능한 2008-07-01부터 제공한다. 그 이전은 미지원.
-- 40은 소정근로시간 한도이며 연장 포함 52시간 한도가 아니다.
-- 시행/규모별 해석: docs/legal-rate-seed-sources.md #25 절.
-- 근무유형 항목 등록 마이그레이션 및 legal_rate_api 적용 후 별도 실행.
SET DEFINE OFF
WHENEVER OSERROR EXIT FAILURE ROLLBACK
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM legal_rate
     WHERE item_code IN ('BREAK_LONG_MIN_MINUTES', 'BREAK_LONG_THRESHOLD_HOURS', 'BREAK_SHORT_MIN_MINUTES', 'BREAK_SHORT_THRESHOLD_HOURS', 'DAILY_STANDARD_WORK_HOURS', 'WEEKLY_PAID_HOLIDAY_MIN_COUNT', 'WEEKLY_STANDARD_WORK_HOURS');
    IF v_count <> 0 THEN
        RAISE_APPLICATION_ERROR(-20225, 'work-pattern legal rates already exist; inspect before importing');
    END IF;
    legal_rate_api.insert_period('BREAK_LONG_MIN_MINUTES', DATE '2008-07-01', NULL, 60, 'MINUTES', '근로기준법 제54조제1항; 확인 2026-11-13', 'https://law.go.kr/LSW/lsInfoP.do?lsiSeq=285279', 'codex-source-check-20261113');
    legal_rate_api.insert_period('BREAK_LONG_THRESHOLD_HOURS', DATE '2008-07-01', NULL, 8, 'HOURS', '근로기준법 제54조제1항; 확인 2026-11-13', 'https://law.go.kr/LSW/lsInfoP.do?lsiSeq=285279', 'codex-source-check-20261113');
    legal_rate_api.insert_period('BREAK_SHORT_MIN_MINUTES', DATE '2008-07-01', NULL, 30, 'MINUTES', '근로기준법 제54조제1항; 확인 2026-11-13', 'https://law.go.kr/LSW/lsInfoP.do?lsiSeq=285279', 'codex-source-check-20261113');
    legal_rate_api.insert_period('BREAK_SHORT_THRESHOLD_HOURS', DATE '2008-07-01', NULL, 4, 'HOURS', '근로기준법 제54조제1항; 확인 2026-11-13', 'https://law.go.kr/LSW/lsInfoP.do?lsiSeq=285279', 'codex-source-check-20261113');
    legal_rate_api.insert_period('DAILY_STANDARD_WORK_HOURS', DATE '2008-07-01', NULL, 8, 'HOURS', '근로기준법 제50조제2항; 확인 2026-11-13', 'https://law.go.kr/LSW/lsInfoP.do?lsiSeq=285279', 'codex-source-check-20261113');
    legal_rate_api.insert_period('WEEKLY_PAID_HOLIDAY_MIN_COUNT', DATE '2008-07-01', NULL, 1, 'COUNT', '근로기준법 제55조제1항; 확인 2026-11-13', 'https://law.go.kr/LSW/lsInfoP.do?lsiSeq=285279', 'codex-source-check-20261113');
    legal_rate_api.insert_period('WEEKLY_STANDARD_WORK_HOURS', DATE '2008-07-01', DATE '2021-07-01', 40, 'HOURS', '근로기준법 제50조제1항; 법률 제8372호 부칙 제4조제5호; 확인 2026-11-13', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-source-check-20261113');
    legal_rate_api.insert_period('WEEKLY_STANDARD_WORK_HOURS', DATE '2021-07-01', NULL, 40, 'HOURS', '근로기준법 제50조제1항; 제2조제1항제7호; 법률 제15513호 부칙 제1조제2항제3호; 확인 2026-11-13', 'https://www.law.go.kr/nwRvsLsInfoR.do?lsiSeq=202870', 'codex-source-check-20261113');
END;
/
COMMIT;
