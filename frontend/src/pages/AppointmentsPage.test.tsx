import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import css from '../styles/global.css?raw';
import {
  createMockAppointmentApi, searchQuery, toAppointmentBody, validateAppointment,
  type AppointmentApi, type AppointmentInput,
} from '../api/appointments';
import { createMockCodeApi } from '../api/codes';
import { createMockEmployeeApi } from '../api/employees';
import { EMP_SEED } from '../data/employees';
import { canManageAppointments, navFor } from '../nav';
import { AppointmentWorkspace } from './AppointmentsPage';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const TODAY = '2026-11-18';
const flush = () => act(async () => { for (let i = 0; i < 4; i++) await new Promise((r) => setTimeout(r, 0)); });

function type(el: HTMLInputElement | HTMLTextAreaElement, value: string) {
  const proto = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
  Object.getOwnPropertyDescriptor(proto, 'value')!.set!.call(el, value);
  el.dispatchEvent(new Event('input', { bubbles: true }));
}
function choose(el: HTMLSelectElement, value: string) {
  Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value')!.set!.call(el, value);
  el.dispatchEvent(new Event('change', { bubbles: true }));
}
const byText = (host: ParentNode, sel: string, text: string) =>
  [...host.querySelectorAll<HTMLElement>(sel)].find((b) => b.textContent?.trim() === text)!;
const fieldOf = (form: HTMLElement, label: string) =>
  byText(form, '.field__label', label).parentElement!.querySelector<HTMLInputElement & HTMLSelectElement & HTMLTextAreaElement>('input, select, textarea')!;

const base: AppointmentInput = {
  apptTypeCode: 'TRANSFER', effectiveDate: '2026-12-01', afterDeptId: 3, afterJobGradeCode: 'G9', afterJobTitleCode: 'T9',
  reason: ' 조직 개편 ', leaveStartDate: '2026-12-01', leaveEndDate: '2026-12-31',
};
const active = EMP_SEED.filter((e) => !e.retireDate || e.retireDate >= TODAY);

describe('인사발령 메뉴 권한', () => {
  it('인사담당자·관리자에게만 보인다(일반 사원은 인사기록카드에서 본인 이력만)', () => {
    expect(canManageAppointments('HR')).toBe(true);
    expect(canManageAppointments('ADMIN')).toBe(true);
    expect(canManageAppointments('EMPLOYEE')).toBe(false);
    expect(navFor('EMPLOYEE').map((n) => n.id)).not.toContain('appointments');
  });
});

describe('발령 입력 검사·요청 본문', () => {
  it('사유는 필수다(근로기준법 제23조)', () => {
    expect(validateAppointment({ ...base, reason: '  ' })).toBe('발령 사유를 입력해 주세요(근로기준법 제23조)');
    expect(validateAppointment({ ...base, apptTypeCode: '' })).toBe('발령 유형을 선택해 주세요');
    expect(validateAppointment({ ...base, afterDeptId: null })).toBe('전보할 부서를 선택해 주세요');
    expect(validateAppointment({ ...base, apptTypeCode: 'PROMOTION', afterJobGradeCode: null })).toBe('승진할 직급을 선택해 주세요');
    expect(validateAppointment(base)).toBeNull();
  });

  it('휴직은 시작·종료 예정일이 필수이고 순서가 맞아야 한다', () => {
    const leave = { ...base, apptTypeCode: 'LEAVE' as const };
    expect(validateAppointment({ ...leave, leaveEndDate: null })).toBe('휴직 발령은 휴직 시작일과 종료 예정일을 입력해 주세요');
    expect(validateAppointment({ ...leave, leaveStartDate: '2026-11-30' })).toBe('휴직 시작일은 발령일 이후여야 해요');
    expect(validateAppointment({ ...leave, leaveEndDate: '2026-11-30' })).toBe('휴직 종료 예정일은 휴직 시작일 이후여야 해요');
    expect(validateAppointment(leave)).toBeNull();
  });

  it('유형이 바꾸지 않는 항목은 비워서 보낸다', () => {
    expect(toAppointmentBody(base)).toEqual({
      apptTypeCode: 'TRANSFER', effectiveDate: '2026-12-01', afterDeptId: 3, afterJobGradeCode: null, afterJobTitleCode: null,
      reason: '조직 개편', leaveStartDate: null, leaveEndDate: null,
    });
    expect(toAppointmentBody({ ...base, apptTypeCode: 'TITLE_CHANGE', afterJobTitleCode: '' }).afterJobTitleCode).toBeNull();
  });

  it('목록 조회는 입력한 조건만 쿼리에 담는다', () => {
    expect(searchQuery({ confirmed: null, type: '', from: '', to: '', deptId: null })).toBe('');
    expect(searchQuery({ confirmed: false, type: 'LEAVE', from: '2026-01-01', to: '2026-12-31', deptId: 7 }))
      .toBe('?confirmed=false&type=LEAVE&from=2026-01-01&to=2026-12-31&deptId=7');
  });
});

describe('AppointmentWorkspace (발령 목록·등록·확정·취소·일괄 발령)', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => { host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host); });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  async function mount(api: AppointmentApi) {
    await act(async () => {
      root.render(<AppointmentWorkspace api={api} employees={createMockEmployeeApi({ employees: EMP_SEED })} codes={createMockCodeApi()} today={TODAY} />);
    });
    await flush();
  }
  const form = () => host.querySelector<HTMLFormElement>('form[aria-label="발령 등록"]')!;
  const pick = async (name: string) => { await act(async () => { host.querySelector<HTMLInputElement>(`input[aria-label="${name} 선택"]`)!.click(); }); };
  const submit = async () => { await act(async () => { form().requestSubmit(); }); await flush(); };
  const rowsText = () => [...host.querySelectorAll('.appt-table tbody tr')].map((r) => r.textContent ?? '');

  it('여러 사원을 골라 한 번에 등록·확정하고, 일부 실패하면 사원별 결과와 실패 사원 선택을 남긴다', async () => {
    const [a, b] = active;
    // b 만 실패시키는 가짜 서버(예: 휴직 중 사원 승진 불가 같은 서버 규칙)
    const api = createMockAppointmentApi([], active.filter((e) => e !== b).map((e) => e.empId));
    const bulk = vi.spyOn(api, 'bulk');
    await mount(api);

    await pick(a.name);
    await pick(b.name);
    await act(async () => { choose(fieldOf(form(), '발령 유형'), 'LEAVE'); });
    await act(async () => {
      type(fieldOf(form(), '발령일'), '2026-12-01');
      type(fieldOf(form(), '휴직 시작일'), '2026-12-01');
      type(fieldOf(form(), '휴직 종료 예정일'), '2027-05-31');
      type(fieldOf(form(), '발령 사유 (근로기준법 제23조)'), '육아휴직 신청');
      byText(form(), 'label', '등록하면서 바로 확정').querySelector('input')!.click();
    });
    expect(byText(form(), 'button', '2명 일괄 발령')).toBeTruthy();
    await submit();

    expect(bulk).toHaveBeenCalledWith([a.empId, b.empId], expect.objectContaining({ apptTypeCode: 'LEAVE', reason: '육아휴직 신청' }), true);
    const result = host.querySelector<HTMLElement>('[aria-label="발령 결과"]')!;
    expect(result.textContent).toContain('등록·확정 1명');
    expect(result.textContent).toContain('실패 1명');
    const items = [...result.querySelectorAll('li')];
    expect(items[0].textContent).toContain('성공');
    expect(items[1].textContent).toContain('실패');
    expect(items[1].textContent).toContain(`사원이 없어요: ${b.empId}`);
    // 성공한 사원은 선택이 풀리고 실패한 사원만 남는다
    expect(host.querySelector<HTMLInputElement>(`input[aria-label="${a.name} 선택"]`)!.checked).toBe(false);
    expect(host.querySelector<HTMLInputElement>(`input[aria-label="${b.name} 선택"]`)!.checked).toBe(true);
    // 목록이 새로 고쳐져 확정된 발령이 보인다
    expect(rowsText()).toHaveLength(1);
    expect(rowsText()[0]).toContain('확정');
    expect(rowsText()[0]).toContain('2026-12-01 ~ 2027-05-31');
  });

  it('사원을 고르지 않거나 사유가 없으면 서버에 보내지 않는다', async () => {
    const api = createMockAppointmentApi();
    const bulk = vi.spyOn(api, 'bulk');
    await mount(api);
    await submit();
    expect(form().querySelector('[role="alert"]')?.textContent).toBe('발령할 사원을 선택해 주세요');
    await pick(active[0].name);
    await act(async () => { choose(fieldOf(form(), '발령 유형'), 'RETURN'); });
    await submit();
    expect(form().querySelector('[role="alert"]')?.textContent).toBe('발령 사유를 입력해 주세요(근로기준법 제23조)');
    expect(bulk).not.toHaveBeenCalled();
  });

  it('미확정 발령을 확정하고, 확정 발령은 사유를 적어 취소 발령을 만든다', async () => {
    const api = createMockAppointmentApi();
    await api.bulk([active[0].empId], { ...base, apptTypeCode: 'RETURN', reason: '복직 신청' }, false);
    await mount(api);
    expect(rowsText()[0]).toContain('미확정');

    await act(async () => { byText(host, '.appt-actions button', '확정').click(); });
    await flush();
    expect(rowsText()[0]).toContain('확정');

    await act(async () => { byText(host, '.appt-actions button', '취소 발령').click(); });
    const dialog = document.querySelector<HTMLFormElement>('form[aria-label="취소 발령"]')!;
    await act(async () => { dialog.requestSubmit(); });
    expect(dialog.querySelector('[role="alert"]')?.textContent).toBe('취소 사유를 입력해 주세요(근로기준법 제23조)');
    await act(async () => { type(fieldOf(dialog, '취소 사유'), '복직일 착오'); });
    await act(async () => { dialog.requestSubmit(); });
    await flush();

    const rows = rowsText();
    expect(rows).toHaveLength(2);
    expect(rows.some((t) => t.includes('복직 취소') && t.includes('미확정') && t.includes('복직일 착오'))).toBe(true);
    expect(rows.some((t) => t.includes('복직 신청') && t.includes('취소 대기'))).toBe(true);
    expect(byText(host, '.appt-actions button', '취소 발령')).toBeUndefined();
  });

  it('유형·확정 여부·기간 필터를 서버 조회 조건으로 보낸다', async () => {
    const api = createMockAppointmentApi();
    const search = vi.spyOn(api, 'search');
    await mount(api);
    await act(async () => { choose(host.querySelector<HTMLSelectElement>('select[aria-label="발령 유형"]')!, 'PROMOTION'); });
    await act(async () => { byText(host, '[role="radio"]', '미확정').click(); });
    await act(async () => { type(host.querySelector<HTMLInputElement>('input[aria-label="발령일 시작"]')!, '2026-01-01'); });
    await flush();
    expect(search).toHaveBeenLastCalledWith({ type: 'PROMOTION', confirmed: false, from: '2026-01-01', to: '', deptId: null });
    await act(async () => { type(host.querySelector<HTMLInputElement>('input[aria-label="발령일 끝"]')!, '2025-12-31'); });
    await flush();
    expect(host.querySelector('.emp-list [role="alert"]')?.textContent).toBe('조회 종료일은 시작일 이후여야 해요');
  });

  it('모바일에서는 발령 한 건이 카드 한 장으로, 처리 버튼은 아래 줄로 내려간다', () => {
    const mobile = css.slice(css.indexOf('@media (max-width: 899px)'));
    expect(mobile).toContain('.emp-table tbody tr {');
    expect(mobile).toContain('.appt-table .appt-actions { order: 4; flex: 1 0 100%;');
    expect(mobile).toContain('.appt-filters > .input { flex: 1 1 100%; }');
  });
});
