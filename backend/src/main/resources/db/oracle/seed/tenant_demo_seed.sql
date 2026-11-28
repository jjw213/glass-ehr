-- 고객사 데모 시드 (#151). 개발·시연용이며 마이그레이션이 아니다(운영 DB에 넣지 않는다).
-- 실행 순서: V051 적용 후 이 파일. 고객사 격리 테스트용으로 고객사 2개와 각 고객사의 법인을 둔다.
--   - T001: V051 이 만든 기존 고객사(법인 C001). 여기서는 넣지 않는다.
--   - T002: 두 번째 데모 고객사와 그 법인 D001.
-- 모두 명백한 가짜 값이다(AGENTS.md 개인정보 규칙). 사업자등록번호도 실존하지 않는 형식 값이다.
-- 다시 넣으려면 D001 법인 행과 T002 고객사 행을 먼저 지운다.

INSERT INTO tenant (tenant_id, tenant_name, slug, status)
VALUES ('T002', '예시고객사 B', 'demo-b', 'ACTIVE');

INSERT INTO enter_company (enter_cd, enter_name, biz_reg_no, use_yn, sort_order, tenant_id)
VALUES ('D001', '예시고객사 B 법인(예시)', '0000000002', 'Y', 1, 'T002');
