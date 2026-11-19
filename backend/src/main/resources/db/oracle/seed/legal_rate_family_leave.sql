-- #37 육아휴직ㆍ육아기 근로시간 단축ㆍ가족돌봄휴직 법령 데이터.
-- 확인일: 2026-11-17. 선행: legal_rate_api 및 #37 V023의 8개 항목 등록.
-- 연혁 원문: https://law.go.kr/LSW/lsRvsDocListP.do?lsId=000130
-- 시행령 연혁 원문: https://law.go.kr/LSW/lsRvsDocListP.do?lsId=003933
SET DEFINE OFF
WHENEVER OSERROR EXIT FAILURE ROLLBACK
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM legal_rate_item
     WHERE (item_code = 'PARENTAL_LEAVE_CHILD_MAX_AGE' AND unit = 'YEARS')
        OR (item_code = 'PARENTAL_LEAVE_MIN_SERVICE_MONTHS' AND unit = 'MONTHS')
        OR (item_code = 'CHILDCARE_REDUCED_CHILD_MAX_AGE' AND unit = 'YEARS')
        OR (item_code = 'CHILDCARE_REDUCED_MIN_SERVICE_MONTHS' AND unit = 'MONTHS')
        OR (item_code = 'CHILDCARE_REDUCED_UNUSED_LEAVE_MULTIPLIER' AND unit = 'COUNT')
        OR (item_code = 'CHILDCARE_REDUCED_MIN_UNIT_MONTHS' AND unit = 'MONTHS')
        OR (item_code = 'FAMILY_CARE_LEAVE_MIN_SERVICE_MONTHS' AND unit = 'MONTHS')
        OR (item_code = 'FAMILY_CARE_LEAVE_MIN_UNIT_DAYS' AND unit = 'DAYS');
    IF v_count <> 8 THEN
        RAISE_APPLICATION_ERROR(-20200, '#37 V023 legal_rate_item prerequisites missing');
    END IF;

    SELECT COUNT(*) INTO v_count FROM legal_rate WHERE item_code IN (
        'PARENTAL_LEAVE_CHILD_MAX_AGE', 'PARENTAL_LEAVE_MIN_SERVICE_MONTHS',
        'CHILDCARE_REDUCED_CHILD_MAX_AGE', 'CHILDCARE_REDUCED_MIN_SERVICE_MONTHS',
        'CHILDCARE_REDUCED_UNUSED_LEAVE_MULTIPLIER', 'CHILDCARE_REDUCED_MIN_UNIT_MONTHS',
        'FAMILY_CARE_LEAVE_MIN_SERVICE_MONTHS', 'FAMILY_CARE_LEAVE_MIN_UNIT_DAYS');
    IF v_count <> 0 THEN
        RAISE_APPLICATION_ERROR(-20201, '#37 family leave data exists; inspect before importing');
    END IF;

    legal_rate_api.insert_period('PARENTAL_LEAVE_CHILD_MAX_AGE', DATE '2010-02-04', DATE '2014-01-14', 6, 'YEARS', '남녀고용평등법 제19조제1항; 법률 제9932호 부칙 제1조ㆍ제3조; 만 나이 이하 상한; 확인 2026-11-17', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=000130', 'codex-source-check-20261117');
    legal_rate_api.insert_period('PARENTAL_LEAVE_CHILD_MAX_AGE', DATE '2014-01-14', NULL, 8, 'YEARS', '남녀고용평등법 제19조제1항; 법률 제12244호 부칙 제1조ㆍ제3조; 만 나이 이하 상한; 확인 2026-11-17', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=000130', 'codex-source-check-20261117');
    legal_rate_api.insert_period('PARENTAL_LEAVE_MIN_SERVICE_MONTHS', DATE '2008-06-22', DATE '2018-05-29', 12, 'MONTHS', '남녀고용평등법 제19조제1항 단서ㆍ구 시행령 제10조제1호; 확인 2026-11-17', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=003933', 'codex-source-check-20261117');
    legal_rate_api.insert_period('PARENTAL_LEAVE_MIN_SERVICE_MONTHS', DATE '2018-05-29', NULL, 6, 'MONTHS', '남녀고용평등법 제19조제1항 단서ㆍ시행령 제10조; 대통령령 제28910호 부칙 제1조; 이 기간 미만이면 거부 가능; 확인 2026-11-17', 'https://law.go.kr/LSW/lsInfoP.do?lsiSeq=203581', 'codex-source-check-20261117');

    legal_rate_api.insert_period('CHILDCARE_REDUCED_CHILD_MAX_AGE', DATE '2010-02-04', DATE '2014-01-14', 6, 'YEARS', '남녀고용평등법 제19조의2제1항(당시 제19조제1항 대상 준용); 법률 제9932호; 확인 2026-11-17', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=000130', 'codex-source-check-20261117');
    legal_rate_api.insert_period('CHILDCARE_REDUCED_CHILD_MAX_AGE', DATE '2014-01-14', DATE '2025-02-23', 8, 'YEARS', '남녀고용평등법 제19조의2제1항; 법률 제12244호ㆍ제16558호; 확인 2026-11-17', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=000130', 'codex-source-check-20261117');
    legal_rate_api.insert_period('CHILDCARE_REDUCED_CHILD_MAX_AGE', DATE '2025-02-23', NULL, 12, 'YEARS', '남녀고용평등법 제19조의2제1항; 법률 제20521호 부칙 제1조ㆍ제3조; 만 나이 이하 상한; 확인 2026-11-17', 'https://law.go.kr/LSW/lsInfoP.do?lsiSeq=265999&viewCls=lsRvsDocInfoR', 'codex-source-check-20261117');
    legal_rate_api.insert_period('CHILDCARE_REDUCED_MIN_SERVICE_MONTHS', DATE '2008-06-22', DATE '2018-05-29', 12, 'MONTHS', '남녀고용평등법 제19조의2제1항 단서ㆍ구 시행령 제15조의2; 확인 2026-11-17', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=003933', 'codex-source-check-20261117');
    legal_rate_api.insert_period('CHILDCARE_REDUCED_MIN_SERVICE_MONTHS', DATE '2018-05-29', NULL, 6, 'MONTHS', '남녀고용평등법 제19조의2제1항 단서ㆍ시행령 제15조의2제1호; 대통령령 제28910호; 확인 2026-11-17', 'https://www.law.go.kr/LSW/lsSideInfoP.do?docCls=jo&joBrNo=02&joNo=0015&lsiSeq=288719&urlMode=lsScJoRltInfoR', 'codex-source-check-20261117');
    legal_rate_api.insert_period('CHILDCARE_REDUCED_UNUSED_LEAVE_MULTIPLIER', DATE '2019-10-01', DATE '2025-02-23', 1, 'COUNT', '남녀고용평등법 제19조의2제4항 단서; 법률 제16558호 부칙 제1조ㆍ제3조; 미사용 육아휴직 기간의 1배 가산; 확인 2026-11-17', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=000130', 'codex-source-check-20261117');
    legal_rate_api.insert_period('CHILDCARE_REDUCED_UNUSED_LEAVE_MULTIPLIER', DATE '2025-02-23', NULL, 2, 'COUNT', '남녀고용평등법 제19조의2제4항 단서; 법률 제20521호 부칙 제1조ㆍ제3조; 미사용 육아휴직 기간의 2배 가산; 확인 2026-11-17', 'https://law.go.kr/LSW/lsInfoP.do?lsiSeq=265999&viewCls=lsRvsDocInfoR', 'codex-source-check-20261117');
    legal_rate_api.insert_period('CHILDCARE_REDUCED_MIN_UNIT_MONTHS', DATE '2019-10-01', DATE '2025-02-23', 3, 'MONTHS', '남녀고용평등법 제19조의4제2항; 법률 제16558호; 1회 최소기간; 확인 2026-11-17', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=000130', 'codex-source-check-20261117');
    legal_rate_api.insert_period('CHILDCARE_REDUCED_MIN_UNIT_MONTHS', DATE '2025-02-23', NULL, 1, 'MONTHS', '남녀고용평등법 제19조의4제2항; 법률 제20521호 부칙 제1조ㆍ제4조; 1회 최소기간; 확인 2026-11-17', 'https://law.go.kr/LSW/lsInfoP.do?lsiSeq=265999&viewCls=lsRvsDocInfoR', 'codex-source-check-20261117');

    legal_rate_api.insert_period('FAMILY_CARE_LEAVE_MIN_SERVICE_MONTHS', DATE '2013-02-02', DATE '2018-05-29', 12, 'MONTHS', '남녀고용평등법 제22조의2제1항 단서ㆍ구 시행령 제16조의3제1호; 법률 제11274호 적용; 확인 2026-11-17', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=003933', 'codex-source-check-20261117');
    legal_rate_api.insert_period('FAMILY_CARE_LEAVE_MIN_SERVICE_MONTHS', DATE '2018-05-29', NULL, 6, 'MONTHS', '남녀고용평등법 제22조의2제1항 단서ㆍ시행령 제16조의3제1항제1호; 대통령령 제28910호; 확인 2026-11-17', 'https://law.go.kr/lsLinkCommonInfo.do?chrClsCd=010202&lspttninfSeq=153389', 'codex-source-check-20261117');
    legal_rate_api.insert_period('FAMILY_CARE_LEAVE_MIN_UNIT_DAYS', DATE '2013-02-02', NULL, 30, 'DAYS', '남녀고용평등법 제22조의2제4항제1호(종전 제2항); 1회 최소기간; 법률 제11274호; 확인 2026-11-17', 'https://www.law.go.kr/lsLinkCommonInfo.do?lsJoLnkSeq=1022178635', 'codex-source-check-20261117');
END;
/
COMMIT;
