/**
 * 공통코드 API (#11). 백엔드: backend/ CommonCodeController (Spring).
 * 응답 형식은 backend CodeDtos 와 같다.
 */
import { CODE_GROUPS, CODES } from '../data/codes';
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

/** 코드값 형식. 백엔드 CodeGroups.CODE_PATTERN, DB CHECK 와 같다. */
export const CODE_PATTERN = /^[A-Z][A-Z0-9_]{0,29}$/;
export const MAX_SORT_ORDER = 99999;

export interface CodeGroup { groupCode: string; name: string; description: string | null; sortOrder: number; active: boolean }
export interface Code { groupCode: string; code: string; name: string; description: string | null; sortOrder: number; active: boolean }
/** 코드 수정 입력. 코드값은 바꿀 수 없다. */
export interface CodeUpdate { name: string; description: string | null; sortOrder: number; active: boolean }
export interface CodeCreate extends CodeUpdate { code: string }

export interface CodeApi {
  /** 여러 그룹의 사용 중인 코드 (useCodes 용) */
  activeCodes(groups: string[]): Promise<Record<string, Code[]>>;
  groups(): Promise<CodeGroup[]>;
  /** 관리 화면용. includeInactive=true 면 사용 중지 코드도 */
  codes(group: string, includeInactive: boolean): Promise<Code[]>;
  create(group: string, input: CodeCreate): Promise<Code>;
  update(group: string, code: string, input: CodeUpdate): Promise<Code>;
}

const enc = encodeURIComponent;

export const httpCodeApi: CodeApi = {
  activeCodes: (groups) => call('GET', `/codes?groups=${groups.map(enc).join(',')}`),
  groups: () => call('GET', '/code-groups'),
  codes: (group, includeInactive) => call('GET', `/code-groups/${enc(group)}/codes?includeInactive=${includeInactive}`),
  create: (group, input) => call('POST', `/code-groups/${enc(group)}/codes`, input),
  update: (group, code, input) => call('PUT', `/code-groups/${enc(group)}/codes/${enc(code)}`, input),
};

/** 입력 검사. 백엔드 CodeDtos 의 Bean Validation 과 같은 규칙(서버도 다시 검사한다). 문제가 없으면 null. */
export function validateCode(input: CodeUpdate & { code?: string }): string | null {
  if (input.code !== undefined && !CODE_PATTERN.test(input.code)) return '코드값은 영문 대문자로 시작하고 영문 대문자·숫자·밑줄만 30자 이내로 써 주세요';
  if (!input.name.trim()) return '코드명을 입력해 주세요';
  if (input.name.length > 100) return '코드명은 100자 이내로 써 주세요';
  if (input.description && input.description.length > 500) return '설명은 500자 이내로 써 주세요';
  if (!Number.isInteger(input.sortOrder) || input.sortOrder < 0 || input.sortOrder > MAX_SORT_ORDER) return `정렬 순서는 0~${MAX_SORT_ORDER} 사이 정수여야 해요`;
  return null;
}

const bySort = (a: Code, b: Code) => a.sortOrder - b.sortOrder || (a.code < b.code ? -1 : a.code > b.code ? 1 : 0);
const clean = (s: string | null) => (s && s.trim() ? s.trim() : null);

/** 브라우저 안의 가짜 백엔드. 새로 고치면 시드로 돌아간다. */
export function createMockCodeApi(): CodeApi {
  const groups = structuredClone(CODE_GROUPS);
  let codes = structuredClone(CODES);
  const requireGroup = (g: string) => {
    const found = groups.find((x) => x.groupCode === g);
    if (!found) throw new ApiError(404, `코드그룹이 없어요: ${g}`);
    return found;
  };
  const check = (input: CodeUpdate & { code?: string }) => {
    const err = validateCode(input);
    if (err) throw new ApiError(400, err);
  };
  return {
    async activeCodes(wanted) {
      const out: Record<string, Code[]> = {};
      for (const g of new Set(wanted)) {
        const group = requireGroup(g);
        out[g] = group.active ? codes.filter((c) => c.groupCode === g && c.active).sort(bySort).map((c) => ({ ...c })) : [];
      }
      return out;
    },
    async groups() { return structuredClone(groups).sort((a, b) => a.sortOrder - b.sortOrder); },
    async codes(g, includeInactive) {
      requireGroup(g);
      return codes.filter((c) => c.groupCode === g && (includeInactive || c.active)).sort(bySort).map((c) => ({ ...c }));
    },
    async create(g, input) {
      requireGroup(g);
      check(input);
      if (codes.some((c) => c.groupCode === g && c.code === input.code)) throw new ApiError(409, `이미 있는 코드값이에요: ${g}/${input.code}`);
      const created: Code = { groupCode: g, code: input.code, name: input.name.trim(), description: clean(input.description), sortOrder: input.sortOrder, active: input.active };
      codes = [...codes, created];
      return { ...created };
    },
    async update(g, code, input) {
      requireGroup(g);
      check(input);
      const cur = codes.find((c) => c.groupCode === g && c.code === code);
      if (!cur) throw new ApiError(404, `코드가 없어요: ${g}/${code}`);
      const next: Code = { ...cur, name: input.name.trim(), description: clean(input.description), sortOrder: input.sortOrder, active: input.active };
      codes = codes.map((c) => (c === cur ? next : c));
      return { ...next };
    },
  };
}

export const codeApi: CodeApi = import.meta.env.VITE_API === 'mock' ? createMockCodeApi() : httpCodeApi;
