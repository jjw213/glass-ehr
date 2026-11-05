/**
 * End-to-end API test on the built-in SQLite stand-in (no Oracle needed):
 *   npm test
 * Uses the exact same SQL, routes and permission rules as the Oracle setup.
 */
import { strict as assert } from 'node:assert';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const dir = mkdtempSync(join(tmpdir(), 'ghr-'));
process.env.DB_CLIENT = 'sqlite';
process.env.SQLITE_FILE = join(dir, 'test.sqlite');
process.env.LOG_REQUESTS = 'false';
process.env.STATIC_DIR = '';

const { openDb } = await import('../src/db');
const { ddl, TABLES_IN_DROP_ORDER } = await import('../src/db/schema');
const { seed } = await import('../src/db/seed');
const { createApp } = await import('../src/app');

const db = await openDb();
for (const t of TABLES_IN_DROP_ORDER) await db.dropTableIfExists(t);
for (const s of ddl('sqlite')) await db.exec(s);
await seed(db);

const server = createApp(db);
await new Promise<void>((r) => server.listen(0, r));
const base = `http://localhost:${(server.address() as { port: number }).port}`;

/** minimal cookie-keeping client */
function client() {
  let cookie = '';
  return async (method: string, path: string, body?: unknown) => {
    const res = await fetch(base + path, {
      method,
      headers: { ...(body !== undefined ? { 'content-type': 'application/json' } : {}), ...(cookie ? { cookie } : {}) },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
    const set = res.headers.get('set-cookie');
    if (set) cookie = set.split(';')[0];
    return { status: res.status, data: (await res.json()) as any };
  };
}

let passed = 0;
const test = async (name: string, fn: () => Promise<void>) => {
  try { await fn(); passed++; console.log('  ✔', name); }
  catch (e) { console.error('  ✘', name); throw e; }
};

const hr = client();       // 김지훈 (HR lead, last approver on most documents)
const dev = client();      // 이도윤 (dev lead)
const yoon = client();     // 윤서준 (HR team member, requester of A-2611)
const lim = client();      // 임하준 (dev team member)

try {
  await test('schema uses no Oracle reserved words and portable SQL', async () => {
    const { ORACLE_RESERVED } = await import('../src/db/sqlite');
    for (const stmt of ddl('oracle')) {
      assert.ok(!stmt.trim().endsWith(';'));
      for (const m of stmt.matchAll(/^\s+([A-Z_]+) /gm)) {
        if (m[1] !== 'CONSTRAINT') assert.ok(!ORACLE_RESERVED.has(m[1]), `reserved column name ${m[1]}`);
      }
    }
    const { readFileSync } = await import('node:fs');
    const repoSrc = readFileSync(new URL('../src/repo.ts', import.meta.url), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
    assert.ok(!/\bLIMIT\b|\bRETURNING\b|\bROWNUM\b|\bNOW\(\)|\bIFNULL\b/i.test(repoSrc), 'non-portable SQL in repo.ts');
  });

  await test('health', async () => {
    const r = await hr('GET', '/api/health');
    assert.equal(r.status, 200); assert.equal(r.data.db, 'sqlite');
  });

  await test('API needs login', async () => {
    assert.equal((await hr('GET', '/api/bootstrap')).status, 401);
  });

  await test('wrong password is rejected', async () => {
    const r = await hr('POST', '/api/login', { loginId: 'hr', password: 'nope' });
    assert.equal(r.status, 401);
  });

  await test('POST without JSON content-type is rejected (CSRF guard)', async () => {
    const r = await fetch(base + '/api/login', { method: 'POST', body: 'loginId=hr&password=1234', headers: { 'content-type': 'application/x-www-form-urlencoded' } });
    assert.equal(r.status, 415);
  });

  await test('login + bootstrap', async () => {
    assert.equal((await hr('POST', '/api/login', { loginId: 'hr', password: '1234' })).status, 200);
    const r = await hr('GET', '/api/bootstrap');
    assert.equal(r.status, 200);
    const b = r.data;
    assert.equal(b.me.name, '김지훈');
    assert.equal(b.depts.length, 6);
    assert.equal(b.employees.length, 24);
    assert.equal(b.payslips.length, 5);
    assert.equal(b.threads.length, 4);
    assert.ok(b.dashboard.headcount === 24 && b.dashboard.trend.length === 12);
    assert.equal(b.approvals.filter((a: any) => a.canDecide).length, 5);
    const a2605 = b.approvals.find((a: any) => a.id === 'A-2605');
    assert.deepEqual(a2605.line.map((s: any) => s.status), ['APPROVED', 'WAIT', 'WAIT']);
  });

  await test('payslips are private: each user sees only their own', async () => {
    await dev('POST', '/api/login', { loginId: 'dy.lee', password: '1234' });
    const mine = (await hr('GET', '/api/payslips')).data;
    const theirs = (await dev('GET', '/api/payslips')).data;
    assert.equal(theirs.length, 5);
    assert.notDeepEqual(mine[0].earnings, theirs[0].earnings);
  });

  await test('cannot approve when it is not your turn', async () => {
    // A-2605 line: 이도윤 → 김지훈 → 박서연 ; currently 김지훈
    const r = await dev('POST', '/api/approvals/A-2605/approve', {});
    assert.equal(r.status, 403);
  });

  await test('multi-step approval moves to the next approver', async () => {
    const r = await hr('POST', '/api/approvals/A-2605/approve', {});
    assert.equal(r.status, 200);
    const doc = r.data.approvals.find((a: any) => a.id === 'A-2605');
    assert.equal(doc.status, '대기');
    assert.equal(doc.canDecide, false);
    assert.deepEqual(doc.line.map((s: any) => s.status), ['APPROVED', 'APPROVED', 'WAIT']);
    assert.equal((await hr('POST', '/api/approvals/A-2605/approve', {})).status, 403);
  });

  await test('approving a leave document approves the leave', async () => {
    await yoon('POST', '/api/login', { loginId: 'sj.yoon', password: '1234' });
    const before = (await yoon('GET', '/api/leaves')).data;
    assert.equal(before[0].status, '대기');
    const r = await hr('POST', '/api/approvals/A-2611/approve', {});
    assert.equal(r.data.approvals.find((a: any) => a.id === 'A-2611').status, '승인');
    const after = (await yoon('GET', '/api/leaves')).data;
    assert.equal(after[0].status, '승인');
  });

  await test('reject ends the document', async () => {
    const r = await hr('POST', '/api/approvals/A-2608/reject', {});
    assert.equal(r.data.approvals.find((a: any) => a.id === 'A-2608').status, '반려');
    assert.equal((await hr('POST', '/api/approvals/A-2608/approve', {})).status, 409);
  });

  await test('leave request creates an approval routed to team lead then HR lead', async () => {
    await lim('POST', '/api/login', { loginId: 'hj.lim', password: '1234' });
    const r = await lim('POST', '/api/leaves', { type: '연차', from: '2030-03-04', to: '2030-03-05', reason: '여행' });
    assert.equal(r.status, 200);
    assert.equal(r.data.leaves[0].days, 2);
    const doc = r.data.approvals[0];
    assert.equal(doc.kind, '휴가');
    assert.deepEqual(doc.line.map((s: any) => s.name), ['이도윤', '김지훈']);
    // team lead approves, then HR lead
    const d1 = (await dev('POST', `/api/approvals/${doc.id}/approve`, {})).data.approvals.find((a: any) => a.id === doc.id);
    assert.equal(d1.status, '대기');
    const d2 = (await hr('POST', `/api/approvals/${doc.id}/approve`, {})).data.approvals.find((a: any) => a.id === doc.id);
    assert.equal(d2.status, '승인');
    assert.equal((await lim('GET', '/api/leaves')).data[0].status, '승인');
  });

  await test('HR team member’s leave goes straight to the HR lead', async () => {
    const r = await yoon('POST', '/api/leaves', { type: '반차', from: '2030-04-02', to: '2030-04-02' });
    assert.equal(r.data.leaves[0].days, 0.5);
    assert.deepEqual(r.data.approvals[0].line.map((s: any) => s.name), ['김지훈']);
  });

  await test('HR lead’s own leave goes to the 경영지원 lead', async () => {
    const r = await hr('POST', '/api/leaves', { type: '연차', from: '2030-05-06', to: '2030-05-06' });
    assert.equal(r.status, 200);
    assert.deepEqual(r.data.approvals[0].line.map((s: any) => s.name), ['박서연']);
  });

  await test('leave validation: weekend-only, too many days, bad dates', async () => {
    assert.equal((await yoon('POST', '/api/leaves', { type: '연차', from: '2030-03-09', to: '2030-03-10' })).status, 400); // Sat–Sun
    assert.equal((await yoon('POST', '/api/leaves', { type: '연차', from: '2030-01-01', to: '2030-02-28' })).status, 400);
    assert.equal((await yoon('POST', '/api/leaves', { type: '연차', from: 'tomorrow', to: '2030-02-28' })).status, 400);
  });

  await test('clock in → out → undo keeps the first clock-in', async () => {
    const x = client();
    await x('POST', '/api/login', { loginId: 'ja.oh', password: '1234' });
    const s0 = (await x('GET', '/api/attendance')).data;
    const a = (await x('POST', '/api/attendance/toggle', {})).data;   // in (or out if seeded)
    const b = (await x('POST', '/api/attendance/toggle', {})).data;
    const c = (await x('POST', '/api/attendance/toggle', {})).data;
    assert.ok(a.clockIn && b.clockIn && c.clockIn);
    assert.equal(a.clockIn, c.clockIn);
    assert.notEqual(Boolean(b.clockOut), Boolean(c.clockOut));
    assert.equal(s0.week.length, 5);
  });

  await test('messages: send, unread on the other side, mark read', async () => {
    const t = (await dev('GET', '/api/threads')).data.find((x: any) => x.name === '김지훈');
    const before = (await hr('GET', '/api/threads')).data.find((x: any) => x.name === '이도윤').unread;
    await dev('POST', `/api/threads/${t.id}/messages`, { text: 'JD 초안 보냈습니다' });
    const mine = (await hr('GET', '/api/threads')).data.find((x: any) => x.name === '이도윤');
    assert.equal(mine.unread, before + 1);
    assert.equal(mine.preview, 'JD 초안 보냈습니다');
    await hr('POST', `/api/threads/${t.id}/read`, {});
    assert.equal((await hr('GET', '/api/threads')).data.find((x: any) => x.name === '이도윤').unread, 0);
  });

  await test('cannot read someone else’s thread', async () => {
    const r = await yoon('POST', '/api/threads/TH-1/messages', { text: 'hi' });
    assert.equal(r.status, 404);
  });

  await test('logout invalidates the session', async () => {
    await yoon('POST', '/api/logout', {});
    assert.equal((await yoon('GET', '/api/me')).status, 401);
  });

  await test('login throttling after 5 failures', async () => {
    const x = client();
    for (let i = 0; i < 5; i++) await x('POST', '/api/login', { loginId: 'ja.oh', password: 'bad' });
    assert.equal((await x('POST', '/api/login', { loginId: 'ja.oh', password: '1234' })).status, 429);
  });

  console.log(`\n${passed} passed`);
} finally {
  server.close();
  await db.close();
  rmSync(dir, { recursive: true, force: true });
}
