import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createMockApis, hm, minutesBetween, plannedMinutes, seoulNowLocal, toOvertimeBody, validateOvertime,
  type OvertimeInput,
} from '../api/overtime';
import { navFor } from '../nav';
import { ApprovalInbox } from './ApprovalInboxPage';
import { OvertimeWorkspace } from './OvertimePage';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const NOW = '2099-03-10T09:00';
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
const fieldOf = (form: ParentNode, label: string) =>
  byText(form, '.field__label', label).parentElement!.querySelector<HTMLInputElement & HTMLSelectElement & HTMLTextAreaElement>('input, select, textarea')!;

const base: OvertimeInput = { workKind: 'OVERTIME', startAt: '2099-03-10T19:00', endAt: '2099-03-10T22:30', breakMinutes: 30, reason: ' 마감 ' };

describe('연장·휴일근로 신청 입력 검사', () => {
  it('예정 근로분은 종료 − 시작 − 휴게이고 자정을 넘겨도 맞다', () => {
    expect(plannedMinutes(base)).toBe(180);
    expect(minutesBetween('2099-03-10T23:00', '2099-03-11T01:00')).toBe(120);
    expect(hm(180)).toBe('3시간');
    expect(hm(61)).toBe('1시간 1분');
    expect(hm(5)).toBe('5분');
  });

  it('사전 신청만 받고, 시간 순서·휴게·사유를 검사한다', () => {
    expect(validateOvertime({ ...base, startAt: '2099-03-10T09:00' }, NOW)).toBe('사전 신청만 할 수 있어요. 시작 시각을 지금 이후로 정해 주세요');
    expect(validateOvertime({ ...base, endAt: '2099-03-10T19:00' }, NOW)).toBe('종료 시각은 시작 시각 뒤여야 해요');
    expect(validateOvertime({ ...base, breakMinutes: -1 }, NOW)).toBe('휴게 시간은 0분 이상 정수로 입력해 주세요');
    expect(validateOvertime({ ...base, breakMinutes: 210 }, NOW)).toBe('휴게 시간을 뺀 근로시간이 없어요');
    expect(validateOvertime({ ...base, reason: '  ' }, NOW)).toBe('사유를 입력해 주세요');
    expect(validateOvertime({ ...base, reason: 'x'.repeat(501) }, NOW)).toBe('사유는 500자 이내로 써 주세요');
    expect(validateOvertime(base, NOW)).toBeNull();
    expect(toOvertimeBody(base).reason).toBe('마감');
  });

  it('지금 시각은 Asia/Seoul 기준이다', () => {
    expect(seoulNowLocal(new Date('2099-03-10T15:30:00Z'))).toBe('2099-03-11T00:30');
  });

  it('신청·결재함 메뉴는 모든 사원에게 보인다', () => {
    for (const role of ['EMPLOYEE', 'HR', 'ADMIN'] as const) {
      expect(navFor(role).map((n) => n.id)).toEqual(expect.arrayContaining(['overtime', 'approvalInbox']));
    }
  });
});

describe('신청 화면과 결재함', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => { host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host); });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  async function fillAndSubmit() {
    const form = host.querySelector<HTMLFormElement>('form')!;
    await act(async () => {
      choose(fieldOf(form, '근로 구분'), 'HOLIDAY');
      type(fieldOf(form, '시작'), '2099-03-15T09:00');
      type(fieldOf(form, '종료'), '2099-03-15T13:00');
      type(fieldOf(form, '사유'), '설비 점검');
    });
    await act(async () => { form.requestSubmit(); });
    await flush();
  }

  it('신청하면 내 신청 목록에 결재 중으로 보인다', async () => {
    const { overtime } = createMockApis();
    const create = vi.spyOn(overtime, 'create');
    await act(async () => { root.render(<OvertimeWorkspace api={overtime} now={() => NOW} />); });
    await flush();
    expect(host.textContent).toContain('신청한 내역이 없어요');

    await fillAndSubmit();
    expect(create).toHaveBeenCalledWith(expect.objectContaining({ workKind: 'HOLIDAY', startAt: '2099-03-15T09:00', breakMinutes: 0 }));
    expect(host.querySelector('[role="status"]')?.textContent).toContain('신청했어요');
    const row = host.querySelector('tbody tr')!;
    expect(row.textContent).toContain('휴일근로');
    expect(row.textContent).toContain('4시간');
    expect(row.textContent).toContain('결재 중');
  });

  it('입력이 틀리면 서버에 보내지 않고, 서버 오류는 그대로 보인다', async () => {
    const { overtime } = createMockApis();
    const create = vi.spyOn(overtime, 'create');
    await act(async () => { root.render(<OvertimeWorkspace api={overtime} now={() => NOW} />); });
    await flush();
    await act(async () => { host.querySelector<HTMLFormElement>('form')!.requestSubmit(); });
    expect(create).not.toHaveBeenCalled();
    expect(host.querySelector('[role="alert"]')?.textContent).toBe('시작·종료 시각을 입력해 주세요');

    create.mockRejectedValueOnce(new Error('같은 시간대에 결재 중이거나 승인된 신청이 있어요'));
    await fillAndSubmit();
    expect(host.querySelector('[role="alert"]')?.textContent).toBe('같은 시간대에 결재 중이거나 승인된 신청이 있어요');
  });

  async function inboxWithOneDoc() {
    const apis = createMockApis();
    await apis.overtime.create({ ...base, startAt: '2099-03-10T19:00' });
    await act(async () => { root.render(<ApprovalInbox api={apis.approvals} />); });
    await flush();
    await act(async () => { host.querySelector<HTMLElement>('tbody tr')!.click(); });
    await flush();
    return apis;
  }
  const modal = () => document.body.querySelector<HTMLElement>('.approval-doc')!;

  it('결재할 문서를 열어 승인하면 단계와 이력이 바뀌고 신청이 승인된다', async () => {
    const apis = await inboxWithOneDoc();
    expect(modal().textContent).toContain('1단계');
    await act(async () => { byText(modal(), 'button', '승인').click(); });
    await flush();
    expect(modal().textContent).toContain('승인');
    expect(byText(modal(), 'button', '승인')).toBeUndefined();
    expect((await apis.overtime.mine())[0].status).toBe('APPROVED');
    expect(await apis.approvals.list('inbox')).toHaveLength(0);
  });

  it('반려는 사유가 있어야 하고, 한도 초과 같은 승인 오류는 문서 창에 보인다', async () => {
    const apis = await inboxWithOneDoc();
    const reject = vi.spyOn(apis.approvals, 'reject');
    await act(async () => { byText(modal(), 'button', '반려').click(); });
    expect(reject).not.toHaveBeenCalled();
    expect(modal().querySelector('[role="alert"]')?.textContent).toBe('반려 사유를 입력해 주세요');

    vi.spyOn(apis.approvals, 'approve').mockRejectedValueOnce(
      new Error('승인하면 2099-03-09 주의 연장근로가 12시간 1분로 1주 한도 12시간를 넘어요(근로기준법 제53조 제1항, 휴일근로 포함)'));
    await act(async () => { byText(modal(), 'button', '승인').click(); });
    await flush();
    expect(modal().querySelector('[role="alert"]')?.textContent).toContain('제53조 제1항');
    expect((await apis.overtime.mine())[0].status).toBe('IN_PROGRESS');

    await act(async () => { type(fieldOf(modal(), '의견 (반려 시 필수)'), '일정 조정'); });
    await act(async () => { byText(modal(), 'button', '반려').click(); });
    await flush();
    expect(reject).toHaveBeenCalledWith(expect.any(Number), '일정 조정');
    expect((await apis.overtime.mine())[0].status).toBe('REJECTED');
  });

  it('내가 올린 문서는 첫 승인 전에 회수할 수 있다', async () => {
    const apis = await inboxWithOneDoc();
    await act(async () => { byText(modal(), 'button', '회수').click(); });
    await flush();
    expect((await apis.overtime.mine())[0].status).toBe('WITHDRAWN');
    expect((await apis.approvals.list('mine'))[0].status).toBe('WITHDRAWN');
  });
});
