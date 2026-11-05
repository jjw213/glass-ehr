/**
 * All SQL lives here. Queries use only syntax shared by Oracle and SQLite:
 * named binds (:x), JOIN, COALESCE, CASE, ORDER BY — no LIMIT/ROWNUM/RETURNING.
 */
import type { Db } from './db';
import { toIso, type Binds } from './db/types';
import { HttpError } from './http';
import type { User } from './auth';
import { addDays, businessDays, hhmm, mmdd, monday, newId, ymd } from './util';
import type {
  Approval, ApprovalKind, ApprovalStatus, Attendance, Dashboard, Dept, Employee, LeaveRequest, Me,
  NewLeave, Payslip, Thread, TrendPoint, WorkStatus, Role, LeaveType,
} from '../../shared/types';

/* ---------------- people ---------------- */

export async function getMe(db: Db, u: User): Promise<Me> {
  const [r] = await db.query<{ id: string; login_id: string; name: string; dept_id: string; position: string; role: Role; email: string; annual_leave: number }>(
    'SELECT ID, LOGIN_ID, NAME, DEPT_ID, POSITION, ROLE, EMAIL, ANNUAL_LEAVE FROM HR_EMPLOYEES WHERE ID = :id', { id: u.id });
  return { id: r.id, loginId: r.login_id, name: r.name, dept: r.dept_id, position: r.position, role: r.role, email: r.email, annualLeave: Number(r.annual_leave) };
}

export async function listDepts(db: Db): Promise<Dept[]> {
  const rows = await db.query<{ id: string; name: string; color: string; descr: string | null; lead_name: string | null }>(
    `SELECT d.ID, d.NAME, d.COLOR, d.DESCR, e.NAME AS LEAD_NAME
       FROM HR_DEPTS d LEFT JOIN HR_EMPLOYEES e ON e.ID = d.LEAD_ID
      ORDER BY d.SORT_ORDER`);
  return rows.map((r) => ({ id: r.id, name: r.name, color: r.color, desc: r.descr ?? '', lead: r.lead_name ?? '-' }));
}

export async function listEmployees(db: Db): Promise<Employee[]> {
  const rows = await db.query<{ id: string; name: string; dept_id: string; position: string; email: string; phone: string | null; joined_on: string; status: WorkStatus; location: string | null }>(
    `SELECT ID, NAME, DEPT_ID, POSITION, EMAIL, PHONE, JOINED_ON, STATUS, LOCATION
       FROM HR_EMPLOYEES WHERE LEFT_ON IS NULL ORDER BY ID`);
  return rows.map((r) => ({ id: r.id, name: r.name, dept: r.dept_id, position: r.position, email: r.email, phone: r.phone ?? '', joined: r.joined_on, status: r.status, location: r.location ?? '' }));
}

/* ---------------- attendance ---------------- */

const WEEKDAYS = ['월', '화', '수', '목', '금'];

export async function getAttendance(db: Db, u: User): Promise<Attendance> {
  const mon = monday();
  const days = WEEKDAYS.map((_, i) => ymd(addDays(mon, i)));
  const rows = await db.query<{ work_date: string; clock_in: unknown; clock_out: unknown }>(
    'SELECT WORK_DATE, CLOCK_IN, CLOCK_OUT FROM HR_ATTENDANCE WHERE EMP_ID = :e AND WORK_DATE >= :a AND WORK_DATE <= :b',
    { e: u.id, a: days[0], b: days[4] });
  const byDay = new Map(rows.map((r) => [r.work_date, r]));
  const today = byDay.get(ymd());
  const week = days.map((day, i) => {
    const r = byDay.get(day);
    const inn = toIso(r?.clock_in), out = toIso(r?.clock_out);
    const h = inn && out ? (new Date(out).getTime() - new Date(inn).getTime()) / 3_600_000 : 0;
    return { d: WEEKDAYS[i], h: Math.round(h * 10) / 10 };
  });
  return { clockIn: toIso(today?.clock_in), clockOut: toIso(today?.clock_out), week };
}

/** 출근 → 퇴근 → (다시 누르면) 퇴근 취소. 처음 출근 시각은 그대로 둔다. */
export async function toggleClock(db: Db, u: User): Promise<Attendance> {
  const day = ymd();
  const now = new Date();
  await db.tx(async (t) => {
    const [r] = await t.query<{ id: string; clock_out: unknown }>('SELECT ID, CLOCK_OUT FROM HR_ATTENDANCE WHERE EMP_ID = :e AND WORK_DATE = :d', { e: u.id, d: day });
    if (!r) await t.exec('INSERT INTO HR_ATTENDANCE (ID, EMP_ID, WORK_DATE, CLOCK_IN) VALUES (:id, :e, :d, :t)', { id: newId('T'), e: u.id, d: day, t: now });
    else if (r.clock_out == null) await t.exec('UPDATE HR_ATTENDANCE SET CLOCK_OUT = :t WHERE ID = :id', { t: now, id: r.id });
    else await t.exec('UPDATE HR_ATTENDANCE SET CLOCK_OUT = NULL WHERE ID = :id', { id: r.id });
  });
  return getAttendance(db, u);
}

/* ---------------- leaves ---------------- */

export async function listLeaves(db: Db, u: User): Promise<LeaveRequest[]> {
  const rows = await db.query<{ id: string; leave_type: LeaveType; from_date: string; to_date: string; days: number; reason: string | null; status: ApprovalStatus }>(
    `SELECT ID, LEAVE_TYPE, FROM_DATE, TO_DATE, DAYS, REASON, STATUS FROM HR_LEAVES
      WHERE EMP_ID = :e ORDER BY FROM_DATE DESC, CREATED_AT DESC`, { e: u.id });
  return rows.map((r) => ({ id: r.id, type: r.leave_type, from: r.from_date, to: r.to_date, days: Number(r.days), reason: r.reason ?? '', status: r.status }));
}

const LEAVE_TYPES: LeaveType[] = ['연차', '반차', '병가', '경조사'];
const isDate = (s: unknown): s is string => typeof s === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(s) && !isNaN(+new Date(s));

/**
 * Creates the leave request AND an approval document routed to
 * the requester's team lead (if not themselves) and then the HR lead.
 */
export async function createLeave(db: Db, u: User, input: NewLeave) {
  if (!LEAVE_TYPES.includes(input.type)) throw new HttpError(400, '휴가 종류가 올바르지 않아요');
  if (!isDate(input.from) || !isDate(input.to)) throw new HttpError(400, '날짜 형식은 YYYY-MM-DD 예요');
  const to = input.type === '반차' ? input.from : input.to;
  if (to < input.from) throw new HttpError(400, '종료일이 시작일보다 빨라요');
  const days = input.type === '반차' ? 0.5 : businessDays(input.from, to);
  if (days <= 0) throw new HttpError(400, '평일이 포함되지 않았어요');
  const reason = String(input.reason ?? '').trim().slice(0, 200) || '개인 사유';

  if (input.type === '연차' || input.type === '반차') {
    const [bal] = await db.query<{ total: number; used: number }>(
      `SELECT e.ANNUAL_LEAVE AS TOTAL,
              COALESCE((SELECT SUM(l.DAYS) FROM HR_LEAVES l WHERE l.EMP_ID = e.ID AND l.STATUS <> '반려' AND l.LEAVE_TYPE IN ('연차','반차')), 0) AS USED
         FROM HR_EMPLOYEES e WHERE e.ID = :e`, { e: u.id });
    if (Number(bal.used) + days > Number(bal.total)) throw new HttpError(400, `남은 연차(${Number(bal.total) - Number(bal.used)}일)보다 많아요`);
  }

  const line = await approvalLineFor(db, u);
  const leaveId = newId('L');
  const aprId = newId('A-');
  const now = new Date();
  const title = `${input.type} 사용 신청 (${input.from.slice(5).replace('-', '/')}${to !== input.from ? '~' + to.slice(5).replace('-', '/') : ''})`;
  await db.tx(async (t) => {
    await t.exec(
      `INSERT INTO HR_APPROVALS (ID, KIND, TITLE, REQUESTER_ID, BODY, AMOUNT, STATUS, CURRENT_SEQ, CREATED_AT)
       VALUES (:id, '휴가', :title, :req, :body, NULL, '대기', 1, :now)`,
      { id: aprId, title, req: u.id, body: `${input.from} ~ ${to} (${days}일)\n사유: ${reason}`, now });
    for (const [i, approver] of line.entries()) {
      await t.exec('INSERT INTO HR_APPROVAL_STEPS (APPROVAL_ID, SEQ, APPROVER_ID, STATUS) VALUES (:a, :s, :p, :st)',
        { a: aprId, s: i + 1, p: approver, st: 'WAIT' });
    }
    await t.exec(
      `INSERT INTO HR_LEAVES (ID, EMP_ID, LEAVE_TYPE, FROM_DATE, TO_DATE, DAYS, REASON, STATUS, APPROVAL_ID, CREATED_AT)
       VALUES (:id, :e, :ty, :f, :t, :d, :r, '대기', :a, :now)`,
      { id: leaveId, e: u.id, ty: input.type, f: input.from, t: to, d: days, r: reason, a: aprId, now });
  });
}

/**
 * Approval line for a leave request:
 *   team lead → HR lead (skipping the requester themself).
 * When both are the requester (the HR lead's own leave), it goes to the 경영지원 lead.
 */
async function approvalLineFor(db: Db, u: User): Promise<string[]> {
  const rows = await db.query<{ id: string; lead_id: string | null }>('SELECT ID, LEAD_ID FROM HR_DEPTS WHERE ID IN (:mine, :hr, :top)', { mine: u.dept, hr: 'hr', top: 'mgmt' });
  const leadOf = (d: string) => rows.find((r) => r.id === d)?.lead_id ?? null;
  let line = [leadOf(u.dept), leadOf('hr')].filter((x): x is string => !!x && x !== u.id);
  if (!line.length) line = [leadOf('mgmt')].filter((x): x is string => !!x && x !== u.id);
  if (!line.length) throw new HttpError(422, '결재선을 만들 수 없어요 (부서장 정보를 확인하세요)');
  return [...new Set(line)];
}

/* ---------------- approvals ---------------- */

/** Documents the user requested or sits on the approval line of. */
export async function listApprovals(db: Db, u: User): Promise<Approval[]> {
  const docs = await db.query<{ id: string; kind: ApprovalKind; title: string; requester: string; dept_id: string; body: string | null; amount: number | null; status: ApprovalStatus; current_seq: number; created_at: unknown }>(
    `SELECT a.ID, a.KIND, a.TITLE, e.NAME AS REQUESTER, e.DEPT_ID, a.BODY, a.AMOUNT, a.STATUS, a.CURRENT_SEQ, a.CREATED_AT
       FROM HR_APPROVALS a JOIN HR_EMPLOYEES e ON e.ID = a.REQUESTER_ID
      WHERE a.REQUESTER_ID = :u
         OR a.ID IN (SELECT s.APPROVAL_ID FROM HR_APPROVAL_STEPS s WHERE s.APPROVER_ID = :u)
      ORDER BY a.CREATED_AT DESC`, { u: u.id });
  if (!docs.length) return [];
  const steps = await db.query<{ approval_id: string; seq: number; approver_id: string; name: string; status: 'WAIT' | 'APPROVED' | 'REJECTED' }>(
    `SELECT s.APPROVAL_ID, s.SEQ, s.APPROVER_ID, e.NAME, s.STATUS
       FROM HR_APPROVAL_STEPS s JOIN HR_EMPLOYEES e ON e.ID = s.APPROVER_ID
      WHERE s.APPROVAL_ID IN (SELECT a.ID FROM HR_APPROVALS a WHERE a.REQUESTER_ID = :u
                                 OR a.ID IN (SELECT x.APPROVAL_ID FROM HR_APPROVAL_STEPS x WHERE x.APPROVER_ID = :u))
      ORDER BY s.APPROVAL_ID, s.SEQ`, { u: u.id });
  const byDoc = new Map<string, typeof steps>();
  for (const s of steps) byDoc.set(s.approval_id, [...(byDoc.get(s.approval_id) ?? []), s]);

  return docs.map((d) => {
    const line = byDoc.get(d.id) ?? [];
    const current = line.find((s) => Number(s.seq) === Number(d.current_seq));
    return {
      id: d.id, kind: d.kind, title: d.title, requester: d.requester, dept: d.dept_id,
      date: mmdd(toIso(d.created_at)!), amount: d.amount == null ? null : Number(d.amount), body: d.body ?? '',
      line: line.map((s) => ({ name: s.name, status: s.status })),
      status: d.status,
      canDecide: d.status === '대기' && current?.approver_id === u.id && current.status === 'WAIT',
    };
  });
}

export async function decide(db: Db, u: User, id: string, action: 'approve' | 'reject') {
  await db.tx(async (t) => {
    const [d] = await t.query<{ status: string; current_seq: number }>('SELECT STATUS, CURRENT_SEQ FROM HR_APPROVALS WHERE ID = :id', { id });
    if (!d) throw new HttpError(404, '문서를 찾을 수 없어요');
    if (d.status !== '대기') throw new HttpError(409, '이미 처리된 문서예요');
    const seq = Number(d.current_seq);
    const [step] = await t.query<{ approver_id: string; status: string }>('SELECT APPROVER_ID, STATUS FROM HR_APPROVAL_STEPS WHERE APPROVAL_ID = :id AND SEQ = :s', { id, s: seq });
    if (!step || step.approver_id !== u.id || step.status !== 'WAIT') throw new HttpError(403, '지금은 결재할 차례가 아니에요');

    const now = new Date();
    await t.exec('UPDATE HR_APPROVAL_STEPS SET STATUS = :st, ACTED_AT = :now WHERE APPROVAL_ID = :id AND SEQ = :s',
      { st: action === 'approve' ? 'APPROVED' : 'REJECTED', now, id, s: seq });

    let final: ApprovalStatus | null = null;
    if (action === 'reject') final = '반려';
    else {
      const [next] = await t.query<{ n: number }>('SELECT COUNT(*) AS N FROM HR_APPROVAL_STEPS WHERE APPROVAL_ID = :id AND SEQ > :s', { id, s: seq });
      if (Number(next.n) === 0) final = '승인';
      else await t.exec('UPDATE HR_APPROVALS SET CURRENT_SEQ = :n WHERE ID = :id', { n: seq + 1, id });
    }
    if (final) {
      await t.exec('UPDATE HR_APPROVALS SET STATUS = :st WHERE ID = :id', { st: final, id });
      // a leave request follows its approval document
      await t.exec('UPDATE HR_LEAVES SET STATUS = :st WHERE APPROVAL_ID = :id', { st: final, id });
    }
  });
}

/* ---------------- payroll (own payslips only) ---------------- */

export async function listPayslips(db: Db, u: User): Promise<Payslip[]> {
  const slips = await db.query<{ id: string; pay_month: string; pay_date: string }>(
    'SELECT ID, PAY_MONTH, PAY_DATE FROM HR_PAYSLIPS WHERE EMP_ID = :e ORDER BY PAY_MONTH DESC', { e: u.id });
  if (!slips.length) return [];
  const items = await db.query<{ payslip_id: string; item_type: 'E' | 'D'; label: string; amount: number }>(
    `SELECT i.PAYSLIP_ID, i.ITEM_TYPE, i.LABEL, i.AMOUNT
       FROM HR_PAYSLIP_ITEMS i JOIN HR_PAYSLIPS p ON p.ID = i.PAYSLIP_ID
      WHERE p.EMP_ID = :e ORDER BY i.PAYSLIP_ID, i.SEQ`, { e: u.id });
  return slips.map((s) => {
    const mine = items.filter((i) => i.payslip_id === s.id);
    const [y, m] = s.pay_month.split('-');
    return {
      month: `${y}년 ${Number(m)}월`, payMonth: s.pay_month, payDate: s.pay_date,
      earnings: mine.filter((i) => i.item_type === 'E').map((i) => ({ label: i.label, amount: Number(i.amount) })),
      deductions: mine.filter((i) => i.item_type === 'D').map((i) => ({ label: i.label, amount: Number(i.amount) })),
    };
  });
}

/* ---------------- messages ---------------- */

export async function listThreads(db: Db, u: User): Promise<Thread[]> {
  const threads = await db.query<{ id: string; other_id: string; name: string; dept_id: string }>(
    `SELECT t.ID, e.ID AS OTHER_ID, e.NAME, e.DEPT_ID
       FROM HR_THREADS t JOIN HR_EMPLOYEES e ON e.ID = CASE WHEN t.EMP_A = :u THEN t.EMP_B ELSE t.EMP_A END
      WHERE t.EMP_A = :u OR t.EMP_B = :u`, { u: u.id });
  if (!threads.length) return [];
  const msgs = await db.query<{ thread_id: string; sender_id: string; body: string; sent_at: unknown; read_at: unknown }>(
    `SELECT m.THREAD_ID, m.SENDER_ID, m.BODY, m.SENT_AT, m.READ_AT
       FROM HR_MESSAGES m JOIN HR_THREADS t ON t.ID = m.THREAD_ID
      WHERE t.EMP_A = :u OR t.EMP_B = :u ORDER BY m.SENT_AT`, { u: u.id });
  const today = ymd();
  const out = threads.map((t) => {
    const mine = msgs.filter((m) => m.thread_id === t.id);
    const last = mine[mine.length - 1];
    return {
      id: t.id, name: t.name, dept: t.dept_id,
      preview: last?.body ?? '',
      unread: mine.filter((m) => m.sender_id !== u.id && m.read_at == null).length,
      msgs: mine.map((m) => {
        const iso = toIso(m.sent_at)!;
        return { from: m.sender_id === u.id ? 'me' as const : 'them' as const, text: m.body, time: ymd(new Date(iso)) === today ? hhmm(iso) : mmdd(iso) };
      }),
      _last: last ? toIso(last.sent_at)! : '',
    };
  });
  out.sort((a, b) => b._last.localeCompare(a._last));
  return out.map(({ _last, ...t }) => t);
}

async function assertMember(db: Db, u: User, threadId: string) {
  const [t] = await db.query<{ id: string }>('SELECT ID FROM HR_THREADS WHERE ID = :id AND (EMP_A = :u OR EMP_B = :u)', { id: threadId, u: u.id });
  if (!t) throw new HttpError(404, '대화를 찾을 수 없어요');
}

export async function sendMessage(db: Db, u: User, threadId: string, text: string) {
  const body = String(text ?? '').trim();
  if (!body) throw new HttpError(400, '메시지가 비어 있어요');
  if (body.length > 1000) throw new HttpError(400, '메시지는 1000자까지 보낼 수 있어요');
  await assertMember(db, u, threadId);
  await db.exec('INSERT INTO HR_MESSAGES (ID, THREAD_ID, SENDER_ID, BODY, SENT_AT) VALUES (:id, :t, :s, :b, :now)',
    { id: newId('M'), t: threadId, s: u.id, b: body, now: new Date() });
}

export async function markRead(db: Db, u: User, threadId: string) {
  await assertMember(db, u, threadId);
  await db.exec('UPDATE HR_MESSAGES SET READ_AT = :now WHERE THREAD_ID = :t AND SENDER_ID <> :u AND READ_AT IS NULL',
    { now: new Date(), t: threadId, u: u.id });
}

/* ---------------- dashboard ---------------- */

export async function getDashboard(db: Db): Promise<Dashboard> {
  const today = ymd();
  const monthStart = today.slice(0, 8) + '01';
  const count = async (sql: string, binds: Binds = {}) => Number((await db.query<{ n: number }>(sql, binds))[0]?.n ?? 0);
  const headcount = await count('SELECT COUNT(*) AS N FROM HR_EMPLOYEES WHERE LEFT_ON IS NULL');
  const hired = await count('SELECT COUNT(*) AS N FROM HR_EMPLOYEES WHERE LEFT_ON IS NULL AND JOINED_ON >= :ms', { ms: monthStart });
  const present = await count('SELECT COUNT(*) AS N FROM HR_ATTENDANCE WHERE WORK_DATE = :d', { d: today });
  const onLeave = await count(
    `SELECT COUNT(*) AS N FROM HR_EMPLOYEES e
      WHERE e.LEFT_ON IS NULL AND (e.STATUS = '휴가' OR EXISTS (
        SELECT 1 FROM HR_LEAVES l WHERE l.EMP_ID = e.ID AND l.STATUS = '승인' AND l.FROM_DATE <= :d AND l.TO_DATE >= :d))`, { d: today });

  // last 12 months of hires / departures
  const now = new Date();
  const months = Array.from({ length: 12 }, (_, i) => {
    const d = new Date(now.getFullYear(), now.getMonth() - 11 + i, 1);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  });
  const people = await db.query<{ joined_on: string; left_on: string | null }>(
    'SELECT JOINED_ON, LEFT_ON FROM HR_EMPLOYEES WHERE JOINED_ON >= :a OR LEFT_ON >= :a', { a: months[0] + '-01' });
  const trend: TrendPoint[] = months.map((m) => ({
    m: `${Number(m.slice(5))}월`,
    hire: people.filter((p) => p.joined_on.startsWith(m)).length,
    leave: people.filter((p) => p.left_on?.startsWith(m)).length,
  }));

  const events = await db.query<{ event_date: string; start_time: string; title: string; tag: string | null; color: string | null }>(
    'SELECT EVENT_DATE, START_TIME, TITLE, TAG, COLOR FROM HR_EVENTS WHERE EVENT_DATE >= :d ORDER BY EVENT_DATE, START_TIME', { d: today });
  const notices = await db.query<{ id: string; title: string; posted_on: string; pinned: number }>(
    'SELECT ID, TITLE, POSTED_ON, PINNED FROM HR_NOTICES ORDER BY PINNED DESC, POSTED_ON DESC');

  return {
    headcount, hiredThisMonth: hired, presentToday: present, onLeaveToday: onLeave,
    trend,
    schedule: events.slice(0, 4).map((e) => ({ date: e.event_date, time: e.start_time, title: e.title, tag: e.tag ?? '', color: e.color ?? '#60a5fa' })),
    notices: notices.slice(0, 4).map((n) => ({ id: n.id, title: n.title, date: n.posted_on.slice(5).replace('-', '.'), pinned: Number(n.pinned) === 1 })),
  };
}
