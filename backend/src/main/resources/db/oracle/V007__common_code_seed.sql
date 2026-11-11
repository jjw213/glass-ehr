-- 공통코드 초기 시드 (#11). 테이블: V006__common_code.sql
-- 회사마다 다른 값(직급 체계, 근무지 등)은 일반적인 예시이며, 인사담당자가 코드 관리 화면에서 고치거나 사용 중지한다.
-- 그룹코드는 로직이 참조하므로 com.glassehr.code.CodeGroups 와 같게 유지한다(CommonCodeSeedScriptTest 가 검사).
-- 근무지 이름은 가짜 예시다.

INSERT INTO common_code_group (group_code, group_name, description, sort_order) VALUES
    ('JOB_GRADE', '직급', '사원~이사 등 직위 등급', 10);
INSERT INTO common_code_group (group_code, group_name, description, sort_order) VALUES
    ('JOB_TITLE', '직책', '팀장·본부장 등 맡은 보직', 20);
INSERT INTO common_code_group (group_code, group_name, description, sort_order) VALUES
    ('JOB_FAMILY', '직군', '경영지원·개발·영업 등 직무 묶음', 30);
INSERT INTO common_code_group (group_code, group_name, description, sort_order) VALUES
    ('EMPLOYMENT_TYPE', '고용형태', '기간제 및 단시간근로자 보호 등에 관한 법률 제2조의 구분(기간제·단시간)과 기간의 정함이 없는 정규직', 40);
INSERT INTO common_code_group (group_code, group_name, description, sort_order) VALUES
    ('WORK_LOCATION', '근무지', '근로자가 실제로 근무하는 장소', 50);
INSERT INTO common_code_group (group_code, group_name, description, sort_order) VALUES
    ('RETIREMENT_REASON', '퇴직사유', '회사 내부 퇴직사유 구분. 4대보험 자격상실 사유코드와의 대응은 급여·4대보험 이슈에서 정한다', 60);

-- 직급
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('JOB_GRADE', 'STAFF', '사원', 10);
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('JOB_GRADE', 'SENIOR_STAFF', '주임', 20);
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('JOB_GRADE', 'ASSISTANT_MANAGER', '대리', 30);
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('JOB_GRADE', 'MANAGER', '과장', 40);
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('JOB_GRADE', 'DEPUTY_GENERAL_MANAGER', '차장', 50);
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('JOB_GRADE', 'GENERAL_MANAGER', '부장', 60);
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('JOB_GRADE', 'DIRECTOR', '이사', 70);

-- 직책
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('JOB_TITLE', 'MEMBER', '팀원', 10);
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('JOB_TITLE', 'PART_LEADER', '파트장', 20);
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('JOB_TITLE', 'TEAM_LEADER', '팀장', 30);
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('JOB_TITLE', 'DIVISION_HEAD', '본부장', 40);
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('JOB_TITLE', 'CEO', '대표이사', 50);

-- 직군
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('JOB_FAMILY', 'MANAGEMENT', '경영지원', 10);
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('JOB_FAMILY', 'ENGINEERING', '개발', 20);
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('JOB_FAMILY', 'RESEARCH', '연구', 30);
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('JOB_FAMILY', 'SALES', '영업', 40);
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('JOB_FAMILY', 'PRODUCTION', '생산', 50);

-- 고용형태: 기간제 및 단시간근로자 보호 등에 관한 법률 제2조 제1호(기간제근로자)·제2호(단시간근로자)의 구분을 따른다.
-- 기간제이면서 단시간인 근로자처럼 두 구분이 겹치는 경우의 표현 방식은 사원 등록 이슈에서 정한다(docs/common-code-design.md).
INSERT INTO common_code (group_code, code, code_name, description, sort_order) VALUES
    ('EMPLOYMENT_TYPE', 'REGULAR', '정규직', '기간의 정함이 없는 근로계약, 통상 근로자', 10);
INSERT INTO common_code (group_code, code, code_name, description, sort_order) VALUES
    ('EMPLOYMENT_TYPE', 'FIXED_TERM', '기간제', '기간제 및 단시간근로자 보호 등에 관한 법률 제2조 제1호 기간제근로자', 20);
INSERT INTO common_code (group_code, code, code_name, description, sort_order) VALUES
    ('EMPLOYMENT_TYPE', 'PART_TIME', '단시간', '기간제 및 단시간근로자 보호 등에 관한 법률 제2조 제2호 단시간근로자', 30);

-- 근무지 (가짜 예시)
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('WORK_LOCATION', 'HQ', '본사', 10);
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('WORK_LOCATION', 'BRANCH_A', 'A지사', 20);
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('WORK_LOCATION', 'FACTORY_A', 'A공장', 30);

-- 퇴직사유
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('RETIREMENT_REASON', 'VOLUNTARY', '자발적 퇴사(개인 사정)', 10);
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('RETIREMENT_REASON', 'CONTRACT_EXPIRED', '계약기간 만료', 20);
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('RETIREMENT_REASON', 'RETIREMENT_AGE', '정년퇴직', 30);
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('RETIREMENT_REASON', 'RECOMMENDED_RESIGNATION', '권고사직', 40);
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('RETIREMENT_REASON', 'DISMISSAL', '해고', 50);
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('RETIREMENT_REASON', 'TRANSFER_AFFILIATE', '전적(계열사 이동)', 60);
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('RETIREMENT_REASON', 'DEATH', '사망', 70);
INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES ('RETIREMENT_REASON', 'OTHER', '기타', 99);
