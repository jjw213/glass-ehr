import { loadAppData } from './bootstrap';
import { ApiError, type Api } from './types';

/**
 * Talks to the server under /api. In dev, Vite proxies /api → Node server (4000),
 * except the Spring backend paths listed in vite.config.ts (공통코드 → 8080).
 *
 * 사용자는 로그인 세션 쿠키(GHR_SESSION, HttpOnly·SameSite=Lax)로 식별한다(#76, docs/auth-design.md 8장).
 * 화면은 X-Actor-Id 를 보내지 않는다. 상태를 바꾸는 요청의 CSRF 검사용으로 모든 요청에 아래 헤더를 붙인다.
 */
export const CSRF_HEADERS: Readonly<Record<string, string>> = { 'X-Requested-With': 'XMLHttpRequest' };
/** JSON 이 아닌 요청(파일 올리기·내려받기)에 붙일 헤더. */
export function csrfHeader(): Record<string, string> { return { ...CSRF_HEADERS }; }

let onUnauthorized: (() => void) | null = null;
/** 세션이 끝나 API 가 401 을 주면 부를 함수(로그인 화면으로 돌아가기). 로그인 요청 자체의 401 은 부르지 않는다. */
export function setUnauthorizedHandler(fn: (() => void) | null) { onUnauthorized = fn; }
/** 로그인 요청이 아닌 API 가 401 을 주면 세션 만료로 보고 처리한다. */
export function notifyIfUnauthorized(status: number, path: string) {
  if (status === 401 && path !== '/auth/login' && path !== '/login') onUnauthorized?.();
}

/**
 * body 가 문자열이면 JSON 으로 바꾸지 않고 그대로 보낸다(예: 공휴일 CSV — Content-Type 은 extraHeaders 로).
 * 204(본문 없음)는 undefined 를 돌려준다.
 */
export async function call<T>(method: 'GET' | 'POST' | 'PUT' | 'DELETE', path: string, body?: unknown, extraHeaders?: Record<string, string>): Promise<T> {
  const hasBody = method !== 'GET' && method !== 'DELETE';
  const headers: Record<string, string> = {
    ...(hasBody ? { 'Content-Type': 'application/json' } : {}),
    ...CSRF_HEADERS,
    ...extraHeaders,
  };
  let res: Response;
  try {
    res = await fetch('/api' + path, {
      method,
      credentials: 'same-origin',
      headers,
      body: hasBody ? (typeof body === 'string' ? body : JSON.stringify(body ?? {})) : undefined,
    });
  } catch {
    throw new ApiError(0, '서버에 연결할 수 없어요. 서버가 켜져 있는지 확인해 주세요');
  }
  if (!res.ok) {
    let msg = `요청에 실패했어요 (${res.status})`;
    try { msg = ((await res.json()) as { error?: string }).error ?? msg; } catch { /* not JSON */ }
    notifyIfUnauthorized(res.status, path);
    throw new ApiError(res.status, msg);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export const httpApi: Api = {
  mode: 'http',
  enterCompanies: () => call('GET', '/enter-companies'),
  // 로그인은 Spring(/api/auth/login, 세션 쿠키)이 정한다(#76). 홈 화면 데이터(/api/bootstrap 등)가 아직 Node 서버라
  // 옮기는 동안만 같은 아이디·비밀번호로 Node 에도 로그인한다. Node 경로를 모두 옮기면 두 번째 호출을 지운다.
  login: async (enterCd, loginId, password) => {
    // 로그인 화면에서 고른 회사(#120). 법인은 로그인 본문과 그룹 관리자 전환 API 에서만 보낸다(docs/tenant-design.md 2장).
    await call('POST', '/auth/login', { enterCd, loginId, password });
    try {
      await call('POST', '/login', { loginId, password });
    } catch (e) {
      try { await call('POST', '/auth/logout'); } catch { /* 이미 끝남 */ }
      throw e;
    }
  },
  switchEnter: async (enterCd) => { await call('POST', '/auth/switch-enter', { enterCd }); },
  logout: async () => {
    const results = await Promise.allSettled([call('POST', '/auth/logout'), call('POST', '/logout')]);
    const failed = results.find((r): r is PromiseRejectedResult => r.status === 'rejected');
    if (failed) throw failed.reason;
  },
  // 사용자·부서·홈 요약은 Spring, 나머지는 아직 Node /api/bootstrap(#77, api/bootstrap.ts)
  bootstrap: () => loadAppData((path) => call('GET', path)),
  addLeave: (input) => call('POST', '/leaves', input),
  decide: (id, action) => call('POST', `/approvals/${encodeURIComponent(id)}/${action}`),
  // 메시지(#80)는 Spring.
  threads: () => call('GET', '/threads'),
  messages: (threadId, beforeId) => call('GET', `/threads/${threadId}/messages${beforeId != null ? `?beforeId=${beforeId}` : ''}`),
  send: (threadId, text) => call('POST', `/threads/${threadId}/messages`, { text }),
  markRead: async (threadId) => { await call('POST', `/threads/${threadId}/read`); },
};
