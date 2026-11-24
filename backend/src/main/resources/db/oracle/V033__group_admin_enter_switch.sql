-- 여러 법인: 그룹 관리자 법인 전환 (#120). 설계: docs/tenant-design.md 4.3·4.4
--
-- 이 마이그레이션이 하는 일
--   1. 전체 공통 역할 GROUP_ADMIN(그룹 관리자)과 권한 ENTER_SWITCH(법인 전환)를 추가하고 GROUP_ADMIN 에만 연결한다.
--      역할 부여는 주체 법인의 app_user_role 로 한다(자동 역할이 아니라 직접 부여).
--   2. login_access_log 에 전환 대상 법인 target_enter_cd(NULL 허용, FK 없음 — 접속기록은 법인 행과 상관없이 남긴다)를 추가하고
--      event_code 에 ENTER_SWITCH 를 더한다. ENTER_SWITCH 일 때만 target_enter_cd 가 있다.
--      enter_cd 는 주체 법인(로그인한 법인)이다.
-- 법인 마스터 관리 권한(ENTER_COMPANY_MANAGE)은 관리 화면 이슈에서 추가한다.
-- 법령 수치 계산 없음. 개인정보 보호법 제29조(안전조치의무)·개인정보의 안전성 확보조치 기준(접속기록 보관).
-- JPA 엔티티: com.glassehr.auth.LoginAccessLog (ddl-auto: validate)

INSERT INTO app_role (role_code, role_name, sort_order) VALUES ('GROUP_ADMIN', '그룹 관리자(법인 전환)', 60);
INSERT INTO app_permission (perm_code, perm_name) VALUES ('ENTER_SWITCH', '법인 전환');
INSERT INTO app_role_permission (role_code, perm_code) VALUES ('GROUP_ADMIN', 'ENTER_SWITCH');

ALTER TABLE login_access_log ADD (target_enter_cd VARCHAR2(10 CHAR));
ALTER TABLE login_access_log DROP CONSTRAINT ck_login_log_event;
ALTER TABLE login_access_log ADD CONSTRAINT ck_login_log_event CHECK (event_code IN ('LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGOUT', 'ENTER_SWITCH'));
ALTER TABLE login_access_log ADD CONSTRAINT ck_login_log_target CHECK (
    (event_code = 'ENTER_SWITCH' AND target_enter_cd IS NOT NULL
        AND REGEXP_LIKE(target_enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(target_enter_cd) >= 2)
    OR (event_code <> 'ENTER_SWITCH' AND target_enter_cd IS NULL));
