-- #143 법정휴가 기준값. 국가법령정보센터 원문 확인일 2026-11-25.
-- #143 구현의 V039 항목 등록 후 SQL*Plus에서 실행한다. 적용례는 docs/legal-rate-seed-sources.md 참조.
SET DEFINE OFF
WHENEVER OSERROR EXIT FAILURE ROLLBACK
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
BEGIN
    legal_rate_api.insert_period('MATERNITY_LEAVE_PAID_DAYS', DATE '2012-08-02', NULL, 60, 'DAYS',
        '근로기준법 제74조제4항', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-20261125');

    legal_rate_api.insert_period('MATERNITY_LEAVE_MULTIPLE_PAID_DAYS', DATE '2012-08-02', DATE '2014-07-01', 60, 'DAYS',
        '근로기준법 제74조제4항', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-20261125');
    legal_rate_api.insert_period('MATERNITY_LEAVE_MULTIPLE_PAID_DAYS', DATE '2014-07-01', NULL, 75, 'DAYS',
        '근로기준법 제74조제4항 및 법률 제12325호 부칙 제1조·제2조', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=001872&lsRvsGubun=all', 'codex-20261125');

    legal_rate_api.insert_period('SPOUSE_MATERNITY_LEAVE_PAID_DAYS', DATE '2013-02-02', DATE '2019-10-01', 3, 'DAYS',
        '남녀고용평등법 제18조의2제1항 및 법률 제11274호 부칙 제1조·제2조', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=000130', 'codex-20261125');
    legal_rate_api.insert_period('SPOUSE_MATERNITY_LEAVE_PAID_DAYS', DATE '2019-10-01', DATE '2025-02-23', 10, 'DAYS',
        '남녀고용평등법 제18조의2제1항 및 법률 제16558호 부칙 제3조', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=000130', 'codex-20261125');
    legal_rate_api.insert_period('SPOUSE_MATERNITY_LEAVE_PAID_DAYS', DATE '2025-02-23', NULL, 20, 'DAYS',
        '남녀고용평등법 제18조의2제1항 및 법률 제20521호 부칙 제2조', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=000130', 'codex-20261125');

    legal_rate_api.insert_period('INFERTILITY_TREATMENT_LEAVE_DAYS_PER_YEAR', DATE '2018-05-29', DATE '2025-02-23', 3, 'DAYS',
        '남녀고용평등법 제18조의3제1항 및 법률 제15109호 부칙', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=000130', 'codex-20261125');
    legal_rate_api.insert_period('INFERTILITY_TREATMENT_LEAVE_DAYS_PER_YEAR', DATE '2025-02-23', NULL, 6, 'DAYS',
        '남녀고용평등법 제18조의3제1항 및 법률 제20521호 부칙 제3조', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=000130', 'codex-20261125');

    legal_rate_api.insert_period('INFERTILITY_TREATMENT_LEAVE_PAID_DAYS', DATE '2018-05-29', DATE '2025-02-23', 1, 'DAYS',
        '남녀고용평등법 제18조의3제1항 및 법률 제15109호 부칙', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=000130', 'codex-20261125');
    legal_rate_api.insert_period('INFERTILITY_TREATMENT_LEAVE_PAID_DAYS', DATE '2025-02-23', DATE '2026-11-27', 2, 'DAYS',
        '남녀고용평등법 제18조의3제1항 및 법률 제20521호 부칙 제3조', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=000130', 'codex-20261125');
    legal_rate_api.insert_period('INFERTILITY_TREATMENT_LEAVE_PAID_DAYS', DATE '2026-11-27', NULL, 4, 'DAYS',
        '남녀고용평등법 제18조의3제1항 및 법률 제21700호 부칙 제1조·제2조', 'https://law.go.kr/LSW/lsRvsDocListP.do?lsId=000130', 'codex-20261125');
END;
/
COMMIT;
