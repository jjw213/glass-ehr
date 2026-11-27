/**
 * 데모(VITE_API=mock)용 공통코드 시드. 백엔드 시드 backend/src/main/resources/db/oracle/V007__common_code_seed.sql 과 같게 유지한다
 * (src/api/codes.test.ts 가 SQL 파일과 비교한다). 시스템 그룹·기본 코드 표시는 V037 과 같다(같은 테스트가 비교한다).
 */
import type { Code, CodeGroup, MockUsages, SysType } from '../api/codes';

/**
 * 처리방식 목록(#141). 서버 고정값이고 백엔드 CodeSysTypes 와 같다(src/api/codes.test.ts 가 V050 CHECK 와 비교한다).
 * 데모 가짜 백엔드가 쓰고, 실제 화면은 GET /api/code-groups/{group}/sys-types 로 받는다. 이 그룹의 코드는 처리방식이 꼭 있어야 한다.
 */
export const SYS_TYPES: Record<string, SysType[]> = {
  EMPLOYMENT_TYPE: [
    { code: 'REGULAR', name: '일반(기간의 정함 없음)' }, { code: 'FIXED_TERM', name: '기간제' },
    { code: 'PART_TIME', name: '단시간' }, { code: 'FIXED_TERM_PART_TIME', name: '기간제·단시간' },
  ],
  RETIREMENT_REASON: [
    { code: 'VOLUNTARY', name: '자진 퇴사' }, { code: 'RECOMMENDED', name: '권고사직' }, { code: 'RETIREMENT_AGE', name: '정년' },
    { code: 'CONTRACT_END', name: '계약 만료' }, { code: 'DISMISSAL', name: '해고' }, { code: 'TRANSFER', name: '전적' },
    { code: 'DEATH', name: '사망' }, { code: 'OTHER', name: '기타' },
  ],
};

const g = (groupCode: string, name: string, description: string, sortOrder: number): CodeGroup =>
  ({ groupCode, name, description, sortOrder, active: true, system: true, editPage: null });

export const CODE_GROUPS: CodeGroup[] = [
  g('JOB_GRADE', '직급', '사원~이사 등 직위 등급', 10),
  g('JOB_TITLE', '직책', '팀장·본부장 등 맡은 보직', 20),
  g('JOB_FAMILY', '직군', '경영지원·개발·영업 등 직무 묶음', 30),
  g('EMPLOYMENT_TYPE', '고용형태', '기간제 및 단시간근로자 보호 등에 관한 법률 제2조의 구분(기간제·단시간)과 기간의 정함이 없는 정규직', 40),
  g('WORK_LOCATION', '근무지', '근로자가 실제로 근무하는 장소', 50),
  g('RETIREMENT_REASON', '퇴직사유', '회사 내부 퇴직사유 구분. 4대보험 자격상실 사유코드와의 대응은 급여·4대보험 이슈에서 정한다', 60),
];

/** 로직이 코드값을 직접 참조하는 기본 코드(V037). */
const SYSTEM_CODES = new Set([
  'EMPLOYMENT_TYPE/FIXED_TERM', 'EMPLOYMENT_TYPE/PART_TIME',
  'RETIREMENT_REASON/VOLUNTARY', 'RETIREMENT_REASON/RECOMMENDED_RESIGNATION', 'RETIREMENT_REASON/CONTRACT_EXPIRED',
  'RETIREMENT_REASON/RETIREMENT_AGE', 'RETIREMENT_REASON/DISMISSAL', 'RETIREMENT_REASON/TRANSFER_AFFILIATE', 'RETIREMENT_REASON/DEATH',
]);

/**
 * 시드 코드의 처리방식(V050, #141). 코드값과 이름이 다른 것만 적는다. 나머지는 처리방식이 있는 그룹이면
 * 코드값과 같은 이름(목록에 없으면 고용형태 REGULAR·퇴직사유 OTHER), 없는 그룹이면 null.
 */
const SEED_SYS_TYPE: Record<string, string> = {
  'RETIREMENT_REASON/RECOMMENDED_RESIGNATION': 'RECOMMENDED',
  'RETIREMENT_REASON/CONTRACT_EXPIRED': 'CONTRACT_END',
  'RETIREMENT_REASON/TRANSFER_AFFILIATE': 'TRANSFER',
};
const FALLBACK_SYS_TYPE: Record<string, string> = { EMPLOYMENT_TYPE: 'REGULAR', RETIREMENT_REASON: 'OTHER' };
const sysTypeOf = (groupCode: string, code: string): string | null => {
  const list = SYS_TYPES[groupCode];
  if (!list) return null;
  return SEED_SYS_TYPE[`${groupCode}/${code}`] ?? (list.some((s) => s.code === code) ? code : FALLBACK_SYS_TYPE[groupCode]);
};

const c = (groupCode: string, code: string, name: string, sortOrder: number, description: string | null = null): Code =>
  ({ groupCode, code, name, description, sortOrder, active: true, system: SYSTEM_CODES.has(`${groupCode}/${code}`), sysType: sysTypeOf(groupCode, code) });

/** 데모 사용처. 이름은 명백한 가짜 값이다. */
export const DEMO_USAGES: MockUsages = {
  'JOB_GRADE/STAFF': [
    { table: 'employee', label: '사원', dateLabel: '입사일', rows: [
      { empNo: 'S01-2026-001', name: '가상사원1', date: '2026-03-02' },
      { empNo: 'S01-2026-002', name: '가상사원2', date: '2026-03-02' },
    ] },
    { table: 'hr_appointment', label: '발령 이력', dateLabel: '발령일', rows: [
      { empNo: 'S01-2026-001', name: '가상사원1', date: '2026-03-02' },
    ] },
  ],
};

export const CODES: Code[] = [
  c('JOB_GRADE', 'STAFF', '사원', 10),
  c('JOB_GRADE', 'SENIOR_STAFF', '주임', 20),
  c('JOB_GRADE', 'ASSISTANT_MANAGER', '대리', 30),
  c('JOB_GRADE', 'MANAGER', '과장', 40),
  c('JOB_GRADE', 'DEPUTY_GENERAL_MANAGER', '차장', 50),
  c('JOB_GRADE', 'GENERAL_MANAGER', '부장', 60),
  c('JOB_GRADE', 'DIRECTOR', '이사', 70),

  c('JOB_TITLE', 'MEMBER', '팀원', 10),
  c('JOB_TITLE', 'PART_LEADER', '파트장', 20),
  c('JOB_TITLE', 'TEAM_LEADER', '팀장', 30),
  c('JOB_TITLE', 'DIVISION_HEAD', '본부장', 40),
  c('JOB_TITLE', 'CEO', '대표이사', 50),

  c('JOB_FAMILY', 'MANAGEMENT', '경영지원', 10),
  c('JOB_FAMILY', 'ENGINEERING', '개발', 20),
  c('JOB_FAMILY', 'RESEARCH', '연구', 30),
  c('JOB_FAMILY', 'SALES', '영업', 40),
  c('JOB_FAMILY', 'PRODUCTION', '생산', 50),

  c('EMPLOYMENT_TYPE', 'REGULAR', '정규직', 10, '기간의 정함이 없는 근로계약, 통상 근로자'),
  c('EMPLOYMENT_TYPE', 'FIXED_TERM', '기간제', 20, '기간제 및 단시간근로자 보호 등에 관한 법률 제2조 제1호 기간제근로자'),
  c('EMPLOYMENT_TYPE', 'PART_TIME', '단시간', 30, '기간제 및 단시간근로자 보호 등에 관한 법률 제2조 제2호 단시간근로자'),

  c('WORK_LOCATION', 'HQ', '본사', 10),
  c('WORK_LOCATION', 'BRANCH_A', 'A지사', 20),
  c('WORK_LOCATION', 'FACTORY_A', 'A공장', 30),

  c('RETIREMENT_REASON', 'VOLUNTARY', '자발적 퇴사(개인 사정)', 10),
  c('RETIREMENT_REASON', 'CONTRACT_EXPIRED', '계약기간 만료', 20),
  c('RETIREMENT_REASON', 'RETIREMENT_AGE', '정년퇴직', 30),
  c('RETIREMENT_REASON', 'RECOMMENDED_RESIGNATION', '권고사직', 40),
  c('RETIREMENT_REASON', 'DISMISSAL', '해고', 50),
  c('RETIREMENT_REASON', 'TRANSFER_AFFILIATE', '전적(계열사 이동)', 60),
  c('RETIREMENT_REASON', 'DEATH', '사망', 70),
  c('RETIREMENT_REASON', 'OTHER', '기타', 99),
];
