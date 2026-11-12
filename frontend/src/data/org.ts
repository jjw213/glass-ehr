/**
 * 데모(VITE_API=mock)용 조직·사원 시드. 백엔드 데모 시드 backend/src/main/resources/db/oracle/seed/employee_demo_seed.sql 의
 * 부서 9개·사원 32명과 같은 값이다(모두 가짜 값). dept_id·emp_id 는 시드 INSERT 순서대로 1부터 매겼다.
 */
import type { EmployeeSummary, MockDept } from '../api/org';

const FROM = '2018-01-01';
const dept = (deptId: number, deptCode: string, name: string, parentDeptId: number | null, sortOrder: number): MockDept => ({
  deptId, deptCode, validFrom: FROM, validTo: null,
  hist: [{ validFrom: FROM, validTo: null, name, parentDeptId, headEmpId: null, sortOrder, changeType: 'CREATE' }],
});

export const DEMO_DEPTS: MockDept[] = [
  dept(1, 'DEMO_CEO', '대표이사실(예시)', null, 10),
  dept(2, 'DEMO_MGMT', '경영지원본부(예시)', null, 20),
  dept(3, 'DEMO_HR', '인사팀(예시)', 2, 10),
  dept(4, 'DEMO_FIN', '재무팀(예시)', 2, 20),
  dept(5, 'DEMO_DEV', '개발본부(예시)', null, 30),
  dept(6, 'DEMO_DEV1', '개발1팀(예시)', 5, 10),
  dept(7, 'DEMO_DEV2', '개발2팀(예시)', 5, 20),
  dept(8, 'DEMO_SALES', '영업팀(예시)', null, 40),
  dept(9, 'DEMO_PROD', '생산팀(예시)', null, 50),
];

type Row = [empNo: string, deptId: number, grade: string, title: string, type: string, hireDate: string];
const ROWS: Row[] = [
  ['S01-2018-001', 1, 'DIRECTOR', 'CEO', 'REGULAR', '2018-01-02'],
  ['S01-2018-002', 2, 'GENERAL_MANAGER', 'DIVISION_HEAD', 'REGULAR', '2018-03-05'],
  ['S01-2019-001', 3, 'MANAGER', 'TEAM_LEADER', 'REGULAR', '2019-02-11'],
  ['S01-2019-002', 3, 'ASSISTANT_MANAGER', 'MEMBER', 'REGULAR', '2019-06-03'],
  ['S01-2019-003', 4, 'MANAGER', 'TEAM_LEADER', 'REGULAR', '2019-09-02'],
  ['S01-2020-001', 4, 'ASSISTANT_MANAGER', 'MEMBER', 'REGULAR', '2020-01-06'],
  ['S01-2020-002', 5, 'DEPUTY_GENERAL_MANAGER', 'DIVISION_HEAD', 'REGULAR', '2020-03-02'],
  ['S01-2020-003', 6, 'MANAGER', 'TEAM_LEADER', 'REGULAR', '2020-05-11'],
  ['S01-2020-004', 6, 'SENIOR_STAFF', 'MEMBER', 'REGULAR', '2020-07-01'],
  ['S01-2021-001', 6, 'SENIOR_STAFF', 'MEMBER', 'REGULAR', '2021-01-04'],
  ['S01-2021-002', 6, 'STAFF', 'MEMBER', 'REGULAR', '2021-03-02'],
  ['S01-2021-003', 7, 'MANAGER', 'TEAM_LEADER', 'REGULAR', '2021-04-01'],
  ['S01-2021-004', 7, 'STAFF', 'MEMBER', 'REGULAR', '2021-07-05'],
  ['S01-2021-005', 7, 'STAFF', 'MEMBER', 'REGULAR', '2021-09-01'],
  ['S01-2022-001', 8, 'MANAGER', 'TEAM_LEADER', 'REGULAR', '2022-01-03'],
  ['S01-2022-002', 8, 'ASSISTANT_MANAGER', 'MEMBER', 'REGULAR', '2022-02-07'],
  ['S01-2022-003', 8, 'STAFF', 'MEMBER', 'REGULAR', '2022-03-02'],
  ['S01-2022-004', 9, 'MANAGER', 'TEAM_LEADER', 'REGULAR', '2022-06-01'],
  ['S01-2023-001', 9, 'SENIOR_STAFF', 'PART_LEADER', 'REGULAR', '2023-01-02'],
  ['S01-2023-002', 9, 'STAFF', 'MEMBER', 'REGULAR', '2023-03-06'],
  ['S01-2023-003', 9, 'STAFF', 'MEMBER', 'REGULAR', '2023-05-02'],
  ['S01-2023-004', 3, 'STAFF', 'MEMBER', 'REGULAR', '2023-08-01'],
  ['S01-2024-001', 6, 'ASSISTANT_MANAGER', 'MEMBER', 'REGULAR', '2024-01-02'],
  ['S01-2024-002', 7, 'STAFF', 'MEMBER', 'REGULAR', '2024-02-05'],
  ['S01-2024-003', 8, 'STAFF', 'MEMBER', 'REGULAR', '2024-07-01'],
  ['S01-2024-004', 4, 'SENIOR_STAFF', 'MEMBER', 'REGULAR', '2024-09-02'],
  ['S01-2025-001', 9, 'STAFF', 'MEMBER', 'FIXED_TERM', '2025-01-02'],
  ['S01-2025-002', 3, 'STAFF', 'MEMBER', 'PART_TIME', '2025-03-03'],
  ['S01-2025-003', 7, 'STAFF', 'MEMBER', 'FIXED_TERM', '2025-06-02'],
  ['S01-2026-001', 8, 'STAFF', 'MEMBER', 'FIXED_TERM', '2026-01-05'],
  ['S01-2026-002', 9, 'STAFF', 'MEMBER', 'PART_TIME', '2026-03-02'],
  ['S01-2026-003', 6, 'STAFF', 'MEMBER', 'REGULAR', '2026-09-01'],
];

export const DEMO_EMPLOYEES: EmployeeSummary[] = ROWS.map(([empNo, currentDeptId, jobGradeCode, jobTitleCode, employmentTypeCode, hireDate], i) => ({
  empId: i + 1, empNo, name: `예시사원${String(i + 1).padStart(2, '0')}`, currentDeptId, jobGradeCode, jobTitleCode, employmentTypeCode, hireDate, retireDate: null,
}));
