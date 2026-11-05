-- Glass HR 전용 Oracle 사용자 만들기
-- SYSTEM(또는 관리자 계정)으로 PDB 에 접속해서 실행하세요.
--   23ai Free : sql system@localhost:1521/FREEPDB1
--   21c XE    : sql system@localhost:1521/XEPDB1
-- 비밀번호는 반드시 바꾸고, server/.env 의 ORACLE_PASSWORD 와 맞추세요.

CREATE USER GLASSHR IDENTIFIED BY "change-me";
GRANT CREATE SESSION, CREATE TABLE TO GLASSHR;
ALTER USER GLASSHR QUOTA UNLIMITED ON USERS;
