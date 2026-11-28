// 법인 관리 화면 (#130)
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createMockEnterCompanyAdminApi, httpEnterCompanyAdminApi, validateNewCompany, type NewCompany } from '../api/enterCompanies';
import { canManageEnterCompanies, navGroupsFor } from '../nav';
import { EnterCompanyManager } from './EnterCompaniesPage';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const flush = () => act(async () => { await new Promise((r) => setTimeout(r, 0)); });

function type(el: HTMLInputElement | HTMLSelectElement, value: string) {
  const proto = el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
  Object.getOwnPropertyDescriptor(proto, 'value')!.set!.call(el, value);
  el.dispatchEvent(new Event(el instanceof HTMLSelectElement ? 'change' : 'input', { bubbles: true }));
}

const systemItems = (role: 'ADMIN' | 'HR', perms: string[] = []) =>
  navGroupsFor(role, perms).find((g) => g.module.id === 'system')!.items.map((n) => n.id);

describe('법인 관리 메뉴', () => {
  it('ENTER_COMPANY_MANAGE 권한(그룹 관리자)이 있을 때만 시스템관리 아래에 보인다', () => {
    expect(canManageEnterCompanies(['ENTER_COMPANY_MANAGE'])).toBe(true);
    expect(canManageEnterCompanies(['ENTER_SWITCH', 'EMP_WRITE'])).toBe(false);
    expect(systemItems('HR', ['ENTER_COMPANY_MANAGE'])).toContain('enterCompanies');
    expect(systemItems('ADMIN')).not.toContain('enterCompanies');
    expect(systemItems('ADMIN', ['EMP_WRITE'])).not.toContain('enterCompanies');
  });
});

const VALID: NewCompany = {
  enterCd: 'C003', enterName: '새 법인(예시)', bizRegNo: '000-00-00003', sourceEnterCd: 'C001',
  adminLoginId: 'c3admin', adminPassword: 'password123',
};

describe('validateNewCompany', () => {
  it('서버와 같은 규칙으로 검사한다', () => {
    expect(validateNewCompany(VALID)).toBeNull();
    expect(validateNewCompany({ ...VALID, enterCd: 'c3' })).toContain('법인 코드');
    expect(validateNewCompany({ ...VALID, enterCd: 'C' })).toContain('법인 코드');
    expect(validateNewCompany({ ...VALID, enterName: '  ' })).toContain('법인 이름');
    expect(validateNewCompany({ ...VALID, bizRegNo: '123456789' })).toContain('사업자등록번호');
    expect(validateNewCompany({ ...VALID, sourceEnterCd: '' })).toContain('원본 법인');
    expect(validateNewCompany({ ...VALID, adminLoginId: '' })).toContain('관리자 아이디');
    expect(validateNewCompany({ ...VALID, adminPassword: '1234567' })).toContain('8~200자');
  });
});

describe('httpEnterCompanyAdminApi', () => {
  afterEach(() => vi.unstubAllGlobals());
  it('POST·PUT 경로와 본문 — 고객사는 보내지 않는다', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({}), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    await httpEnterCompanyAdminApi.create(VALID);
    await httpEnterCompanyAdminApi.setActive('C003', false);
    const calls = fetchMock.mock.calls as unknown as [string, RequestInit][];
    expect(calls[0][0]).toBe('/api/admin/enter-companies');
    expect(calls[0][1].method).toBe('POST');
    expect(JSON.parse(String(calls[0][1].body))).toEqual(VALID);
    expect(calls[1][0]).toBe('/api/admin/enter-companies/C003/active');
    expect(calls[1][1].method).toBe('PUT');
    expect(JSON.parse(String(calls[1][1].body))).toEqual({ active: false });
  });
});

describe('EnterCompanyManager', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => { host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host); });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  async function mount() {
    const api = createMockEnterCompanyAdminApi(() => 'C001');
    const toast = vi.fn();
    await act(async () => { root.render(<EnterCompanyManager api={api} toast={toast} locked={['C001']} />); });
    await flush();
    await flush();
    return { api, toast };
  }
  const q = <T extends Element>(sel: string) => host.querySelector<T>(sel)!;
  const button = (label: string) => q<HTMLButtonElement>(`[aria-label="${label}"]`);
  const submitModal = async () => {
    await act(async () => { q<HTMLFormElement>('.modal form').requestSubmit(); });
    await flush();
    await flush();
  };
  async function fill(c: NewCompany) {
    await act(async () => {
      type(q<HTMLInputElement>('[aria-label="법인 코드"]'), c.enterCd);
      type(q<HTMLInputElement>('[aria-label="법인 이름"]'), c.enterName);
      type(q<HTMLInputElement>('[aria-label="사업자등록번호"]'), c.bizRegNo);
      type(q<HTMLSelectElement>('[aria-label="원본 법인"]'), c.sourceEnterCd);
      type(q<HTMLInputElement>('[aria-label="관리자 아이디"]'), c.adminLoginId);
      type(q<HTMLInputElement>('[aria-label="관리자 초기 비밀번호"]'), c.adminPassword);
    });
  }

  it('목록 → 법인 추가 → 복사 결과와 목록 반영', async () => {
    const { api, toast } = await mount();
    expect(q('[aria-label="법인 목록"]').textContent).toContain('C002');
    await act(async () => { button('법인 추가').click(); });
    await fill(VALID);
    await submitModal();
    expect(toast).toHaveBeenCalledWith('새 법인(예시) 법인을 만들었어요');
    expect(q('[aria-label="복사 결과"]').textContent).toContain('결재 양식');
    expect(host.textContent).toContain('첫 관리자 아이디: c3admin');
    expect(host.textContent).not.toContain('password123');
    expect(q('[aria-label="법인 목록"]').textContent).toContain('C003');
    expect((await api.list()).find((c) => c.enterCd === 'C003')).toMatchObject({ bizRegNo: '0000000003', active: true });
  });

  it('잘못된 입력은 보내지 않고, 서버 409 는 그대로 알려 준다', async () => {
    const { api } = await mount();
    await act(async () => { button('법인 추가').click(); });
    await fill({ ...VALID, enterCd: 'x' });
    await submitModal();
    expect(q('[role="alert"]').textContent).toContain('법인 코드');
    await fill({ ...VALID, enterCd: 'C002' });
    await submitModal();
    expect(q('[role="alert"]').textContent).toContain('이미 쓰는 법인 코드');
    expect(await api.list()).toHaveLength(2);
  });

  it('보고 있는 법인은 사용 중지 버튼이 꺼져 있고, 다른 법인은 중지·다시 사용한다', async () => {
    const { api } = await mount();
    expect(button('C001 사용 중지').disabled).toBe(true);
    await act(async () => { button('C002 사용 중지').click(); });
    await flush();
    await flush();
    expect((await api.list()).find((c) => c.enterCd === 'C002')!.active).toBe(false);
    await act(async () => { button('C002 다시 사용').click(); });
    await flush();
    await flush();
    expect((await api.list()).find((c) => c.enterCd === 'C002')!.active).toBe(true);
  });
});
