import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createMockFlexWorkApi, type FlexWorkApi } from '../api/flexWork';
import { navFor } from '../nav';
import { FlexWorkPage } from './FlexWorkPage';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

describe('사원 근무계획 화면', () => {
  let host: HTMLDivElement;
  let root: Root;
  let api: FlexWorkApi;

  beforeEach(() => {
    host = document.createElement('div');
    document.body.appendChild(host);
    root = createRoot(host);
    api = createMockFlexWorkApi();
  });

  afterEach(() => {
    act(() => root.unmount());
    host.remove();
  });

  async function render() {
    await act(async () => root.render(<FlexWorkPage api={api} />));
  }

  function input(label: string): HTMLInputElement {
    return host.querySelector<HTMLInputElement>(`input[aria-label="${label}"]`)!;
  }

  function type(label: string, value: string) {
    const element = input(label);
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(element, value);
    element.dispatchEvent(new Event('input', { bubbles: true }));
  }

  async function submit() {
    await act(async () => host.querySelector<HTMLButtonElement>('button[type="submit"]')!.click());
  }

  it('일반 사원이 메뉴에서 본인의 선택적 배정과 의무 근로시간대를 볼 수 있다', async () => {
    expect(navFor('EMPLOYEE').some((item) => item.id === 'flexWork')).toBe(true);
    await render();
    expect(host.querySelector('option')?.textContent).toBe('2026-03-02 ~ 2026-03-08');
    expect(host.textContent).toContain('의무 근로시간대 10:00 ~ 15:00');
    expect(host.querySelectorAll('tbody tr')).toHaveLength(7);
  });

  it('배정이 없으면 계획 입력 대신 빈 상태를 표시한다', async () => {
    api.mine = async () => [];
    const detail = vi.spyOn(api, 'detail');

    await render();

    expect(host.textContent).toContain('선택적 근로시간제 배정이 없어요');
    expect(detail).not.toHaveBeenCalled();
    expect(host.querySelector('form')).toBeNull();
  });

  it('시간과 휴게를 저장하고 서버가 저장한 계획을 다시 표시한다', async () => {
    const save = vi.spyOn(api, 'savePlan');
    await render();
    await act(async () => {
      type('2026-03-02 시업', '09:00');
      type('2026-03-02 종업', '17:00');
      type('2026-03-02 휴게(분)', '30');
    });

    await submit();

    expect(save).toHaveBeenCalledWith(1, [{
      date: '2026-03-02', start: '2026-03-02T09:00:00', end: '2026-03-02T17:00:00', minutes: 450,
    }]);
    expect(host.textContent).toContain('7시간 30분');
    expect(host.querySelector('[role="status"]')?.textContent).toContain('근무계획을 저장했어요');
    expect(input('2026-03-02 휴게(분)').value).toBe('30');
  });

  it('기존 자정 넘김 계획을 다시 저장해도 종업일과 휴게 시간이 보존된다', async () => {
    const overnight = { date: '2026-03-02', start: '2026-03-02T22:00:00', end: '2026-03-03T02:00:00', minutes: 220 };
    await api.savePlan(1, [overnight]);
    const save = vi.spyOn(api, 'savePlan');

    await render();
    expect(input('2026-03-02 종업일').value).toBe('2026-03-03');
    expect(input('2026-03-02 휴게(분)').value).toBe('20');
    await submit();

    expect(save).toHaveBeenCalledWith(1, [overnight]);
  });

  it('시업만 입력하면 저장을 막고 오류를 표시한다', async () => {
    const save = vi.spyOn(api, 'savePlan');
    await render();
    await act(async () => type('2026-03-02 시업', '09:00'));

    await submit();

    expect(save).not.toHaveBeenCalled();
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('2026-03-02 시업·종업 시각을 입력해 주세요');
  });

  it('서버가 계획을 거절하면 입력을 유지하고 오류를 표시한다', async () => {
    vi.spyOn(api, 'savePlan').mockRejectedValueOnce(new Error('서버에서 근무계획을 거절했어요'));
    await render();
    await act(async () => {
      type('2026-03-02 시업', '09:00');
      type('2026-03-02 종업', '17:00');
    });

    await submit();

    expect(host.querySelector('[role="alert"]')?.textContent).toBe('서버에서 근무계획을 거절했어요');
    expect(input('2026-03-02 시업').value).toBe('09:00');
    expect(input('2026-03-02 종업').value).toBe('17:00');
  });
});
