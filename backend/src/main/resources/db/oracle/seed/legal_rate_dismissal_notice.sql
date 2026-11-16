-- #18 법령 데이터. 확인일: 2026-11-16. 사람 승인 전 운영 적용 금지.
-- 선행: legal_rate_api 및 #18 V022의 두 항목 등록. 이 파일은 항목·테이블을 만들지 않는다.
-- 근로기준법 제26조 본문: 30일. 법률 제8372호(2007-04-11) 부칙 제1조 공포일 시행.
-- 2007-04-11 이전의 법적 의무 부존재를 뜻하지 않으며 그 이전은 이 시드의 지원 범위 밖이다.
-- 제26조 단서 제1호: 계속근로 3개월 미만. 법률 제16270호 부칙 제1조 단서에 따라 2019-01-15 시행.
-- 같은 부칙 제2조: 시행 후 근로계약 체결자부터 적용. effective_from만으로 구계약까지 적용하면 안 된다.
-- 적용 시작일·계약 적용례 출처: https://www.law.go.kr/LSW/lsInfoP.do?lsiSeq=206706
-- 현행 원문: https://www.law.go.kr/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1027156649
-- 확인하지 못한 구 제35조 예외를 0개월이나 6개월 같은 단일 값으로 대체하지 않는다.
SET DEFINE OFF
WHENEVER OSERROR EXIT FAILURE ROLLBACK
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM legal_rate_item
     WHERE (item_code = 'DISMISSAL_NOTICE_DAYS' AND unit = 'DAYS')
        OR (item_code = 'DISMISSAL_NOTICE_EXEMPT_SERVICE_MONTHS' AND unit = 'MONTHS');
    IF v_count <> 2 THEN
        RAISE_APPLICATION_ERROR(-20200, '#18 V022 legal_rate_item prerequisites missing');
    END IF;
    SELECT COUNT(*) INTO v_count FROM legal_rate
     WHERE item_code IN ('DISMISSAL_NOTICE_DAYS', 'DISMISSAL_NOTICE_EXEMPT_SERVICE_MONTHS');
    IF v_count <> 0 THEN
        RAISE_APPLICATION_ERROR(-20201, '#18 dismissal notice data exists; inspect before importing');
    END IF;
    legal_rate_api.insert_period(
        'DISMISSAL_NOTICE_DAYS', DATE '2007-04-11', NULL, 30, 'DAYS',
        '근로기준법 제26조 본문; 법률 제8372호 부칙 제1조; 확인 2026-11-16',
        'https://www.law.go.kr/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1027156649', 'codex-source-check-20261116');
    legal_rate_api.insert_period(
        'DISMISSAL_NOTICE_EXEMPT_SERVICE_MONTHS', DATE '2019-01-15', NULL, 3, 'MONTHS',
        '근로기준법 제26조 단서 제1호; 법률 제16270호 부칙 제1조 단서 및 제2조(시행 후 근로계약 체결자); 확인 2026-11-16',
        'https://www.law.go.kr/LSW/lsInfoP.do?lsiSeq=206706', 'codex-source-check-20261116');
END;
/
COMMIT;
