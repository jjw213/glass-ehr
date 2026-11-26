-- #158 단시간ㆍ초단시간근로자 기준값. 원문 확인: 2026-11-26.
-- 15시간 기준은 1997-03-27 시행령 제정 때부터 존재하며, 현행법은 법 제18조제3항에 둔다.
-- 항목 등록 마이그레이션 및 legal_rate_api 적용 후 별도 실행.
SET DEFINE OFF
WHENEVER OSERROR EXIT FAILURE ROLLBACK
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM legal_rate
     WHERE item_code IN ('SHORT_TIME_WORKER_WEEKLY_HOURS_THRESHOLD', 'SHORT_TIME_WORKER_AVERAGE_WEEKS');
    IF v_count <> 0 THEN
        RAISE_APPLICATION_ERROR(-20258, 'part-time-worker legal rates already exist; inspect before importing');
    END IF;

    legal_rate_api.insert_period('SHORT_TIME_WORKER_WEEKLY_HOURS_THRESHOLD', DATE '1997-03-27', NULL, 15, 'HOURS',
        '근로기준법 제18조제3항(구법 제25조제3항)ㆍ구 근로기준법 시행령 제9조제2항; 15시간 미만은 제55조ㆍ제60조 미적용; 대통령령 제15320호; 확인 2026-11-26',
        'https://www.law.go.kr/LSW/lsRvsRsnListP.do?chrClsCd=010202&lsId=003058&lsRvsGubun=all', 'codex-source-check-20261126');
    legal_rate_api.insert_period('SHORT_TIME_WORKER_AVERAGE_WEEKS', DATE '1997-03-27', NULL, 4, 'WEEKS',
        '근로기준법 제18조제3항(구법 제25조제3항)ㆍ구 근로기준법 시행령 제9조제2항; 4주 미만 근로 시 그 기간; 대통령령 제15320호; 확인 2026-11-26',
        'https://www.law.go.kr/LSW/lsRvsRsnListP.do?chrClsCd=010202&lsId=003058&lsRvsGubun=all', 'codex-source-check-20261126');
END;
/
COMMIT;
