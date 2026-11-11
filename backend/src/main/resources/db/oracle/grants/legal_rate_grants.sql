-- legal_rate 권한 스크립트 (#7). 설계: docs/legal-rate-design.md 3절 4항
-- 마이그레이션이 아니다. 스키마 소유(배포) 계정으로 V001~V004 적용 후 DBA/배포 담당자가 SQL*Plus/SQLcl 로 실행한다.
-- 계정 이름은 실행 시 치환 변수로 받는다(저장소에 실제 계정명·비밀번호를 쓰지 않는다).
--   &&app_user : 애플리케이션 접속 계정(조회 + 쓰기 패키지 실행만)
--
-- 원칙
--   - 앱 계정은 legal_rate / legal_rate_item 에 INSERT·UPDATE·DELETE 권한을 갖지 않는다.
--   - 값 쓰기는 legal_rate_api(AUTHID DEFINER) 실행 권한으로만 한다 → 겹침 검사·잠금을 우회할 수 없다.
--   - 항목코드 등록(legal_rate_item INSERT)은 마이그레이션(배포 계정)으로만 한다.
--   - 앱 SQL은 스키마 이름 없이 테이블을 부른다. 앱 계정에서 동의어를 만들거나
--     접속 시 ALTER SESSION SET CURRENT_SCHEMA 로 소유 스키마를 지정한다(운영 방식 결정 시 확정).

-- 혹시 직접 부여된 DML 권한이 있으면 회수한다(없으면 ORA-01927 은 무시해도 된다).
REVOKE INSERT, UPDATE, DELETE ON legal_rate FROM &&app_user;
REVOKE INSERT, UPDATE, DELETE ON legal_rate_item FROM &&app_user;

GRANT SELECT ON legal_rate TO &&app_user;
GRANT SELECT ON legal_rate_item TO &&app_user;
GRANT EXECUTE ON legal_rate_api TO &&app_user;
