import { afterEach, describe, expect, it, vi } from 'vitest';
import { call, httpApi, setExpectedEnter, setUnauthorizedHandler } from './http';

/** #76 로그인·세션: Spring /api/auth/login 연결, CSRF 헤더, 세션 만료 처리 */
type Call = [string, RequestInit];
const json = (status: number, body: unknown = {}) => new Response(status === 204 ? null : JSON.stringify(body), { status });

afterEach(() => { setUnauthorizedHandler(null); setExpectedEnter(null); vi.unstubAllGlobals(); });

describe('httpApi 로그인·로그아웃', () => {
  it('Spring /api/auth/login 으로 로그인하고, 옮기는 동안 Node /api/login 에도 로그인한다', async () => {
    const fetchMock = vi.fn(async () => json(200, { loginId: 'hr' }));
    vi.stubGlobal('fetch', fetchMock);
    await httpApi.login('C002', 'hr', 'pw-12345678');
    const calls = fetchMock.mock.calls as unknown as Call[];
    expect(calls.map((c) => c[0])).toEqual(['/api/auth/login', '/api/login']);
    expect(calls[0][1].method).toBe('POST');
    // 로그인 화면에서 고른 회사를 보낸다(#120)
    expect(JSON.parse(String(calls[0][1].body))).toEqual({ enterCd: 'C002', loginId: 'hr', password: 'pw-12345678' });
    expect(JSON.parse(String(calls[1][1].body))).toEqual({ loginId: 'hr', password: 'pw-12345678' });
    expect(calls[0][1].headers).toMatchObject({ 'X-Requested-With': 'XMLHttpRequest' });
  });

  it('Spring 이 거부하면(401) Node 로는 보내지 않고, 세션 만료 처리도 하지 않는다', async () => {
    const fetchMock = vi.fn(async () => json(401, { error: '아이디 또는 비밀번호가 맞지 않아요' }));
    vi.stubGlobal('fetch', fetchMock);
    const lost = vi.fn();
    setUnauthorizedHandler(lost);
    await expect(httpApi.login('C001', 'hr', 'bad')).rejects.toMatchObject({ status: 401, message: '아이디 또는 비밀번호가 맞지 않아요' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(lost).not.toHaveBeenCalled();
  });

  it('잠금(423)은 서버 문구를 그대로 보여 준다', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json(423, { error: '계정이 잠겼어요' })));
    await expect(httpApi.login('C001', 'hr', 'x')).rejects.toMatchObject({ status: 423, message: '계정이 잠겼어요' });
  });

  it('Node 로그인이 실패하면 Spring 세션을 로그아웃으로 되돌린다', async () => {
    const fetchMock = vi.fn(async (url: string) => (url === '/api/login' ? json(401, { error: 'x' }) : json(url === '/api/auth/logout' ? 204 : 200)));
    vi.stubGlobal('fetch', fetchMock);
    await expect(httpApi.login('C001', 'hr', 'pw')).rejects.toMatchObject({ status: 401 });
    expect((fetchMock.mock.calls as unknown as Call[]).map((c) => c[0])).toEqual(['/api/auth/login', '/api/login', '/api/auth/logout']);
  });

  it('회사 목록은 공개 API /api/enter-companies 에서 받는다(#120)', async () => {
    const fetchMock = vi.fn(async () => json(200, [{ enterCd: 'C001', enterName: '가짜법인' }]));
    vi.stubGlobal('fetch', fetchMock);
    await expect(httpApi.enterCompanies()).resolves.toEqual([{ enterCd: 'C001', enterName: '가짜법인' }]);
    expect((fetchMock.mock.calls as unknown as Call[])[0][0]).toBe('/api/enter-companies');
  });

  it('법인 전환은 POST /api/auth/switch-enter 에 대상 법인만 보내고, 403 은 세션 만료로 보지 않는다(#120)', async () => {
    const fetchMock = vi.fn(async () => json(200, { enterCd: 'C002', homeEnterCd: 'C001' }));
    vi.stubGlobal('fetch', fetchMock);
    await httpApi.switchEnter('C002');
    const [url, init] = (fetchMock.mock.calls as unknown as Call[])[0];
    expect(url).toBe('/api/auth/switch-enter');
    expect(init.method).toBe('POST');
    expect(JSON.parse(String(init.body))).toEqual({ enterCd: 'C002' });
    expect(init.headers).toMatchObject({ 'X-Requested-With': 'XMLHttpRequest' });

    vi.stubGlobal('fetch', vi.fn(async () => json(403, { error: '권한이 없어요' })));
    const lost = vi.fn();
    setUnauthorizedHandler(lost);
    await expect(httpApi.switchEnter('C002')).rejects.toMatchObject({ status: 403 });
    expect(lost).not.toHaveBeenCalled();
  });

  it('로그아웃은 Spring·Node 세션을 모두 끝낸다', async () => {
    const fetchMock = vi.fn(async () => json(204));
    vi.stubGlobal('fetch', fetchMock);
    await httpApi.logout();
    expect((fetchMock.mock.calls as unknown as Call[]).map((c) => c[0]).sort()).toEqual(['/api/auth/logout', '/api/logout']);
  });
});

describe('세션 만료', () => {
  it('업무 요청에 화면이 보고 있는 현재 법인을 함께 보내 서버가 오래된 쓰기를 거부할 수 있게 한다', async () => {
    const fetchMock = vi.fn(async () => json(204));
    vi.stubGlobal('fetch', fetchMock);
    setExpectedEnter('C001');
    await call('POST', '/employees', {});
    expect((fetchMock.mock.calls as unknown as Call[])[0][1].headers).toMatchObject({ 'X-Expected-Enter': 'C001' });
  });

  it('로그인 외 API 가 401 을 주면 등록한 처리(로그인 화면으로)를 부른다', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json(401, { error: '로그인이 필요해요' })));
    const lost = vi.fn();
    setUnauthorizedHandler(lost);
    await expect(call('GET', '/employees')).rejects.toMatchObject({ status: 401 });
    expect(lost).toHaveBeenCalledTimes(1);
  });

  it('403 은 세션 만료가 아니다', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json(403, { error: '권한이 없어요' })));
    const lost = vi.fn();
    setUnauthorizedHandler(lost);
    await expect(call('POST', '/employees', {})).rejects.toMatchObject({ status: 403 });
    expect(lost).not.toHaveBeenCalled();
  });
});
