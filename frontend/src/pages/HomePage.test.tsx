import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AppData, DashboardSummary } from '../api';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

/** #77 홈: Spring 요약이 있으면 인원 카드·추이, 없으면(권한 없음) 숨김. 휴가·일정·공지는 Spring 기능이 없어 숨김. */
let data: AppData;
vi.mock('../state/store', () => ({
  useStore: () => ({ pendingCount: 0, go: vi.fn(), toast: vi.fn(), dept: () => undefined }),
  useData: () => data,
}));
vi.mock('../components/PunchWidget', () => ({ PunchWidget: () => null }));
vi.mock('../components/WeeklyHoursWidget', () => ({ WeeklyHoursWidget: () => null }));

const { HomePage } = await import('./HomePage');

const SUMMARY: DashboardSummary = {
  today: '2099-03-02', headcount: 42, hiredThisMonth: 3, presentToday: 21,
  // 2098-04 ~ 2099-03
  trend: Array.from({ length: 12 }, (_, i) => ({ month: i < 9 ? `2098-${String(i + 4).padStart(2, '0')}` : `2099-0${i - 8}`, hire: i, leave: 0 })),
};

const make = (dashboard: DashboardSummary | null): AppData => ({
  me: { loginId: 'emp1', empId: 1, name: '가짜사원', dept: '', role: 'EMPLOYEE', roles: [], permissions: [], annualLeave: 15 },
  depts: [], legacyDepts: [], dashboard,
  approvals: [], leaves: [], payslips: [], threads: [],
});

describe('HomePage', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => { host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host); });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  it('요약이 있으면 재직 인원·오늘 출근·추이를 보여 준다', () => {
    data = make(SUMMARY);
    act(() => root.render(<HomePage />));
    const text = host.textContent ?? '';
    expect(text).toContain('재직 인원');
    expect(text).toContain('42');
    expect(text).toContain('출근율 50%');
    expect(text).toContain('입사 · 퇴사 추이');
    expect(text).toContain('3월'); // 'YYYY-MM' → 'M월'
    expect(text).not.toContain('휴가 · 부재');
    expect(text).not.toContain('다가오는 일정');
    expect(text).not.toContain('공지사항');
  });

  it('요약을 볼 권한이 없으면(null) 인원 카드와 추이를 숨기고 결재 대기는 보인다', () => {
    data = make(null);
    act(() => root.render(<HomePage />));
    const text = host.textContent ?? '';
    expect(text).not.toContain('재직 인원');
    expect(text).not.toContain('입사 · 퇴사 추이');
    expect(text).toContain('내 결재 대기');
  });
});
