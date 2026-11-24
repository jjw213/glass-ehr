/**
 * 근태코드 API (#143). 백엔드: backend/ AttendanceCodeController. 응답 형식은 AttendanceCodeDtos 와 같다.
 * 설계: docs/attendance-code-design.md. 사용처 보기는 공통코드 API(codeApi.usages)를 그대로 쓴다.
 */
import { ATTENDANCE_CODES } from '../data/attendanceCodes';
import { IN_USE_MESSAGE, validateCode, type UsageTable } from './codes';
import { call } from './http';
import { ApiError } from './types';

export type AttendanceGroup = 'LEAVE_TYPE' | 'ATTENDANCE_TYPE';
export const ATTENDANCE_GROUPS: { group: AttendanceGroup; label: string }[] = [
  { group: 'LEAVE_TYPE', label: '휴가 종류' },
  { group: 'ATTENDANCE_TYPE', label: '근태 구분' },
];

/** 그룹별 처리방식. 백엔드 AttendanceCodeService.PROCESS_TYPES, DB CHECK ck_common_code_process 와 같다. */
export const PROCESS_TYPES: Record<AttendanceGroup, { value: string; label: string }[]> = {
  LEAVE_TYPE: [
    { value: 'ANNUAL', label: '연차' }, { value: 'STATUTORY', label: '법정휴가' },
    { value: 'COMPANY', label: '회사 약정휴가' }, { value: 'UNPAID', label: '무급휴가' },
  ],
  ATTENDANCE_TYPE: [
    { value: 'BUSINESS_TRIP', label: '출장' }, { value: 'OUTSIDE_WORK', label: '외근' },
    { value: 'REMOTE', label: '재택' }, { value: 'EDUCATION', label: '교육' },
  ],
};
/** 화면에서 추가할 수 있는 휴가 처리방식. 연차·법정휴가는 기본 코드만(백엔드 USER_LEAVE_PROCESS_TYPES). */
export const USER_LEAVE_PROCESS_TYPES = ['COMPANY', 'UNPAID'];

export const processLabel = (group: AttendanceGroup, value: string) =>
  PROCESS_TYPES[group].find((p) => p.value === value)?.label ?? value;

/** 법정 기준값(legal_rate, 조회일 기준). value=null 이면 아직 등록되지 않았다. 화면에서 고치지 않는다. */
export interface LegalValue { itemCode: string; label: string; value: number | null; unit: string; effectiveFrom: string | null }

/**
 * 휴가 종류 상세 설정.
 * @property paid null = 법정휴가(유급 기간은 legalValues)
 * @property annualLimitDays null = 한도 없음
 */
export interface LeaveDetail {
  unitDay: boolean; unitHalfDay: boolean; unitHour: boolean; paid: boolean | null; proofRequired: boolean;
  annualLimitDays: number | null; genderLimit: 'F' | 'M' | null; legalBasis: string | null; legalValues: LegalValue[];
}
export interface AttendanceCode {
  groupCode: string; code: string; name: string; description: string | null; sortOrder: number; active: boolean;
  system: boolean; processType: string; inUse: boolean; usageCount: number; leave: LeaveDetail | null;
}
export type LeaveDetailInput = Pick<LeaveDetail, 'unitDay' | 'unitHalfDay' | 'unitHour' | 'paid' | 'proofRequired' | 'annualLimitDays'>;
export interface AttendanceCodeUpdate {
  name: string; description: string | null; sortOrder: number; active: boolean; processType: string; leave: LeaveDetailInput | null;
}
export interface AttendanceCodeCreate extends AttendanceCodeUpdate { code: string }

export interface AttendanceCodeApi {
  list(group: AttendanceGroup): Promise<AttendanceCode[]>;
  create(group: AttendanceGroup, input: AttendanceCodeCreate): Promise<AttendanceCode>;
  update(group: AttendanceGroup, code: string, input: AttendanceCodeUpdate): Promise<AttendanceCode>;
  remove(group: AttendanceGroup, code: string): Promise<void>;
}

const enc = encodeURIComponent;
export const httpAttendanceCodeApi: AttendanceCodeApi = {
  list: (group) => call('GET', `/attendance-codes/${enc(group)}`),
  create: (group, input) => call('POST', `/attendance-codes/${enc(group)}`, input),
  update: (group, code, input) => call('PUT', `/attendance-codes/${enc(group)}/${enc(code)}`, input),
  remove: (group, code) => call('DELETE', `/attendance-codes/${enc(group)}/${enc(code)}`),
};

/** 휴가 상세 설정 입력 검사. 백엔드 LeaveDetailRequest·AttendanceCodeService 와 같은 규칙(서버도 다시 검사한다). */
export function validateLeave(processType: string, d: LeaveDetailInput, system: boolean): string | null {
  if (!d.unitDay && !d.unitHalfDay && !d.unitHour) return '사용 단위를 하나 이상 골라 주세요';
  if (system) return null;   // 기본 코드의 유급 여부·한도는 바꾸지 않는다
  if (d.paid === null) return '유급 여부를 정해 주세요';
  if (processType === 'UNPAID' && d.paid) return '무급휴가는 유급으로 둘 수 없어요';
  const n = d.annualLimitDays;
  if (n !== null) {
    if (!Number.isFinite(n) || n < 0.5) return '연간 한도는 0.5일 이상이어야 해요';
    if (n > 366) return '연간 한도는 366일 이하여야 해요';
    if (Math.round(n * 10) !== n * 10) return '연간 한도는 소수 첫째 자리까지 써 주세요';
  }
  return null;
}

const clean = (s: string | null) => (s && s.trim() ? s.trim() : null);

/** 브라우저 안의 가짜 백엔드. 새로 고치면 시드로 돌아간다. usages 는 `그룹/코드` → 사용처 테이블(공통코드 데모와 같은 키). */
export function createMockAttendanceCodeApi(opts: { usages?: Record<string, UsageTable[]> } = {}): AttendanceCodeApi {
  let codes = structuredClone(ATTENDANCE_CODES);
  const usages = opts.usages ?? {};
  const count = (g: string, code: string) => (usages[`${g}/${code}`] ?? []).reduce((n, t) => n + t.count, 0);
  const withUsage = (c: AttendanceCode): AttendanceCode => {
    const usageCount = count(c.groupCode, c.code);
    return { ...structuredClone(c), inUse: usageCount > 0, usageCount };
  };
  const find = (g: string, code: string) => {
    const found = codes.find((c) => c.groupCode === g && c.code === code);
    if (!found) throw new ApiError(404, `코드가 없어요: ${g}/${code}`);
    return found;
  };
  const check = (err: string | null) => { if (err) throw new ApiError(400, err); };
  const requireProcess = (g: AttendanceGroup, p: string) => {
    if (!PROCESS_TYPES[g].some((x) => x.value === p)) throw new ApiError(400, `이 그룹에 없는 처리방식이에요: ${p}`);
    if (g === 'LEAVE_TYPE' && !USER_LEAVE_PROCESS_TYPES.includes(p)) throw new ApiError(400, '연차·법정휴가는 기본 코드만 둘 수 있어요. 회사 약정휴가나 무급휴가로 추가해 주세요');
  };
  const leaveOf = (g: AttendanceGroup, input: AttendanceCodeUpdate, system: boolean, cur: LeaveDetail | null): LeaveDetail | null => {
    if (g !== 'LEAVE_TYPE') {
      if (input.leave) throw new ApiError(400, '근태 구분에는 휴가 상세 설정을 두지 않아요');
      return null;
    }
    if (!input.leave) throw new ApiError(400, '휴가 상세 설정을 입력해 주세요');
    check(validateLeave(input.processType, input.leave, system));
    const d = input.leave;
    if (system && cur) {
      if (d.paid !== cur.paid || d.annualLimitDays !== cur.annualLimitDays) throw new ApiError(409, '법정휴가·연차의 유급 여부와 일수는 법령 기준값을 따라요. 바꿀 수 없어요');
      return { ...cur, unitDay: d.unitDay, unitHalfDay: d.unitHalfDay, unitHour: d.unitHour, proofRequired: d.proofRequired };
    }
    return { ...d, genderLimit: cur?.genderLimit ?? null, legalBasis: cur?.legalBasis ?? null, legalValues: cur?.legalValues ?? [] };
  };
  return {
    async list(g) {
      return codes.filter((c) => c.groupCode === g)
        .sort((a, b) => a.sortOrder - b.sortOrder || (a.code < b.code ? -1 : a.code > b.code ? 1 : 0)).map(withUsage);
    },
    async create(g, input) {
      requireProcess(g, input.processType);
      check(validateCode(input));
      if (codes.some((c) => c.groupCode === g && c.code === input.code)) throw new ApiError(409, `이미 있는 코드값이에요: ${g}/${input.code}`);
      const created: AttendanceCode = {
        groupCode: g, code: input.code, name: input.name.trim(), description: clean(input.description), sortOrder: input.sortOrder,
        active: input.active, system: false, processType: input.processType, inUse: false, usageCount: 0, leave: leaveOf(g, input, false, null),
      };
      codes = [...codes, created];
      return withUsage(created);
    },
    async update(g, code, input) {
      const cur = find(g, code);
      check(validateCode(input));
      if (cur.system) {
        if (input.processType !== cur.processType) throw new ApiError(409, '기본 코드는 처리방식을 바꿀 수 없어요');
      } else requireProcess(g, input.processType);
      const next: AttendanceCode = {
        ...cur, name: input.name.trim(), description: clean(input.description), sortOrder: input.sortOrder, active: input.active,
        processType: input.processType, leave: leaveOf(g, input, cur.system, cur.leave),
      };
      codes = codes.map((c) => (c === cur ? next : c));
      return withUsage(next);
    },
    async remove(g, code) {
      const cur = find(g, code);
      if (cur.system) throw new ApiError(409, '기본 코드라 삭제할 수 없어요. 사용 안 함으로 바꿔 주세요');
      const used = usages[`${g}/${code}`] ?? [];
      if (used.length) throw new ApiError(409, `${IN_USE_MESSAGE} (${used.map((u) => `${u.label} ${u.count}건`).join(', ')})`);
      codes = codes.filter((c) => c !== cur);
    },
  };
}

export const attendanceCodeApi: AttendanceCodeApi = import.meta.env.VITE_API === 'mock' ? createMockAttendanceCodeApi() : httpAttendanceCodeApi;
