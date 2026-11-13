import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { filenameOf, ImportRejected, type EmployeeImportApi, type ImportPreview } from '../api/employeeImport';
import { createMockCodeApi } from '../api/codes';
import { createMockEmployeeApi } from '../api/employees';
import { EMP_SEED } from '../data/employees';
import type { ViewerRole } from '../lib/privacy';
import { EmployeeDirectory } from '../pages/PeoplePage';
import { EmployeeExportButton, EmployeeImportDialog } from './EmployeeImport';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const flush = () => act(async () => { for (let i = 0; i < 3; i++) await new Promise((r) => setTimeout(r, 0)); });
const byText = (host: ParentNode, sel: string, text: string) =>
  [...host.querySelectorAll<HTMLElement>(sel)].find((b) => b.textContent?.trim() === text);

const OK: ImportPreview = {
  totalRows: 2, errorRows: 0, confirmable: true, fileErrors: [],
  rows: [
    { rowNo: 2, name: '예시사원일', siteCode: 'S01', hireDate: '2026-03-02', errors: [] },
    { rowNo: 3, name: '예시사원이', siteCode: 'S01', hireDate: '2026-03-02', errors: [] },
  ],
};
const BAD: ImportPreview = {
  totalRows: 2, errorRows: 1, confirmable: false, fileErrors: [],
  rows: [
    { rowNo: 2, name: '예시사원일', siteCode: 'S01', hireDate: '2026-03-02', errors: [] },
    { rowNo: 3, name: '', siteCode: 'S99', hireDate: null, errors: ['사업장이 없어요: S99', '성명을 입력해 주세요'] },
  ],
};

function fakeApi(over: Partial<EmployeeImportApi> = {}): EmployeeImportApi {
  return {
    template: vi.fn(async () => ({ blob: new Blob(['x']), filename: 'employee-import-template.csv' })),
    exportList: vi.fn(async () => ({ blob: new Blob(['x']), filename: 'employees.csv' })),
    preview: vi.fn(async () => OK),
    confirm: vi.fn(async () => ({ created: 2, empNos: ['S01-2026-101', 'S01-2026-102'] })),
    ...over,
  };
}

describe('EmployeeImportDialog (엑셀 일괄 등록)', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => { host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host); });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  async function mount(api: EmployeeImportApi) {
    const onDone = vi.fn();
    const save = vi.fn();
    await act(async () => {
      root.render(<EmployeeImportDialog api={api} open onClose={() => {}} onDone={onDone} save={save} />);
    });
    return { onDone, save };
  }

  async function pick() {
    const input = host.querySelector<HTMLInputElement>('input[type="file"]')!;
    const file = new File(['사업장코드*\r\n'], 'employees.csv', { type: 'text/csv' });
    Object.defineProperty(input, 'files', { value: [file], configurable: true });
    await act(async () => { input.dispatchEvent(new Event('change', { bubbles: true })); });
    await flush();
    return file;
  }

  const confirmButton = () => [...host.querySelectorAll<HTMLButtonElement>('.form__actions button')].at(-1)!;

  it('양식을 내려받는다', async () => {
    const api = fakeApi();
    const { save } = await mount(api);
    await act(async () => { byText(host, 'button', '양식 내려받기')!.click(); });
    await flush();
    expect(api.template).toHaveBeenCalled();
    expect(save).toHaveBeenCalledWith(expect.objectContaining({ filename: 'employee-import-template.csv' }));
  });

  it('오류 행만 행 번호와 함께 보이고 확정 버튼은 잠긴다', async () => {
    const api = fakeApi({ preview: vi.fn(async () => BAD) });
    await mount(api);
    expect(confirmButton().disabled).toBe(true);
    await pick();
    expect(host.textContent).toContain('전체 2행 · 오류 1행');
    const rows = [...host.querySelectorAll('tbody tr')];
    expect(rows).toHaveLength(1);
    expect(rows[0].textContent).toContain('3');
    expect(rows[0].textContent).toContain('사업장이 없어요: S99');
    expect(rows[0].textContent).toContain('성명을 입력해 주세요');
    expect(confirmButton().disabled).toBe(true);
    expect(api.confirm).not.toHaveBeenCalled();
  });

  it('오류가 없으면 같은 파일로 확정한다', async () => {
    const api = fakeApi();
    const { onDone } = await mount(api);
    const file = await pick();
    expect(host.querySelectorAll('tbody tr')).toHaveLength(2);
    expect(confirmButton().disabled).toBe(false);
    expect(confirmButton().textContent).toBe('2명 등록');
    await act(async () => { confirmButton().click(); });
    await flush();
    expect(api.confirm).toHaveBeenCalledWith(file);
    expect(onDone).toHaveBeenCalledWith({ created: 2, empNos: ['S01-2026-101', 'S01-2026-102'] });
  });

  it('확정 때 서버가 다시 막으면 새 오류 목록을 보인다', async () => {
    const api = fakeApi({ confirm: vi.fn(async () => { throw new ImportRejected('오류가 있는 행이 있어 등록하지 않았어요', BAD); }) });
    const { onDone } = await mount(api);
    await pick();
    await act(async () => { confirmButton().click(); });
    await flush();
    expect(onDone).not.toHaveBeenCalled();
    expect(host.querySelector('[role="alert"]')!.textContent).toBe('오류가 있는 행이 있어 등록하지 않았어요');
    expect(host.textContent).toContain('사업장이 없어요: S99');
    expect(confirmButton().disabled).toBe(true);
  });

  it('파일 오류(머리글 등)를 보인다', async () => {
    const api = fakeApi({ preview: vi.fn(async () => ({ totalRows: 0, errorRows: 0, confirmable: false, fileErrors: ['필수 열이 없어요: 성명'], rows: [] })) });
    await mount(api);
    await pick();
    expect(host.textContent).toContain('필수 열이 없어요: 성명');
    expect(confirmButton().disabled).toBe(true);
  });
});

describe('목록 내려받기·일괄 등록 버튼 권한', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => { host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host); });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  async function mount(role: ViewerRole) {
    const importApi = fakeApi();
    await act(async () => {
      root.render(<EmployeeDirectory api={createMockEmployeeApi({ employees: EMP_SEED })} codes={createMockCodeApi()}
        viewer={{ id: 'u', role }} toast={vi.fn()} today="2026-11-12" importApi={importApi} />);
    });
    await flush();
    return importApi;
  }

  it.each<[ViewerRole, boolean, boolean]>([
    ['HR', true, true], ['ADMIN', true, true], ['PAYROLL', true, false], ['EMPLOYEE', false, false],
  ])('%s: 내려받기 %s, 일괄 등록 %s', async (role, canExport, canImport) => {
    await mount(role);
    expect(Boolean(byText(host, 'button', '목록 내려받기'))).toBe(canExport);
    expect(Boolean(byText(host, 'button', '엑셀 일괄 등록'))).toBe(canImport);
  });

  it('내려받기 버튼은 서버 내려받기를 호출해 저장한다', async () => {
    const api = fakeApi();
    const save = vi.fn();
    await act(async () => { root.render(<EmployeeExportButton api={api} toast={vi.fn()} save={save} />); });
    await act(async () => { byText(host, 'button', '목록 내려받기')!.click(); });
    await flush();
    expect(api.exportList).toHaveBeenCalledWith(true);
    expect(save).toHaveBeenCalledWith(expect.objectContaining({ filename: 'employees.csv' }));
  });

  it('Content-Disposition 파일 이름', () => {
    expect(filenameOf('attachment; filename="employees.csv"', 'x.csv')).toBe('employees.csv');
    expect(filenameOf(null, 'x.csv')).toBe('x.csv');
  });
});
