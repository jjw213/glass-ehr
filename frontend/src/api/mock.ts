/**
 * In-browser fake backend with the same interface as the real server.
 * Used when VITE_API=mock (the shareable demo). Data resets on reload.
 */
import { APPROVALS, DASHBOARD, LEAVES, ME, PAYSLIPS, THREADS, WEEK_HOURS } from '../data/mock';
import { EMP_DEPT_TREE } from '../data/employees';
import { DEMO_EMPLOYEES } from '../data/org';
import { toDepts, toSessionUser } from './bootstrap';
import { ApiError, type Api, type Approval, type Attendance, type AuthMe, type DashboardSummary, type LeaveRequest, type Thread } from './types';

/** 데모 계정이 연결된 사원(인사팀 팀장 예시사원03). */
const DEMO_SELF_EMP_ID = 3;

const ymd = (d = new Date()) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const wait = (ms = 250) => new Promise((r) => setTimeout(r, ms));
const clone = <T,>(x: T): T => structuredClone(x);
const hhmm = (d: Date) => d.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });

let approvals: Approval[] = clone(APPROVALS);
let leaves: LeaveRequest[] = clone(LEAVES);
let threads: Thread[] = clone(THREADS);
let clockIn: Date | null = (() => { const d = new Date(); d.setHours(8, 52, 0, 0); return d; })();
let clockOut: Date | null = null;

const attendance = (): Attendance => {
  const idx = new Date().getDay() - 1;
  return {
    clockIn: clockIn?.toISOString() ?? null,
    clockOut: clockOut?.toISOString() ?? null,
    week: WEEK_HOURS.map((d, i) => ({ ...d, h: i < idx ? d.h : i === idx && clockIn && clockOut ? +((clockOut.getTime() - clockIn.getTime()) / 3.6e6).toFixed(1) : 0 })),
  };
};

function businessDays(from: string, to: string) {
  const a = new Date(from), b = new Date(to);
  if (isNaN(+a) || isNaN(+b) || b < a) return 0;
  let n = 0;
  for (const d = new Date(a); d <= b; d.setDate(d.getDate() + 1)) if (d.getDay() % 6 !== 0) n++;
  return n;
}

const REPLIES = ['확인했습니다 👍', '네, 반영해서 다시 공유드릴게요.', '감사합니다!', '오후에 잠깐 통화 가능하실까요?'];

export const mockApi: Api = {
  mode: 'mock',
  async login(loginId, password) {
    await wait(500);
    if (loginId.trim() !== 'hr' || password !== '1234') throw new ApiError(401, '아이디 또는 비밀번호가 맞지 않아요');
  },
  async logout() { await wait(100); },
  async bootstrap() {
    await wait(200);
    // Spring 과 같은 모양(#77): 데모 계정 hr = 인사팀 예시사원(data/org.ts), 부서는 데모 조직 트리, 요약은 데모 숫자.
    const self = DEMO_EMPLOYEES.find((e) => e.empId === DEMO_SELF_EMP_ID);
    const auth: AuthMe = { loginId: ME.loginId, empId: DEMO_SELF_EMP_ID, roles: ['HR'], permissions: ['EMP_READ_ALL', 'EMP_WRITE'], managedDeptIds: [] };
    const now = new Date();
    const dashboard: DashboardSummary = {
      today: ymd(now), headcount: DASHBOARD.headcount, hiredThisMonth: DASHBOARD.hiredThisMonth, presentToday: DASHBOARD.presentToday,
      trend: DASHBOARD.trend.map((p, i) => {
        const d = new Date(now.getFullYear(), now.getMonth() - (DASHBOARD.trend.length - 1 - i), 1);
        return { month: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`, hire: p.hire, leave: p.leave };
      }),
    };
    return {
      me: toSessionUser(auth, self, ME.annualLeave), depts: toDepts(EMP_DEPT_TREE), dashboard,
      approvals: clone(approvals), leaves: clone(leaves), attendance: attendance(), payslips: clone(PAYSLIPS), threads: clone(threads),
    };
  },
  async toggleClock() {
    await wait();
    if (clockIn && !clockOut) clockOut = new Date();
    else if (clockIn && clockOut) clockOut = null;
    else clockIn = new Date();
    return attendance();
  },
  async addLeave(input) {
    await wait();
    const to = input.type === '반차' ? input.from : input.to;
    const days = input.type === '반차' ? 0.5 : businessDays(input.from, to);
    if (days <= 0) throw new ApiError(400, '평일이 포함되지 않았어요');
    const id = 'L' + Date.now();
    leaves = [{ id, type: input.type, from: input.from, to, days, reason: input.reason || '개인 사유', status: '대기' }, ...leaves];
    approvals = [{
      id: 'A-' + id, kind: '휴가', title: `${input.type} 사용 신청`, requester: ME.name, dept: ME.dept,
      date: `${String(new Date().getMonth() + 1).padStart(2, '0')}.${String(new Date().getDate()).padStart(2, '0')}`,
      amount: null, body: `${input.from} ~ ${to} (${days}일)\n사유: ${input.reason || '개인 사유'}`,
      line: [{ name: '박서연', status: 'WAIT' }], status: '대기', canDecide: false,
    }, ...approvals];
    return { leaves: clone(leaves), approvals: clone(approvals) };
  },
  async decide(id, action) {
    await wait();
    approvals = approvals.map((a) => {
      if (a.id !== id) return a;
      const status = action === 'approve' ? '승인' as const : '반려' as const;
      return { ...a, status, canDecide: false, line: a.line.map((s) => (s.status === 'WAIT' ? { ...s, status: action === 'approve' ? 'APPROVED' as const : 'REJECTED' as const } : s)) };
    });
    return { approvals: clone(approvals), leaves: clone(leaves) };
  },
  async threads() { await wait(80); return clone(threads); },
  async send(threadId, text) {
    await wait(120);
    const push = (from: 'me' | 'them', t: string) => {
      threads = threads.map((th) => (th.id === threadId ? { ...th, preview: t, msgs: [...th.msgs, { from, text: t, time: hhmm(new Date()) }] } : th));
    };
    push('me', text);
    setTimeout(() => push('them', REPLIES[Math.floor(Math.random() * REPLIES.length)]), 1800); // demo-only auto reply
    return clone(threads);
  },
  async markRead(threadId) { threads = threads.map((t) => (t.id === threadId ? { ...t, unread: 0 } : t)); },
};
