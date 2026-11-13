-- #17 사용자 요청에 따라 공식 원문 확인 후 작성(2026-11-13).
-- V011 적용 후 실행. 항목 등록과 값 입력을 분리한다.
-- 제4조 제1항 본문의 일반 상한이며 단서 예외 판단은 별도 처리한다.
-- 법률 제8074호 부칙 제1항: 2007-07-01 시행.
-- 부칙 제2항: 시행 후 체결/갱신/연장하는 계약부터 적용.
-- 시행 전부터 이어진 계약의 기산점은 이 시드로 소급 확정하지 않는다.
SET DEFINE OFF
WHENEVER OSERROR EXIT FAILURE ROLLBACK
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM legal_rate WHERE item_code = 'FIXED_TERM_MAX_USE_YEARS';
    IF v_count <> 0 THEN
        RAISE_APPLICATION_ERROR(-20201, 'FIXED_TERM_MAX_USE_YEARS already exists; inspect before importing');
    END IF;
    legal_rate_api.insert_period(
        'FIXED_TERM_MAX_USE_YEARS', DATE '2007-07-01', NULL, 2, 'YEARS',
        '기간제 및 단시간근로자 보호 등에 관한 법률 제4조 제1항 본문; 법률 제8074호 부칙 제1항 및 제2항',
        'https://law.go.kr/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1010523569', 'codex-source-check-20261113');
END;
/
COMMIT;
