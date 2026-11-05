import type { Db } from './db';
import { HttpError, Router, type Ctx } from './http';
import { checkThrottle, clearFails, createSession, destroySession, recordFail, requireUser, verifyPassword } from './auth';
import * as repo from './repo';
import type { Bootstrap, NewLeave } from '../../shared/types';

const obj = (b: unknown): Record<string, unknown> => {
  if (!b || typeof b !== 'object' || Array.isArray(b)) throw new HttpError(400, '요청 본문이 필요해요');
  return b as Record<string, unknown>;
};

export function buildRoutes(db: Db) {
  const r = new Router();
  const user = (ctx: Ctx) => requireUser(db, ctx);

  r.get('/api/health', async () => {
    await db.query(db.dialect === 'oracle' ? 'SELECT 1 AS OK FROM DUAL' : 'SELECT 1 AS OK');
    return { ok: true, db: db.dialect };
  });

  /* ---------- auth ---------- */
  r.post('/api/login', async (ctx) => {
    const b = obj(ctx.body);
    const loginId = String(b.loginId ?? '').trim();
    const password = String(b.password ?? '');
    if (!loginId || !password) throw new HttpError(400, '아이디와 비밀번호를 입력해 주세요');
    const key = `${ctx.ip}|${loginId}`;
    checkThrottle(key);
    const [emp] = await db.query<{ id: string; password_hash: string }>(
      'SELECT ID, PASSWORD_HASH FROM HR_EMPLOYEES WHERE LOGIN_ID = :l AND LEFT_ON IS NULL', { l: loginId });
    if (!emp || !(await verifyPassword(password, emp.password_hash))) {
      recordFail(key);
      throw new HttpError(401, '아이디 또는 비밀번호가 맞지 않아요');
    }
    clearFails(key);
    await createSession(db, emp.id, ctx.res);
    return { ok: true };
  });

  r.post('/api/logout', async (ctx) => { await destroySession(db, ctx); return { ok: true }; });

  /* ---------- everything the app shows after login ---------- */
  r.get('/api/bootstrap', async (ctx): Promise<Bootstrap> => {
    const u = await user(ctx);
    const [me, depts, employees, dashboard, approvals, leaves, attendance, payslips, threads] = await Promise.all([
      repo.getMe(db, u), repo.listDepts(db), repo.listEmployees(db), repo.getDashboard(db), repo.listApprovals(db, u),
      repo.listLeaves(db, u), repo.getAttendance(db, u), repo.listPayslips(db, u), repo.listThreads(db, u),
    ]);
    return { me, depts, employees, dashboard, approvals, leaves, attendance, payslips, threads };
  });

  r.get('/api/me', async (ctx) => repo.getMe(db, await user(ctx)));
  r.get('/api/employees', async (ctx) => { await user(ctx); return repo.listEmployees(db); });
  r.get('/api/depts', async (ctx) => { await user(ctx); return repo.listDepts(db); });
  r.get('/api/dashboard', async (ctx) => { await user(ctx); return repo.getDashboard(db); });

  /* ---------- attendance ---------- */
  r.get('/api/attendance', async (ctx) => repo.getAttendance(db, await user(ctx)));
  r.post('/api/attendance/toggle', async (ctx) => repo.toggleClock(db, await user(ctx)));

  /* ---------- leaves ---------- */
  r.get('/api/leaves', async (ctx) => repo.listLeaves(db, await user(ctx)));
  r.post('/api/leaves', async (ctx) => {
    const u = await user(ctx);
    const b = obj(ctx.body);
    await repo.createLeave(db, u, { type: b.type, from: b.from, to: b.to ?? b.from, reason: b.reason ?? '' } as NewLeave);
    return { leaves: await repo.listLeaves(db, u), approvals: await repo.listApprovals(db, u) };
  });

  /* ---------- approvals ---------- */
  r.get('/api/approvals', async (ctx) => repo.listApprovals(db, await user(ctx)));
  r.post('/api/approvals/:id/:action', async (ctx) => {
    const u = await user(ctx);
    const action = ctx.params.action;
    if (action !== 'approve' && action !== 'reject') throw new HttpError(404, '알 수 없는 동작이에요');
    await repo.decide(db, u, ctx.params.id, action);
    return { approvals: await repo.listApprovals(db, u), leaves: await repo.listLeaves(db, u) };
  });

  /* ---------- payroll ---------- */
  r.get('/api/payslips', async (ctx) => repo.listPayslips(db, await user(ctx)));

  /* ---------- messages ---------- */
  r.get('/api/threads', async (ctx) => repo.listThreads(db, await user(ctx)));
  r.post('/api/threads/:id/messages', async (ctx) => {
    const u = await user(ctx);
    await repo.sendMessage(db, u, ctx.params.id, String(obj(ctx.body).text ?? ''));
    return repo.listThreads(db, u);
  });
  r.post('/api/threads/:id/read', async (ctx) => {
    const u = await user(ctx);
    await repo.markRead(db, u, ctx.params.id);
    return { ok: true };
  });

  return r;
}
