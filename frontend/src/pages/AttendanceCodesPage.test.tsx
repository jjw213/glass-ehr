import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createMockAttendanceCodeApi } from '../api/attendanceCodes';
import { createMockCodeApi } from '../api/codes';
import { ATTENDANCE_CODE_GROUPS, ATTENDANCE_CODES } from '../data/attendanceCodes';
import { canManageAttendanceCodes, navGroupsFor } from '../nav';
import { AttendanceCodeManager } from './AttendanceCodesPage';
import { CodeManager } from './CodesPage';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const flush = () => act(async () => { await new Promise((r) => setTimeout(r, 0)); });

function type(el: HTMLInputElement | HTMLTextAreaElement, value: string) {
  const proto = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
  Object.getOwnPropertyDescriptor(proto, 'value')!.set!.call(el, value);
  el.dispatchEvent(new Event('input', { bubbles: true }));
}

const buttonByText = (host: Element, text: string) =>
  [...host.querySelectorAll('button')].find((b) => b.textContent?.includes(text))!;

describe('근태코드 관리 메뉴', () => {
  it('근태관리 아래, 인사담당자·관리자에게만', () => {
    expect(canManageAttendanceCodes('HR')).toBe(true);
    expect(canManageAttendanceCodes('ADMIN')).toBe(true);
    expect(canManageAttendanceCodes('EMPLOYEE')).toBe(false);
    expect(navGroupsFor('HR').find((g) => g.module.id === 'attendance')!.items.map((n) => n.id)).toContain('attendanceCodes');
  });
});

describe('AttendanceCodeManager', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => { host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host); });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  async function mount() {
    const api = createMockAttendanceCodeApi();
    const toast = vi.fn();
    await act(async () => { root.render(<AttendanceCodeManager api={api} usageApi={createMockCodeApi()} toast={toast} />); });
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
  const codesShown = () => [...host.querySelectorAll('tbody code')].map((c) => c.textContent);

  it('휴가 종류·근태 구분 탭', async () => {
    await mount();
    expect([...host.querySelectorAll('[role="tab"]')].map((t) => t.textContent)).toEqual(['휴가 종류', '근태 구분']);
    expect(codesShown()).toEqual(['ANNUAL', 'MATERNITY', 'SPOUSE_MATERNITY', 'MENSTRUAL', 'FAMILY_CARE', 'INFERTILITY_TREATMENT', 'CONDOLENCE', 'SICK', 'FOUNDATION_DAY']);
    await act(async () => { buttonByText(host, '근태 구분').click(); });
    await flush();
    expect(codesShown()).toEqual(['BUSINESS_TRIP', 'OUTSIDE_WORK', 'REMOTE', 'EDUCATION']);
  });

  it('리프레시 휴가(약정·유급·종일만·연간 5일) 추가 → 수정 → 삭제', async () => {
    const { api, toast } = await mount();
    await act(async () => { buttonByText(host, '코드 추가').click(); });
    const inputs = host.querySelectorAll<HTMLInputElement>('.modal input.input');
    await act(async () => {
      type(inputs[0], 'refresh');
      type(inputs[1], '리프레시 휴가');
      type(host.querySelector<HTMLInputElement>('[aria-label="연간 한도 일수"]')!, '5');
    });
    await submitModal();
    expect(toast).toHaveBeenCalledWith('근태코드 REFRESH를 추가했어요');
    const created = (await api.list('LEAVE_TYPE')).find((c) => c.code === 'REFRESH')!;
    expect(created).toMatchObject({ processType: 'COMPANY', system: false });
    expect(created.leave).toMatchObject({ unitDay: true, unitHalfDay: false, unitHour: false, paid: true, annualLimitDays: 5 });

    await act(async () => { button('리프레시 휴가 수정').click(); });
    await act(async () => { type(host.querySelector<HTMLInputElement>('[aria-label="연간 한도 일수"]')!, '3'); });
    await submitModal();
    expect(toast).toHaveBeenCalledWith('근태코드 REFRESH를 수정했어요');
    expect((await api.list('LEAVE_TYPE')).find((c) => c.code === 'REFRESH')!.leave!.annualLimitDays).toBe(3);

    await act(async () => { button('리프레시 휴가 삭제').click(); });
    await act(async () => { buttonByText(host.querySelector('.modal')!, '삭제').click(); });
    await flush();
    await flush();
    expect(toast).toHaveBeenCalledWith('근태코드 REFRESH를 삭제했어요');
    expect(codesShown()).not.toContain('REFRESH');
  });

  it('법정휴가 기본 코드는 삭제 불가, 일수는 법령 기준값만 보인다', async () => {
    await mount();
    expect(button('출산전후휴가 삭제').disabled).toBe(true);
    expect(button('출산전후휴가 삭제').parentElement!.title).toBe('기본 코드라 삭제할 수 없어요. 사용 안 함으로 바꿔 주세요');
    expect(button('경조사 삭제').disabled).toBe(false);
    await act(async () => { button('출산전후휴가 수정').click(); });
    expect(host.querySelector('[aria-label="연간 한도 일수"]')).toBeNull();
    expect(host.querySelector<HTMLSelectElement>('.modal select')!.disabled).toBe(true);
    const legal = host.querySelector('[aria-label="법정 기준값"]')!.textContent;
    expect(legal).toContain('유급 일수');
    expect(legal).toContain('기준값 미등록');
  });
});

describe('공통코드 화면의 근태코드 그룹', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => { host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host); });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  it('LEAVE_TYPE 은 편집 버튼 없이 근태관리 링크만', async () => {
    const api = createMockCodeApi({
      groups: ATTENDANCE_CODE_GROUPS,
      codes: ATTENDANCE_CODES.map(({ groupCode, code, name, description, sortOrder, active, system }) => ({ groupCode, code, name, description, sortOrder, active, system })),
    });
    await act(async () => { root.render(<CodeManager api={api} toast={vi.fn()} />); });
    await flush();
    await flush();
    const link = host.querySelector<HTMLAnchorElement>('a[href="#attendanceCodes"]')!;
    expect(link.textContent).toContain('근태코드 관리에서 편집');
    expect(buttonByText(host, '코드 추가')).toBeUndefined();
    expect(host.querySelector('[aria-label="연차 수정"]')).toBeNull();
    expect(host.querySelector('[aria-label="연차 삭제"]')).toBeNull();
    expect(host.querySelector('[aria-label="연차 사용"]')).toBeNull();
  });
});
