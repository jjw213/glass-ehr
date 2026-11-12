/**
 * 데모(VITE_API=mock)·테스트용 가짜 사원·조직. 모두 명백한 가짜 값이다(성명 `예시사원NN`, 주소 `가상시 …`).
 * 부서 구성은 backend/src/main/resources/db/oracle/seed/employee_demo_seed.sql 의 예시 조직과 같은 모양이다.
 * 주민등록번호는 넣지 않는다(백엔드 데모 시드와 같음).
 */
import type { DeptNode, EmployeeView } from '../api/employees';

const dept = (deptId: number, deptCode: string, name: string, parentDeptId: number | null, sortOrder: number, children: DeptNode[] = []): DeptNode =>
  ({ deptId, deptCode, name, parentDeptId, headEmpId: null, sortOrder, validFrom: '2018-01-01', validTo: null, children });

export const EMP_DEPT_TREE: DeptNode[] = [
  dept(1, 'DEMO_CEO', '대표이사실(예시)', null, 10),
  dept(2, 'DEMO_MGMT', '경영지원본부(예시)', null, 20, [
    dept(3, 'DEMO_HR', '인사팀(예시)', 2, 10),
    dept(4, 'DEMO_FIN', '재무팀(예시)', 2, 20),
  ]),
  dept(5, 'DEMO_DEV', '개발본부(예시)', null, 30, [
    dept(6, 'DEMO_DEV1', '개발1팀(예시)', 5, 10),
    dept(7, 'DEMO_DEV2', '개발2팀(예시)', 5, 20),
  ]),
  dept(8, 'DEMO_SALES', '영업팀(예시)', null, 40),
  dept(9, 'DEMO_PROD', '생산팀(예시)', null, 50),
];

interface Row {
  n: number; deptId: number; grade: string; title: string; family: string; type: string; hire: string; gender: 'M' | 'F';
  birth: string; retire?: string; retireReason?: string; contractEnd?: string; minutes?: number;
}

const ROWS: Row[] = [
  { n: 1, deptId: 1, grade: 'DIRECTOR', title: 'CEO', family: 'MANAGEMENT', type: 'REGULAR', hire: '2018-01-02', gender: 'M', birth: '1970-01-01' },
  { n: 2, deptId: 2, grade: 'GENERAL_MANAGER', title: 'DIVISION_HEAD', family: 'MANAGEMENT', type: 'REGULAR', hire: '2018-03-02', gender: 'F', birth: '1975-02-02' },
  { n: 3, deptId: 3, grade: 'MANAGER', title: 'TEAM_LEADER', family: 'MANAGEMENT', type: 'REGULAR', hire: '2019-04-01', gender: 'F', birth: '1985-03-03' },
  { n: 4, deptId: 3, grade: 'ASSISTANT_MANAGER', title: 'MEMBER', family: 'MANAGEMENT', type: 'REGULAR', hire: '2021-07-01', gender: 'M', birth: '1990-04-04' },
  { n: 5, deptId: 4, grade: 'MANAGER', title: 'TEAM_LEADER', family: 'MANAGEMENT', type: 'REGULAR', hire: '2019-09-02', gender: 'M', birth: '1984-05-05' },
  { n: 6, deptId: 5, grade: 'GENERAL_MANAGER', title: 'DIVISION_HEAD', family: 'ENGINEERING', type: 'REGULAR', hire: '2018-06-01', gender: 'M', birth: '1978-06-06' },
  { n: 7, deptId: 6, grade: 'DEPUTY_GENERAL_MANAGER', title: 'TEAM_LEADER', family: 'ENGINEERING', type: 'REGULAR', hire: '2020-01-02', gender: 'F', birth: '1982-07-07' },
  { n: 8, deptId: 6, grade: 'STAFF', title: 'MEMBER', family: 'ENGINEERING', type: 'FIXED_TERM', hire: '2026-03-02', gender: 'M', birth: '1998-08-08', contractEnd: '2027-03-01' },
  { n: 9, deptId: 7, grade: 'SENIOR_STAFF', title: 'MEMBER', family: 'ENGINEERING', type: 'REGULAR', hire: '2023-02-01', gender: 'F', birth: '1995-09-09' },
  { n: 10, deptId: 7, grade: 'STAFF', title: 'MEMBER', family: 'RESEARCH', type: 'PART_TIME', hire: '2025-05-01', gender: 'M', birth: '1999-10-10', minutes: 1200 },
  { n: 11, deptId: 8, grade: 'MANAGER', title: 'TEAM_LEADER', family: 'SALES', type: 'REGULAR', hire: '2019-11-01', gender: 'M', birth: '1986-11-11', retire: '2099-12-31', retireReason: 'VOLUNTARY' },
  { n: 12, deptId: 8, grade: 'STAFF', title: 'MEMBER', family: 'SALES', type: 'REGULAR', hire: '2022-08-01', gender: 'F', birth: '1997-12-12', retire: '2025-06-30', retireReason: 'VOLUNTARY' },
  { n: 13, deptId: 9, grade: 'ASSISTANT_MANAGER', title: 'PART_LEADER', family: 'PRODUCTION', type: 'REGULAR', hire: '2020-10-05', gender: 'M', birth: '1989-01-13' },
  { n: 14, deptId: 9, grade: 'STAFF', title: 'MEMBER', family: 'PRODUCTION', type: 'REGULAR', hire: '2099-01-02', gender: 'F', birth: '2000-02-14' },
];

const nn = (n: number) => String(n).padStart(2, '0');

/** 같은 입사연도 안에서 순번을 매긴다(사번 체계 #9: 사업장코드-입사연도-순번). */
const seqOf = (r: Row) => ROWS.filter((x) => x.hire.slice(0, 4) === r.hire.slice(0, 4) && x.n <= r.n).length;

export const EMP_SEED: EmployeeView[] = ROWS.map((r) => ({
  empId: r.n,
  empNo: `S01-${r.hire.slice(0, 4)}-${String(seqOf(r)).padStart(3, '0')}`,
  siteCode: 'S01',
  name: `예시사원${nn(r.n)}`,
  gender: r.gender,
  birthDate: r.birth,
  address: `가상시 예시구 샘플로 ${r.n}길 ${r.n * 10}`,
  careerHistory: r.n % 3 === 0 ? `예시회사(가상) ${r.n}년 근무` : null,
  jobDescription: '예시 업무(가상)',
  jobFamilyCode: r.family,
  hireDate: r.hire,
  renewalDate: null,
  contractStartDate: r.contractEnd ? r.hire : null,
  contractEndDate: r.contractEnd ?? null,
  employmentTypeCode: r.type,
  weeklyContractMinutes: r.minutes ?? 2400,
  workLocationCode: r.deptId === 9 ? 'FACTORY_A' : 'HQ',
  employmentNote: null,
  currentDeptId: r.deptId,
  jobGradeCode: r.grade,
  jobTitleCode: r.title,
  retireDate: r.retire ?? null,
  retireReasonCode: r.retireReason ?? null,
  retireReasonDetail: null,
  recordRetentionUntil: null,
  otherNote: null,
  rrnMasked: null,
}));
