import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createMockPunchApi, elapsedMinutes, formatElapsed, seoulTime, type PunchApi } from '../api/punches';
import { PunchWidget } from './PunchWidget';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

/* 시각은 가짜 값(2099년). 2099-03-02 09:00 KST = 00:00Z */
const T0 = Date.parse('2099-03-02T00:00:00Z');
const flush = () => act(async () => { await new Promise((r) => setTimeout(r, 0)); });
const buttonByText = (host: HTMLElement, text: string) => [...host.querySelectorAll('button')].find((b) => b.textContent === text)!;
const byLabel = (host: HTMLElement, label: string) => host.querySelector(`[aria-label="${label}"]`)?.textContent;

describe('출퇴근 표시 계산', () => {
  it('근무 중이면 서버 시각 보정을 더해 첫 출근부터 센다', () => {
    const t = { firstInAt: '2099-03-02T09:00:00+09:00', lastOutAt: null, working: true };
    // 단말 시계가 10분 늦어도(offset +10분) 서버 기준으로 센다.
    expect(elapsedMinutes(t, T0 + 80 * 60000, 10 * 60000)).toBe(90);
    expect(elapsedMinutes(t, T0 + 59_999, 0)).toBe(0);
  });
  it('퇴근했으면 마지막 퇴근까지이고, 출근이 없으면 null', () => {
    expect(elapsedMinutes({ firstInAt: '2099-03-02T09:00:00+09:00', lastOutAt: '2099-03-02T18:05:00+09:00', working: false }, T0 + 20 * 3600_000, 0)).toBe(545);
    expect(elapsedMinutes({ firstInAt: null, lastOutAt: null, working: false }, T0, 0)).toBeNull();
  });
  it('시간 표시는 서울 기준', () => {
    expect(formatElapsed(545)).toBe('9시간 5분');
    expect(formatElapsed(7)).toBe('7분');
    expect(seoulTime('2099-03-02T00:30:00Z')).toBe('09:30');
  });
});

describe('가짜 출퇴근 API (backend PunchRules 와 같은 판정)', () => {
  it('중복 출근과 출근 없는 퇴근을 저장하고 표시한다', async () => {
    let now = T0;
    const api = createMockPunchApi(() => now);
    expect((await api.punch('OUT')).check).toBe('OUT_WITHOUT_IN');
    now += 60_000;
    expect((await api.punch('IN')).check).toBeNull();
    expect((await api.punch('IN')).check).toBe('DUPLICATE_IN');
    now += 60 * 60000;
    expect((await api.punch('OUT')).check).toBeNull();
    const t = await api.today();
    expect(t.punches).toHaveLength(4);
    expect(t.firstInAt).toBe('2099-03-02T09:01:00+09:00');
    expect(t.lastOutAt).toBe('2099-03-02T10:01:00+09:00');
    expect(t.working).toBe(false);
    expect(t.elapsedMinutes).toBe(60);
  });
});

describe('대시보드 출퇴근 위젯', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => { host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host); });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  it('출근하면 오늘 출근 시각과 경과 시간을 보여 준다', async () => {
    let now = T0;
    const api = createMockPunchApi(() => now);
    await act(async () => root.render(<PunchWidget api={api} now={() => now} />));
    await flush();
    expect(byLabel(host, '오늘 출근 시각')).toBe('—');
    expect(host.textContent).toContain('출근 전');
    await act(async () => buttonByText(host, '출근').click());
    await flush();
    expect(byLabel(host, '오늘 출근 시각')).toBe('09:00');
    expect(host.textContent).toContain('근무 중');
    expect(host.querySelector('[role="status"]')?.textContent).toContain('출근 09:00 기록했어요');
    now += 125 * 60000;
    await act(async () => root.render(<PunchWidget api={api} now={() => now} />));
    await flush();
    expect(byLabel(host, '근무 경과 시간')).toBe('2시간 5분');
  });

  it('단말 시계가 틀려도 서버 시각 기준으로 경과 시간을 센다', async () => {
    const server = createMockPunchApi(() => T0);
    await server.punch('IN');
    const api: PunchApi = { today: () => server.today(), punch: (t) => server.punch(t) };
    // 단말은 서버보다 3시간 빠르다.
    const device = () => T0 + 3 * 3600_000;
    await act(async () => root.render(<PunchWidget api={api} now={device} />));
    await flush();
    expect(byLabel(host, '근무 경과 시간')).toBe('0분');
  });

  it('출근 없이 퇴근하면 서버 안내 문구를 보여 준다', async () => {
    const api = createMockPunchApi(() => T0);
    await act(async () => root.render(<PunchWidget api={api} now={() => T0} />));
    await flush();
    await act(async () => buttonByText(host, '퇴근').click());
    await flush();
    expect(host.querySelector('[role="status"]')?.textContent).toContain('출근 기록이 없어');
  });

  it('불러오기에 실패하면 오류와 다시 불러오기를 보여 주고 버튼을 잠근다', async () => {
    const api: PunchApi = { today: () => Promise.reject(new Error('사원과 연결된 계정만 출퇴근을 기록할 수 있어요')), punch: () => Promise.reject(new Error('x')) };
    await act(async () => root.render(<PunchWidget api={api} />));
    await flush();
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('사원과 연결된 계정만');
    expect(buttonByText(host, '출근').disabled).toBe(true);
  });
});
