/**
 * 근무유형 API (#25). 백엔드: backend/ WorkPatternController (Spring).
 * 응답 형식은 backend WorkPatternDtos 와 같다. 시각은 자정부터의 분(0~1439), 길이는 분.
 *
 * 법정 기준(1일·1주 한도, 휴게, 주휴일) 검사는 서버만 한다. 서버가 legal_rate 적용값으로 검사하므로
 * 화면에는 법정 수치를 두지 않는다. 화면은 형식 검사와 소정근로시간 미리보기만 한다.
 */
import { WORK_PATTERNS } from '../data/workPatterns';
import { call } from './http';
import { ApiError } from './types';

/** 근무유형 코드 형식. 백엔드 WorkPatternDtos.CODE_PATTERN, DB CHECK 와 같다. */
export const PATTERN_CODE_PATTERN = /^[A-Z0-9][A-Z0-9_]*$/;
export const MINUTES_PER_DAY = 24 * 60;

/** 요일 구분. W 근무일 · O 휴무일(무급) · H 주휴일(근로기준법 제55조 제1항 유급휴일). 월~일 7글자. */
export type DayType = 'W' | 'O' | 'H';
export const WEEKDAYS = ['월', '화', '수', '목', '금', '토', '일'] as const;
export const DAY_TYPE_LABEL: Record<DayType, string> = { W: '근무', O: '휴무', H: '주휴' };

export interface WorkPatternSpec {
  workStartMin: number;
  workEndMin: number;
  breakStartMin: number | null;
  breakMinutes: number;
  dayTypes: string;
}
export interface WorkPatternInput extends WorkPatternSpec {
  patternCode: string;
  patternName: string;
  active: boolean;
  note: string | null;
}
export interface WorkPattern extends WorkPatternInput {
  patternId: number;
  dailyContractMinutes: number;
  weeklyContractMinutes: number;
  /** 사원에게 배정된 적이 있으면 시각·휴게·요일을 바꿀 수 없다 */
  assigned: boolean;
}
export interface Violation { code: string; message: string }
export interface CheckResult { ok: boolean; dailyContractMinutes: number; weeklyContractMinutes: number; violations: Violation[] }

export interface WorkPatternApi {
  list(): Promise<WorkPattern[]>;
  /** 저장하지 않고 서버 검사(법정 기준 포함) */
  check(input: WorkPatternInput): Promise<CheckResult>;
  create(input: WorkPatternInput): Promise<WorkPattern>;
  update(patternId: number, input: WorkPatternInput): Promise<WorkPattern>;
}

export const httpWorkPatternApi: WorkPatternApi = {
  list: () => call('GET', '/attendance/work-patterns'),
  check: (input) => call('POST', '/attendance/work-patterns/check', input),
  create: (input) => call('POST', '/attendance/work-patterns', input),
  update: (id, input) => call('PUT', `/attendance/work-patterns/${id}`, input),
};

/** "HH:MM" ↔ 분 */
export const toHm = (m: number) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
export function parseHm(s: string): number | null {
  const m = /^(\d{2}):(\d{2})$/.exec(s);
  if (!m) return null;
  const h = Number(m[1]);
  const mm = Number(m[2]);
  return h < 24 && mm < 60 ? h * 60 + mm : null;
}
/** 분 → "8시간 30분" */
export function duration(m: number): string {
  const h = Math.floor(m / 60);
  const r = m % 60;
  if (h === 0) return `${r}분`;
  return r === 0 ? `${h}시간` : `${h}시간 ${r}분`;
}

/** 시업~종업 분. 종업이 시업 이하이면 다음 날 종업(백엔드 WorkPatternRules.spanMinutes 와 같다). */
export const spanMinutes = (start: number, end: number) => (end > start ? end - start : end + MINUTES_PER_DAY - start);

/** 소정근로시간 미리보기(휴게 제외, 근로기준법 제50조 제3항). 법정 한도 판정은 하지 않는다. */
export function contractMinutes(s: WorkPatternSpec): { daily: number; weekly: number } {
  const daily = spanMinutes(s.workStartMin, s.workEndMin) - s.breakMinutes;
  const workDays = [...s.dayTypes].filter((c) => c === 'W').length;
  return { daily, weekly: daily * workDays };
}

/**
 * 형식 검사. 백엔드 WorkPatternRules.checkStructure·WorkPatternDtos 와 같은 규칙(서버도 다시 검사한다). 문제가 없으면 null.
 * 주휴일 1일 이상은 DB CHECK 도 강제한다. 필요한 횟수는 서버가 legal_rate 로 다시 검사한다.
 */
export function validateWorkPattern(i: WorkPatternInput): string | null {
  if (!PATTERN_CODE_PATTERN.test(i.patternCode) || i.patternCode.length > 20) return '근무유형 코드는 영문 대문자·숫자·밑줄만 20자 이내로 써 주세요';
  if (!i.patternName.trim()) return '근무유형 이름을 입력해 주세요';
  if (i.patternName.length > 100) return '근무유형 이름은 100자 이내로 써 주세요';
  if (!/^[WOH]{7}$/.test(i.dayTypes)) return '요일 구분은 월~일 7개를 근무·휴무·주휴 중에서 골라 주세요';
  if (!i.dayTypes.includes('W')) return '근무일을 하루 이상 지정해 주세요';
  if (!i.dayTypes.includes('H')) return '주휴일을 지정해 주세요(근로기준법 제55조 제1항)';
  if (!Number.isInteger(i.breakMinutes) || i.breakMinutes < 0) return '휴게시간은 0분 이상이어야 해요';
  if (i.breakMinutes === 0 && i.breakStartMin !== null) return '휴게시간이 0분이면 휴게 시작 시각을 비워 주세요';
  if (i.breakMinutes > 0 && i.breakStartMin === null) return '휴게 시작 시각을 입력해 주세요';
  const span = spanMinutes(i.workStartMin, i.workEndMin);
  if (i.breakStartMin !== null) {
    const off = i.breakStartMin >= i.workStartMin ? i.breakStartMin - i.workStartMin : i.breakStartMin + MINUTES_PER_DAY - i.workStartMin;
    if (off <= 0 || off + i.breakMinutes >= span) return '휴게시간은 시업과 종업 사이(근로시간 도중)에 있어야 해요(근로기준법 제54조 제1항)';
  }
  if (i.note && i.note.length > 500) return '비고는 500자 이내로 써 주세요';
  return null;
}

const sameSpec = (a: WorkPatternSpec, b: WorkPatternSpec) =>
  a.workStartMin === b.workStartMin && a.workEndMin === b.workEndMin && a.breakStartMin === b.breakStartMin
  && a.breakMinutes === b.breakMinutes && a.dayTypes === b.dayTypes;

/**
 * 브라우저 안의 가짜 백엔드(데모용). 법정 기준값이 없으므로 형식 검사만 하고, 법정 기준 검사는 하지 않는다.
 * 실제 저장 거부(1일·1주 한도, 휴게 부족, 주휴일)는 서버(legal_rate)가 한다.
 */
export function createMockWorkPatternApi(): WorkPatternApi {
  let rows: WorkPattern[] = structuredClone(WORK_PATTERNS);
  let nextId = rows.reduce((m, r) => Math.max(m, r.patternId), 0) + 1;
  const check = (i: WorkPatternInput) => {
    const err = validateWorkPattern(i);
    if (err) throw new ApiError(400, err);
  };
  const clean = (s: string | null) => (s && s.trim() ? s.trim() : null);
  return {
    async list() { return structuredClone(rows).sort((a, b) => (a.patternCode < b.patternCode ? -1 : 1)); },
    async check(i) {
      const err = validateWorkPattern(i);
      const { daily, weekly } = contractMinutes(i);
      return err
        ? { ok: false, dailyContractMinutes: 0, weeklyContractMinutes: 0, violations: [{ code: 'INVALID', message: err }] }
        : { ok: true, dailyContractMinutes: daily, weeklyContractMinutes: weekly, violations: [] };
    },
    async create(i) {
      check(i);
      if (rows.some((r) => r.patternCode === i.patternCode)) throw new ApiError(409, `이미 있는 근무유형 코드예요: ${i.patternCode}`);
      const { daily, weekly } = contractMinutes(i);
      const created: WorkPattern = { ...i, patternName: i.patternName.trim(), note: clean(i.note), patternId: nextId++, dailyContractMinutes: daily, weeklyContractMinutes: weekly, assigned: false };
      rows = [...rows, created];
      return structuredClone(created);
    },
    async update(id, i) {
      check(i);
      const cur = rows.find((r) => r.patternId === id);
      if (!cur) throw new ApiError(404, `근무유형이 없어요: ${id}`);
      if (cur.patternCode !== i.patternCode) throw new ApiError(400, '근무유형 코드는 바꿀 수 없어요');
      if (cur.assigned && !sameSpec(cur, i)) throw new ApiError(409, '사원에게 배정된 근무유형은 시각·휴게·요일을 바꿀 수 없어요. 새 근무유형을 만들어 다시 배정해 주세요');
      const { daily, weekly } = contractMinutes(i);
      const next: WorkPattern = { ...cur, ...i, patternName: i.patternName.trim(), note: clean(i.note), dailyContractMinutes: daily, weeklyContractMinutes: weekly };
      rows = rows.map((r) => (r === cur ? next : r));
      return structuredClone(next);
    },
  };
}

export const workPatternApi: WorkPatternApi = import.meta.env.VITE_API === 'mock' ? createMockWorkPatternApi() : httpWorkPatternApi;
