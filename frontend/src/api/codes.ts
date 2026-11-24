/**
 * 공통코드 API (#11, #140). 백엔드: backend/ CommonCodeController (Spring).
 * 응답 형식은 backend CodeDtos 와 같다.
 */
import { CODE_GROUPS, CODES, DEMO_USAGES } from '../data/codes';
import { call } from './http';
import { ApiError } from './types';

/** 로직이 참조하는 그룹코드. 백엔드 CodeGroups 와 같다. */
export const CODE_GROUP = {
  JOB_GRADE: 'JOB_GRADE',
  JOB_TITLE: 'JOB_TITLE',
  JOB_FAMILY: 'JOB_FAMILY',
  EMPLOYMENT_TYPE: 'EMPLOYMENT_TYPE',
  WORK_LOCATION: 'WORK_LOCATION',
  RETIREMENT_REASON: 'RETIREMENT_REASON',
} as const;

/** 코드값·그룹코드 형식. 백엔드 CodeGroups.CODE_PATTERN, DB CHECK 와 같다. */
export const CODE_PATTERN = /^[A-Z][A-Z0-9_]{0,29}$/;
export const MAX_SORT_ORDER = 99999;
/** 사용처 행 목록 한 페이지 크기. 백엔드 CommonCodeService.USAGE_PAGE_SIZE 와 같다. */
export const USAGE_PAGE_SIZE = 20;
/** 참조 중인 코드 삭제를 막을 때의 안내. 백엔드 CommonCodeService.IN_USE_MESSAGE 와 같다. */
export const IN_USE_MESSAGE = '사용 중이라 삭제할 수 없어요. 사용 안 함으로 바꿔 주세요';

/**
 * @property system   시스템 그룹(로직이 참조). 삭제·사용 여부 변경 불가
 * @property editPage 다른 모듈 화면에서 편집하는 그룹의 화면(PageId). null 이면 이 화면에서 편집
 */
export interface CodeGroup {
  groupCode: string; name: string; description: string | null; sortOrder: number; active: boolean;
  system: boolean; editPage: string | null;
}
/** @property system 기본 코드(로직이 코드값을 참조). 삭제 불가 */
export interface Code { groupCode: string; code: string; name: string; description: string | null; sortOrder: number; active: boolean; system: boolean }
/** 관리 화면 코드 목록 한 줄. inUse: 어떤 테이블이든 참조 중, usageCount: 참조 건수 합 */
export interface CodeListItem extends Code { inUse: boolean; usageCount: number }
/** 코드 수정 입력. 코드값은 바꿀 수 없다. */
export interface CodeUpdate { name: string; description: string | null; sortOrder: number; active: boolean }
export interface CodeCreate extends CodeUpdate { code: string }
/** 그룹 수정 입력. 그룹코드는 바꿀 수 없다. */
export type GroupUpdate = CodeUpdate;
export interface GroupCreate extends GroupUpdate { groupCode: string }

export interface UsageTable { table: string; label: string; count: number }
/** rowsVisible=false 면 행 목록(사번·이름)을 볼 권한이 없다(EMP_READ_ALL). 건수만 보인다 */
export interface UsageSummary { groupCode: string; code: string; total: number; tables: UsageTable[]; rowsVisible: boolean }
export interface UsageRow { empNo: string; name: string; date: string | null }
export interface UsagePage { table: string; label: string; dateLabel: string; total: number; page: number; size: number; rows: UsageRow[] }

export interface CodeApi {
  /** 여러 그룹의 사용 중인 코드 (useCodes 용) */
  activeCodes(groups: string[]): Promise<Record<string, Code[]>>;
  groups(): Promise<CodeGroup[]>;
  createGroup(input: GroupCreate): Promise<CodeGroup>;
  updateGroup(group: string, input: GroupUpdate): Promise<CodeGroup>;
  deleteGroup(group: string): Promise<void>;
  /** 관리 화면용. includeInactive=true 면 사용 중지 코드도 */
  codes(group: string, includeInactive: boolean): Promise<CodeListItem[]>;
  create(group: string, input: CodeCreate): Promise<Code>;
  update(group: string, code: string, input: CodeUpdate): Promise<Code>;
  deleteCode(group: string, code: string): Promise<void>;
  usages(group: string, code: string): Promise<UsageSummary>;
  usageRows(group: string, code: string, table: string, page: number): Promise<UsagePage>;
}

const enc = encodeURIComponent;
const codePath = (group: string, code: string) => `/code-groups/${enc(group)}/codes/${enc(code)}`;

export const httpCodeApi: CodeApi = {
  activeCodes: (groups) => call('GET', `/codes?groups=${groups.map(enc).join(',')}`),
  groups: () => call('GET', '/code-groups'),
  createGroup: (input) => call('POST', '/code-groups', input),
  updateGroup: (group, input) => call('PUT', `/code-groups/${enc(group)}`, input),
  deleteGroup: (group) => call('DELETE', `/code-groups/${enc(group)}`),
  codes: (group, includeInactive) => call('GET', `/code-groups/${enc(group)}/codes?includeInactive=${includeInactive}`),
  create: (group, input) => call('POST', `/code-groups/${enc(group)}/codes`, input),
  update: (group, code, input) => call('PUT', codePath(group, code), input),
  deleteCode: (group, code) => call('DELETE', codePath(group, code)),
  usages: (group, code) => call('GET', `${codePath(group, code)}/usages`),
  usageRows: (group, code, table, page) => call('GET', `${codePath(group, code)}/usages?table=${enc(table)}&page=${page}`),
};

/** 입력 검사. 백엔드 CodeDtos 의 Bean Validation 과 같은 규칙(서버도 다시 검사한다). 문제가 없으면 null. */
export function validateCode(input: CodeUpdate & { code?: string }): string | null {
  if (input.code !== undefined && !CODE_PATTERN.test(input.code)) return '코드값은 영문 대문자로 시작하고 영문 대문자·숫자·밑줄만 30자 이내로 써 주세요';
  if (!input.name.trim()) return '코드명을 입력해 주세요';
  if (input.name.length > 100) return '코드명은 100자 이내로 써 주세요';
  return validateCommon(input);
}

/** 그룹 입력 검사. 백엔드 GroupCreateRequest·GroupUpdateRequest 와 같은 규칙. */
export function validateGroup(input: GroupUpdate & { groupCode?: string }): string | null {
  if (input.groupCode !== undefined && !CODE_PATTERN.test(input.groupCode)) return '그룹코드는 영문 대문자로 시작하고 영문 대문자·숫자·밑줄만 30자 이내로 써 주세요';
  if (!input.name.trim()) return '그룹명을 입력해 주세요';
  if (input.name.length > 100) return '그룹명은 100자 이내로 써 주세요';
  return validateCommon(input);
}

function validateCommon(input: CodeUpdate): string | null {
  if (input.description && input.description.length > 500) return '설명은 500자 이내로 써 주세요';
  if (!Number.isInteger(input.sortOrder) || input.sortOrder < 0 || input.sortOrder > MAX_SORT_ORDER) return `정렬 순서는 0~${MAX_SORT_ORDER} 사이 정수여야 해요`;
  return null;
}

const bySort = <T extends { sortOrder: number }>(key: (x: T) => string) => (a: T, b: T) =>
  a.sortOrder - b.sortOrder || (key(a) < key(b) ? -1 : key(a) > key(b) ? 1 : 0);
const clean = (s: string | null) => (s && s.trim() ? s.trim() : null);

/** 가짜 백엔드의 사용처 데이터. 키는 `그룹/코드`, 값은 테이블별 행 목록. */
export interface MockUsage { table: string; label: string; dateLabel: string; rows: UsageRow[] }
export type MockUsages = Record<string, MockUsage[]>;

/**
 * 브라우저 안의 가짜 백엔드. 새로 고치면 시드로 돌아간다.
 * rowsVisible=false 면 EMP_READ_ALL 이 없는 역할처럼 건수만 준다.
 */
export function createMockCodeApi(opts: { usages?: MockUsages; rowsVisible?: boolean } = {}): CodeApi {
  let groups = structuredClone(CODE_GROUPS);
  let codes = structuredClone(CODES);
  const usageData = structuredClone(opts.usages ?? DEMO_USAGES);
  const rowsVisible = opts.rowsVisible ?? true;
  const requireGroup = (g: string) => {
    const found = groups.find((x) => x.groupCode === g);
    if (!found) throw new ApiError(404, `코드그룹이 없어요: ${g}`);
    return found;
  };
  const requireCode = (g: string, code: string) => {
    requireGroup(g);
    const found = codes.find((c) => c.groupCode === g && c.code === code);
    if (!found) throw new ApiError(404, `코드가 없어요: ${g}/${code}`);
    return found;
  };
  const check = (err: string | null) => { if (err) throw new ApiError(400, err); };
  const tablesOf = (g: string, code: string): UsageTable[] =>
    (usageData[`${g}/${code}`] ?? []).filter((u) => u.rows.length > 0).map((u) => ({ table: u.table, label: u.label, count: u.rows.length }));
  const describe = (t: UsageTable[]) => t.map((u) => `${u.label} ${u.count}건`).join(', ');
  return {
    async activeCodes(wanted) {
      const out: Record<string, Code[]> = {};
      for (const g of new Set(wanted)) {
        const group = requireGroup(g);
        out[g] = group.active ? codes.filter((c) => c.groupCode === g && c.active).sort(bySort((c) => c.code)).map((c) => ({ ...c })) : [];
      }
      return out;
    },
    async groups() { return structuredClone(groups).sort(bySort((g) => g.groupCode)); },
    async createGroup(input) {
      check(validateGroup(input));
      if (groups.some((g) => g.groupCode === input.groupCode)) throw new ApiError(409, `이미 있는 그룹코드예요: ${input.groupCode}`);
      const created: CodeGroup = { groupCode: input.groupCode, name: input.name.trim(), description: clean(input.description), sortOrder: input.sortOrder, active: input.active, system: false, editPage: null };
      groups = [...groups, created];
      return { ...created };
    },
    async updateGroup(g, input) {
      const cur = requireGroup(g);
      check(validateGroup(input));
      if (cur.system && cur.active !== input.active) throw new ApiError(409, '시스템 그룹은 사용 여부를 바꿀 수 없어요. 이름·설명·순서만 고칠 수 있어요');
      const next: CodeGroup = { ...cur, name: input.name.trim(), description: clean(input.description), sortOrder: input.sortOrder, active: input.active };
      groups = groups.map((x) => (x === cur ? next : x));
      return { ...next };
    },
    async deleteGroup(g) {
      const cur = requireGroup(g);
      if (cur.system) throw new ApiError(409, '시스템 그룹이라 삭제할 수 없어요. 사용하지 않는 코드는 사용 안 함으로 바꿔 주세요');
      for (const c of codes.filter((x) => x.groupCode === g)) {
        if (c.system) throw new ApiError(409, `기본 코드(${c.code})가 있어 그룹을 삭제할 수 없어요`);
        const used = tablesOf(g, c.code);
        if (used.length) throw new ApiError(409, `그룹 안의 코드(${c.code})가 ${IN_USE_MESSAGE} (${describe(used)})`);
      }
      codes = codes.filter((x) => x.groupCode !== g);
      groups = groups.filter((x) => x !== cur);
    },
    async codes(g, includeInactive) {
      requireGroup(g);
      return codes.filter((c) => c.groupCode === g && (includeInactive || c.active)).sort(bySort((c) => c.code)).map((c) => {
        const usageCount = tablesOf(g, c.code).reduce((n, t) => n + t.count, 0);
        return { ...c, inUse: usageCount > 0, usageCount };
      });
    },
    async create(g, input) {
      requireGroup(g);
      check(validateCode(input));
      if (codes.some((c) => c.groupCode === g && c.code === input.code)) throw new ApiError(409, `이미 있는 코드값이에요: ${g}/${input.code}`);
      const created: Code = { groupCode: g, code: input.code, name: input.name.trim(), description: clean(input.description), sortOrder: input.sortOrder, active: input.active, system: false };
      codes = [...codes, created];
      return { ...created };
    },
    async update(g, code, input) {
      const cur = requireCode(g, code);
      check(validateCode(input));
      const next: Code = { ...cur, name: input.name.trim(), description: clean(input.description), sortOrder: input.sortOrder, active: input.active };
      codes = codes.map((c) => (c === cur ? next : c));
      return { ...next };
    },
    async deleteCode(g, code) {
      const cur = requireCode(g, code);
      if (cur.system) throw new ApiError(409, '기본 코드라 삭제할 수 없어요. 사용 안 함으로 바꿔 주세요');
      const used = tablesOf(g, code);
      if (used.length) throw new ApiError(409, `${IN_USE_MESSAGE} (${describe(used)})`);
      codes = codes.filter((c) => c !== cur);
    },
    async usages(g, code) {
      requireCode(g, code);
      const tables = tablesOf(g, code);
      return { groupCode: g, code, total: tables.reduce((n, t) => n + t.count, 0), tables, rowsVisible };
    },
    async usageRows(g, code, table, page) {
      requireCode(g, code);
      if (!rowsVisible) throw new ApiError(403, '사용처 사원 목록은 전체 사원 조회 권한이 있어야 볼 수 있어요. 건수만 볼 수 있어요');
      const u = (usageData[`${g}/${code}`] ?? []).find((x) => x.table === table);
      if (!u) throw new ApiError(400, `이 코드그룹을 참조하지 않는 테이블이에요: ${table}`);
      const rows = u.rows.slice(page * USAGE_PAGE_SIZE, (page + 1) * USAGE_PAGE_SIZE);
      return { table, label: u.label, dateLabel: u.dateLabel, total: u.rows.length, page, size: USAGE_PAGE_SIZE, rows: structuredClone(rows) };
    },
  };
}

export const codeApi: CodeApi = import.meta.env.VITE_API === 'mock' ? createMockCodeApi() : httpCodeApi;
