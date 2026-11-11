import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createMockCodeApi } from '../api/codes';
import { canManageCodes, navFor } from '../nav';
import { CodeManager } from './CodesPage';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const flush = () => act(async () => { await new Promise((r) => setTimeout(r, 0)); });

/** React 가 감지하도록 네이티브 setter 로 값을 넣고 input 이벤트를 보낸다 */
function type(el: HTMLInputElement | HTMLTextAreaElement, value: string) {
  const proto = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
  Object.getOwnPropertyDescriptor(proto, 'value')!.set!.call(el, value);
  el.dispatchEvent(new Event('input', { bubbles: true }));
}

const buttonByText = (host: HTMLElement, text: string) =>
  [...host.querySelectorAll('button')].find((b) => b.textContent?.includes(text))!;

describe('공통코드 메뉴 권한', () => {
  it('인사담당자·관리자에게만 보인다', () => {
    expect(canManageCodes('HR')).toBe(true);
    expect(canManageCodes('ADMIN')).toBe(true);
    expect(canManageCodes('EMPLOYEE')).toBe(false);
    expect(navFor('EMPLOYEE').map((n) => n.id)).not.toContain('codes');
    expect(navFor('HR').map((n) => n.id)).toContain('codes');
  });
});

describe('CodeManager', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => { host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host); });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  async function mount() {
    const api = createMockCodeApi();
    const toast = vi.fn();
    await act(async () => { root.render(<CodeManager api={api} toast={toast} />); });
    await flush();
    await flush();
    return { api, toast };
  }

  it('코드그룹과 첫 그룹의 코드를 보인다', async () => {
    await mount();
    const tabs = [...host.querySelectorAll('[role="tab"]')].map((t) => t.textContent);
    expect(tabs).toEqual(['직급JOB_GRADE', '직책JOB_TITLE', '직군JOB_FAMILY', '고용형태EMPLOYMENT_TYPE', '근무지WORK_LOCATION', '퇴직사유RETIREMENT_REASON']);
    expect([...host.querySelectorAll('tbody code')].map((c) => c.textContent)).toContain('STAFF');
  });

  it('코드를 추가하면 목록에 나오고 알림을 띄운다', async () => {
    const { api, toast } = await mount();
    await act(async () => { buttonByText(host, '고용형태').click(); });
    await flush();
    await act(async () => { buttonByText(host, '코드 추가').click(); });

    const inputs = host.querySelectorAll<HTMLInputElement>('.modal input.input');
    await act(async () => { type(inputs[0], 'intern'); type(inputs[1], '인턴'); });
    expect(inputs[0].value).toBe('INTERN');   // 대문자로 바꿔 넣는다
    expect(inputs[2].value).toBe('40');       // 다음 정렬 순서 제안
    await act(async () => { host.querySelector<HTMLFormElement>('.modal form')!.requestSubmit(); });
    await flush();

    expect(toast).toHaveBeenCalledWith('코드 INTERN를 추가했어요');
    expect(host.querySelector('.modal')).toBeNull();
    expect((await api.activeCodes(['EMPLOYMENT_TYPE'])).EMPLOYMENT_TYPE.map((c) => c.code)).toEqual(['REGULAR', 'FIXED_TERM', 'PART_TIME', 'INTERN']);
    expect([...host.querySelectorAll('tbody code')].map((c) => c.textContent)).toContain('INTERN');
  });

  it('잘못된 입력은 저장하지 않고 폼에 오류를 보인다', async () => {
    const { toast } = await mount();
    await act(async () => { buttonByText(host, '코드 추가').click(); });
    const inputs = host.querySelectorAll<HTMLInputElement>('.modal input.input');
    await act(async () => { type(inputs[0], 'STAFF'); type(inputs[1], '사원'); });
    await act(async () => { host.querySelector<HTMLFormElement>('.modal form')!.requestSubmit(); });
    await flush();
    expect(host.querySelector('.modal [role="alert"]')?.textContent).toBe('이미 있는 코드값이에요: JOB_GRADE/STAFF');
    expect(toast).not.toHaveBeenCalled();
  });

  it('사용 스위치를 끄면 기본 목록에서 빠지고, 사용 중지 보기로 다시 보인다', async () => {
    const { api } = await mount();
    await act(async () => { host.querySelector<HTMLButtonElement>('[aria-label="사원 사용"]')!.click(); });
    await flush();
    expect((await api.codes('JOB_GRADE', true)).find((c) => c.code === 'STAFF')?.active).toBe(false);
    expect([...host.querySelectorAll('tbody code')].map((c) => c.textContent)).not.toContain('STAFF');

    await act(async () => { host.querySelector<HTMLButtonElement>('[aria-label="사용 중지 코드 보기"]')!.click(); });
    expect(host.querySelector('tr.off code')?.textContent).toBe('STAFF');
  });
});
