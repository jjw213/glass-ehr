import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createMockEnterSettingApi, httpEnterSettingApi, validateSetting } from '../api/enterSettings';
import { canManageEnterSettings, navGroupsFor } from '../nav';
import { EnterSettingManager } from './EnterSettingsPage';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const flush = () => act(async () => { await new Promise((r) => setTimeout(r, 0)); });

function type(el: HTMLInputElement, value: string) {
  Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(el, value);
  el.dispatchEvent(new Event('input', { bubbles: true }));
}

describe('회사 설정 메뉴', () => {
  it('시스템관리 아래, 인사담당자·관리자에게만', () => {
    expect(canManageEnterSettings('HR')).toBe(true);
    expect(canManageEnterSettings('ADMIN')).toBe(true);
    expect(canManageEnterSettings('EMPLOYEE')).toBe(false);
    expect(navGroupsFor('ADMIN').find((g) => g.module.id === 'system')!.items.map((n) => n.id)).toContain('enterSettings');
  });
});

describe('validateSetting', () => {
  const s = { label: '상시근로자 수', unit: '명', min: 1, max: 100 };
  it('적용 시작일·정수 범위를 검사한다', () => {
    expect(validateSetting(s, '', '10')).toBe('적용 시작일을 넣어 주세요');
    expect(validateSetting(s, '2099-01-01', '0')).toContain('1~100명');
    expect(validateSetting(s, '2099-01-01', '1.5')).toContain('정수');
    expect(validateSetting(s, '2099-01-01', '')).toContain('정수');
    expect(validateSetting(s, '2099-01-01', '100')).toBeNull();
  });
});

describe('httpEnterSettingApi', () => {
  afterEach(() => vi.unstubAllGlobals());
  it('법인 코드를 보내지 않고 키·적용 시작일·값만 보낸다', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({}), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    await httpEnterSettingApi.update('WORKPLACE_REGULAR_HEADCOUNT', { effectiveFrom: '2099-01-01', value: 12 });
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('/api/enter-settings/WORKPLACE_REGULAR_HEADCOUNT');
    expect(init.method).toBe('PUT');
    expect(JSON.parse(String(init.body))).toEqual({ effectiveFrom: '2099-01-01', value: 12 });
  });
});

describe('EnterSettingManager', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => { host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host); });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  async function mount() {
    const api = createMockEnterSettingApi(() => '2099-06-01');
    const toast = vi.fn();
    await act(async () => { root.render(<EnterSettingManager api={api} toast={toast} />); });
    await flush();
    await flush();
    return { api, toast };
  }
  const button = (label: string) => host.querySelector<HTMLButtonElement>(`[aria-label="${label}"]`)!;
  const submitModal = async () => {
    await act(async () => { host.querySelector<HTMLFormElement>('.modal form')!.requestSubmit(); });
    await flush();
    await flush();
  };

  it('기본값 표시 → 상시근로자 수 수정 → 오늘 값·이력 반영', async () => {
    const { api, toast } = await mount();
    const firstRow = () => host.querySelector('tbody tr')!.textContent!;
    expect(firstRow()).toContain('상시근로자 수');
    expect(firstRow()).toContain('30명');
    expect(firstRow()).toContain('기본값');
    expect(host.textContent).toContain('변경 이력이 없어요');

    await act(async () => { button('상시근로자 수 수정').click(); });
    await act(async () => {
      type(host.querySelector<HTMLInputElement>('[aria-label="적용 시작일"]')!, '2099-03-01');
      type(host.querySelector<HTMLInputElement>('[aria-label="값"]')!, '12');
    });
    await submitModal();
    expect(toast).toHaveBeenCalledWith('상시근로자 수을(를) 저장했어요');
    expect(firstRow()).toContain('12명');
    expect(firstRow()).toContain('2099-03-01부터 12명');
    expect(firstRow()).not.toContain('기본값');
    expect(host.querySelector('[aria-label="변경 이력"]')!.textContent).toContain('새로 등록 12명');
    expect((await api.history())[0]).toMatchObject({ key: 'WORKPLACE_REGULAR_HEADCOUNT', oldValue: null, newValue: 12 });
  });

  it('범위 밖 값은 저장하지 않고 알려 준다', async () => {
    const { api } = await mount();
    await act(async () => { button('상시근로자 수 수정').click(); });
    await act(async () => { type(host.querySelector<HTMLInputElement>('[aria-label="값"]')!, '0'); });
    await submitModal();
    expect(host.querySelector('[role="alert"]')!.textContent).toContain('1~1000000명');
    expect(await api.history()).toEqual([]);
  });

  it.each(['1.5', '-30'])('잘못된 숫자 %s를 다른 정수로 바꾸지 않고 거부한다', async (invalidValue) => {
    const { api } = await mount();
    await act(async () => { button('상시근로자 수 수정').click(); });
    const valueInput = host.querySelector<HTMLInputElement>('[aria-label="값"]')!;
    await act(async () => { type(valueInput, invalidValue); });
    expect(valueInput.value).toBe(invalidValue);
    await submitModal();
    expect(host.querySelector('[role="alert"]')!.textContent).toContain('사이 정수');
    expect(await api.history()).toEqual([]);
  });
});
