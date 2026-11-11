/**
 * 데모(VITE_API=mock)용 공통코드 시드. 백엔드 시드 backend/src/main/resources/db/oracle/V007__common_code_seed.sql 과 같게 유지한다
 * (src/api/codes.test.ts 가 SQL 파일과 비교한다).
 */
import type { Code, CodeGroup } from '../api/codes';

export const CODE_GROUPS: CodeGroup[] = [
  { groupCode: 'JOB_GRADE', name: '직급', description: '사원~이사 등 직위 등급', sortOrder: 10, active: true },
  { groupCode: 'JOB_TITLE', name: '직책', description: '팀장·본부장 등 맡은 보직', sortOrder: 20, active: true },
  { groupCode: 'JOB_FAMILY', name: '직군', description: '경영지원·개발·영업 등 직무 묶음', sortOrder: 30, active: true },
  { groupCode: 'EMPLOYMENT_TYPE', name: '고용형태', description: '기간제 및 단시간근로자 보호 등에 관한 법률 제2조의 구분(기간제·단시간)과 기간의 정함이 없는 정규직', sortOrder: 40, active: true },
  { groupCode: 'WORK_LOCATION', name: '근무지', description: '근로자가 실제로 근무하는 장소', sortOrder: 50, active: true },
  { groupCode: 'RETIREMENT_REASON', name: '퇴직사유', description: '회사 내부 퇴직사유 구분. 4대보험 자격상실 사유코드와의 대응은 급여·4대보험 이슈에서 정한다', sortOrder: 60, active: true },
];

const c = (groupCode: string, code: string, name: string, sortOrder: number, description: string | null = null): Code =>
  ({ groupCode, code, name, description, sortOrder, active: true });

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
