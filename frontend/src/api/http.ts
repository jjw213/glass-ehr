import { ApiError, type Api } from './types';

/**
 * Talks to the server under /api. In dev, Vite proxies /api → Node server (4000),
 * except the Spring backend paths listed in vite.config.ts (공통코드 → 8080).
 */
export async function call<T>(method: 'GET' | 'POST' | 'PUT', path: string, body?: unknown): Promise<T> {
  const hasBody = method !== 'GET';
  let res: Response;
  try {
    res = await fetch('/api' + path, {
      method,
      credentials: 'same-origin',
      headers: hasBody ? { 'Content-Type': 'application/json' } : undefined,
      body: hasBody ? JSON.stringify(body ?? {}) : undefined,
    });
  } catch {
    throw new ApiError(0, '서버에 연결할 수 없어요. 서버가 켜져 있는지 확인해 주세요');
  }
  if (!res.ok) {
    let msg = `요청에 실패했어요 (${res.status})`;
    try { msg = ((await res.json()) as { error?: string }).error ?? msg; } catch { /* not JSON */ }
    throw new ApiError(res.status, msg);
  }
  return res.json() as Promise<T>;
}

export const httpApi: Api = {
  mode: 'http',
  login: async (loginId, password) => { await call('POST', '/login', { loginId, password }); },
  logout: async () => { await call('POST', '/logout'); },
  bootstrap: () => call('GET', '/bootstrap'),
  toggleClock: () => call('POST', '/attendance/toggle'),
  addLeave: (input) => call('POST', '/leaves', input),
  decide: (id, action) => call('POST', `/approvals/${encodeURIComponent(id)}/${action}`),
  threads: () => call('GET', '/threads'),
  send: (threadId, text) => call('POST', `/threads/${encodeURIComponent(threadId)}/messages`, { text }),
  markRead: async (threadId) => { await call('POST', `/threads/${encodeURIComponent(threadId)}/read`); },
};
