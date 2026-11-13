import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createMockAgreementApi, seoulToday, validateAgreement, type AgreementApi, type AgreementInput } from '../api/agreements';
import { canManageAgreements, navFor } from '../nav';
import { AgreementManager } from './AgreementsPage';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const flush = () => act(async () => { await new Promise((r) => setTimeout(r, 0)); });
function type(el: HTMLInputElement | HTMLSelectElement, value: string) {
  const proto = el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
  Object.getOwnPropertyDescriptor(proto, 'value')!.set!.call(el, value);
  el.dispatchEvent(new Event(el instanceof HTMLSelectElement ? 'change' : 'input', { bubbles: true }));
}
const buttonByText = (host: HTMLElement, text: string) =>
  [...host.querySelectorAll('button')].find((b) => b.textContent?.includes(text))!;
const byLabel = <T extends HTMLElement = HTMLInputElement>(host: HTMLElement, label: string) =>
  host.querySelector<T>(`[aria-label="${label}"]`)!;

const base: AgreementInput = {
  agreementType: 'SELECTIVE', title: '합의서', signedDate: '2026-12-20', validFrom: '2027-01-01', validUntil: '2027-12-31',
  scopeType: 'ALL', scopeSiteCode: null, targetDesc: '연구직', repType: 'MAJORITY_UNION', repUnionName: '가상노조',
  repEmpId: null, note: null,
};

describe('서면합의서 메뉴 권한', () => {
  it('인사담당자·관리자에게만 보인다', () => {
    expect(canManageAgreements('HR')).toBe(true);
    expect(canManageAgreements('ADMIN')).toBe(true);
    expect(canManageAgreements('PAYROLL')).toBe(false);
    expect(navFor('EMPLOYEE').map((n) => n.id)).not.toContain('agreements');
  });
});

describe('형식 검사', () => {
  it('정상 입력은 통과', () => expect(validateAgreement(base)).toBeNull());
  it('종료일이 시작일보다 빠르면 거부, 같은 날은 허용', () => {
    expect(validateAgreement({ ...base, validUntil: '2026-12-31' })).toBe('유효기간 종료일은 시작일 이후여야 해요');
    expect(validateAgreement({ ...base, signedDate: '2027-01-01', validUntil: '2027-01-01' })).toBeNull();
  });
  it('근로자대표 구분별 필수값', () => {
    expect(validateAgreement({ ...base, repUnionName: ' ' })).toBe('노동조합 이름을 입력해 주세요');
    expect(validateAgreement({ ...base, repType: 'MAJORITY_PERSON', repUnionName: null })).toBe('근로자 과반수를 대표하는 사원을 골라 주세요');
    expect(validateAgreement({ ...base, repType: 'MAJORITY_PERSON', repEmpId: 3 })).toBeNull();
  });
  it('사업장 한정이면 사업장 코드 필요', () => {
    expect(validateAgreement({ ...base, scopeType: 'SITE' })).toBe('대상 사업장을 골라 주세요');
  });
  it('서울 기준 오늘', () => {
    // UTC 2026-11-13 15:30 = 서울 2026-11-14 00:30
    expect(seoulToday(new Date('2026-11-13T15:30:00Z'))).toBe('2026-11-14');
  });
});

describe('가짜 API의 유효 합의서 판정(서버와 같은 규칙)', () => {
  it('양 끝 포함, 하루라도 넘으면 없음, 끝 없는 적용은 끝 없는 합의만', async () => {
    const api = createMockAgreementApi(() => '2026-11-14');
    await api.create(base);
    const q = { type: 'SELECTIVE' as const, siteCode: null };
    expect((await api.check({ ...q, from: '2027-01-01', until: '2027-12-31' })).ok).toBe(true);
    expect((await api.check({ ...q, from: '2027-01-01', until: '2028-01-01' })).ok).toBe(false);
    expect((await api.check({ ...q, from: '2027-01-01', until: null })).ok).toBe(false);
    const r = await api.check({ type: 'DISCRETIONARY', from: '2027-01-01', until: '2027-01-31', siteCode: null });
    expect(r.ok).toBe(false);
    expect(r.message).toContain('근로기준법 제58조 제3항');
  });
  it('사업장 한정 합의는 그 사업장만', async () => {
    const api = createMockAgreementApi(() => '2026-11-14');
    await api.create({ ...base, agreementType: 'COMPENSATORY_LEAVE', scopeType: 'SITE', scopeSiteCode: 'S01', validUntil: null });
    const q = { type: 'COMPENSATORY_LEAVE' as const, from: '2027-02-01', until: null };
    expect((await api.check({ ...q, siteCode: 'S01' })).ok).toBe(true);
    expect((await api.check({ ...q, siteCode: 'S02' })).ok).toBe(false);
    expect((await api.check({ ...q, siteCode: null })).ok).toBe(false);
  });
  it('만료 예정은 오늘부터 설정 일수 안(양 끝 포함)', async () => {
    const api = createMockAgreementApi(() => '2026-12-01', 30);
    await api.create({ ...base, validFrom: '2026-01-01', signedDate: '2026-01-01', validUntil: '2026-12-31' });
    await api.create({ ...base, title: '내일 넘어감', validFrom: '2026-01-01', signedDate: '2026-01-01', validUntil: '2027-01-01' });
    await api.create({ ...base, title: '이미 만료', validFrom: '2026-01-01', signedDate: '2026-01-01', validUntil: '2026-11-30' });
    const e = await api.expiring();
    expect(e.agreements.map((a) => a.daysUntilExpiry)).toEqual([30]);
  });
  it('유형은 수정할 수 없다', async () => {
    const api = createMockAgreementApi(() => '2026-11-14');
    const a = await api.create(base);
    await expect(api.update(a.agreementId, { ...base, agreementType: 'DISCRETIONARY' })).rejects.toThrow('합의 유형은 바꿀 수 없어요');
  });
});

describe('AgreementManager 화면', () => {
  let host: HTMLDivElement;
  let root: Root;
  let api: AgreementApi;
  const toast = vi.fn();
  const save = vi.fn();

  beforeEach(() => {
    host = document.createElement('div');
    document.body.appendChild(host);
    root = createRoot(host);
    // 예시 합의서(~2026-12-31)가 30일 안에 만료되는 날로 고정
    api = createMockAgreementApi(() => '2026-12-10', 30);
    toast.mockReset();
    save.mockReset();
  });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  const render = async () => { await act(async () => root.render(<AgreementManager api={api} toast={toast} save={save} />)); await flush(); };

  it('목록과 만료 예정 알림을 보인다', async () => {
    await render();
    expect(host.textContent).toContain('연구소 선택적 근로시간제 합의서(예시)');
    expect(host.textContent).toContain('근로기준법 제52조');
    const alert = byLabel<HTMLUListElement>(host, '만료 예정 합의서');
    expect(alert.textContent).toContain('21일 남음');
  });

  it('등록 폼: 형식 오류를 보이고, 고치면 저장한다', async () => {
    await render();
    await act(async () => buttonByText(host, '합의서 등록').click());
    await act(async () => buttonByText(document.body, '저장').click());
    expect(document.body.textContent).toContain('합의서 제목을 입력해 주세요');
    await act(async () => {
      type(byLabel(document.body, '제목'), '2027 보상휴가제 합의');
      type(byLabel<HTMLSelectElement>(document.body, '합의 유형'), 'COMPENSATORY_LEAVE');
      type(byLabel(document.body, '대상 근로자 범위'), '전 직원');
      type(byLabel(document.body, '노동조합 이름'), '가상노조');
    });
    await act(async () => buttonByText(document.body, '저장').click());
    await flush();
    expect(toast).toHaveBeenCalledWith('합의서를 등록했어요');
    expect((await api.list()).map((a) => a.title)).toContain('2027 보상휴가제 합의');
  });

  it('유효 합의서 확인 결과를 보인다', async () => {
    await render();
    await act(async () => {
      type(byLabel<HTMLSelectElement>(host, '확인할 제도'), 'SELECTIVE');
      type(byLabel(host, '적용 시작일'), '2026-02-01');
      type(byLabel(host, '적용 종료일'), '2026-12-31');
    });
    await act(async () => buttonByText(host, '확인').click());
    await flush();
    expect(host.querySelector('[data-testid="check-result"]')!.textContent).toContain('유효한 합의서가 있어요');
    await act(async () => type(byLabel(host, '적용 종료일'), '2027-01-01'));
    await act(async () => buttonByText(host, '확인').click());
    await flush();
    expect(host.querySelector('[data-testid="check-result"]')!.textContent).toContain('근로기준법 제52조');
  });

  it('첨부 올리기·내려받기·삭제', async () => {
    await render();
    await act(async () => byLabel<HTMLButtonElement>(host, '연구소 선택적 근로시간제 합의서(예시) 첨부').click());
    await flush();
    const input = byLabel(document.body, '첨부할 파일');
    const file = new File(['%PDF-1.7'], '합의서.pdf', { type: 'application/pdf' });
    Object.defineProperty(input, 'files', { value: [file], configurable: true });
    await act(async () => input.dispatchEvent(new Event('change', { bubbles: true })));
    await flush();
    expect(toast).toHaveBeenCalledWith('합의서.pdf을 첨부했어요');
    await act(async () => byLabel<HTMLButtonElement>(document.body, '합의서.pdf 내려받기').click());
    await flush();
    expect(save).toHaveBeenCalledWith(expect.objectContaining({ filename: '합의서.pdf' }));
    await act(async () => byLabel<HTMLButtonElement>(document.body, '합의서.pdf 삭제').click());
    await flush();
    expect(document.body.textContent).toContain('첨부된 파일이 없어요');
  });
});
