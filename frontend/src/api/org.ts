/**
 * 조직도 화면(#13)이 쓰는 API. 백엔드: backend/ OrgController(#12), EmployeeController(#14).
 * 응답 형식은 backend OrgDtos.OrgTree·DeptNode·DeptView, EmployeeDtos.EmployeeSummary 와 같다.
 * 날짜는 모두 'YYYY-MM-DD' 문자열(Asia/Seoul 달력 날짜).
 */
import { DEMO_DEPTS, DEMO_EMPLOYEES } from '../data/org';
import { call } from './http';
import { ApiError } from './types';

/** 부서코드 형식. 백엔드 OrgDtos.DEPT_CODE_PATTERN + 20자 제한(V008)과 같다. */
export const DEPT_CODE_PATTERN = /^[A-Z0-9][A-Z0-9_]{0,19}$/;
export const MAX_DEPT_SORT_ORDER = 99999;

export interface DeptNode {
  deptId: number; deptCode: string; name: string; parentDeptId: number | null; headEmpId: number | null;
  sortOrder: number; validFrom: string; validTo: string | null; children: DeptNode[];
}
export interface OrgTree { baseDate: string; roots: DeptNode[] }

export type DeptChangeType = 'CREATE' | 'RENAME' | 'MOVE' | 'UPDATE';
export interface DeptView {
  deptId: number; deptCode: string; name: string; parentDeptId: number | null; headEmpId: number | null;
  sortOrder: number; validFrom: string; validTo: string | null; changeType: DeptChangeType;
  deptValidFrom: string; deptValidTo: string | null;
}

/** 부서 변경 입력. effectiveDate 부터 적용할 속성 전체(바꾸지 않는 값도 그대로). */
export interface DeptChange {
  name: string; parentDeptId: number | null; headEmpId: number | null; sortOrder: number; effectiveDate: string; reason: string | null;
}
/** 부서 신설 입력. effectiveDate = 신설일. */
export interface DeptCreate extends DeptChange { deptCode: string }

/** 사원 목록 한 줄(주소·주민등록번호 없음). */
export interface EmployeeSummary {
  empId: number; empNo: string; name: string; currentDeptId: number | null; jobGradeCode: string | null;
  jobTitleCode: string | null; employmentTypeCode: string; hireDate: string; retireDate: string | null;
}

export interface OrgApi {
  tree(date: string): Promise<OrgTree>;
  /** 퇴직자 포함 전체. 기준일 재직 여부는 화면에서 거른다(과거 조직도용). */
  employees(): Promise<EmployeeSummary[]>;
  createDept(input: DeptCreate): Promise<DeptView>;
  changeDept(deptId: number, input: DeptChange): Promise<DeptView>;
}

export const httpOrgApi: OrgApi = {
  tree: (date) => call('GET', `/org/tree?date=${encodeURIComponent(date)}`),
  employees: () => call('GET', '/employees?includeRetired=true'),
  createDept: (input) => call('POST', '/org/depts', input),
  changeDept: (deptId, input) => call('PUT', `/org/depts/${deptId}`, input),
};

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** 입력 검사. 백엔드 OrgDtos 의 Bean Validation 과 같은 규칙(서버도 다시 검사한다). 문제가 없으면 null. */
export function validateDept(input: DeptChange & { deptCode?: string }): string | null {
  if (input.deptCode !== undefined && !DEPT_CODE_PATTERN.test(input.deptCode)) return '부서코드는 영문 대문자·숫자·밑줄만 20자 이내로 써 주세요(첫 글자는 영문 대문자나 숫자)';
  if (!input.name.trim()) return '부서명을 입력해 주세요';
  if (input.name.length > 100) return '부서명은 100자 이내로 써 주세요';
  if (!Number.isInteger(input.sortOrder) || input.sortOrder < 0 || input.sortOrder > MAX_DEPT_SORT_ORDER) return `정렬 순서는 0~${MAX_DEPT_SORT_ORDER} 사이 정수여야 해요`;
  if (!ISO_DATE.test(input.effectiveDate)) return '적용일을 입력해 주세요';
  if (input.reason && input.reason.length > 500) return '사유는 500자 이내로 써 주세요';
  return null;
}

/* ---------------- 브라우저 안의 가짜 백엔드 ---------------- */

export interface MockHist { validFrom: string; validTo: string | null; name: string; parentDeptId: number | null; headEmpId: number | null; sortOrder: number; changeType: DeptChangeType }
export interface MockDept { deptId: number; deptCode: string; validFrom: string; validTo: string | null; hist: MockHist[] }

/** 반개구간 [from, to) 에 d 가 들어가는지. to=null 은 종료일 미정. 'YYYY-MM-DD' 는 문자열 비교로 순서가 맞다. */
export const within = (d: string, from: string, to: string | null) => from <= d && (to === null || d < to);

/**
 * 백엔드 OrgDeptService 의 규칙을 줄여 옮긴 가짜 구현. 새로 고치면 시드로 돌아간다.
 * 순환 검사·적용일 검사는 백엔드와 같게 거부하고, 메시지는 화면 확인용이다.
 */
export function createMockOrgApi(): OrgApi {
  const depts: MockDept[] = structuredClone(DEMO_DEPTS);
  const employees = structuredClone(DEMO_EMPLOYEES);
  let nextId = Math.max(...depts.map((d) => d.deptId)) + 1;

  const histAt = (d: MockDept, date: string) => (within(date, d.validFrom, d.validTo) ? d.hist.find((h) => within(date, h.validFrom, h.validTo)) : undefined);
  const require = (id: number) => {
    const d = depts.find((x) => x.deptId === id);
    if (!d) throw new ApiError(404, `부서가 없어요: ${id}`);
    return d;
  };
  const check = (input: DeptChange & { deptCode?: string }) => {
    const err = validateDept(input);
    if (err) throw new ApiError(400, err);
  };
  const checkParent = (parentId: number | null, date: string) => {
    if (parentId === null) return;
    const p = depts.find((x) => x.deptId === parentId);
    if (!p || p.validFrom > date || p.validTo !== null) throw new ApiError(409, '상위부서는 적용일에 존속하고 폐지 예정이 없는 부서여야 해요');
  };
  /** date 기준으로 deptId 를 parentId 아래에 두면 순환이 생기는지 */
  const cycleAt = (deptId: number, parentId: number | null, date: string) => {
    const seen = new Set<number>();
    for (let cur = parentId; cur !== null; ) {
      if (cur === deptId) return true;
      if (seen.has(cur)) return true;
      seen.add(cur);
      const d = depts.find((x) => x.deptId === cur);
      cur = d ? histAt(d, date)?.parentDeptId ?? null : null;
    }
    return false;
  };
  const view = (d: MockDept, h: MockHist): DeptView => ({
    deptId: d.deptId, deptCode: d.deptCode, name: h.name, parentDeptId: h.parentDeptId, headEmpId: h.headEmpId, sortOrder: h.sortOrder,
    validFrom: h.validFrom, validTo: h.validTo, changeType: h.changeType, deptValidFrom: d.validFrom, deptValidTo: d.validTo,
  });

  return {
    async tree(date) {
      const live = depts.flatMap((d) => { const h = histAt(d, date); return h ? [{ d, h }] : []; });
      const ids = new Set(live.map((x) => x.d.deptId));
      const node = ({ d, h }: (typeof live)[number]): DeptNode => ({
        deptId: d.deptId, deptCode: d.deptCode, name: h.name, parentDeptId: h.parentDeptId, headEmpId: h.headEmpId, sortOrder: h.sortOrder,
        validFrom: h.validFrom, validTo: h.validTo,
        children: sortNodes(live.filter((c) => c.h.parentDeptId === d.deptId).map(node)),
      });
      // 상위부서가 그날 없으면 최상위로 보인다(백엔드와 같음)
      return { baseDate: date, roots: sortNodes(live.filter((x) => x.h.parentDeptId === null || !ids.has(x.h.parentDeptId)).map(node)) };
    },
    async employees() { return employees.map((e) => ({ ...e })); },
    async createDept(input) {
      check(input);
      if (depts.some((d) => d.deptCode === input.deptCode)) throw new ApiError(409, `이미 있는 부서코드예요: ${input.deptCode}`);
      checkParent(input.parentDeptId, input.effectiveDate);
      const h: MockHist = { validFrom: input.effectiveDate, validTo: null, name: input.name.trim(), parentDeptId: input.parentDeptId, headEmpId: input.headEmpId, sortOrder: input.sortOrder, changeType: 'CREATE' };
      const d: MockDept = { deptId: nextId++, deptCode: input.deptCode, validFrom: input.effectiveDate, validTo: null, hist: [h] };
      depts.push(d);
      return view(d, h);
    },
    async changeDept(deptId, input) {
      check(input);
      const d = require(deptId);
      if (d.validTo !== null) throw new ApiError(409, '폐지(예정)된 부서는 바꿀 수 없어요');
      const last = d.hist[d.hist.length - 1];
      if (input.effectiveDate <= last.validFrom) throw new ApiError(409, `적용일은 마지막 변경일(${last.validFrom})보다 뒤여야 해요`);
      checkParent(input.parentDeptId, input.effectiveDate);
      const later = [input.effectiveDate, ...depts.flatMap((x) => x.hist.map((h) => h.validFrom)).filter((v) => v > input.effectiveDate)];
      if (later.some((date) => cycleAt(deptId, input.parentDeptId, date))) throw new ApiError(409, '자기 자신이나 하위부서 아래로는 옮길 수 없어요');
      const name = input.name.trim();
      const changeType: DeptChangeType = input.parentDeptId !== last.parentDeptId ? 'MOVE' : name !== last.name ? 'RENAME' : 'UPDATE';
      last.validTo = input.effectiveDate;
      const h: MockHist = { validFrom: input.effectiveDate, validTo: null, name, parentDeptId: input.parentDeptId, headEmpId: input.headEmpId, sortOrder: input.sortOrder, changeType };
      d.hist.push(h);
      return view(d, h);
    },
  };
}

/** 백엔드 NODE_ORDER 와 같다: 정렬 순서 → 부서코드 */
export const sortNodes = (l: DeptNode[]) =>
  [...l].sort((a, b) => a.sortOrder - b.sortOrder || (a.deptCode < b.deptCode ? -1 : a.deptCode > b.deptCode ? 1 : 0));

export const orgApi: OrgApi = import.meta.env.VITE_API === 'mock' ? createMockOrgApi() : httpOrgApi;
