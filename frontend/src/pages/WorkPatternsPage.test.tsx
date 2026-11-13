import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  contractMinutes, createMockWorkPatternApi, duration, parseHm, toHm, validateWorkPattern,
  type WorkPatternApi, type WorkPatternInput,
} from '../api/workPatterns';
import { canManageWorkPatterns, navFor } from '../nav';
import { WorkPatternManager } from './WorkPatternsPage';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const flush = () => act(async () => { await new Promise((r) => setTimeout(r, 0)); });

function type(el: HTMLInputElement, value: string) {
  Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(el, value);
  el.dispatchEvent(new Event('input', { bubbles: true }));
}
const buttonByText = (host: HTMLElement, text: string) =>
  [...host.querySelectorAll('button')].find((b) => b.textContent?.includes(text))!;
const byLabel = (host: HTMLElement, label: string) => host.querySelector<HTMLElement>(`[aria-label="${label}"]`)!;

const base: WorkPatternInput = {
  patternCode: 'DAY2', patternName: '주간', workStartMin: 540, workEndMin: 1080, breakStartMin: 720, breakMinutes: 60,
  dayTypes: 'WWWWWOH', active: true, note: null,
};

describe('근무유형 메뉴 권한', () => {
  it('인사담당자·관리자에게만 보인다', () => {
    expect(canManageWorkPatterns('HR')).toBe(true);
    expect(canManageWorkPatterns('ADMIN')).toBe(true);
    expect(canManageWorkPatterns('EMPLOYEE')).toBe(false);
    expect(navFor('EMPLOYEE').map((n) => n.id)).not.toContain('workPatterns');
  });
});

describe('근무유형 형식 검사·미리보기 (법정 수치 없음)', () => {
  it('시각 변환', () => {
    expect(parseHm('09:05')).toBe(545);
    expect(parseHm('24:00')).toBeNull();
    expect(parseHm('9:00')).toBeNull();
    expect(toHm(545)).toBe('09:05');
    expect(duration(30)).toBe('30분');
    expect(duration(510)).toBe('8시간 30분');
  });

  it('소정근로시간은 휴게를 빼고, 종업이 시업보다 이르면 다음 날로 본다', () => {
    expect(contractMinutes(base)).toEqual({ daily: 480, weekly: 2400 });
    expect(contractMinutes({ ...base, workStartMin: 1320, workEndMin: 300, breakStartMin: 60, breakMinutes: 60, dayTypes: 'WWWOOOH' }))
      .toEqual({ daily: 360, weekly: 1080 });
  });

  it('주휴일·근무일이 없거나 휴게가 근로시간 밖이면 오류', () => {
    expect(validateWorkPattern(base)).toBeNull();
    expect(validateWorkPattern({ ...base, dayTypes: 'WWWWWOO' })).toContain('주휴일');
    expect(validateWorkPattern({ ...base, dayTypes: 'OOOOOOH' })).toContain('근무일');
    expect(validateWorkPattern({ ...base, breakStartMin: 540 })).toContain('근로시간 도중');
    expect(validateWorkPattern({ ...base, breakStartMin: 1020 })).toContain('근로시간 도중');
    expect(validateWorkPattern({ ...base, breakStartMin: null })).toContain('휴게 시작');
    expect(validateWorkPattern({ ...base, patternCode: 'day' })).toContain('코드');
  });
});

describe('WorkPatternManager', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => { host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host); });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  async function mount(api: WorkPatternApi = createMockWorkPatternApi()) {
    const toast = vi.fn();
    await act(async () => { root.render(<WorkPatternManager api={api} toast={toast} />); });
    await flush();
    return { api, toast };
  }

  it('목록에 소정근로시간과 주휴일을 보인다', async () => {
    await mount();
    const codes = [...host.querySelectorAll('tbody code')].map((c) => c.textContent);
    expect(codes).toEqual(['DAY', 'PART_AM']);
    expect(host.querySelector('tbody tr')!.textContent).toContain('8시간 / 40시간');
    expect(host.querySelector('tbody tr')!.textContent).toContain('일 주휴');
  });

  it('추가하면 서버 검사 후 저장하고 알림을 띄운다', async () => {
    const api = createMockWorkPatternApi();
    const check = vi.spyOn(api, 'check');
    const { toast } = await mount(api);
    await act(async () => { buttonByText(host, '근무유형 추가').click(); });
    const inputs = host.querySelectorAll<HTMLInputElement>('.modal input.input');
    await act(async () => { type(inputs[0], 'night'); type(inputs[1], '야간'); });
    expect(inputs[0].value).toBe('NIGHT');
    expect(host.querySelector('[data-testid="preview"]')!.textContent).toContain('1일 8시간 · 1주 40시간');
    await act(async () => { host.querySelector<HTMLFormElement>('.modal form')!.requestSubmit(); });
    await flush();
    expect(check).toHaveBeenCalledOnce();
    expect(toast).toHaveBeenCalledWith('근무유형 NIGHT를 추가했어요');
    expect((await api.list()).map((r) => r.patternCode)).toContain('NIGHT');
  });

  it('주휴일을 지정하지 않으면 저장하지 않는다', async () => {
    const api = createMockWorkPatternApi();
    const create = vi.spyOn(api, 'create');
    await mount(api);
    await act(async () => { buttonByText(host, '근무유형 추가').click(); });
    const inputs = host.querySelectorAll<HTMLInputElement>('.modal input.input');
    await act(async () => { type(inputs[0], 'X'); type(inputs[1], '엑스'); });
    // 일요일: 주휴 → 근무
    await act(async () => { byLabel(host, '일요일 주휴').click(); });
    expect(byLabel(host, '일요일 근무')).toBeTruthy();
    await act(async () => { host.querySelector<HTMLFormElement>('.modal form')!.requestSubmit(); });
    await flush();
    expect(host.querySelector('.modal [role="alert"]')!.textContent).toContain('주휴일');
    expect(create).not.toHaveBeenCalled();
  });

  it('서버가 법정 기준 위반을 돌려주면 모두 보이고 저장하지 않는다', async () => {
    const api = createMockWorkPatternApi();
    vi.spyOn(api, 'check').mockResolvedValue({
      ok: false, dailyContractMinutes: 0, weeklyContractMinutes: 0,
      violations: [
        { code: 'DAILY_LIMIT_EXCEEDED', message: '1일 소정근로시간이 법정 한도를 넘어요(근로기준법 제50조 제2항)' },
        { code: 'BREAK_INSUFFICIENT', message: '휴게시간이 부족해요(근로기준법 제54조 제1항)' },
      ],
    });
    const create = vi.spyOn(api, 'create');
    await mount(api);
    await act(async () => { buttonByText(host, '근무유형 추가').click(); });
    const inputs = host.querySelectorAll<HTMLInputElement>('.modal input.input');
    await act(async () => { type(inputs[0], 'LONG'); type(inputs[1], '긴 근무'); });
    await act(async () => { host.querySelector<HTMLFormElement>('.modal form')!.requestSubmit(); });
    await flush();
    const items = [...host.querySelectorAll('.violations li')].map((li) => li.textContent);
    expect(items).toHaveLength(2);
    expect(items[0]).toContain('제50조');
    expect(create).not.toHaveBeenCalled();
    expect(host.querySelector('.modal')).not.toBeNull();
  });

  it('배정된 근무유형은 시각 입력이 잠기고 이름만 바꿀 수 있다', async () => {
    const { api, toast } = await mount();
    await act(async () => { byLabel(host, '주간 근무 수정').click(); });
    expect(host.querySelector('.modal [role="note"]')!.textContent).toContain('배정된');
    expect((byLabel(host, '시업 시각') as HTMLInputElement).disabled).toBe(true);
    expect((byLabel(host, '월요일 근무') as HTMLButtonElement).disabled).toBe(true);
    const inputs = host.querySelectorAll<HTMLInputElement>('.modal input.input');
    await act(async () => { type(inputs[1], '주간 근무 A'); });
    await act(async () => { host.querySelector<HTMLFormElement>('.modal form')!.requestSubmit(); });
    await flush();
    expect(toast).toHaveBeenCalledWith('근무유형 DAY를 수정했어요');
    expect((await api.list()).find((r) => r.patternCode === 'DAY')!.patternName).toBe('주간 근무 A');
  });
});
