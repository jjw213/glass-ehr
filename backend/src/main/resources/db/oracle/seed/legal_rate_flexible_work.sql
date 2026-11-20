-- #38 유연근무제 법정 한도. 원문 확인: 2026-11-19.
-- 대상 서비스 범위(상시 30인 이상)에 공통 적용되는 2021-07-01부터 제공한다.
SET DEFINE OFF
WHENEVER OSERROR EXIT FAILURE ROLLBACK
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM legal_rate
     WHERE item_code IN (
        'FLEX_SHORT_UNIT_PERIOD_MAX_WEEKS', 'FLEX_SHORT_WEEK_MAX_HOURS',
        'FLEX_WRITTEN_UNIT_PERIOD_MAX_MONTHS', 'FLEX_WRITTEN_WEEK_MAX_HOURS', 'FLEX_WRITTEN_DAY_MAX_HOURS',
        'FLEX_LONG_UNIT_PERIOD_MAX_MONTHS', 'FLEX_LONG_WEEK_MAX_HOURS', 'FLEX_LONG_DAY_MAX_HOURS', 'FLEX_LONG_DAILY_REST_MIN_HOURS',
        'SELECTIVE_SETTLEMENT_MAX_MONTHS', 'SELECTIVE_SETTLEMENT_RND_MAX_MONTHS', 'SELECTIVE_RND_DAILY_REST_MIN_HOURS');
    IF v_count <> 0 THEN
        RAISE_APPLICATION_ERROR(-20238, 'flexible-work legal rates already exist; inspect before importing');
    END IF;

    legal_rate_api.insert_period('FLEX_SHORT_UNIT_PERIOD_MAX_WEEKS', DATE '2021-07-01', NULL, 2, 'WEEKS', '근로기준법 제51조제1항; 확인 2026-11-19', 'https://www.law.go.kr/LSW/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1029728259', 'codex-source-check-20261119');
    legal_rate_api.insert_period('FLEX_SHORT_WEEK_MAX_HOURS', DATE '2021-07-01', NULL, 48, 'HOURS', '근로기준법 제51조제1항 단서; 확인 2026-11-19', 'https://www.law.go.kr/LSW/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1029728259', 'codex-source-check-20261119');
    legal_rate_api.insert_period('FLEX_WRITTEN_UNIT_PERIOD_MAX_MONTHS', DATE '2021-07-01', NULL, 3, 'MONTHS', '근로기준법 제51조제2항 본문·제2호; 확인 2026-11-19', 'https://www.law.go.kr/LSW/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1029728259', 'codex-source-check-20261119');
    legal_rate_api.insert_period('FLEX_WRITTEN_WEEK_MAX_HOURS', DATE '2021-07-01', NULL, 52, 'HOURS', '근로기준법 제51조제2항 단서; 확인 2026-11-19', 'https://www.law.go.kr/LSW/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1029728259', 'codex-source-check-20261119');
    legal_rate_api.insert_period('FLEX_WRITTEN_DAY_MAX_HOURS', DATE '2021-07-01', NULL, 12, 'HOURS', '근로기준법 제51조제2항 단서; 확인 2026-11-19', 'https://www.law.go.kr/LSW/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1029728259', 'codex-source-check-20261119');
    legal_rate_api.insert_period('FLEX_LONG_UNIT_PERIOD_MAX_MONTHS', DATE '2021-07-01', NULL, 6, 'MONTHS', '근로기준법 제51조의2제1항; 법률 제17862호 부칙 제1조제2항제2호; 확인 2026-11-19', 'https://www.law.go.kr/LSW/lsInfoP.do?efYd=20210406&lsiSeq=228171', 'codex-source-check-20261119');
    legal_rate_api.insert_period('FLEX_LONG_WEEK_MAX_HOURS', DATE '2021-07-01', NULL, 52, 'HOURS', '근로기준법 제51조의2제1항 단서; 확인 2026-11-19', 'https://www.law.go.kr/LSW/lsSideInfoP.do?docCls=jo&joBrNo=02&joNo=0051&lsiSeq=285279&urlMode=lsScJoRltInfoR', 'codex-source-check-20261119');
    legal_rate_api.insert_period('FLEX_LONG_DAY_MAX_HOURS', DATE '2021-07-01', NULL, 12, 'HOURS', '근로기준법 제51조의2제1항 단서; 확인 2026-11-19', 'https://www.law.go.kr/LSW/lsSideInfoP.do?docCls=jo&joBrNo=02&joNo=0051&lsiSeq=285279&urlMode=lsScJoRltInfoR', 'codex-source-check-20261119');
    legal_rate_api.insert_period('FLEX_LONG_DAILY_REST_MIN_HOURS', DATE '2021-07-01', NULL, 11, 'HOURS', '근로기준법 제51조의2제2항제1호; 확인 2026-11-19', 'https://www.law.go.kr/LSW/lsSideInfoP.do?docCls=jo&joBrNo=02&joNo=0051&lsiSeq=285279&urlMode=lsScJoRltInfoR', 'codex-source-check-20261119');
    legal_rate_api.insert_period('SELECTIVE_SETTLEMENT_MAX_MONTHS', DATE '2021-07-01', NULL, 1, 'MONTHS', '근로기준법 제52조제1항; 확인 2026-11-19', 'https://www.law.go.kr/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1029728241', 'codex-source-check-20261119');
    legal_rate_api.insert_period('SELECTIVE_SETTLEMENT_RND_MAX_MONTHS', DATE '2021-07-01', NULL, 3, 'MONTHS', '근로기준법 제52조제1항 괄호; 법률 제17862호 부칙 제1조제2항제2호; 확인 2026-11-19', 'https://www.law.go.kr/LSW/lsInfoP.do?efYd=20210406&lsiSeq=228171', 'codex-source-check-20261119');
    legal_rate_api.insert_period('SELECTIVE_RND_DAILY_REST_MIN_HOURS', DATE '2021-07-01', NULL, 11, 'HOURS', '근로기준법 제52조제2항제1호; 확인 2026-11-19', 'https://www.law.go.kr/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1029728241', 'codex-source-check-20261119');
END;
/
COMMIT;
