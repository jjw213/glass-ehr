/**
 * In-browser fake backend with the same interface as the real server.
 * Used when VITE_API=mock (the shareable demo). Data resets on reload.
 */
import { APPROVALS, DASHBOARD, DEMO_THREADS, DEPTS, LEAVES, ME, PAYSLIPS } from '../data/mock';
import { EMP_DEPT_TREE } from '../data/employees';
import { DEMO_EMPLOYEES } from '../data/org';
import { toDepts, toSessionUser } from './bootstrap';
import { messageError } from './messages';
import {
  ApiError, type Api, type Approval, type AuthMe, type ChatMessage, type DashboardSummary, type EnterCompany, type LeaveRequest,
  type MessageThread, type ThreadMember,
} from './types';

/** 데모 계정이 연결된 사원(인사팀 팀장 예시사원03). */
const DEMO_SELF_EMP_ID = 3;

const ymd = (d = new Date()) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const wait = (ms = 250) => new Promise((r) => setTimeout(r, ms));
const clone = <T,>(x: T): T => structuredClone(x);

let approvals: Approval[] = clone(APPROVALS);
let leaves: LeaveRequest[] = clone(LEAVES);
/** 서울 지역 시각 'YYYY-MM-DDTHH:mm:ss'(Spring 응답과 같은 모양). */
const seoulDateTime = (d: Date) => d.toLocaleString('sv-SE', { timeZone: 'Asia/Seoul', hourCycle: 'h23' }).replace(' ', 'T');

interface MockMsg { id: number; threadId: number; sender: number; text: string; sentAt: string; readBy: Set<number> }
let msgSeq = 0;
let msgs: MockMsg[] = DEMO_THREADS.flatMap((t) => t.msgs.map(([sender, text, daysAgo, hm, readBySelf]) => {
  const d = new Date(Date.now() - daysAgo * 86_400_000);
  const sentAt = `${seoulDateTime(d).slice(0, 10)}T${hm}:00`;
  return { id: ++msgSeq, threadId: t.id, sender, text, sentAt, readBy: new Set(t.members.filter((m) => m !== DEMO_SELF_EMP_ID || readBySelf)) };
}));

const member = (empId: number): ThreadMember => {
  const e = DEMO_EMPLOYEES.find((x) => x.empId === empId);
  return { empId, name: e?.name ?? `사원${empId}`, deptId: e?.currentDeptId ?? null };
};
const view = (m: MockMsg): ChatMessage => ({ id: m.id, senderEmpId: m.sender, mine: m.sender === DEMO_SELF_EMP_ID, text: m.text, sentAt: m.sentAt });
/** Spring MessageService 처럼 참여자가 아니면 403. */
const memberThread = (threadId: number) => {
  const t = DEMO_THREADS.find((x) => x.id === threadId);
  if (!t) throw new ApiError(404, '대화를 찾을 수 없어요');
  if (!t.members.includes(DEMO_SELF_EMP_ID)) throw new ApiError(403, '이 대화에 참여하고 있지 않아요');
  return t;
};
const listThreads = (): MessageThread[] => DEMO_THREADS
  .filter((t) => t.members.includes(DEMO_SELF_EMP_ID))
  .map((t) => {
    const own = msgs.filter((m) => m.threadId === t.id);
    const last = own[own.length - 1];
    return {
      id: t.id,
      members: t.members.filter((m) => m !== DEMO_SELF_EMP_ID).map(member).sort((a, b) => a.name.localeCompare(b.name)),
      preview: last?.text ?? '', lastSentAt: last?.sentAt ?? null,
      unread: own.filter((m) => m.sender !== DEMO_SELF_EMP_ID && !m.readBy.has(DEMO_SELF_EMP_ID)).length,
    };
  })
  .sort((a, b) => (b.lastSentAt ?? '').localeCompare(a.lastSentAt ?? '') || b.id - a.id);
const PAGE_SIZE = 50;

function businessDays(from: string, to: string) {
  const a = new Date(from), b = new Date(to);
  if (isNaN(+a) || isNaN(+b) || b < a) return 0;
  let n = 0;
  for (const d = new Date(a); d <= b; d.setDate(d.getDate() + 1)) if (d.getDay() % 6 !== 0) n++;
  return n;
}

const REPLIES = ['확인했습니다 👍', '네, 반영해서 다시 공유드릴게요.', '감사합니다!', '오후에 잠깐 통화 가능하실까요?'];

/** 데모 법인(가짜 이름). 데모 계정 hr 은 첫 법인 소속이고, 법인 전환 메뉴를 보여 주려고 그룹 관리자 역할도 준다(#120). */
export const DEMO_COMPANIES: EnterCompany[] = [
  { enterCd: 'C001', enterName: '글래스HR(예시)' },
  { enterCd: 'C002', enterName: '글래스물류(예시)' },
];
const DEMO_HOME_ENTER = DEMO_COMPANIES[0].enterCd;
let currentEnter = DEMO_HOME_ENTER;

export const mockApi: Api = {
  mode: 'mock',
  async enterCompanies() { await wait(100); return clone(DEMO_COMPANIES); },
  async login(enterCd, loginId, password) {
    await wait(500);
    // Spring 처럼 회사가 다르면 아이디·비밀번호가 틀린 것과 같은 문구(#119).
    if (enterCd !== DEMO_HOME_ENTER || loginId.trim() !== 'hr' || password !== '1234') throw new ApiError(401, '아이디 또는 비밀번호가 맞지 않아요');
    currentEnter = DEMO_HOME_ENTER;
  },
  async switchEnter(enterCd) {
    await wait(200);
    if (!DEMO_COMPANIES.some((c) => c.enterCd === enterCd)) throw new ApiError(404, '회사를 찾을 수 없어요');
    currentEnter = enterCd;
  },
  async logout() { await wait(100); currentEnter = DEMO_HOME_ENTER; },
  async bootstrap() {
    await wait(200);
    // Spring 과 같은 모양(#77): 데모 계정 hr = 인사팀 예시사원(data/org.ts), 부서는 데모 조직 트리, 요약은 데모 숫자.
    // 데모는 법인별 데이터를 따로 두지 않고 현재 법인 표시만 바꾼다.
    const self = DEMO_EMPLOYEES.find((e) => e.empId === DEMO_SELF_EMP_ID);
    const auth: AuthMe = {
      enterCd: currentEnter, homeEnterCd: DEMO_HOME_ENTER,
      loginId: ME.loginId, empId: DEMO_SELF_EMP_ID, roles: ['GROUP_ADMIN', 'HR'], permissions: ['EMP_READ_ALL', 'EMP_WRITE', 'ENTER_SWITCH'], managedDeptIds: [],
    };
    const now = new Date();
    const dashboard: DashboardSummary = {
      today: ymd(now), headcount: DASHBOARD.headcount, hiredThisMonth: DASHBOARD.hiredThisMonth, presentToday: DASHBOARD.presentToday,
      trend: DASHBOARD.trend.map((p, i) => {
        const d = new Date(now.getFullYear(), now.getMonth() - (DASHBOARD.trend.length - 1 - i), 1);
        return { month: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`, hire: p.hire, leave: p.leave };
      }),
    };
    return {
      me: toSessionUser(auth, self, ME.annualLeave, DEMO_COMPANIES), depts: toDepts(EMP_DEPT_TREE), legacyDepts: clone(DEPTS), dashboard,
      approvals: clone(approvals), leaves: clone(leaves), payslips: clone(PAYSLIPS), threads: listThreads(),
      companies: clone(DEMO_COMPANIES),
    };
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
  async threads() { await wait(80); return listThreads(); },
  async messages(threadId, beforeId) {
    await wait(80);
    memberThread(threadId);
    const older = msgs.filter((m) => m.threadId === threadId && (beforeId == null || m.id < beforeId));
    return { messages: older.slice(-PAGE_SIZE).map(view), hasMore: older.length > PAGE_SIZE };
  },
  async send(threadId, text) {
    await wait(120);
    const t = memberThread(threadId);
    const err = messageError(text);
    if (err) throw new ApiError(400, err);
    const push = (sender: number, body: string) => {
      const m: MockMsg = { id: ++msgSeq, threadId, sender, text: body, sentAt: seoulDateTime(new Date()), readBy: new Set([sender]) };
      msgs = [...msgs, m];
      return m;
    };
    const sent = push(DEMO_SELF_EMP_ID, text.trim());
    const other = t.members.find((m) => m !== DEMO_SELF_EMP_ID);
    // 데모 전용 자동 답장
    if (other != null) setTimeout(() => push(other, REPLIES[Math.floor(Math.random() * REPLIES.length)]), 1800);
    return view(sent);
  },
  async markRead(threadId) {
    memberThread(threadId);
    for (const m of msgs) if (m.threadId === threadId) m.readBy.add(DEMO_SELF_EMP_ID);
  },
};
