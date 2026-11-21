/**
 * 유연근무제 API (#38). 백엔드: backend/ FlexibleWorkController (Spring).
 * 법정 한도(단위기간·정산기간·상한·휴식)와 코어타임 검사는 서버만 한다(legal_rate 적용값). 화면에는 법정 수치를 두지 않는다.
 * 화면은 형식 검사(HH:MM, 종업 > 시업, 휴게 ≤ 근로시간)와 합계 미리보기만 한다.
 */
import { call } from './http';
import { ApiError } from './types';

export type WorkSystem = 'FLEXIBLE_SHORT' | 'FLEXIBLE_WRITTEN' | 'FLEXIBLE_LONG' | 'SELECTIVE' | 'DISCRETIONARY';
export const WORK_SYSTEM_LABEL: Record<WorkSystem, string> = {
  FLEXIBLE_SHORT: '탄력적 근로시간제(2주 이내)',
  FLEXIBLE_WRITTEN: '탄력적 근로시간제(3개월 이내)',
  FLEXIBLE_LONG: '탄력적 근로시간제(3개월 초과)',
  SELECTIVE: '선택적 근로시간제',
  DISCRETIONARY: '재량근로',
};

export interface FlexAssign {
  flexId: number; empId: number; workSystem: WorkSystem; article: string;
  periodStart: string; periodEnd: string; jobCode: string | null;
  coreStart: string | null; coreEnd: string | null; deemedMinutes: number | null;
  agreementId: number | null; note: string | null;
}
/** start·end: 'YYYY-MM-DDTHH:MM:SS' (서울 시각), 근로 없는 날은 null·0 */
export interface PlanDay { date: string; start: string | null; end: string | null; minutes: number }
export interface FlexDetail { assign: FlexAssign; totalLimitMinutes: number; plan: PlanDay[] }
export interface SettleDay { date: string; dayKind: string; planMinutes: number; actualMinutes: number; overtimeMinutes: number }
export interface Settlement {
  flexId: number; workSystem: WorkSystem; periodStart: string; periodEnd: string; workFrom: string; workTo: string;
  check: string; totalLimitMinutes: number; actualMinutes: number; recognizedMinutes: number; overtimeMinutes: number;
  nightMinutes: number; holidayMinutes: number; coreTimeMissDays: number; days: SettleDay[];
}

export interface FlexWorkApi {
  mine(): Promise<FlexAssign[]>;
  detail(flexId: number): Promise<FlexDetail>;
  savePlan(flexId: number, plan: PlanDay[]): Promise<FlexDetail>;
  settlement(flexId: number): Promise<Settlement>;
}

export const httpFlexWorkApi: FlexWorkApi = {
  mine: () => call('GET', '/attendance/flex-work/me'),
  detail: (id) => call('GET', `/attendance/flex-work/${id}`),
  savePlan: (id, plan) => call('PUT', `/attendance/flex-work/${id}/plan`, plan),
  settlement: (id) => call('GET', `/attendance/flex-work/${id}/settlement`),
};

/** 기간의 모든 날짜(YYYY-MM-DD). 시간대 영향을 받지 않도록 UTC 날짜 산술만 쓴다. */
export function datesBetween(from: string, to: string): string[] {
  const out: string[] = [];
  const end = Date.parse(`${to}T00:00:00Z`);
  for (let t = Date.parse(`${from}T00:00:00Z`); t <= end; t += 86_400_000) out.push(new Date(t).toISOString().slice(0, 10));
  return out;
}

const HM = /^([01]\d|2[0-3]):([0-5]\d)$/;
export const parseHm = (s: string): number | null => {
  const m = HM.exec(s.trim());
  return m ? Number(m[1]) * 60 + Number(m[2]) : null;
};
export const hmOf = (iso: string | null) => (iso ? iso.slice(11, 16) : '');

/** 화면 한 줄(시업·종업·휴게분). 모두 비우면 근로하지 않는 날. */
export interface PlanRow { date: string; start: string; endDate: string; end: string; breakMinutes: string }

export function rowOf(p: PlanDay | undefined, date: string): PlanRow {
  if (!p || !p.start || !p.end) return { date, start: '', endDate: date, end: '', breakMinutes: '' };
  const span = (Date.parse(`${p.end}Z`) - Date.parse(`${p.start}Z`)) / 60_000;
  return { date, start: hmOf(p.start), endDate: p.end.slice(0, 10), end: hmOf(p.end), breakMinutes: String(span - p.minutes) };
}

/** 화면 줄을 ISO 서울 시각으로 변환한다. 종업일이 다르면 자정을 넘는 근무로 저장한다. */
export function toPlanDay(r: PlanRow): PlanDay | string {
  if (r.start === '' && r.end === '' && r.breakMinutes === '') {
    return { date: r.date, start: null, end: null, minutes: 0 };
  }
  if (parseHm(r.start) === null || parseHm(r.end) === null) return `${r.date} 시업·종업 시각을 입력해 주세요`;
  const start = `${r.date}T${r.start}:00`;
  const end = `${r.endDate}T${r.end}:00`;
  const span = (Date.parse(`${end}Z`) - Date.parse(`${start}Z`)) / 60_000;
  if (!Number.isFinite(span) || span <= 0) return `${r.date} 종업은 시업보다 늦어야 해요`;
  const brk = Number(r.breakMinutes);
  if (!Number.isInteger(brk) || brk < 0 || brk >= span) return `${r.date} 휴게는 0분 이상, 근로시간보다 짧아야 해요`;
  return { date: r.date, start, end, minutes: span - brk };
}

export const duration = (m: number) => {
  const h = Math.floor(m / 60);
  const r = m % 60;
  return h === 0 ? `${r}분` : r === 0 ? `${h}시간` : `${h}시간 ${r}분`;
};

/** 백엔드 없이 화면을 볼 때(VITE_API=mock)와 테스트용. 서버 법정 검사는 흉내 내지 않는다. */
export function createMockFlexWorkApi(): FlexWorkApi {
  const assigns: FlexAssign[] = [{
    flexId: 1, empId: 5, workSystem: 'SELECTIVE', article: '근로기준법 제52조', periodStart: '2026-03-02', periodEnd: '2026-03-08',
    jobCode: 'GENERAL', coreStart: '10:00', coreEnd: '15:00', deemedMinutes: null, agreementId: 1, note: null,
  }];
  const plans = new Map<number, PlanDay[]>([[1, []]]);
  const find = (id: number) => {
    const a = assigns.find((x) => x.flexId === id);
    if (!a) throw new ApiError(404, '유연근무 배정이 없어요');
    return a;
  };
  const detail = (id: number): FlexDetail => ({ assign: find(id), totalLimitMinutes: 0, plan: plans.get(id) ?? [] });
  return {
    mine: async () => assigns,
    detail: async (id) => detail(id),
    savePlan: async (id, plan) => { find(id); plans.set(id, plan.filter((p) => p.start)); return detail(id); },
    settlement: async (id) => {
      const a = find(id);
      const days = (plans.get(id) ?? []).map((p) => ({ date: p.date, dayKind: 'WORKDAY', planMinutes: p.minutes, actualMinutes: 0, overtimeMinutes: 0 }));
      return {
        flexId: id, workSystem: a.workSystem, periodStart: a.periodStart, periodEnd: a.periodEnd, workFrom: a.periodStart,
        workTo: a.periodEnd, check: 'OK', totalLimitMinutes: 0, actualMinutes: 0, recognizedMinutes: 0, overtimeMinutes: 0,
        nightMinutes: 0, holidayMinutes: 0, coreTimeMissDays: 0, days,
      };
    },
  };
}

export const flexWorkApi: FlexWorkApi = import.meta.env.VITE_API === 'mock' ? createMockFlexWorkApi() : httpFlexWorkApi;
