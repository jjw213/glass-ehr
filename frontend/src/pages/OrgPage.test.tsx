import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createMockCodeApi } from '../api/codes';
import { createMockOrgApi, type OrgApi } from '../api/org';
import type { Role } from '../api/types';
import { canManageOrg } from '../nav';
import { OrgChart } from './OrgPage';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const TODAY = '2026-11-12';
const flush = () => act(async () => { await new Promise((r) => setTimeout(r, 0)); });

/** React 가 감지하도록 네이티브 setter 로 값을 넣고 이벤트를 보낸다 */
function type(el: HTMLInputElement | HTMLTextAreaElement, value: string) {
  const proto = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
  Object.getOwnPropertyDescriptor(proto, 'value')!.set!.call(el, value);
  el.dispatchEvent(new Event('input', { bubbles: true }));
}
function choose(el: HTMLSelectElement, value: string) {
  Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value')!.set!.call(el, value);
  el.dispatchEvent(new Event('change', { bubbles: true }));
}

describe('조직도 쓰기 권한', () => {
  it('인사담당자·관리자만 부서를 추가·이동할 수 있다', () => {
    expect(canManageOrg('HR')).toBe(true);
    expect(canManageOrg('ADMIN')).toBe(true);
    expect(canManageOrg('EMPLOYEE')).toBe(false);
  });
});

describe('OrgChart', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => { host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host); });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  const $ = <T extends Element = HTMLElement>(sel: string) => host.querySelector<T>(sel);
  const label = (text: string) => $<HTMLButtonElement>(`[aria-label="${text}"]`);
  const item = (name: string) => $(`[role="treeitem"][aria-label="${name}"]`);
  const count = (deptId: number) => $(`[data-testid="count-${deptId}"]`)?.textContent;
  const childNames = (name: string) => [...(item(name)?.querySelectorAll(':scope > ul > [role="treeitem"]') ?? [])].map((e) => e.getAttribute('aria-label'));
  const deptButton = (name: string) => item(name)!.querySelector<HTMLButtonElement>('.org-node__main')!;
  const buttonByText = (text: string) => [...host.querySelectorAll('button')].find((b) => b.textContent?.includes(text))!;

  async function mount({ role = 'EMPLOYEE' as Role, mobile = false, api = createMockOrgApi() as OrgApi } = {}) {
    const toast = vi.fn();
    await act(async () => { root.render(<OrgChart api={api} codes={createMockCodeApi()} role={role} toast={toast} mobile={mobile} today={TODAY} />); });
    await flush();
    await flush();
    return { api, toast };
  }

  async function setDate(value: string) {
    await act(async () => { type($<HTMLInputElement>('input[aria-label="기준일"]')!, value); });
    await flush();
  }

  it('기준일 트리와 부서별 인원 수(직속·하위 포함)를 보인다', async () => {
    await mount();
    expect([...host.querySelectorAll('.org-tree > [role="treeitem"]')].map((e) => e.getAttribute('aria-label')))
      .toEqual(['대표이사실(예시)', '경영지원본부(예시)', '개발본부(예시)', '영업팀(예시)', '생산팀(예시)']);
    expect(childNames('개발본부(예시)')).toEqual(['개발1팀(예시)', '개발2팀(예시)']);
    expect(count(2)).toBe('8명');   // 경영지원본부 1 + 인사팀 4 + 재무팀 3
    expect(item('경영지원본부(예시)')!.querySelector('.org-node__name .muted')?.textContent).toBe('직속 1명');
    expect(count(3)).toBe('4명');
    expect(count(5)).toBe('12명');
    expect($('.page-head')?.textContent).toContain(`${TODAY} 기준 · 9개 부서 · 32명`);
  });

  it('트리를 접고 펼친다', async () => {
    await mount();
    await act(async () => { label('개발본부(예시) 접기')!.click(); });
    expect(item('개발본부(예시)')!.getAttribute('aria-expanded')).toBe('false');
    expect(item('개발1팀(예시)')).toBeNull();
    expect(count(5)).toBe('12명');   // 접어도 인원 수는 하위 포함

    await act(async () => { label('개발본부(예시) 펼치기')!.click(); });
    expect(item('개발1팀(예시)')).not.toBeNull();

    await act(async () => { buttonByText('모두 접기').click(); });
    expect(host.querySelectorAll('[role="treeitem"]')).toHaveLength(5);
    await act(async () => { buttonByText('모두 펼치기').click(); });
    expect(host.querySelectorAll('[role="treeitem"]')).toHaveLength(9);
  });

  it('부서를 누르면 부서원 목록이 나온다', async () => {
    await mount();
    await act(async () => { deptButton('인사팀(예시)').click(); });
    const rows = [...host.querySelectorAll('[aria-label="인사팀(예시) 부서원"] li')].map((li) => li.textContent);
    expect(rows).toHaveLength(4);
    expect(rows[0]).toContain('예시사원03');
    expect(rows[0]).toContain('과장 · 팀장');   // 공통코드 이름으로 보인다
    expect(item('인사팀(예시)')!.getAttribute('aria-selected')).toBe('true');

    await act(async () => { deptButton('인사팀(예시)').click(); });   // 다시 누르면 선택 해제
    expect($('[aria-label="인사팀(예시) 부서원"]')).toBeNull();
  });

  it('기준일을 바꾸면 그날의 조직도와 인원을 보인다', async () => {
    const api = createMockOrgApi();
    // 2026-06-01 개발2팀을 경영지원본부 아래로 옮긴 이력
    await api.changeDept(7, { name: '데이터팀(예시)', parentDeptId: 2, headEmpId: null, sortOrder: 30, effectiveDate: '2026-06-01', reason: null });
    await mount({ api });
    expect(childNames('경영지원본부(예시)')).toEqual(['인사팀(예시)', '재무팀(예시)', '데이터팀(예시)']);
    expect($('.org-layout')?.textContent).toContain('오늘');

    await setDate('2026-05-31');
    expect(childNames('개발본부(예시)')).toEqual(['개발1팀(예시)', '개발2팀(예시)']);
    expect(childNames('경영지원본부(예시)')).toEqual(['인사팀(예시)', '재무팀(예시)']);
    expect($('.org-layout')?.textContent).toContain('과거 조직도');
    expect($('.org-note')).not.toBeNull();

    await setDate('2020-01-01');
    expect(count(3)).toBe('2명');   // 2019년 입사 2명만 재직
    expect(count(8)).toBe('0명');

    await act(async () => { buttonByText('오늘로').click(); });
    await flush();
    expect(item('데이터팀(예시)')).not.toBeNull();
  });

  it('기준일에 없는 부서를 고른 상태였으면 선택을 푼다', async () => {
    const api = createMockOrgApi();
    await api.createDept({ deptCode: 'NEW1', name: '신규팀', parentDeptId: null, headEmpId: null, sortOrder: 60, effectiveDate: '2026-10-01', reason: null });
    await mount({ api });
    await act(async () => { deptButton('신규팀').click(); });
    expect(host.textContent).toContain('신규팀 부서원');
    expect(host.textContent).toContain('이 부서에 바로 소속된 사원이 없어요');
    await setDate('2026-09-30');
    expect(item('신규팀')).toBeNull();
    expect(host.textContent).toContain('부서를 눌러 보세요');
  });

  it('일반 사원은 조회만 한다(추가·이동 버튼 없음)', async () => {
    await mount({ role: 'EMPLOYEE' });
    expect(host.textContent).not.toContain('부서 추가');
    expect(label('인사팀(예시) 이동')).toBeNull();
    expect(label('인사팀(예시) 하위 부서 추가')).toBeNull();
  });

  it('인사담당자는 부서를 추가한다', async () => {
    const { api, toast } = await mount({ role: 'HR' });
    await act(async () => { label('개발본부(예시) 하위 부서 추가')!.click(); });
    const inputs = host.querySelectorAll<HTMLInputElement>('.modal input.input');
    expect($<HTMLSelectElement>('.modal select')!.value).toBe('5');   // 누른 부서가 상위부서
    await act(async () => { type(inputs[0], 'dev3'); type(inputs[1], '개발3팀'); });
    expect(inputs[0].value).toBe('DEV3');
    expect(inputs[2].value).toBe(TODAY);   // 신설일 = 기준일
    expect(inputs[3].value).toBe('30');    // 형제 부서 다음 정렬 순서
    await act(async () => { $<HTMLFormElement>('.modal form')!.requestSubmit(); });
    await flush();
    await flush();

    expect(toast).toHaveBeenCalledWith(`개발3팀 부서를 ${TODAY}부터 추가했어요`);
    expect($('.modal')).toBeNull();
    expect(childNames('개발본부(예시)')).toEqual(['개발1팀(예시)', '개발2팀(예시)', '개발3팀']);
    expect(count(10)).toBe('0명');
    expect((await api.tree(TODAY)).roots.find((r) => r.deptId === 5)!.children).toHaveLength(3);
  });

  it('잘못된 입력·서버 거부는 폼에 오류로 보인다', async () => {
    const { toast } = await mount({ role: 'ADMIN' });
    await act(async () => { buttonByText('부서 추가').click(); });
    const inputs = host.querySelectorAll<HTMLInputElement>('.modal input.input');
    await act(async () => { type(inputs[0], 'DEMO_HR'); type(inputs[1], '중복팀'); });
    await act(async () => { $<HTMLFormElement>('.modal form')!.requestSubmit(); });
    await flush();
    expect($('.modal [role="alert"]')?.textContent).toBe('이미 있는 부서코드예요: DEMO_HR');

    await act(async () => { type(inputs[1], ''); });
    await act(async () => { $<HTMLFormElement>('.modal form')!.requestSubmit(); });
    expect($('.modal [role="alert"]')?.textContent).toBe('부서명을 입력해 주세요');
    expect(toast).not.toHaveBeenCalled();
  });

  it('인사담당자는 부서를 다른 상위부서로 옮긴다(자기 하위는 후보에서 빠짐)', async () => {
    const { toast } = await mount({ role: 'HR' });
    await act(async () => { label('개발본부(예시) 이동')!.click(); });
    const options = [...host.querySelectorAll<HTMLOptionElement>('.modal select option')].map((o) => o.textContent);
    expect(options).not.toContain('개발본부(예시)');
    expect(options).not.toContain('개발본부(예시) › 개발1팀(예시)');
    expect(options).toContain('경영지원본부(예시) › 인사팀(예시)');
    await act(async () => { buttonByText('취소').click(); });

    await act(async () => { label('개발2팀(예시) 이동')!.click(); });
    const select = $<HTMLSelectElement>('.modal select')!;
    await act(async () => { $<HTMLFormElement>('.modal form')!.requestSubmit(); });
    expect($('.modal [role="alert"]')?.textContent).toMatch(/같은 상위부서/);

    await act(async () => { choose(select, '2'); });
    const dateInput = host.querySelector<HTMLInputElement>('.modal input[type="date"]')!;
    expect(dateInput.value).toBe(TODAY);
    await act(async () => { type(dateInput, '2026-12-01'); });
    await act(async () => { $<HTMLFormElement>('.modal form')!.requestSubmit(); });
    await flush();
    await flush();

    expect(toast).toHaveBeenCalledWith('개발2팀(예시) 부서를 2026-12-01부터 옮겼어요');
    expect($<HTMLInputElement>('input[aria-label="기준일"]')!.value).toBe('2026-12-01');   // 적용일 조직도로 이동
    // 정렬 순서가 같으면(재무팀 20·개발2팀 20) 부서코드 순(백엔드 NODE_ORDER)
    expect(childNames('경영지원본부(예시)')).toEqual(['인사팀(예시)', '개발2팀(예시)', '재무팀(예시)']);
    expect(count(2)).toBe('13명');
    expect($('[aria-label="개발2팀(예시) 부서원"]')).not.toBeNull();   // 옮긴 부서를 고른 상태

    await setDate(TODAY);
    expect(childNames('개발본부(예시)')).toEqual(['개발1팀(예시)', '개발2팀(예시)']);   // 적용일 전에는 그대로
  });

  it('과거 조직도에서 이동해도 그 뒤의 부서명·정렬 변경을 되돌리지 않는다', async () => {
    const api = createMockOrgApi();
    // 2026-06-01 개발2팀 → 데이터팀으로 이름·정렬 변경 (상위부서는 그대로 개발본부)
    await api.changeDept(7, { name: '데이터팀(예시)', parentDeptId: 5, headEmpId: null, sortOrder: 30, effectiveDate: '2026-06-01', reason: null });
    const spy = vi.spyOn(api, 'changeDept');
    const { toast } = await mount({ role: 'HR', api });
    await setDate('2026-05-31');   // 이름 바뀌기 전의 과거 조직도에서
    await act(async () => { label('개발2팀(예시) 이동')!.click(); });
    await act(async () => { choose($<HTMLSelectElement>('.modal select')!, '2'); });
    await act(async () => { type(host.querySelector<HTMLInputElement>('.modal input[type="date"]')!, '2026-12-01'); });
    await act(async () => { $<HTMLFormElement>('.modal form')!.requestSubmit(); });
    await flush();
    await flush();

    expect(spy).toHaveBeenCalledWith(7, expect.objectContaining({ name: '데이터팀(예시)', sortOrder: 30, parentDeptId: 2, effectiveDate: '2026-12-01' }));
    expect(toast).toHaveBeenCalledWith('데이터팀(예시) 부서를 2026-12-01부터 옮겼어요');
    expect(childNames('경영지원본부(예시)')).toEqual(['인사팀(예시)', '재무팀(예시)', '데이터팀(예시)']);
  });

  it('사원 목록을 못 받으면 0명으로 보이지 않고 다시 시도할 수 있다', async () => {
    const api = createMockOrgApi();
    const real = api.employees.bind(api);
    const spy = vi.spyOn(api, 'employees').mockRejectedValueOnce(new Error('사원 목록 오류'));
    const { toast } = await mount({ api });
    expect(toast).toHaveBeenCalledWith('사원 목록 오류');
    expect(count(3)).toBe('?');
    expect($('.page-head')?.textContent).toContain('인원 확인 불가');
    await act(async () => { deptButton('인사팀(예시)').click(); });
    expect(host.textContent).not.toContain('이 부서에 바로 소속된 사원이 없어요');
    expect($('[role="alert"]')?.textContent).toContain('불러오지 못했어요');

    spy.mockImplementation(real);
    await act(async () => { buttonByText('사원 목록 다시 불러오기').click(); });
    await flush();
    expect(count(3)).toBe('4명');
    expect(host.querySelectorAll('[aria-label="인사팀(예시) 부서원"] li')).toHaveLength(4);
    expect($('[role="alert"]')).toBeNull();
  });

  it('사원 목록 실패 상태에서 기준일을 바꾸면 다시 불러온다', async () => {
    const api = createMockOrgApi();
    const spy = vi.spyOn(api, 'employees').mockRejectedValueOnce(new Error('사원 목록 오류'));
    await mount({ api });
    expect(count(3)).toBe('?');
    await setDate('2020-01-01');
    await flush();
    expect(spy).toHaveBeenCalledTimes(2);
    expect(count(3)).toBe('2명');
  });

  it('모바일에서는 한 열 세로 트리로 보인다', async () => {
    await mount({ mobile: true });
    const layout = $('.org-layout')!;
    expect(layout.dataset.layout).toBe('mobile');
    expect(layout.classList.contains('org-layout--mobile')).toBe(true);
    expect($('[role="tree"]')).not.toBeNull();
    await act(async () => { deptButton('영업팀(예시)').click(); });
    await flush();
    expect(host.querySelectorAll('[aria-label="영업팀(예시) 부서원"] li')).toHaveLength(5);
  });
});
