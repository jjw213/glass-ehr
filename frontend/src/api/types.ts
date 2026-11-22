import type { Approval, Attendance, Bootstrap, Dept, LeaveRequest, NewLeave, Role } from '../../../shared/types';
import type { ChatMessage, MessagePage, MessageThread } from './messages';

export * from '../../../shared/types';
export type { ChatMessage, MessagePage, MessageThread, ThreadMember } from './messages';

/** Spring GET /api/auth/me (#19·#76). */
export interface AuthMe { loginId: string; empId: number | null; roles: string[]; permissions: string[]; managedDeptIds: number[] }

/**
 * 로그인한 사용자(#77). Spring /api/auth/me + 사원 목록의 본인 행. 사원과 연결되지 않은 계정은 이름 대신 로그인 아이디.
 * `role` 은 화면 메뉴 표시용(ADMIN·HR 외 역할은 EMPLOYEE 로 본다). 실제 차단은 서버가 권한으로 한다.
 */
export interface SessionUser {
  loginId: string; empId: number | null; name: string;
  /** Spring 부서 ID(문자열). 없으면 '' */
  dept: string;
  role: Role; roles: string[]; permissions: string[];
  /** 연차 일수. 휴가 모듈을 Spring 으로 옮길 때까지 Node /api/bootstrap 값 */
  annualLeave: number;
}

/** Spring GET /api/dashboard/summary (#77). 사원 조회 범위(전체·부서장 맡은 부서) 안의 집계. */
export interface DashboardSummary {
  today: string; headcount: number; hiredThisMonth: number; presentToday: number;
  /** 이번 달 포함 최근 12개월, 오래된 달부터. month = 'YYYY-MM' */
  trend: { month: string; hire: number; leave: number }[];
}

/**
 * 로그인 직후 화면 데이터(#77). 사용자·부서·홈 요약·메시지 대화방(#80)은 Spring, 나머지는 아직 Node 서버(옮기는 이슈에서 바꾼다).
 * dashboard 는 요약을 볼 권한(EMP_READ_ALL·EMP_READ_DEPT)이 없으면 null — 화면이 인원 카드를 숨긴다.
 */
export interface AppData extends Omit<Bootstrap, 'me' | 'depts' | 'employees' | 'dashboard' | 'threads'> {
  me: SessionUser;
  depts: Dept[];
  /** Node 에 남은 결재의 기존 부서 ID를 이름으로 표시하기 위한 임시 조회 목록. */
  legacyDepts: Dept[];
  dashboard: DashboardSummary | null;
  /** Spring GET /api/threads. 메시지 본문은 화면이 대화방을 열 때 페이지로 받는다. */
  threads: MessageThread[];
}

/** 요약 API 를 볼 수 있는 권한(서버 DashboardController 와 같게). */
export const canSeeDashboard = (permissions: string[]) => permissions.includes('EMP_READ_ALL') || permissions.includes('EMP_READ_DEPT');

/** Spring 역할 목록 → 화면 메뉴용 역할. */
export function menuRole(roles: string[]): Role {
  if (roles.includes('ADMIN')) return 'ADMIN';
  if (roles.includes('HR')) return 'HR';
  return 'EMPLOYEE';
}

/** Spring 조직도의 부서에 붙일 아바타 색(조직에 색 정보가 없어 순서대로). */
export const DEPT_COLORS = ['#f59e0b', '#60a5fa', '#4ade80', '#f472b6', '#a78bfa', '#22d3ee', '#fb7185', '#facc15'];

/** Everything the UI can ask of the backend. Two implementations: http.ts (real server) and mock.ts (in-browser demo). */
export interface Api {
  readonly mode: 'http' | 'mock';
  login(loginId: string, password: string): Promise<void>;
  logout(): Promise<void>;
  bootstrap(): Promise<AppData>;
  toggleClock(): Promise<Attendance>;
  addLeave(input: NewLeave): Promise<{ leaves: LeaveRequest[]; approvals: Approval[] }>;
  decide(id: string, action: 'approve' | 'reject'): Promise<{ approvals: Approval[]; leaves: LeaveRequest[] }>;
  /** 메시지(#80, Spring /api/threads). 참여자가 아닌 대화방은 403. */
  threads(): Promise<MessageThread[]>;
  messages(threadId: number, beforeId?: number): Promise<MessagePage>;
  send(threadId: number, text: string): Promise<ChatMessage>;
  markRead(threadId: number): Promise<void>;
}

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}
