import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AppData } from '../api';
import { punchApi } from '../api/punches';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

/** #78 근태 화면: 출퇴근 버튼·상태는 Spring 출퇴근 API(api/punches)로, Node attendance 는 쓰지 않는다. 모두 가짜 값. */
vi.mock('../api/punches', async (orig) => {
  const m = await orig<typeof import('../api/punches')>();
  return { ...m, punchApi: m.createMockPunchApi(() => Date.parse('2099-03-02T00:00:00Z')) };
});
vi.mock('../components/WeeklyHoursWidget', () => ({ WeeklyHoursWidget: ({ refreshKey }: { refreshKey: number }) => <p data-testid="weekly">{refreshKey}</p> }));
const data: AppData = {
  me: { loginId: 'emp1', empId: 1, name: '가짜사원', dept: '', role: 'EMPLOYEE', roles: [], permissions: [], enterCd: 'C001', enterName: '가짜법인', homeEnterCd: 'C001', annualLeave: 15 }, companies: [],
  depts: [], legacyDepts: [], dashboard: null, approvals: [], leaves: [], payslips: [], threads: [],
};
vi.mock('../state/store', () => ({ useStore: () => ({ addLeave: vi.fn() }), useData: () => data }));

const { AttendancePage } = await import('./AttendancePage');
const flush = () => act(async () => { await new Promise((r) => setTimeout(r, 0)); });
const button = (host: HTMLElement, text: string) => [...host.querySelectorAll('button')].find((b) => b.textContent === text)!;

describe('AttendancePage 출퇴근 (#78)', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => { host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host); });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  it('Spring 출퇴근 응답으로 상태를 보여 주고, 기록하면 주간 근로시간을 다시 읽는다', async () => {
    await act(async () => root.render(<AttendancePage />));
    await flush();
    expect(host.textContent).toContain('출근 전');
    expect(host.querySelector('[data-testid="weekly"]')?.textContent).toBe('0');
    await act(async () => button(host, '출근').click());
    await flush();
    expect(host.textContent).toContain('근무 중');
    expect(host.querySelector('[data-testid="weekly"]')?.textContent).toBe('1');
    expect((await punchApi.today()).punches.map((p) => p.type)).toEqual(['IN']);
  });
});
