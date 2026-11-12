-- 사원 데모 시드 (#14). 개발·시연용이며 마이그레이션이 아니다(운영 DB에 넣지 않는다).
-- 실행 순서: V001~V010 적용 후 이 파일. 다시 넣으려면 DEMO_ 부서와 예시사원을 먼저 지운다.
--
-- 모두 명백한 가짜 값이다(AGENTS.md 개인정보 규칙).
--   - 성명: '예시사원NN' (실존 인물 이름을 쓰지 않음)
--   - 주소: '가상시 예시구 샘플로 N' (존재하지 않는 행정구역)
--   - 주민등록번호: 넣지 않는다(emp_rrn 은 애플리케이션이 암호화해야 하므로 SQL 시드로 만들 수 없고,
--     실제 주민번호 형식 값도 쓰지 않는다). 연락처·계좌번호 컬럼은 명부 기재사항이 아니라 없다.
--   - 1주 소정근로시간(분)은 예시 근로계약 값이다(법령 기준값 아님).
-- 검사: EmployeeSchemaScriptTest(시드 부분)

-- 부서 (DEMO_ 코드)
INSERT INTO org_dept (dept_code, valid_from) VALUES ('DEMO_CEO', DATE '2018-01-01');
INSERT INTO org_dept (dept_code, valid_from) VALUES ('DEMO_MGMT', DATE '2018-01-01');
INSERT INTO org_dept (dept_code, valid_from) VALUES ('DEMO_HR', DATE '2018-01-01');
INSERT INTO org_dept (dept_code, valid_from) VALUES ('DEMO_FIN', DATE '2018-01-01');
INSERT INTO org_dept (dept_code, valid_from) VALUES ('DEMO_DEV', DATE '2018-01-01');
INSERT INTO org_dept (dept_code, valid_from) VALUES ('DEMO_DEV1', DATE '2018-01-01');
INSERT INTO org_dept (dept_code, valid_from) VALUES ('DEMO_DEV2', DATE '2018-01-01');
INSERT INTO org_dept (dept_code, valid_from) VALUES ('DEMO_SALES', DATE '2018-01-01');
INSERT INTO org_dept (dept_code, valid_from) VALUES ('DEMO_PROD', DATE '2018-01-01');

INSERT INTO org_dept_hist (dept_id, valid_from, dept_name, parent_dept_id, sort_order, change_type)
    SELECT dept_id, DATE '2018-01-01', '대표이사실(예시)', NULL, 10, 'CREATE' FROM org_dept WHERE dept_code = 'DEMO_CEO';
INSERT INTO org_dept_hist (dept_id, valid_from, dept_name, parent_dept_id, sort_order, change_type)
    SELECT dept_id, DATE '2018-01-01', '경영지원본부(예시)', NULL, 20, 'CREATE' FROM org_dept WHERE dept_code = 'DEMO_MGMT';
INSERT INTO org_dept_hist (dept_id, valid_from, dept_name, parent_dept_id, sort_order, change_type)
    SELECT d.dept_id, DATE '2018-01-01', '인사팀(예시)', p.dept_id, 10, 'CREATE'
    FROM org_dept d, org_dept p WHERE d.dept_code = 'DEMO_HR' AND p.dept_code = 'DEMO_MGMT';
INSERT INTO org_dept_hist (dept_id, valid_from, dept_name, parent_dept_id, sort_order, change_type)
    SELECT d.dept_id, DATE '2018-01-01', '재무팀(예시)', p.dept_id, 20, 'CREATE'
    FROM org_dept d, org_dept p WHERE d.dept_code = 'DEMO_FIN' AND p.dept_code = 'DEMO_MGMT';
INSERT INTO org_dept_hist (dept_id, valid_from, dept_name, parent_dept_id, sort_order, change_type)
    SELECT dept_id, DATE '2018-01-01', '개발본부(예시)', NULL, 30, 'CREATE' FROM org_dept WHERE dept_code = 'DEMO_DEV';
INSERT INTO org_dept_hist (dept_id, valid_from, dept_name, parent_dept_id, sort_order, change_type)
    SELECT d.dept_id, DATE '2018-01-01', '개발1팀(예시)', p.dept_id, 10, 'CREATE'
    FROM org_dept d, org_dept p WHERE d.dept_code = 'DEMO_DEV1' AND p.dept_code = 'DEMO_DEV';
INSERT INTO org_dept_hist (dept_id, valid_from, dept_name, parent_dept_id, sort_order, change_type)
    SELECT d.dept_id, DATE '2018-01-01', '개발2팀(예시)', p.dept_id, 20, 'CREATE'
    FROM org_dept d, org_dept p WHERE d.dept_code = 'DEMO_DEV2' AND p.dept_code = 'DEMO_DEV';
INSERT INTO org_dept_hist (dept_id, valid_from, dept_name, parent_dept_id, sort_order, change_type)
    SELECT dept_id, DATE '2018-01-01', '영업팀(예시)', NULL, 40, 'CREATE' FROM org_dept WHERE dept_code = 'DEMO_SALES';
INSERT INTO org_dept_hist (dept_id, valid_from, dept_name, parent_dept_id, sort_order, change_type)
    SELECT dept_id, DATE '2018-01-01', '생산팀(예시)', NULL, 50, 'CREATE' FROM org_dept WHERE dept_code = 'DEMO_PROD';

-- 사원 32명. 컬럼 순서:
-- emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code,
-- hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code,
-- current_dept_id, job_grade_code, job_title_code
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2018-001', 'S01', '예시사원01', 'M', DATE '1975-03-14', '가상시 예시구 샘플로 1', '(예시) 가상상사 20년', '경영 총괄', 'MANAGEMENT', DATE '2018-01-02', NULL, NULL, 'REGULAR', 2400, 'HQ', dept_id, 'DIRECTOR', 'CEO' FROM org_dept WHERE dept_code = 'DEMO_CEO';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2018-002', 'S01', '예시사원02', 'F', DATE '1980-07-21', '가상시 예시구 샘플로 2', '(예시) 가상물산 15년', '경영지원 총괄', 'MANAGEMENT', DATE '2018-03-05', NULL, NULL, 'REGULAR', 2400, 'HQ', dept_id, 'GENERAL_MANAGER', 'DIVISION_HEAD' FROM org_dept WHERE dept_code = 'DEMO_MGMT';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2019-001', 'S01', '예시사원03', 'F', DATE '1985-11-02', '가상시 예시구 샘플로 3', '(예시) 가상기업 인사 8년', '인사 기획·운영', 'MANAGEMENT', DATE '2019-02-11', NULL, NULL, 'REGULAR', 2400, 'HQ', dept_id, 'MANAGER', 'TEAM_LEADER' FROM org_dept WHERE dept_code = 'DEMO_HR';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2019-002', 'S01', '예시사원04', 'M', DATE '1988-05-09', '가상시 예시구 샘플로 4', NULL, '급여·4대보험 실무', 'MANAGEMENT', DATE '2019-06-03', NULL, NULL, 'REGULAR', 2400, 'HQ', dept_id, 'ASSISTANT_MANAGER', 'MEMBER' FROM org_dept WHERE dept_code = 'DEMO_HR';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2019-003', 'S01', '예시사원05', 'F', DATE '1990-09-30', '가상시 예시구 샘플로 5', '(예시) 가상회계법인 5년', '재무·회계', 'MANAGEMENT', DATE '2019-09-02', NULL, NULL, 'REGULAR', 2400, 'HQ', dept_id, 'MANAGER', 'TEAM_LEADER' FROM org_dept WHERE dept_code = 'DEMO_FIN';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2020-001', 'S01', '예시사원06', 'M', DATE '1991-01-17', '가상시 견본구 테스트길 6', NULL, '자금·세무', 'MANAGEMENT', DATE '2020-01-06', NULL, NULL, 'REGULAR', 2400, 'HQ', dept_id, 'ASSISTANT_MANAGER', 'MEMBER' FROM org_dept WHERE dept_code = 'DEMO_FIN';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2020-002', 'S01', '예시사원07', 'F', DATE '1982-04-25', '가상시 견본구 테스트길 7', '(예시) 가상소프트 12년', '개발 조직 총괄', 'ENGINEERING', DATE '2020-03-02', NULL, NULL, 'REGULAR', 2400, 'HQ', dept_id, 'DEPUTY_GENERAL_MANAGER', 'DIVISION_HEAD' FROM org_dept WHERE dept_code = 'DEMO_DEV';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2020-003', 'S01', '예시사원08', 'M', DATE '1987-08-08', '가상시 견본구 테스트길 8', NULL, '백엔드 개발 리드', 'ENGINEERING', DATE '2020-05-11', NULL, NULL, 'REGULAR', 2400, 'HQ', dept_id, 'MANAGER', 'TEAM_LEADER' FROM org_dept WHERE dept_code = 'DEMO_DEV1';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2020-004', 'S01', '예시사원09', 'M', DATE '1993-12-12', '가상시 견본구 테스트길 9', NULL, '백엔드 개발', 'ENGINEERING', DATE '2020-07-01', NULL, NULL, 'REGULAR', 2400, 'HQ', dept_id, 'SENIOR_STAFF', 'MEMBER' FROM org_dept WHERE dept_code = 'DEMO_DEV1';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2021-001', 'S01', '예시사원10', 'F', DATE '1994-02-03', '가상시 견본구 테스트길 10', NULL, '프론트엔드 개발', 'ENGINEERING', DATE '2021-01-04', NULL, NULL, 'REGULAR', 2400, 'HQ', dept_id, 'SENIOR_STAFF', 'MEMBER' FROM org_dept WHERE dept_code = 'DEMO_DEV1';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2021-002', 'S01', '예시사원11', 'M', DATE '1995-06-19', '가상시 견본구 테스트길 11', NULL, '백엔드 개발', 'ENGINEERING', DATE '2021-03-02', NULL, NULL, 'REGULAR', 2400, 'HQ', dept_id, 'STAFF', 'MEMBER' FROM org_dept WHERE dept_code = 'DEMO_DEV1';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2021-003', 'S01', '예시사원12', 'F', DATE '1989-10-10', '가상시 견본구 테스트길 12', '(예시) 가상데이터 6년', '데이터 플랫폼 리드', 'ENGINEERING', DATE '2021-04-01', NULL, NULL, 'REGULAR', 2400, 'HQ', dept_id, 'MANAGER', 'TEAM_LEADER' FROM org_dept WHERE dept_code = 'DEMO_DEV2';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2021-004', 'S01', '예시사원13', 'M', DATE '1996-03-28', '가상시 견본구 테스트길 13', NULL, '데이터 엔지니어링', 'ENGINEERING', DATE '2021-07-05', NULL, NULL, 'REGULAR', 2400, 'HQ', dept_id, 'STAFF', 'MEMBER' FROM org_dept WHERE dept_code = 'DEMO_DEV2';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2021-005', 'S01', '예시사원14', 'F', DATE '1997-07-07', '가상시 견본구 테스트길 14', NULL, '연구 개발', 'RESEARCH', DATE '2021-09-01', NULL, NULL, 'REGULAR', 2400, 'HQ', dept_id, 'STAFF', 'MEMBER' FROM org_dept WHERE dept_code = 'DEMO_DEV2';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2022-001', 'S01', '예시사원15', 'M', DATE '1990-05-05', '가상시 모형구 데모대로 15', '(예시) 가상유통 7년', '영업 관리', 'SALES', DATE '2022-01-03', NULL, NULL, 'REGULAR', 2400, 'BRANCH_A', dept_id, 'MANAGER', 'TEAM_LEADER' FROM org_dept WHERE dept_code = 'DEMO_SALES';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2022-002', 'S01', '예시사원16', 'F', DATE '1993-08-15', '가상시 모형구 데모대로 16', NULL, '법인 영업', 'SALES', DATE '2022-02-07', NULL, NULL, 'REGULAR', 2400, 'BRANCH_A', dept_id, 'ASSISTANT_MANAGER', 'MEMBER' FROM org_dept WHERE dept_code = 'DEMO_SALES';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2022-003', 'S01', '예시사원17', 'M', DATE '1998-01-20', '가상시 모형구 데모대로 17', NULL, '영업 지원', 'SALES', DATE '2022-03-02', NULL, NULL, 'REGULAR', 2400, 'BRANCH_A', dept_id, 'STAFF', 'MEMBER' FROM org_dept WHERE dept_code = 'DEMO_SALES';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2022-004', 'S01', '예시사원18', 'F', DATE '1986-11-11', '가상시 모형구 데모대로 18', '(예시) 가상공업 10년', '생산 관리', 'PRODUCTION', DATE '2022-06-01', NULL, NULL, 'REGULAR', 2400, 'FACTORY_A', dept_id, 'MANAGER', 'TEAM_LEADER' FROM org_dept WHERE dept_code = 'DEMO_PROD';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2023-001', 'S01', '예시사원19', 'M', DATE '1992-02-22', '가상시 모형구 데모대로 19', NULL, '설비 운영', 'PRODUCTION', DATE '2023-01-02', NULL, NULL, 'REGULAR', 2400, 'FACTORY_A', dept_id, 'SENIOR_STAFF', 'PART_LEADER' FROM org_dept WHERE dept_code = 'DEMO_PROD';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2023-002', 'S01', '예시사원20', 'F', DATE '1999-04-04', '가상시 모형구 데모대로 20', NULL, '품질 검사', 'PRODUCTION', DATE '2023-03-06', NULL, NULL, 'REGULAR', 2400, 'FACTORY_A', dept_id, 'STAFF', 'MEMBER' FROM org_dept WHERE dept_code = 'DEMO_PROD';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2023-003', 'S01', '예시사원21', 'M', DATE '1997-09-09', '가상시 모형구 데모대로 21', NULL, '생산 라인 작업', 'PRODUCTION', DATE '2023-05-02', NULL, NULL, 'REGULAR', 2400, 'FACTORY_A', dept_id, 'STAFF', 'MEMBER' FROM org_dept WHERE dept_code = 'DEMO_PROD';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2023-004', 'S01', '예시사원22', 'F', DATE '2000-12-24', '가상시 모형구 데모대로 22', NULL, '채용·교육 운영', 'MANAGEMENT', DATE '2023-08-01', NULL, NULL, 'REGULAR', 2400, 'HQ', dept_id, 'STAFF', 'MEMBER' FROM org_dept WHERE dept_code = 'DEMO_HR';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2024-001', 'S01', '예시사원23', 'M', DATE '1994-06-06', '가상시 표본구 연습로 23', NULL, '인프라 운영', 'ENGINEERING', DATE '2024-01-02', NULL, NULL, 'REGULAR', 2400, 'HQ', dept_id, 'ASSISTANT_MANAGER', 'MEMBER' FROM org_dept WHERE dept_code = 'DEMO_DEV1';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2024-002', 'S01', '예시사원24', 'F', DATE '1998-10-01', '가상시 표본구 연습로 24', NULL, '서비스 기획', 'ENGINEERING', DATE '2024-02-05', NULL, NULL, 'REGULAR', 2400, 'HQ', dept_id, 'STAFF', 'MEMBER' FROM org_dept WHERE dept_code = 'DEMO_DEV2';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2024-003', 'S01', '예시사원25', 'M', DATE '2001-03-03', '가상시 표본구 연습로 25', NULL, '영업 지원', 'SALES', DATE '2024-07-01', NULL, NULL, 'REGULAR', 2400, 'BRANCH_A', dept_id, 'STAFF', 'MEMBER' FROM org_dept WHERE dept_code = 'DEMO_SALES';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2024-004', 'S01', '예시사원26', 'F', DATE '1996-08-18', '가상시 표본구 연습로 26', NULL, '회계 실무', 'MANAGEMENT', DATE '2024-09-02', NULL, NULL, 'REGULAR', 2400, 'HQ', dept_id, 'SENIOR_STAFF', 'MEMBER' FROM org_dept WHERE dept_code = 'DEMO_FIN';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2025-001', 'S01', '예시사원27', 'M', DATE '2000-01-15', '가상시 표본구 연습로 27', NULL, '생산 라인 작업', 'PRODUCTION', DATE '2025-01-02', DATE '2025-01-02', DATE '2026-12-31', 'FIXED_TERM', 2400, 'FACTORY_A', dept_id, 'STAFF', 'MEMBER' FROM org_dept WHERE dept_code = 'DEMO_PROD';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2025-002', 'S01', '예시사원28', 'F', DATE '2002-05-27', '가상시 표본구 연습로 28', NULL, '사무 보조', 'MANAGEMENT', DATE '2025-03-03', NULL, NULL, 'PART_TIME', 1200, 'HQ', dept_id, 'STAFF', 'MEMBER' FROM org_dept WHERE dept_code = 'DEMO_HR';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2025-003', 'S01', '예시사원29', 'M', DATE '1995-11-30', '가상시 표본구 연습로 29', NULL, 'QA 테스트', 'ENGINEERING', DATE '2025-06-02', DATE '2025-06-02', DATE '2027-06-01', 'FIXED_TERM', 2400, 'HQ', dept_id, 'STAFF', 'MEMBER' FROM org_dept WHERE dept_code = 'DEMO_DEV2';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2026-001', 'S01', '예시사원30', 'F', DATE '2003-02-14', '가상시 표본구 연습로 30', NULL, '고객 응대', 'SALES', DATE '2026-01-05', DATE '2026-01-05', DATE '2026-12-31', 'FIXED_TERM', 2400, 'BRANCH_A', dept_id, 'STAFF', 'MEMBER' FROM org_dept WHERE dept_code = 'DEMO_SALES';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2026-002', 'S01', '예시사원31', 'M', DATE '2001-07-31', '가상시 표본구 연습로 31', NULL, '포장·출고', 'PRODUCTION', DATE '2026-03-02', NULL, NULL, 'PART_TIME', 1200, 'FACTORY_A', dept_id, 'STAFF', 'MEMBER' FROM org_dept WHERE dept_code = 'DEMO_PROD';
INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date, address, career_history, job_description, job_family_code, hire_date, contract_start_date, contract_end_date, employment_type_code, weekly_contract_minutes, work_location_code, current_dept_id, job_grade_code, job_title_code)
    SELECT 'S01-2026-003', 'S01', '예시사원32', 'F', DATE '1999-09-19', '가상시 표본구 연습로 32', NULL, '백엔드 개발', 'ENGINEERING', DATE '2026-09-01', NULL, NULL, 'REGULAR', 2400, 'HQ', dept_id, 'STAFF', 'MEMBER' FROM org_dept WHERE dept_code = 'DEMO_DEV1';

-- 채번 상태: 사업장·연도별 마지막 순번(위 사번과 같아야 한다. EmployeeSchemaScriptTest 가 검사)
INSERT INTO emp_no_seq (site_code, hire_year, last_seq) VALUES ('S01', 2018, 2);
INSERT INTO emp_no_seq (site_code, hire_year, last_seq) VALUES ('S01', 2019, 3);
INSERT INTO emp_no_seq (site_code, hire_year, last_seq) VALUES ('S01', 2020, 4);
INSERT INTO emp_no_seq (site_code, hire_year, last_seq) VALUES ('S01', 2021, 5);
INSERT INTO emp_no_seq (site_code, hire_year, last_seq) VALUES ('S01', 2022, 4);
INSERT INTO emp_no_seq (site_code, hire_year, last_seq) VALUES ('S01', 2023, 4);
INSERT INTO emp_no_seq (site_code, hire_year, last_seq) VALUES ('S01', 2024, 4);
INSERT INTO emp_no_seq (site_code, hire_year, last_seq) VALUES ('S01', 2025, 3);
INSERT INTO emp_no_seq (site_code, hire_year, last_seq) VALUES ('S01', 2026, 3);
