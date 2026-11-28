-- 여러 법인: 새 법인 만들기와 기준 데이터 복사 (#130). 설계: docs/tenant-design.md 6장 7번·결정 4
--
-- 이 마이그레이션이 하는 일
--   전체 공통 권한 ENTER_COMPANY_MANAGE(법인 관리: 목록·추가·사용 중지)를 추가하고 GROUP_ADMIN 에만 연결한다
--   (V033 주석에서 관리 화면 이슈로 미뤄 둔 권한).
-- 테이블 변경 없음. 새 법인 행·기준 데이터 복사는 com.glassehr.tenant.EnterCompanyAdminService 가 한 트랜잭션으로 한다.
-- 법령 수치 계산 없음.

INSERT INTO app_permission (perm_code, perm_name) VALUES ('ENTER_COMPANY_MANAGE', '법인 관리');
INSERT INTO app_role_permission (role_code, perm_code) VALUES ('GROUP_ADMIN', 'ENTER_COMPANY_MANAGE');
