import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  remainingText, toFamilyLeaveBody, validateFamilyLeave,
  type Balance, type FamilyLeave, type FamilyLeaveApi, type FamilyLeaveInput, type MonthUsage,
} from '../api/familyLeaves';
import type { WorkPattern } from '../api/workPatterns';
import { canManageFamilyLeaves, navFor } from '../nav';
import { FamilyLeaveWorkspace } from './FamilyLeavesPage';

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

const base: FamilyLeaveInput = {
  kind: 'PARENTAL_LEAVE', childLabel: '첫째', childBirthDate: null, familyRelation: null,
  startDate: '2026-12-01', endDate: '2027-05-31', workPatternId: null, extension: false, reason: '양육',
};

const leave = (p: Partial<FamilyLeave>): FamilyLeave => ({
  ...base, reqId: 7, empId: 1, status: 'REQUESTED', warnings: [], requestedBy: 'hr1', requestedAt: '2026-11-18T10:00:00',
  decidedBy: null, decidedAt: null, decisionNote: null, leaveApptId: null, returnApptId: null,
  reducedAssignId: null, restoreAssignId: null, ...p,
});

// 화면 표시만 확인하는 값이다. 한도 수치는 서버가 legal_rate 에서 읽으며, 여기 숫자는 법령 정답이 아니다.
const usage = (p: Partial<MonthUsage>): MonthUsage => ({
  limitMonths: null, usedMonths: 0, usedDays: 0, remainingMonths: null, remainingLessDays: 0, uses: 0, usesMax: null, ...p,
});
const balance: Balance = {
  empId: 1, asOf: '2026-11-18',
  children: [{ childLabel: '첫째', parentalLeave: usage({ usedMonths: 2, usedDays: 3, uses: 1 }), reducedHours: usage({}) }],
  familyCareLeave: { year: 2026, limitDays: null, usedDays: 0, remainingDays: null },
  notes: ['법령 기준값(PARENTAL_LEAVE_MAX_MONTHS, 2026-11-18 기준)이 없어 확인하지 못했어요'],
};

const pattern = { patternId: 4, patternName: '단축 6시간', weeklyContractMinutes: 1800, active: true } as WorkPattern;

function fakeApi(): FamilyLeaveApi & { [k: string]: ReturnType<typeof vi.fn> } {
  return {
    create: vi.fn(async () => leave({ warnings: ['시작일 전날까지 계속근로가 3개월이라 사업주가 허용하지 않을 수 있어요'] })),
    listByEmployee: vi.fn(async () => [leave({ status: 'APPROVED' })]),
    search: vi.fn(async () => [leave({})]),
    balance: vi.fn(async () => balance),
    approve: vi.fn(async () => leave({ status: 'APPROVED' })),
    reject: vi.fn(async () => leave({ status: 'REJECTED' })),
    cancel: vi.fn(async () => leave({ status: 'CANCELLED' })),
  } as unknown as FamilyLeaveApi & { [k: string]: ReturnType<typeof vi.fn> };
}

describe('육아·가족돌봄 휴직 메뉴 권한', () => {
  it('인사담당자·관리자에게만 보인다', () => {
    expect(canManageFamilyLeaves('HR')).toBe(true);
    expect(canManageFamilyLeaves('ADMIN')).toBe(true);
    expect(canManageFamilyLeaves('EMPLOYEE')).toBe(false);
    expect(navFor('EMPLOYEE').map((n) => n.id)).not.toContain('familyLeaves');
  });
});

describe('신청 입력 검사', () => {
  it('종류별 필수 항목을 확인한다', () => {
    expect(validateFamilyLeave(1, base)).toBeNull();
    expect(validateFamilyLeave(0, base)).toMatch('사원 ID');
    expect(validateFamilyLeave(1, { ...base, endDate: '2026-11-30' })).toMatch('종료일');
    expect(validateFamilyLeave(1, { ...base, childLabel: ' ' })).toMatch('자녀 구분');
    expect(validateFamilyLeave(1, { ...base, kind: 'FAMILY_CARE_LEAVE', childLabel: null })).toMatch('가족');
    const reduced = { ...base, kind: 'CHILDCARE_REDUCED_HOURS' as const };
    expect(validateFamilyLeave(1, reduced)).toMatch('생년월일');
    expect(validateFamilyLeave(1, { ...reduced, childBirthDate: '2024-01-01' })).toMatch('근무유형');
    expect(validateFamilyLeave(1, { ...reduced, childBirthDate: '2024-01-01', workPatternId: 4 })).toBeNull();
    expect(validateFamilyLeave(1, { ...base, reason: '' })).toMatch('사유');
  });
  it('종류에 맞지 않는 항목은 보내지 않는다', () => {
    const body = toFamilyLeaveBody({
      ...base, kind: 'FAMILY_CARE_LEAVE', childBirthDate: '2024-01-01', familyRelation: ' 부모 ', workPatternId: 4, extension: true,
    });
    expect(body.childLabel).toBeNull();
    expect(body.childBirthDate).toBeNull();
    expect(body.familyRelation).toBe('부모');
    expect(body.workPatternId).toBeNull();
    expect(body.extension).toBe(false);
  });
  it('기준값이 없으면 남은 기간을 확인 불가로 보여 준다', () => {
    expect(remainingText(usage({}))).toMatch('확인 불가');
    expect(remainingText(usage({ remainingMonths: 3, remainingLessDays: 4 }))).toBe('3개월에서 4일 뺀 기간');
    expect(remainingText(usage({ remainingMonths: 3 }))).toBe('3개월');
  });
});

describe('육아·가족돌봄 휴직 화면', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => { host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host); });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  it('단축을 고르면 근무유형을 받고, 저장하면 경고를 보여 준다', async () => {
    const api = fakeApi();
    act(() => root.render(<FamilyLeaveWorkspace api={api} patterns={[pattern]} today="2026-11-18" />));
    await flush();
    const form = host.querySelector('form[aria-label="휴직 신청 입력"]') as HTMLFormElement;
    act(() => type(form.querySelector('input') as HTMLInputElement, '1'));
    act(() => choose(form.querySelector('select[aria-label="종류"]') as HTMLSelectElement, 'CHILDCARE_REDUCED_HOURS'));
    act(() => type(form.querySelector('input[aria-label="자녀 구분"]') as HTMLInputElement, '첫째'));
    act(() => type(form.querySelector('input[aria-label="자녀 생년월일"]') as HTMLInputElement, '2024-01-01'));
    act(() => type(form.querySelector('input[aria-label="종료일"]') as HTMLInputElement, '2027-05-31'));
    act(() => type(form.querySelector('input[aria-label="신청 사유"]') as HTMLInputElement, '양육'));

    act(() => form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
    await flush();
    expect(api.create).not.toHaveBeenCalled();
    expect(host.textContent).toContain('근무유형을 골라 주세요');

    act(() => choose(form.querySelector('select[aria-label="단축 근무유형"]') as HTMLSelectElement, '4'));
    act(() => form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
    await flush();
    expect(api.create).toHaveBeenCalledWith(1, expect.objectContaining({
      kind: 'CHILDCARE_REDUCED_HOURS', workPatternId: 4, childBirthDate: '2024-01-01', familyRelation: null,
    }));
    expect(host.textContent).toContain('계속근로가 3개월');
    expect(api.search).toHaveBeenCalledTimes(2);
  });

  it('승인 대기 목록에서 승인하면 목록을 다시 읽는다', async () => {
    const api = fakeApi();
    act(() => root.render(<FamilyLeaveWorkspace api={api} patterns={[]} today="2026-11-18" />));
    await flush();
    expect(api.search).toHaveBeenCalledWith('REQUESTED');
    const table = host.querySelector('table[aria-label="승인 대기 목록"]') as HTMLTableElement;
    const approve = Array.from(table.querySelectorAll('button')).find((b) => b.textContent === '승인') as HTMLButtonElement;
    act(() => approve.click());
    await flush();
    expect(api.approve).toHaveBeenCalledWith(7);
    expect(api.search).toHaveBeenCalledTimes(2);
  });

  it('반려는 사유가 없으면 보내지 않는다', async () => {
    const api = fakeApi();
    const prompt = vi.spyOn(window, 'prompt').mockReturnValueOnce('').mockReturnValueOnce(' 서류 미비 ');
    act(() => root.render(<FamilyLeaveWorkspace api={api} patterns={[]} today="2026-11-18" />));
    await flush();
    const reject = () => Array.from(host.querySelectorAll('table[aria-label="승인 대기 목록"] button')).find((b) => b.textContent === '반려') as HTMLButtonElement;
    act(() => reject().click());
    await flush();
    expect(api.reject).not.toHaveBeenCalled();
    act(() => reject().click());
    await flush();
    expect(api.reject).toHaveBeenCalledWith(7, '서류 미비');
    prompt.mockRestore();
  });

  it('남은 기간을 자녀별로 보여 주고 기준값이 없으면 안내한다', async () => {
    const api = fakeApi();
    act(() => root.render(<FamilyLeaveWorkspace api={api} patterns={[]} today="2026-11-18" />));
    await flush();
    const form = host.querySelector('form[aria-label="남은 기간 조회"]') as HTMLFormElement;
    act(() => type(form.querySelector('input') as HTMLInputElement, '1'));
    act(() => form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
    await flush();
    expect(api.balance).toHaveBeenCalledWith(1, '2026-11-18');
    const table = host.querySelector('table[aria-label="자녀별 남은 기간"]') as HTMLTableElement;
    expect(table.textContent).toContain('2개월 3일');
    expect(table.textContent).toContain('확인 불가');
    expect(host.textContent).toContain('한도 확인 불가(기준값 없음)');
    expect(host.textContent).toContain('PARENTAL_LEAVE_MAX_MONTHS');
    expect(host.querySelector('ul[aria-label="신청 이력"]')?.textContent).toContain('승인');
  });
});
