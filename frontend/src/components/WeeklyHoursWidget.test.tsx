import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/types';
import { createMockWeeklyHoursApi, formatHours, gaugePercent, type WeeklyHoursApi } from '../api/weeklyHours';
import { WeeklyHoursWidget } from './WeeklyHoursWidget';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const flush = () => act(async () => { await new Promise((r) => setTimeout(r, 0)); });

describe('주간 근로시간 표시 계산', () => {
  it('분을 시간으로 보여 준다', () => {
    expect(formatHours(3150)).toBe('52시간 30분');
    expect(formatHours(2400)).toBe('40시간');
    expect(formatHours(45)).toBe('45분');
    expect(formatHours(-30)).toBe('-30분');
  });
  it('게이지는 한도 대비 비율이고 초과는 100에서 멈춘다', () => {
    expect(gaugePercent({ workedMinutes: 1560, limitMinutes: 3120 })).toBe(50);
    expect(gaugePercent({ workedMinutes: 4000, limitMinutes: 3120 })).toBe(100);
    expect(gaugePercent({ workedMinutes: 10, limitMinutes: 0 })).toBe(0);
  });
});

describe('WeeklyHoursWidget', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => { host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host); });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  it('내 게이지와 부서별 표를 보여 주고 근접·초과자를 강조한다', async () => {
    act(() => root.render(<WeeklyHoursWidget api={createMockWeeklyHoursApi()} />));
    await flush();
    const meter = host.querySelector('[role="meter"]')!;
    expect(meter.getAttribute('aria-valuenow')).toBe('1950');
    expect(meter.getAttribute('aria-valuemax')).toBe('3120');
    expect(host.querySelector('[aria-label="누적 근로시간"]')?.textContent).toBe('32시간 30분');
    expect(host.querySelector('.weekly-table__row--over')?.textContent).toContain('가상직원C');
    expect(host.querySelector('.weekly-table__row--near')?.textContent).toContain('가상직원B');
    expect(host.querySelectorAll('.weekly-table tbody tr')).toHaveLength(3);
  });

  it('본인이 한도 근접이면 경고 문구를 보여 준다', async () => {
    const base = createMockWeeklyHoursApi();
    const api: WeeklyHoursApi = {
      async mine() {
        const m = await base.mine();
        m.me.week = { ...m.me.week, workedMinutes: 2940, remainingMinutes: 180, overtimeRemainingMinutes: 180, status: 'NEAR' };
        return m;
      },
      team: () => Promise.reject(new ApiError(403, '권한이 없어요')),
    };
    act(() => root.render(<WeeklyHoursWidget api={api} />));
    await flush();
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('3시간 남았어요');
    // 부서장·인사가 아니면(403) 부서별 표를 숨기고 오류도 보이지 않는다.
    expect(host.querySelector('.weekly-hours__team')).toBeNull();
    expect(host.textContent).not.toContain('권한이 없어요');
  });

  it('한도 초과면 초과 경고를 보여 준다', async () => {
    const base = createMockWeeklyHoursApi();
    const api: WeeklyHoursApi = {
      async mine() {
        const m = await base.mine();
        m.me.week = { ...m.me.week, workedMinutes: 3200, remainingMinutes: -80, status: 'OVER' };
        return m;
      },
      team: base.team,
    };
    act(() => root.render(<WeeklyHoursWidget api={api} />));
    await flush();
    expect(host.querySelector('.weekly-hours__warn--over')?.textContent).toContain('한도를 넘었어요');
    expect(host.querySelector('.gauge--over')).not.toBeNull();
  });

  it('서버 오류는 알려 준다', async () => {
    const api: WeeklyHoursApi = {
      mine: () => Promise.reject(new ApiError(409, '기준값이 없어요')),
      team: () => Promise.reject(new ApiError(403, 'x')),
    };
    act(() => root.render(<WeeklyHoursWidget api={api} />));
    await flush();
    expect(host.querySelector('[role="alert"]')?.textContent).toBe('기준값이 없어요');
  });

  it('refreshKey가 바뀌면 주간 현황을 다시 조회한다', async () => {
    const base = createMockWeeklyHoursApi();
    const api: WeeklyHoursApi = { mine: vi.fn(base.mine), team: vi.fn(base.team) };
    act(() => root.render(<WeeklyHoursWidget api={api} refreshKey={0} />));
    await flush();
    act(() => root.render(<WeeklyHoursWidget api={api} refreshKey={1} />));
    await flush();
    expect(api.mine).toHaveBeenCalledTimes(2);
    expect(api.team).toHaveBeenCalledTimes(2);
  });
});
