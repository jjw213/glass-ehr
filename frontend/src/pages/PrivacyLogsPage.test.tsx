import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { call, setActorId } from '../api/http';
import { createMockPrivacyLogApi, toSearchParams, validateQuery, type PrivacyLogRow } from '../api/privacyLogs';
import { canReadPrivacyLogs, navFor } from '../nav';
import { daysBefore, PrivacyLogViewer } from './PrivacyLogsPage';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const flush = () => act(async () => { await new Promise((r) => setTimeout(r, 0)); });

function type(el: HTMLInputElement, value: string) {
  Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(el, value);
  el.dispatchEvent(new Event('input', { bubbles: true }));
}

const row = (p: Partial<PrivacyLogRow>): PrivacyLogRow => ({
  source: 'PRIVACY', accessedAt: '2026-11-10T09:00:00', accessorId: 'hr1', accessorRoles: 'HR,EMPLOYEE', targetEmpId: 4,
  item: 'EMP_PERSONAL', action: 'VIEW', purpose: null, result: 'SUCCESS', clientIp: '127.0.0.1', requestPath: '/api/employees/4', ...p,
});

const ROWS: PrivacyLogRow[] = [
  row({}),
  row({ source: 'RRN', accessedAt: '2026-11-12T10:30:00', item: 'RRN', purpose: 'SOCIAL_INSURANCE', requestPath: null }),
  row({ accessedAt: '2026-11-11T08:00:00', accessorId: 'emp1', accessorRoles: 'EMPLOYEE', item: 'EMP_LIST', action: 'DOWNLOAD', result: 'DENIED', targetEmpId: null }),
  row({ accessedAt: '2026-09-01T08:00:00', accessorId: 'old' }),
];

describe('개인정보 조회 로그 메뉴 권한', () => {
  it('관리자에게만 보인다(인사담당자·일반 사원은 메뉴가 없다)', () => {
    expect(canReadPrivacyLogs('ADMIN')).toBe(true);
    expect(canReadPrivacyLogs('HR')).toBe(false);
    expect(canReadPrivacyLogs('EMPLOYEE')).toBe(false);
    expect(navFor('HR').map((n) => n.id)).not.toContain('privacyLogs');
  });
});

describe('조회 조건', () => {
  it('빈 값은 보내지 않고 나머지는 쿼리로 보낸다', () => {
    expect(toSearchParams({ from: '', to: '', accessorId: ' ', targetEmpId: '' })).toBe('');
    expect(toSearchParams({ from: '2026-11-01', to: '2026-11-13', accessorId: ' hr1 ', targetEmpId: '4' }))
      .toBe('?from=2026-11-01&to=2026-11-13&accessorId=hr1&targetEmpId=4');
  });
  it('끝 날짜가 빠르거나 사원번호가 숫자가 아니면 막는다', () => {
    expect(validateQuery({ from: '2026-11-13', to: '2026-11-12', accessorId: '', targetEmpId: '' })).toMatch('끝 날짜');
    expect(validateQuery({ from: '', to: '', accessorId: '', targetEmpId: '4x' })).toMatch('숫자');
    expect(validateQuery({ from: '2026-11-13', to: '2026-11-13', accessorId: '', targetEmpId: '7' })).toBeNull();
  });
  it('기본 기간은 30일 전부터(월·연 경계 포함)', () => {
    expect(daysBefore('2026-11-13', 30)).toBe('2026-10-14');
    expect(daysBefore('2026-01-15', 30)).toBe('2025-12-16');
    expect(daysBefore('2028-03-01', 1)).toBe('2028-02-29');
  });
});

describe('요청자 헤더', () => {
  afterEach(() => { setActorId(null); vi.unstubAllGlobals(); });
  it('로그인 아이디를 모든 요청에 X-Actor-Id 로 싣고, 역할 헤더는 보내지 않는다', async () => {
    const fetchMock = vi.fn(async () => new Response('{}', { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    setActorId('admin1');
    await call('GET', '/admin/privacy-logs');
    const init = (fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1];
    expect(init.headers).toEqual({ 'X-Actor-Id': 'admin1' });
    setActorId(null);
    await call('GET', '/org/tree');
    expect((fetchMock.mock.calls[1] as unknown as [string, RequestInit])[1].headers).toBeUndefined();
  });
});

describe('PrivacyLogViewer', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => { host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host); });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  it('기본 30일 기간으로 불러와 최근 순으로 누가·언제·누구의·무엇을 보여 준다', async () => {
    const api = createMockPrivacyLogApi(ROWS);
    await act(async () => { root.render(<PrivacyLogViewer api={api} today="2026-11-13" />); });
    await flush();
    expect(api.calls[0]).toEqual({ from: '2026-10-14', to: '2026-11-13', accessorId: '', targetEmpId: '' });
    const trs = [...host.querySelectorAll('tbody tr')].map((tr) => tr.textContent ?? '');
    expect(trs).toHaveLength(3); // 9월 기록은 기간 밖
    expect(trs[0]).toContain('2026-11-12 10:30:00');
    expect(trs[0]).toContain('주민등록번호 (4대보험 신고)');
    expect(trs[1]).toContain('emp1');
    expect(trs[1]).toContain('사원 목록 파일');
    expect(trs[1]).toContain('다운로드');
    expect(trs[1]).toContain('거부');
    expect(trs[2]).toContain('인적사항');
  });

  it('조회한 사람으로 거른다', async () => {
    const api = createMockPrivacyLogApi(ROWS);
    await act(async () => { root.render(<PrivacyLogViewer api={api} today="2026-11-13" />); });
    await flush();
    const inputs = host.querySelectorAll('input');
    act(() => type(inputs[2] as HTMLInputElement, 'emp1'));
    await act(async () => { host.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });
    await flush();
    expect(api.calls.at(-1)?.accessorId).toBe('emp1');
    expect(host.querySelectorAll('tbody tr')).toHaveLength(1);
  });

  it('잘못된 기간은 서버에 보내지 않고 알려 준다', async () => {
    const api = createMockPrivacyLogApi(ROWS);
    await act(async () => { root.render(<PrivacyLogViewer api={api} today="2026-11-13" />); });
    await flush();
    const inputs = host.querySelectorAll('input');
    act(() => type(inputs[1] as HTMLInputElement, '2026-01-01'));
    await act(async () => { host.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); });
    expect(api.calls).toHaveLength(1);
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('끝 날짜');
  });

  it('서버가 403 을 주면(관리자가 아님) 로그를 보이지 않고 오류를 알린다', async () => {
    const api = createMockPrivacyLogApi(ROWS, () => false);
    await act(async () => { root.render(<PrivacyLogViewer api={api} today="2026-11-13" />); });
    await flush();
    expect(host.querySelector('tbody')).toBeNull();
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('권한');
  });
});
