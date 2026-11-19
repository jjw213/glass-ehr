/**
 * 주 52시간 모니터링 API (#31). 백엔드: backend/ WeeklyHoursController (Spring). 응답 형식은 WeeklyHoursService 레코드와 같다.
 *
 * - 이번 주(서울 기준 월~일)만 본다. 한도는 서버가 legal_rate(근로기준법 제50조 제1항 + 제53조 제1항)에서 읽어 준다.
 *   화면은 한도 수치를 따로 갖지 않고 응답의 limits 만 쓴다.
 * - 경고 기준(남은 분)은 회사 설정값으로 서버가 판정해 status(NORMAL·NEAR·OVER)로 준다.
 * - /team 은 전체 조회 권한(인사) 또는 부서장만 된다. 403 이면 위젯이 부서별 표를 숨긴다.
 */
import { call } from './http';

export type WeeklyStatus = 'NORMAL' | 'NEAR' | 'OVER';

export interface WeeklyLimits {
  standardMinutes: number;
  overtimeMinutes: number;
  warnRemainMinutes: number;
}

export interface WeekSummary {
  workedMinutes: number;
  regularMinutes: number;
  extraMinutes: number;
  overtimeMinutes: number;
  holidayMinutes: number;
  limitOvertimeMinutes: number;
  limitMinutes: number;
  overtimeLimitMinutes: number;
  remainingMinutes: number;
  overtimeRemainingMinutes: number;
  status: WeeklyStatus;
  missingOut: boolean;
}

export interface EmpWeek {
  empId: number;
  empNo: string;
  name: string;
  deptId: number | null;
  week: WeekSummary;
}

export interface MyWeek {
  weekStart: string;
  weekEnd: string;
  today: string;
  limits: WeeklyLimits;
  me: EmpWeek;
}

export interface DeptWeek {
  deptId: number | null;
  deptName: string;
  nearCount: number;
  overCount: number;
  employees: EmpWeek[];
}

export interface TeamWeek {
  weekStart: string;
  weekEnd: string;
  today: string;
  limits: WeeklyLimits;
  nearCount: number;
  overCount: number;
  departments: DeptWeek[];
}

export interface WeeklyHoursApi {
  mine(): Promise<MyWeek>;
  team(): Promise<TeamWeek>;
}

export const httpWeeklyHoursApi: WeeklyHoursApi = {
  mine: () => call('GET', '/attendance/weekly-hours/me'),
  team: () => call('GET', '/attendance/weekly-hours/team'),
};

// ---- 표시 계산 ----

/** 분 → "41시간 30분" (분이 0이면 "41시간") */
export function formatHours(min: number): string {
  const sign = min < 0 ? '-' : '';
  const abs = Math.abs(min);
  const h = Math.floor(abs / 60);
  const m = abs % 60;
  if (!h) return `${sign}${m}분`;
  return m ? `${sign}${h}시간 ${m}분` : `${sign}${h}시간`;
}

/** 게이지 채움 비율(0~100, 한도 초과는 100). 한도가 0이면 0. */
export function gaugePercent(w: Pick<WeekSummary, 'workedMinutes' | 'limitMinutes'>): number {
  if (w.limitMinutes <= 0) return 0;
  return Math.min(100, Math.max(0, Math.floor((w.workedMinutes * 100) / w.limitMinutes)));
}

export const STATUS_LABEL: Record<WeeklyStatus, string> = { NORMAL: '정상', NEAR: '한도 근접', OVER: '한도 초과' };

// ---- 데모용 가짜 백엔드 ----

/**
 * 브라우저 안의 가짜 백엔드(VITE_API=mock). 판정은 서버가 하므로 화면 확인용 고정 응답만 준다.
 * 한도 값은 데모 표시용 가짜 값이며 법령 기준값이 아니다(실서버는 legal_rate 에서 읽는다).
 */
export function createMockWeeklyHoursApi(): WeeklyHoursApi {
  const limits: WeeklyLimits = { standardMinutes: 2400, overtimeMinutes: 720, warnRemainMinutes: 240 };
  const week = (worked: number, overtime: number, status: WeeklyStatus): WeekSummary => ({
    workedMinutes: worked, regularMinutes: Math.min(worked, limits.standardMinutes), extraMinutes: 0,
    overtimeMinutes: overtime, holidayMinutes: 0, limitOvertimeMinutes: overtime,
    limitMinutes: limits.standardMinutes + limits.overtimeMinutes, overtimeLimitMinutes: limits.overtimeMinutes,
    remainingMinutes: limits.standardMinutes + limits.overtimeMinutes - worked,
    overtimeRemainingMinutes: limits.overtimeMinutes - overtime, status, missingOut: false,
  });
  const dates = { weekStart: '2099-03-02', weekEnd: '2099-03-08', today: '2099-03-05' };
  const me: EmpWeek = { empId: 1, empNo: 'E0001', name: '데모사원', deptId: 10, week: week(1950, 0, 'NORMAL') };
  const team: DeptWeek[] = [
    { deptId: 10, deptName: '데모개발팀', nearCount: 1, overCount: 1, employees: [
      { empId: 3, empNo: 'E0003', name: '가상직원C', deptId: 10, week: week(3150, 750, 'OVER') },
      { empId: 2, empNo: 'E0002', name: '가상직원B', deptId: 10, week: week(2940, 540, 'NEAR') },
      me,
    ] },
  ];
  return {
    async mine() { return structuredClone({ ...dates, limits, me }); },
    async team() { return structuredClone({ ...dates, limits, nearCount: 1, overCount: 1, departments: team }); },
  };
}

export const weeklyHoursApi: WeeklyHoursApi = import.meta.env.VITE_API === 'mock' ? createMockWeeklyHoursApi() : httpWeeklyHoursApi;
