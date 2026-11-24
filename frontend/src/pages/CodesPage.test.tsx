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

  async function mount(opts: Parameters<typeof createMockCodeApi>[0] = {}) {
    const api = createMockCodeApi(opts);
    const toast = vi.fn();
    await act(async () => { root.render(<CodeManager api={api} toast={toast} />); });
    await flush();
    await flush();
    return { api, toast };
  }

  const submitModal = async () => {
    await act(async () => { host.querySelector<HTMLFormElement>('.modal form')!.requestSubmit(); });
    await flush();
    await flush();
  };
  const button = (label: string) => host.querySelector<HTMLButtonElement>(`[aria-label="${label}"]`)!;

  it('코드그룹과 첫 그룹의 코드를 보인다', async () => {
    await mount();
    const tabs = [...host.querySelectorAll('[role="tab"]')].map((t) => t.textContent);
    // 기존 6개 그룹은 모두 시스템 그룹(V037)이라 '시스템' 표시가 붙는다
    expect(tabs).toEqual(['직급 시스템JOB_GRADE', '직책 시스템JOB_TITLE', '직군 시스템JOB_FAMILY', '고용형태 시스템EMPLOYMENT_TYPE', '근무지 시스템WORK_LOCATION', '퇴직사유 시스템RETIREMENT_REASON']);
    expect([...host.querySelectorAll('tbody code')].map((c) => c.textContent)).toContain('STAFF');
    expect(button('직급 그룹 삭제').disabled).toBe(true);
    expect(button('직급 그룹 삭제').parentElement!.title).toBe('시스템 그룹이라 삭제할 수 없어요');
  });

  it('사용자 그룹 추가 → 코드 추가 → 코드 삭제 → 그룹 삭제', async () => {
    const { api, toast } = await mount();
    await act(async () => { buttonByText(host, '그룹 추가').click(); });
    let inputs = host.querySelectorAll<HTMLInputElement>('.modal input.input');
    await act(async () => { type(inputs[0], 'skill_level'); type(inputs[1], '기술등급'); });
    await submitModal();
    expect(toast).toHaveBeenCalledWith('코드그룹 SKILL_LEVEL를 추가했어요');
    expect(host.querySelector('[role="tab"][aria-selected="true"]')?.textContent).toBe('기술등급SKILL_LEVEL');

    await act(async () => { buttonByText(host, '코드 추가').click(); });
    inputs = host.querySelectorAll<HTMLInputElement>('.modal input.input');
    await act(async () => { type(inputs[0], 'BASIC'); type(inputs[1], '초급'); });
    await submitModal();
    expect([...host.querySelectorAll('tbody code')].map((c) => c.textContent)).toEqual(['BASIC']);
    expect(button('기술등급 그룹 삭제').disabled).toBe(false);

    await act(async () => { button('초급 삭제').click(); });
    await act(async () => { buttonByText(host.querySelector('.modal')!, '삭제').click(); });
    await flush();
    expect(toast).toHaveBeenCalledWith('코드 BASIC를 삭제했어요');
    expect(await api.codes('SKILL_LEVEL', true)).toEqual([]);

    await act(async () => { button('기술등급 그룹 삭제').click(); });
    await act(async () => { buttonByText(host.querySelector('.modal')!, '삭제').click(); });
    await flush();
    await flush();
    expect(toast).toHaveBeenCalledWith('코드그룹 SKILL_LEVEL를 삭제했어요');
    expect((await api.groups()).map((g) => g.groupCode)).not.toContain('SKILL_LEVEL');
    expect([...host.querySelectorAll('[role="tab"]')].map((t) => t.textContent)).not.toContain('기술등급SKILL_LEVEL');
  });

  it('사용 중·기본 코드는 삭제 버튼이 막히고 이유를 보인다', async () => {
    await mount();
    expect(button('사원 삭제').disabled).toBe(true);
    expect(button('사원 삭제').parentElement!.title).toBe('사용 중(3건)이라 삭제할 수 없어요. 사용 안 함으로 바꿔 주세요');
    expect(button('주임 삭제').disabled).toBe(false);
    await act(async () => { buttonByText(host, '퇴직사유').click(); });
    await flush();
    expect(button('해고 삭제').disabled).toBe(true);
    expect(button('해고 삭제').parentElement!.title).toBe('기본 코드라 삭제할 수 없어요. 사용 안 함으로 바꿔 주세요');
  });

  it('사용처: 테이블별 건수 → 행 목록, 권한이 없으면 건수만', async () => {
    await mount();
    await act(async () => { button('사원 사용처').click(); });
    await flush();
    const modal = () => host.querySelector('.modal')!;
    expect(button('사원 목록').textContent).toBe('2건');
    expect(button('발령 이력 목록').textContent).toBe('1건');
    await act(async () => { button('사원 목록').click(); });
    await flush();
    expect([...modal().querySelectorAll('tbody code')].map((c) => c.textContent)).toEqual(['S01-2026-001', 'S01-2026-002']);

    act(() => root.unmount());
    root = createRoot(host);
    await mount({ rowsVisible: false });
    await act(async () => { button('사원 사용처').click(); });
    await flush();
    expect(host.querySelector('[aria-label="사원 목록"]')).toBeNull();
    expect(modal().textContent).toContain('사원2건');
    expect(modal().textContent).toContain('건수만 보여 드려요');
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
