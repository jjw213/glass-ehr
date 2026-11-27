import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  toRetirementBody, validateRetirement,
  type Certificate, type Retirement, type RetirementApi, type RetirementInput,
} from '../api/retirements';
import { canManageRetirements, navFor } from '../nav';
import { CertificatePrint, RetirementWorkspace } from './RetirementsPage';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const flush = () => act(async () => { await new Promise((r) => setTimeout(r, 0)); });

function type(el: HTMLInputElement, value: string) {
  Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(el, value);
  el.dispatchEvent(new Event('input', { bubbles: true }));
}
function choose(el: HTMLSelectElement, value: string) {
  Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value')!.set!.call(el, value);
  el.dispatchEvent(new Event('change', { bubbles: true }));
}

const base: RetirementInput = {
  empId: 1, retireDate: '2026-11-30', reasonCode: 'VOLUNTARY', reasonDetail: null,
  dismissalNoticeDate: null, writtenNoticeDate: null, noticeExempt: null,
};

const retirement = (p: Partial<Retirement>): Retirement => ({
  caseId: 3, empId: 1, empNo: 'S01-2024-001', empName: '예시사원', reasonCode: 'DISMISSAL', retireDate: '2026-11-30',
  recordRetentionUntil: '2029-11-30',
  notice: { check: 'SHORT', dismissalNoticeDate: '2026-11-27', writtenNoticeDate: '2026-11-27', noticeExempt: null, noticeDays: 3, shortfallDays: 7 },
  checklist: [
    { itemCode: 'DISMISSAL_NOTICE', label: '해고 예고(예고일 기록)', legalBasis: '근로기준법 제26조', done: true, doneAt: '2026-11-16T10:00:00', doneBy: 'hr1', note: null },
    { itemCode: 'HANDOVER', label: '업무 인수인계 완료', legalBasis: null, done: false, doneAt: null, doneBy: null, note: null },
  ],
  warnings: ['해고 예고 기간이 7일 모자라요'], createdBy: 'hr1', createdAt: '2026-11-16T10:00:00', ...p,
});

const cert = (p: Partial<Certificate>): Certificate => ({
  certId: 9, empId: 1, empNo: 'S01-2024-001', requestedOn: '2026-11-30', items: ['USE_PERIOD'], purpose: null,
  empName: '예시사원', useFrom: '2024-01-02', useTo: '2026-11-30', jobType: null, positionText: null,
  wageAmount: null, wageUnitCode: null, retireReason: null, issuedBy: 'hr1', issuedAt: '2026-11-30T09:00:00', ...p,
});

function fakeApi(): RetirementApi & { [k: string]: ReturnType<typeof vi.fn> } {
  return {
    template: vi.fn(async (code: string) => (code === 'DISMISSAL'
      ? [{ itemCode: 'DISMISSAL_NOTICE', label: '해고 예고(예고일 기록)', legalBasis: '근로기준법 제26조' }]
      : [{ itemCode: 'RESIGNATION_LETTER', label: '사직서 접수 확인', legalBasis: null }])),
    register: vi.fn(async () => retirement({})),
    get: vi.fn(async () => retirement({ warnings: [] })),
    mark: vi.fn(async () => retirement({ warnings: [] })),
    retentionExpired: vi.fn(async () => [{ empId: 5, empNo: 'S01-2020-005', empName: '예시퇴직', retireDate: '2023-01-01', retireReasonCode: 'VOLUNTARY', recordRetentionUntil: '2026-01-01', daysOverdue: 319 }]),
    issueCertificate: vi.fn(async () => cert({})),
  } as unknown as RetirementApi & { [k: string]: ReturnType<typeof vi.fn> };
}

describe('퇴직 처리 메뉴 권한', () => {
  it('인사담당자·관리자에게만 보인다', () => {
    expect(canManageRetirements('HR')).toBe(true);
    expect(canManageRetirements('ADMIN')).toBe(true);
    expect(canManageRetirements('EMPLOYEE')).toBe(false);
    expect(navFor('EMPLOYEE').map((n) => n.id)).not.toContain('retirements');
  });
});

describe('퇴직 처리 입력 검사', () => {
  it('해고는 예고일·서면통지일이 필수이고 해고일보다 늦을 수 없다', () => {
    const d = { ...base, reasonCode: 'DISMISSAL' };
    expect(validateRetirement(d, true)).toMatch('제26조');
    expect(validateRetirement({ ...d, dismissalNoticeDate: '2026-11-01' }, true)).toMatch('제27조');
    expect(validateRetirement({ ...d, dismissalNoticeDate: '2026-12-01', writtenNoticeDate: '2026-11-01' }, true)).toMatch('예고일');
    expect(validateRetirement({ ...d, dismissalNoticeDate: '2026-11-01', writtenNoticeDate: '2026-12-01' }, true)).toMatch('서면통지일');
    expect(validateRetirement({ ...d, dismissalNoticeDate: '2026-11-01', writtenNoticeDate: '2026-11-01' }, true)).toBeNull();
  });
  it('해고가 아니면 예고 항목을 보내지 않는다', () => {
    expect(validateRetirement(base, false)).toBeNull();
    expect(validateRetirement({ ...base, empId: Number.NaN }, false)).toMatch('사원 ID');
    const body = toRetirementBody({ ...base, reasonDetail: '  ', dismissalNoticeDate: '2026-11-01', noticeExempt: 'UNAVOIDABLE' }, false);
    expect(body.dismissalNoticeDate).toBeNull();
    expect(body.noticeExempt).toBeNull();
    expect(body.reasonDetail).toBeNull();
  });
});

describe('퇴직 처리 화면', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => { host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host); });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  const reasons = [
    { code: 'VOLUNTARY', name: '자발적 퇴사', sysType: 'VOLUNTARY' }, { code: 'DISMISSAL', name: '해고', sysType: 'DISMISSAL' },
    { code: 'MGMT_DISMISSAL', name: '경영상 해고', sysType: 'DISMISSAL' },
  ];

  it('관리자가 만든 해고 처리방식 코드(경영상 해고)도 예고 입력칸이 나온다(#141)', async () => {
    const api = fakeApi();
    act(() => root.render(<RetirementWorkspace api={api} reasons={reasons} today="2026-11-16" />));
    await flush();
    const form = host.querySelector('form[aria-label="퇴직 처리 등록"]') as HTMLFormElement;
    act(() => choose(form.querySelector('select') as HTMLSelectElement, 'MGMT_DISMISSAL'));
    await flush();
    expect(form.querySelector('fieldset[aria-label="해고 예고·서면통지"]')).not.toBeNull();
    act(() => choose(form.querySelector('select') as HTMLSelectElement, 'VOLUNTARY'));
    await flush();
    expect(form.querySelector('fieldset')).toBeNull();
  });

  it('해고를 고르면 예고 입력칸이 나오고, 빠지면 서버에 보내지 않는다', async () => {
    const api = fakeApi();
    act(() => root.render(<RetirementWorkspace api={api} reasons={reasons} today="2026-11-16" />));
    await flush();
    const form = host.querySelector('form[aria-label="퇴직 처리 등록"]') as HTMLFormElement;
    expect(form.querySelector('fieldset')).toBeNull();
    act(() => type(form.querySelector('input') as HTMLInputElement, '1'));
    act(() => choose(form.querySelector('select') as HTMLSelectElement, 'DISMISSAL'));
    await flush();
    expect(form.querySelector('fieldset[aria-label="해고 예고·서면통지"]')).not.toBeNull();
    expect(host.textContent).toContain('이 유형의 체크리스트');
    expect(api.template).toHaveBeenLastCalledWith('DISMISSAL');

    act(() => form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
    await flush();
    expect(host.querySelector('[role="alert"]')?.textContent).toMatch('제26조');
    expect(api.register).not.toHaveBeenCalled();
  });

  it('등록하면 예고 미달 경고와 체크리스트가 보이고 항목을 완료 표시한다', async () => {
    const api = fakeApi();
    act(() => root.render(<RetirementWorkspace api={api} reasons={reasons} today="2026-11-16" />));
    await flush();
    const form = host.querySelector('form[aria-label="퇴직 처리 등록"]') as HTMLFormElement;
    act(() => type(form.querySelector('input') as HTMLInputElement, '1'));
    act(() => choose(form.querySelector('select') as HTMLSelectElement, 'DISMISSAL'));
    await flush();
    const dates = form.querySelectorAll('fieldset input[type="date"]');
    act(() => type(dates[0] as HTMLInputElement, '2026-11-13'));
    act(() => type(dates[1] as HTMLInputElement, '2026-11-13'));
    act(() => form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
    await flush();
    expect(api.register).toHaveBeenCalledWith(expect.objectContaining({
      empId: 1, reasonCode: 'DISMISSAL', dismissalNoticeDate: '2026-11-13', writtenNoticeDate: '2026-11-13', noticeExempt: null,
    }));
    expect(host.textContent).toContain('예고 기간 미달');
    expect(host.textContent).toContain('7일 부족');
    expect(host.querySelector('[role="status"]')?.textContent).toContain('7일 모자라요');

    const boxes = host.querySelectorAll('.checklist input[type="checkbox"]');
    expect((boxes[0] as HTMLInputElement).checked).toBe(true);
    act(() => (boxes[1] as HTMLInputElement).click());
    await flush();
    expect(api.mark).toHaveBeenCalledWith(1, 'HANDOVER', true, null);
  });

  it('보존기한이 지난 퇴직자 목록을 기준일로 불러온다', async () => {
    const api = fakeApi();
    act(() => root.render(<RetirementWorkspace api={api} reasons={reasons} today="2026-11-16" />));
    await flush();
    expect(api.retentionExpired).toHaveBeenCalledWith('2026-11-16');
    const table = host.querySelector('table[aria-label="보존기한 경과 목록"]');
    expect(table?.textContent).toContain('예시퇴직');
    expect(table?.textContent).toContain('319일');
    expect(table?.textContent).toContain('자발적 퇴사');
  });

  it('사용증명서는 항목을 하나 이상 골라야 발급한다', async () => {
    const api = fakeApi();
    act(() => root.render(<RetirementWorkspace api={api} reasons={reasons} today="2026-11-16" />));
    await flush();
    const form = host.querySelector('form[aria-label="사용증명서 발급"]') as HTMLFormElement;
    act(() => type(form.querySelector('input') as HTMLInputElement, '1'));
    act(() => form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
    await flush();
    expect(api.issueCertificate).not.toHaveBeenCalled();
    act(() => (form.querySelector('fieldset input[type="checkbox"]') as HTMLInputElement).click());
    act(() => form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
    await flush();
    expect(api.issueCertificate).toHaveBeenCalledWith(1, { requestedOn: '2026-11-16', items: ['USE_PERIOD'], purpose: null });
    expect(host.querySelector('article[aria-label="사용증명서"]')).not.toBeNull();
  });

  it('인쇄 화면은 요구한 항목만 그린다', () => {
    act(() => root.render(<CertificatePrint cert={cert({ items: ['USE_PERIOD'], wageAmount: 3000000, jobType: '숨김' })} />));
    const text = host.textContent ?? '';
    expect(text).toContain('사용 기간');
    expect(text).toContain('2024-01-02 ~ 2026-11-30');
    expect(text).not.toContain('임금');
    expect(text).not.toContain('업무 종류');
    expect(text).not.toContain('숨김');

    act(() => root.render(<CertificatePrint cert={cert({ items: ['WAGE', 'POSITION'], useTo: null, wageAmount: 3000000, wageUnitCode: 'MONTHLY', positionText: '사원' })} />));
    expect(host.textContent).toContain('월급 3,000,000원');
    expect(host.textContent).toContain('사원');
    expect(host.textContent).not.toContain('사용 기간');
  });
});
