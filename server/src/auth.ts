import { createHash, randomBytes, scrypt as _scrypt, timingSafeEqual } from 'node:crypto';
import { promisify } from 'node:util';
import type { ServerResponse } from 'node:http';
import type { Db } from './db';
import { config } from './config';
import { HttpError, type Ctx } from './http';
import type { Role } from '../../shared/types';

const scrypt = promisify(_scrypt) as (pw: string, salt: Buffer, len: number) => Promise<Buffer>;
export const COOKIE = 'ghr_session';

/* ---------- passwords (scrypt, built into Node) ---------- */
export async function hashPassword(pw: string): Promise<string> {
  const salt = randomBytes(16);
  const hash = await scrypt(pw, salt, 64);
  return `scrypt$${salt.toString('base64')}$${hash.toString('base64')}`;
}
export async function verifyPassword(pw: string, stored: string): Promise<boolean> {
  const [algo, s, h] = stored.split('$');
  if (algo !== 'scrypt' || !s || !h) return false;
  const expected = Buffer.from(h, 'base64');
  const got = await scrypt(pw, Buffer.from(s, 'base64'), expected.length);
  return got.length === expected.length && timingSafeEqual(got, expected);
}

/* ---------- sessions (random token in an HttpOnly cookie, only its hash stored) ---------- */
const sha256 = (s: string) => createHash('sha256').update(s).digest('hex');

export async function createSession(db: Db, empId: string, res: ServerResponse) {
  const token = randomBytes(32).toString('base64url');
  const now = new Date();
  const expires = new Date(now.getTime() + config.sessionHours * 3600_000);
  await db.exec('INSERT INTO HR_SESSIONS (TOKEN_HASH, EMP_ID, CREATED_AT, EXPIRES_AT) VALUES (:h, :e, :c, :x)', { h: sha256(token), e: empId, c: now, x: expires });
  setCookie(res, token, config.sessionHours * 3600);
}

export async function destroySession(db: Db, ctx: Ctx) {
  const token = ctx.cookies[COOKIE];
  if (token) await db.exec('DELETE FROM HR_SESSIONS WHERE TOKEN_HASH = :h', { h: sha256(token) });
  setCookie(ctx.res, '', 0);
}

function setCookie(res: ServerResponse, value: string, maxAge: number) {
  const parts = [`${COOKIE}=${value}`, 'Path=/', 'HttpOnly', 'SameSite=Lax', `Max-Age=${maxAge}`];
  if (config.cookieSecure) parts.push('Secure');
  res.setHeader('Set-Cookie', parts.join('; '));
}

export interface User { id: string; name: string; role: Role; dept: string }

/** Resolve the signed-in user or throw 401. */
export async function requireUser(db: Db, ctx: Ctx): Promise<User> {
  const token = ctx.cookies[COOKIE];
  if (!token) throw new HttpError(401, '로그인이 필요해요');
  const rows = await db.query<{ id: string; name: string; role: Role; dept_id: string; expires_at: unknown }>(
    `SELECT e.ID, e.NAME, e.ROLE, e.DEPT_ID, s.EXPIRES_AT
       FROM HR_SESSIONS s JOIN HR_EMPLOYEES e ON e.ID = s.EMP_ID
      WHERE s.TOKEN_HASH = :h AND e.LEFT_ON IS NULL`,
    { h: sha256(token) },
  );
  const r = rows[0];
  if (!r || new Date(String(r.expires_at instanceof Date ? r.expires_at.toISOString() : r.expires_at)) < new Date()) {
    throw new HttpError(401, '세션이 만료됐어요. 다시 로그인해 주세요');
  }
  return { id: r.id, name: r.name, role: r.role, dept: r.dept_id };
}

/* ---------- login throttling (per IP + login id, in memory) ---------- */
const fails = new Map<string, { n: number; until: number }>();
export function checkThrottle(key: string) {
  const f = fails.get(key);
  if (f && f.n >= 5 && f.until > Date.now()) {
    throw new HttpError(429, `로그인 시도가 너무 많아요. ${Math.ceil((f.until - Date.now()) / 60000)}분 뒤에 다시 시도하세요`);
  }
}
export function recordFail(key: string) {
  const f = fails.get(key);
  const n = f && f.until > Date.now() ? f.n + 1 : 1;
  fails.set(key, { n, until: Date.now() + 5 * 60_000 });
}
export const clearFails = (key: string) => fails.delete(key);
