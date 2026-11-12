/**
 * 사원(근로자 명부) API (#14 백엔드, #15 화면). 백엔드: backend/ EmployeeController (Spring).
 * 응답 형식은 backend EmployeeDtos 와, 조직 트리는 OrgDtos 와 같다.
 */
import { EMP_DEPT_TREE, EMP_SEED } from '../data/employees';
import { call } from './http';
import { ApiError } from './types';

/** 목록 한 줄. 주소·이력·주민등록번호는 서버가 넣지 않는다(EmployeeSummary). */
export interface EmployeeSummary {
  empId: number; empNo: string; name: string; currentDeptId: number | null;
  jobGradeCode: string | null; jobTitleCode: string | null; employmentTypeCode: string;
  hireDate: string; retireDate: string | null;
}

/** 상세(EmployeeView). 주민등록번호는 마스킹 값(rrnMasked)만 온다. */
export interface EmployeeView {
  empId: number; empNo: string; siteCode: string; name: string; gender: 'M' | 'F';
  birthDate: string; address: string; careerHistory: string | null; jobDescription: string; jobFamilyCode: string | null;
  hireDate: string; renewalDate: string | null; contractStartDate: string | null; contractEndDate: string | null;
  employmentTypeCode: string; weeklyContractMinutes: number | null; workLocationCode: string | null; employmentNote: string | null;
  currentDeptId: number | null; jobGradeCode: string | null; jobTitleCode: string | null;
  retireDate: string | null; retireReasonCode: string | null; retireReasonDetail: string | null; recordRetentionUntil: string | null;
  otherNote: string | null; rrnMasked: string | null;
}

/** 입사 등록(EmployeeCreateRequest). 사번은 서버가 채번한다. */
export interface EmployeeCreate {
  siteCode: string; name: string; gender: 'M' | 'F' | ''; birthDate: string; address: string;
  careerHistory: string | null; jobDescription: string; jobFamilyCode: string | null;
  hireDate: string; renewalDate: string | null; contractStartDate: string | null; contractEndDate: string | null;
  employmentTypeCode: string; weeklyContractMinutes: number | null; workLocationCode: string | null;
  employmentNote: string | null; otherNote: string | null;
  deptId: number | null; jobGradeCode: string | null; jobTitleCode: string | null;
  /** 선택. 응답·로그·화면 상태에 원문을 남기지 않는다. */
  rrn: string | null;
}

/** 조직 트리 노드(OrgDtos.DeptNode) */
export interface DeptNode {
  deptId: number; deptCode: string; name: string; parentDeptId: number | null; headEmpId: number | null;
  sortOrder: number; validFrom: string; validTo: string | null; children: DeptNode[];
}
export interface OrgTree { baseDate: string; roots: DeptNode[] }

/** 원문 조회 목적(RrnAccessLog.Purpose) */
export type RrnPurpose = 'VIEW' | 'SOCIAL_INSURANCE' | 'WITHHOLDING';

/** 요청자. 백엔드는 인증 이슈 전까지 X-Actor-Id·X-Actor-Role 헤더로 받는다(docs/employee-design.md 5장). */
export interface Actor { id: string; role: string }

export interface EmployeeApi {
  list(includeRetired: boolean): Promise<EmployeeSummary[]>;
  get(empId: number): Promise<EmployeeView>;
  create(input: EmployeeCreate, actor: Actor): Promise<EmployeeView>;
  /** 주민등록번호 원문. 서버가 매번 조회 로그를 남긴다. */
  revealRrn(empId: number, purpose: RrnPurpose, actor: Actor): Promise<string>;
  orgTree(date: string): Promise<OrgTree>;
}

const actorHeaders = (a: Actor) => ({ 'X-Actor-Id': a.id, 'X-Actor-Role': a.role });

export const httpEmployeeApi: EmployeeApi = {
  list: (includeRetired) => call('GET', `/employees?includeRetired=${includeRetired}`),
  get: (empId) => call('GET', `/employees/${empId}`),
  create: (input, actor) => call('POST', '/employees', toCreateBody(input), actorHeaders(actor)),
  revealRrn: async (empId, purpose, actor) =>
    (await call<{ empId: number; rrn: string }>('GET', `/employees/${empId}/rrn?purpose=${purpose}`, undefined, actorHeaders(actor))).rrn,
  orgTree: (date) => call('GET', `/org/tree?date=${encodeURIComponent(date)}`),
};

/* ---------- 날짜·상태 ---------- */

/** Asia/Seoul 기준 오늘 'YYYY-MM-DD' (AGENTS.md: 날짜는 Asia/Seoul 기준) */
export const seoulToday = (now: Date = new Date()) =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);

/**
 * 재직상태. 날짜만으로 정한다(휴직은 인사발령 이슈에서 발령 이력으로 추가).
 * 퇴직일 당일까지는 재직으로 본다(백엔드 EmployeeDeptMemberChecker 와 같은 규칙).
 */
export type EmpStatus = 'PENDING' | 'ACTIVE' | 'RETIRING' | 'RETIRED';
export const STATUS_LABEL: Record<EmpStatus, string> = { PENDING: '입사예정', ACTIVE: '재직', RETIRING: '퇴직예정', RETIRED: '퇴직' };

export function statusOf(e: { hireDate: string; retireDate: string | null }, today: string): EmpStatus {
  if (e.retireDate && e.retireDate < today) return 'RETIRED';
  if (e.hireDate > today) return 'PENDING';
  if (e.retireDate) return 'RETIRING';
  return 'ACTIVE';
}

/** 필터 값. ACTIVE 는 퇴직예정도 포함한다(아직 재직 중이므로). */
export type StatusFilter = 'ALL' | 'ACTIVE' | 'PENDING' | 'RETIRED';
export function matchesStatus(s: EmpStatus, f: StatusFilter) {
  if (f === 'ALL') return true;
  if (f === 'ACTIVE') return s === 'ACTIVE' || s === 'RETIRING';
  return s === f;
}

/* ---------- 조직 ---------- */

export interface FlatDept { deptId: number; name: string; depth: number; path: string; ancestors: number[] }

/** 트리를 정렬 순서대로 펼친다. path 는 '개발본부 > 개발1팀' 형식. */
export function flattenDepts(roots: DeptNode[]): FlatDept[] {
  const out: FlatDept[] = [];
  const walk = (nodes: DeptNode[], depth: number, names: string[], ancestors: number[]) => {
    for (const n of [...nodes].sort((a, b) => a.sortOrder - b.sortOrder || a.deptId - b.deptId)) {
      const path = [...names, n.name];
      out.push({ deptId: n.deptId, name: n.name, depth, path: path.join(' > '), ancestors });
      walk(n.children, depth + 1, path, [...ancestors, n.deptId]);
    }
  };
  walk(roots, 0, [], []);
  return out;
}

/* ---------- 검색 ---------- */

export interface EmployeeQuery { q: string; deptId: number | null; jobGradeCode: string; status: StatusFilter }

/**
 * 이름·사번 검색(부분 일치, 사번은 대소문자 무시) + 부서(하위부서 포함)·직급·재직상태 필터.
 * 목록은 30인 이상 사업장 규모라 한 번에 받아 화면에서 거른다.
 */
export function filterEmployees(list: EmployeeSummary[], query: EmployeeQuery, depts: FlatDept[], today: string): EmployeeSummary[] {
  const q = query.q.trim().toLowerCase();
  const byId = new Map(depts.map((d) => [d.deptId, d]));
  const inDept = (id: number | null) => {
    if (query.deptId === null) return true;
    if (id === null) return false;
    if (id === query.deptId) return true;
    return byId.get(id)?.ancestors.includes(query.deptId) ?? false;
  };
  return list.filter((e) =>
    (!q || e.name.toLowerCase().includes(q) || e.empNo.toLowerCase().includes(q)) &&
    inDept(e.currentDeptId) &&
    (!query.jobGradeCode || e.jobGradeCode === query.jobGradeCode) &&
    matchesStatus(statusOf(e, today), query.status),
  );
}

/* ---------- 입력 검사 ---------- */

/** 1주 전체 분(7일 × 24시간 × 60분). 법령 기준값이 아니라 입력 오류 방지용 상한(백엔드 EmployeeDtos.MINUTES_PER_WEEK 와 같음). */
export const MINUTES_PER_WEEK = 7 * 24 * 60;
export const SITE_CODE_PATTERN = /^S[0-9][0-9]$/;
const RRN_PATTERN = /^[0-9]{6}-?[0-9]{7}$/;
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

/** 백엔드 EmployeeCreateRequest Bean Validation 과 같은 규칙(서버도 다시 검사한다). 문제가 없으면 null. */
export function validateEmployee(i: EmployeeCreate): string | null {
  if (!SITE_CODE_PATTERN.test(i.siteCode)) return '사업장코드는 S01 형식이에요';
  if (!i.name.trim()) return '성명을 입력해 주세요';
  if (i.name.length > 100) return '성명은 100자 이내로 써 주세요';
  if (i.gender !== 'M' && i.gender !== 'F') return '성별을 선택해 주세요';
  if (!DATE_PATTERN.test(i.birthDate)) return '생년월일을 입력해 주세요';
  if (!i.address.trim()) return '주소를 입력해 주세요';
  if (i.address.length > 300) return '주소는 300자 이내로 써 주세요';
  if (i.careerHistory && i.careerHistory.length > 4000) return '이력은 4000자 이내로 써 주세요';
  if (!i.jobDescription.trim()) return '종사 업무를 입력해 주세요';
  if (i.jobDescription.length > 500) return '종사 업무는 500자 이내로 써 주세요';
  if (!DATE_PATTERN.test(i.hireDate)) return '입사일을 입력해 주세요';
  if (!i.employmentTypeCode) return '고용형태를 선택해 주세요';
  if (i.contractStartDate && i.contractEndDate && i.contractEndDate < i.contractStartDate) return '계약 종료일이 시작일보다 빨라요';
  if (i.weeklyContractMinutes !== null) {
    if (!Number.isInteger(i.weeklyContractMinutes) || i.weeklyContractMinutes <= 0) return '1주 소정근로시간은 0보다 커야 해요';
    if (i.weeklyContractMinutes > MINUTES_PER_WEEK) return '1주 소정근로시간(분)이 너무 커요';
  }
  if (i.employmentNote && i.employmentNote.length > 1000) return '고용 관련 메모는 1000자 이내로 써 주세요';
  if (i.otherNote && i.otherNote.length > 1000) return '기타 사항은 1000자 이내로 써 주세요';
  if (i.rrn !== null && !RRN_PATTERN.test(i.rrn.trim())) return '주민등록번호 형식이 올바르지 않아요(숫자 6자리-숫자 7자리)';
  return null;
}

const blank = (s: string | null) => (s && s.trim() ? s.trim() : null);

/** 빈 문자열은 null 로 보낸다(서버가 빈 값과 미입력을 구분하지 않도록). */
export function toCreateBody(i: EmployeeCreate) {
  return {
    ...i,
    name: i.name.trim(), address: i.address.trim(), jobDescription: i.jobDescription.trim(),
    careerHistory: blank(i.careerHistory), employmentNote: blank(i.employmentNote), otherNote: blank(i.otherNote),
    jobFamilyCode: blank(i.jobFamilyCode), workLocationCode: blank(i.workLocationCode),
    jobGradeCode: blank(i.jobGradeCode), jobTitleCode: blank(i.jobTitleCode),
    renewalDate: blank(i.renewalDate), contractStartDate: blank(i.contractStartDate), contractEndDate: blank(i.contractEndDate),
    rrn: blank(i.rrn),
  };
}

/** 주민등록번호 마스킹(백엔드 Rrn.masked 와 같음: 앞 6자리만 보이고 뒷자리 7자리를 가린다). */
export const maskRrn = (rrn: string) => {
  const d = rrn.replace('-', '');
  return `${d.slice(0, 6)}-*******`;
};

/* ---------- 데모용 가짜 백엔드 ---------- */

const RRN_ROLES = new Set(['HR', 'PAYROLL']);

/** 브라우저 안의 가짜 백엔드(VITE_API=mock·테스트). 새로 고치면 시드로 돌아간다. 데이터는 모두 가짜 값이다. */
export function createMockEmployeeApi(seed: { employees: EmployeeView[]; rrns?: Record<number, string>; tree?: DeptNode[] } = { employees: EMP_SEED }) {
  let employees = structuredClone(seed.employees);
  const rrns = new Map<number, string>(Object.entries(seed.rrns ?? {}).map(([k, v]) => [Number(k), v]));
  const tree = structuredClone(seed.tree ?? EMP_DEPT_TREE);
  /** 원문 조회 기록(테스트 확인용). 원문은 남기지 않는다. */
  const revealLog: Array<{ empId: number; actorId: string; role: string; purpose: RrnPurpose; ok: boolean }> = [];
  const seqs = new Map<string, number>();
  for (const e of employees) {
    const key = e.empNo.slice(0, 8);
    seqs.set(key, Math.max(seqs.get(key) ?? 0, Number(e.empNo.slice(9))));
  }
  const summary = (e: EmployeeView): EmployeeSummary => ({
    empId: e.empId, empNo: e.empNo, name: e.name, currentDeptId: e.currentDeptId, jobGradeCode: e.jobGradeCode,
    jobTitleCode: e.jobTitleCode, employmentTypeCode: e.employmentTypeCode, hireDate: e.hireDate, retireDate: e.retireDate,
  });
  const find = (empId: number) => {
    const e = employees.find((x) => x.empId === empId);
    if (!e) throw new ApiError(404, '사원을 찾을 수 없어요');
    return e;
  };

  const api: EmployeeApi & { revealLog: typeof revealLog } = {
    revealLog,
    async list(includeRetired) {
      // 백엔드 findByRetireDateIsNull 과 같다(퇴직일이 정해진 사람은 includeRetired=true 일 때만)
      return employees.filter((e) => includeRetired || !e.retireDate).sort((a, b) => (a.empNo < b.empNo ? -1 : 1)).map(summary);
    },
    async get(empId) {
      const e = find(empId);
      return { ...structuredClone(e), rrnMasked: rrns.has(empId) ? maskRrn(rrns.get(empId)!) : null };
    },
    async create(input) {
      const err = validateEmployee(input);
      if (err) throw new ApiError(400, err);
      const body = toCreateBody(input);
      const key = `${body.siteCode}-${body.hireDate.slice(0, 4)}`;
      const seq = (seqs.get(key) ?? 0) + 1;
      if (seq > 999) throw new ApiError(409, '이 사업장·입사연도의 사번이 모두 쓰였어요');
      seqs.set(key, seq);
      const empId = employees.reduce((m, e) => Math.max(m, e.empId), 0) + 1;
      const created: EmployeeView = {
        empId, empNo: `${key}-${String(seq).padStart(3, '0')}`, siteCode: body.siteCode, name: body.name, gender: body.gender as 'M' | 'F',
        birthDate: body.birthDate, address: body.address, careerHistory: body.careerHistory, jobDescription: body.jobDescription,
        jobFamilyCode: body.jobFamilyCode, hireDate: body.hireDate, renewalDate: body.renewalDate, contractStartDate: body.contractStartDate,
        contractEndDate: body.contractEndDate, employmentTypeCode: body.employmentTypeCode, weeklyContractMinutes: body.weeklyContractMinutes,
        workLocationCode: body.workLocationCode, employmentNote: body.employmentNote, currentDeptId: body.deptId,
        jobGradeCode: body.jobGradeCode, jobTitleCode: body.jobTitleCode, retireDate: null, retireReasonCode: null,
        retireReasonDetail: null, recordRetentionUntil: null, otherNote: body.otherNote, rrnMasked: null,
      };
      employees = [...employees, created];
      if (body.rrn) rrns.set(empId, body.rrn.includes('-') ? body.rrn : `${body.rrn.slice(0, 6)}-${body.rrn.slice(6)}`);
      return api.get(empId);
    },
    async revealRrn(empId, purpose, actor) {
      const ok = RRN_ROLES.has(actor.role);
      revealLog.push({ empId, actorId: actor.id, role: actor.role, purpose, ok });
      if (!ok) throw new ApiError(403, '주민등록번호 원문은 인사·급여 담당자만 볼 수 있어요');
      find(empId);
      const rrn = rrns.get(empId);
      if (!rrn) throw new ApiError(404, '등록된 주민등록번호가 없어요');
      return rrn;
    },
    async orgTree(date) { return { baseDate: date, roots: structuredClone(tree) }; },
  };
  return api;
}

export const employeeApi: EmployeeApi = import.meta.env.VITE_API === 'mock' ? createMockEmployeeApi() : httpEmployeeApi;
