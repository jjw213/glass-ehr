import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import css from '../styles/global.css?raw';
import { createMockCodeApi } from '../api/codes';
import { createMockEmployeeApi } from '../api/employees';
import { EMP_SEED } from '../data/employees';
import type { ViewerRole } from '../lib/privacy';
import { EmployeeDirectory } from './PeoplePage';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const TODAY = '2026-11-12';
/** 명백한 가짜 주민등록번호(테스트 전용) */
const FAKE_RRN = '000000-0000004';

const flush = () => act(async () => { for (let i = 0; i < 3; i++) await new Promise((r) => setTimeout(r, 0)); });

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
const names = (host: HTMLElement) => [...host.querySelectorAll('tbody .emp-table__name')].map((n) => n.textContent);
const fieldOf = (form: HTMLElement, label: string) =>
  byText(form, '.field__label', label).parentElement!.querySelector<HTMLInputElement & HTMLSelectElement>('input, select, textarea')!;
const rowValue = (host: ParentNode, label: string) =>
  [...host.querySelectorAll('.emp-rows > div')].find((r) => r.querySelector('dt')?.textContent === label)?.querySelector('dd')?.textContent;

describe('EmployeeDirectory (사원 목록·인사기록카드)', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => { host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host); });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  async function mount(role: ViewerRole = 'HR') {
    const api = createMockEmployeeApi({ employees: EMP_SEED, rrns: { 4: FAKE_RRN } });
    const toast = vi.fn();
    await act(async () => {
      root.render(<EmployeeDirectory api={api} codes={createMockCodeApi()} viewer={{ id: `${role.toLowerCase()}-user`, role }} toast={toast} today={TODAY} />);
    });
    await flush();
    return { api, toast };
  }

  async function open(name: string) {
    const row = [...host.querySelectorAll<HTMLTableRowElement>('tbody tr')].find((r) => r.querySelector('.emp-table__name')?.textContent === name)!;
    await act(async () => { row.click(); });
    await flush();
    return document.querySelector<HTMLElement>('[role="dialog"]')!;
  }

  it('기본은 재직자(퇴직예정 포함)만, 코드값 대신 이름으로 보인다', async () => {
    await mount();
    const shown = names(host);
    expect(shown).toContain('예시사원11');      // 퇴직예정
    expect(shown).not.toContain('예시사원12');  // 퇴직
    expect(shown).not.toContain('예시사원14');  // 입사예정
    expect(shown).toHaveLength(12);
    const row = [...host.querySelectorAll('tbody tr')].find((r) => r.textContent?.includes('예시사원03'))!;
    expect(row.querySelector('.emp-table__dept')?.textContent).toBe('인사팀(예시)');
    expect(row.querySelector('.emp-table__grade')?.textContent).toBe('과장');
  });

  it('이름·사번으로 검색한다', async () => {
    await mount();
    const search = host.querySelector<HTMLInputElement>('input[aria-label="이름 또는 사번 검색"]')!;
    await act(async () => { type(search, '사원07'); });
    expect(names(host)).toEqual(['예시사원07']);
    await act(async () => { type(search, 's01-2026'); });
    expect(names(host)).toEqual(['예시사원08']);
    await act(async () => { type(search, '없는사람'); });
    expect(host.textContent).toContain('검색 결과가 없어요');
  });

  it('부서(하위부서 포함)·직급·재직상태로 거른다', async () => {
    await mount();
    await act(async () => { choose(host.querySelector('select[aria-label="부서"]')!, '5'); });
    // 목록은 사번 순(백엔드 findAllByOrderByEmpNoAsc 와 같음)
    expect(names(host)).toEqual(['예시사원06', '예시사원07', '예시사원09', '예시사원10', '예시사원08']);
    await act(async () => { choose(host.querySelector('select[aria-label="직급"]')!, 'STAFF'); });
    expect(names(host)).toEqual(['예시사원10', '예시사원08']);

    await act(async () => { choose(host.querySelector('select[aria-label="부서"]')!, ''); choose(host.querySelector('select[aria-label="직급"]')!, ''); });
    await act(async () => { byText(host, '[role="radio"]', '퇴직').click(); });
    expect(names(host)).toEqual(['예시사원12']);
    await act(async () => { byText(host, '[role="radio"]', '입사예정').click(); });
    expect(names(host)).toEqual(['예시사원14']);
    await act(async () => { byText(host, '[role="radio"]', '전체').click(); });
    expect(names(host)).toHaveLength(EMP_SEED.length);
  });

  it('인사기록카드는 인적사항·발령 이력·근로계약·근태 요약 탭을 가진다', async () => {
    await mount();
    const card = await open('예시사원08');
    expect([...card.querySelectorAll('[role="tab"]')].map((t) => t.textContent)).toEqual(['인적사항', '발령 이력', '근로계약', '근태 요약']);
    expect(rowValue(card, '주소')).toBe('가상시 예시구 샘플로 8길 80');

    await act(async () => { byText(card, '[role="tab"]', '발령 이력').click(); });
    expect(card.querySelector('.emp-timeline')?.textContent).toContain('입사');
    expect(card.querySelector('.emp-timeline')?.textContent).toContain('개발1팀(예시) · 사원 · 팀원');

    await act(async () => { byText(card, '[role="tab"]', '근로계약').click(); });
    expect(rowValue(card, '고용형태')).toBe('기간제');
    expect(rowValue(card, '계약기간')).toBe('2026-03-02 ~ 2027-03-01');
    expect(rowValue(card, '1주 소정근로시간')).toBe('40시간');

    await act(async () => { byText(card, '[role="tab"]', '근태 요약').click(); });
    expect(card.querySelector('[role="tabpanel"]')?.textContent).toContain('근태 모듈');
  });

  it('퇴직자 카드에는 퇴직 발령과 사유가 보인다', async () => {
    await mount();
    await act(async () => { byText(host, '[role="radio"]', '퇴직').click(); });
    const card = await open('예시사원12');
    await act(async () => { byText(card, '[role="tab"]', '발령 이력').click(); });
    expect(card.querySelector('.emp-timeline li')?.textContent).toContain('퇴직');
    expect(card.querySelector('.emp-timeline li')?.textContent).toContain('자발적 퇴사(개인 사정)');
  });

  it('일반 직원에게는 개인정보를 가리고 근로계약·근태는 잠근다', async () => {
    await mount('EMPLOYEE');
    expect(host.textContent).not.toContain('신규 등록');
    const card = await open('예시사원04');
    expect(rowValue(card, '생년월일')).toBe('****-**-**');
    expect(rowValue(card, '주소')).toBe('가상시 ***');
    expect(rowValue(card, '주민등록번호')).toBe('비공개');
    expect(rowValue(card, '이력')).toBe('비공개');
    expect(card.textContent).not.toContain('1990-04-04');
    expect(card.textContent).not.toContain('000000');
    expect(card.textContent).not.toContain('원문 보기');
    await act(async () => { byText(card, '[role="tab"]', '근로계약').click(); });
    expect(card.querySelector('[role="tabpanel"]')?.textContent).toContain('인사·급여 담당자만 볼 수 있어요');
    await act(async () => { byText(card, '[role="tab"]', '근태 요약').click(); });
    expect(card.querySelector('[role="tabpanel"]')?.textContent).toContain('인사·급여 담당자만 볼 수 있어요');
  });

  it('인사담당은 마스킹 값을 보고, 원문 보기를 누를 때만 원문을 받는다(조회 기록)', async () => {
    const { api } = await mount('HR');
    const card = await open('예시사원04');
    expect(card.querySelector('[data-testid="rrn"]')?.textContent).toBe('000000-*******');
    expect(api.revealLog).toHaveLength(0);

    await act(async () => { byText(card, 'button', '원문 보기').click(); });
    await flush();
    expect(card.querySelector('[data-testid="rrn"]')?.textContent).toBe(FAKE_RRN);
    expect(api.revealLog).toEqual([{ empId: 4, actorId: 'hr-user', role: 'HR', purpose: 'VIEW', ok: true }]);

    await act(async () => { byText(card, 'button', '가리기').click(); });
    expect(card.querySelector('[data-testid="rrn"]')?.textContent).toBe('000000-*******');
  });

  it('원문은 30초 뒤 자동으로 다시 가린다', async () => {
    await mount('PAYROLL');
    const card = await open('예시사원04');
    vi.useFakeTimers();
    try {
      await act(async () => { byText(card, 'button', '원문 보기').click(); });
      await act(async () => { await vi.advanceTimersByTimeAsync(0); });
      expect(card.querySelector('[data-testid="rrn"]')?.textContent).toBe(FAKE_RRN);
      await act(async () => { await vi.advanceTimersByTimeAsync(30_000); });
      expect(card.querySelector('[data-testid="rrn"]')?.textContent).toBe('000000-*******');
    } finally {
      vi.useRealTimers();
    }
  });

  it('관리자는 인적사항은 보지만 주민등록번호 원문 보기는 없다', async () => {
    await mount('ADMIN');
    const card = await open('예시사원04');
    expect(rowValue(card, '생년월일')).toBe('1990-04-04');
    expect(card.querySelector('[data-testid="rrn"]')?.textContent).toBe('000000-*******');
    expect(card.textContent).not.toContain('원문 보기');
  });

  it('신규 등록: 잘못된 입력은 막고, 등록하면 사번을 받아 목록과 카드에 보인다', async () => {
    const { toast } = await mount('HR');
    await act(async () => { byText(host, 'button', '신규 등록').click(); });
    const form = document.querySelector<HTMLFormElement>('.modal form')!;

    await act(async () => { form.requestSubmit(); });
    expect(form.querySelector('[role="alert"]')?.textContent).toBe('성명을 입력해 주세요');

    await act(async () => {
      type(fieldOf(form, '성명'), '예시신규01');
      choose(fieldOf(form, '성별'), 'F');
      type(fieldOf(form, '생년월일'), '2001-01-01');
      type(fieldOf(form, '주민등록번호 (선택)'), '0000000000009');
      type(fieldOf(form, '주소'), '가상시 예시구 신규로 1');
      choose(fieldOf(form, '부서'), '6');
      choose(fieldOf(form, '고용형태'), 'REGULAR');
      choose(fieldOf(form, '직급'), 'STAFF');
      type(fieldOf(form, '종사 업무'), '예시 업무(가상)');
      type(fieldOf(form, '1주 소정근로시간 (시간)'), '40');
    });
    expect(fieldOf(form, '주민등록번호 (선택)').type).toBe('password');
    await act(async () => { form.requestSubmit(); });
    await flush();

    const year = TODAY.slice(0, 4);
    const nextSeq = EMP_SEED.filter((e) => e.empNo.startsWith(`S01-${year}-`)).length + 1;
    const empNo = `S01-${year}-${String(nextSeq).padStart(3, '0')}`;
    expect(toast).toHaveBeenCalledWith(`예시신규01 님을 사번 ${empNo}로 등록했어요`);
    expect(document.querySelector('.modal')).toBeNull();
    expect(names(host)).toContain('예시신규01');

    const card = document.querySelector<HTMLElement>('[role="dialog"]')!;
    expect(card.getAttribute('aria-label')).toBe('예시신규01 인사기록카드');
    expect(card.querySelector('[data-testid="rrn"]')?.textContent).toBe('000000-*******');
    expect(document.body.textContent).not.toContain('0000000000009');
  });
});

describe('모바일 레이아웃', () => {
  it('좁은 화면에서는 사원 목록이 카드 형태로 바뀌고 부가 열을 숨긴다', () => {
    const mobile = css.slice(css.indexOf('@media (max-width: 899px)'));
    expect(mobile).toContain('.emp-table thead { display: none; }');
    expect(mobile).toContain('.emp-table td.hide-sm { display: none; }');
    expect(mobile).toMatch(/\.emp-table tbody tr \{\s*display: flex;/);
    // 인사기록카드는 모바일에서 아래에서 올라오는 시트(.drawer__panel 공통 규칙)
    expect(mobile).toContain('.drawer__panel { height: auto; max-height: 90vh;');
  });
});
