/**
 * 출퇴근 기록 API (#28). 백엔드: backend/ PunchController (Spring). 응답 형식은 backend PunchDtos 와 같다.
 *
 * - 기록 시각은 서버 시각이다. 화면은 단말 시각을 참고로만 보내고(clientTime), 경과 시간은 서버가 준
 *   serverNow 와 단말 시계의 차이를 보정해서 센다.
 * - 원시 기록은 추가만 한다(수정·삭제 API 없음). 중복 출근·출근 없는 퇴근도 저장되고 check 로 표시된다.
 * - 근무 경과 시간은 첫 출근부터의 단순 경과(휴게 미공제)이며 근로시간 산정값이 아니다.
 */
import { call } from './http';

export type PunchType = 'IN' | 'OUT';
export type PunchCheck = 'DUPLICATE_IN' | 'OUT_WITHOUT_IN';

export interface Punch {
  punchId: number;
  type: PunchType;
  /** 서버 기록 시각(ISO, +09:00) */
  punchedAt: string;
  workDate: string;
  check: PunchCheck | null;
  checkMessage: string | null;
}

export interface PunchToday {
  serverNow: string;
  workDate: string;
  firstInAt: string | null;
  lastOutAt: string | null;
  working: boolean;
  elapsedMinutes: number | null;
  punches: Punch[];
}

export interface PunchApi {
  today(): Promise<PunchToday>;
  punch(type: PunchType): Promise<Punch>;
}

export const httpPunchApi: PunchApi = {
  today: () => call('GET', '/attendance/punches/today'),
  punch: (type) => call('POST', '/attendance/punches', { type, clientTime: new Date().toISOString() }),
};

// ---- 표시 계산 ----

/**
 * 지금 시점의 근무 경과 분(버림). serverOffsetMs = 서버 시각 - 단말 시각(조회 때 잰 값).
 * 근무 중이면 첫 출근부터 (단말 시각 + 보정)까지, 퇴근했으면 마지막 퇴근까지. 출근 기록이 없으면 null.
 */
export function elapsedMinutes(t: Pick<PunchToday, 'firstInAt' | 'lastOutAt' | 'working'>, deviceNowMs: number, serverOffsetMs: number): number | null {
  if (!t.firstInAt) return null;
  const start = Date.parse(t.firstInAt);
  const end = t.working || !t.lastOutAt ? deviceNowMs + serverOffsetMs : Date.parse(t.lastOutAt);
  return Math.max(0, Math.floor((end - start) / 60000));
}

/** 분 → "8시간 5분" */
export function formatElapsed(min: number): string {
  const h = Math.floor(min / 60);
  const m = min % 60;
  return h ? `${h}시간 ${m}분` : `${m}분`;
}

/** ISO 시각 → 서울 기준 "09:00" */
export function seoulTime(iso: string): string {
  return new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Seoul', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date(iso));
}

// ---- 데모용 가짜 백엔드 ----

const seoulDate = (ms: number) => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' }).format(new Date(ms));
/** ms → "YYYY-MM-DDTHH:mm:ss+09:00" */
const seoulIso = (ms: number) => new Date(ms + 9 * 3600_000).toISOString().slice(0, 19) + '+09:00';

/** 브라우저 안의 가짜 백엔드(VITE_API=mock). 서버와 같은 판정 규칙(backend PunchRules)을 흉내 낸다. */
export function createMockPunchApi(now: () => number = Date.now): PunchApi {
  const rows: Punch[] = [];
  const todayRows = () => rows.filter((r) => r.workDate === seoulDate(now()));
  const MSG: Record<PunchCheck, string> = {
    DUPLICATE_IN: '오늘 이미 출근 기록이 있어요. 출근 시각은 첫 출근 기록으로 보고, 이 기록은 관리자 확인 대상이에요',
    OUT_WITHOUT_IN: '오늘 출근 기록이 없어 퇴근만 기록했어요. 관리자 확인 대상이며 출근 누락은 정정 신청으로 고쳐 주세요',
  };
  return {
    async today() {
      const n = now();
      const list = todayRows();
      const firstIn = list.find((r) => r.type === 'IN')?.punchedAt ?? null;
      const working = list.length > 0 && list[list.length - 1].type === 'IN';
      const outs = firstIn ? list.filter((r) => r.type === 'OUT' && r.punchedAt >= firstIn) : [];
      const lastOutAt = !working && outs.length ? outs[outs.length - 1].punchedAt : null;
      const base = { firstInAt: firstIn, lastOutAt, working };
      return { serverNow: seoulIso(n), workDate: seoulDate(n), ...base, elapsedMinutes: elapsedMinutes(base, n, 0), punches: structuredClone(list) };
    },
    async punch(type) {
      const n = now();
      const hasIn = todayRows().some((r) => r.type === 'IN');
      const check: PunchCheck | null = type === 'IN' ? (hasIn ? 'DUPLICATE_IN' : null) : (hasIn ? null : 'OUT_WITHOUT_IN');
      const p: Punch = { punchId: rows.length + 1, type, punchedAt: seoulIso(n), workDate: seoulDate(n), check, checkMessage: check && MSG[check] };
      rows.push(p);
      return structuredClone(p);
    },
  };
}

export const punchApi: PunchApi = import.meta.env.VITE_API === 'mock' ? createMockPunchApi() : httpPunchApi;
