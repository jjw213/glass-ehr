import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AppData, Payslip } from '../api';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

/** #81 급여명세서: 급여 모듈 전(payslips=null)은 '준비 중', 데모 샘플이 있으면 명세를 그대로 보여 준다. 모두 가짜 값. */
let data: AppData;
vi.mock('../state/store', () => ({ useData: () => data }));

const { PayrollPage } = await import('./PayrollPage');

const SLIP: Payslip = {
  month: '2099년 3월', payMonth: '2099-03', payDate: '2099-03-25',
  earnings: [{ label: '기본급(예시)', amount: 1_000_000 }],
  deductions: [{ label: '공제(예시)', amount: 100_000 }],
};

const make = (payslips: Payslip[] | null): AppData => ({
  me: { loginId: 'emp1', empId: 1, name: '가짜사원', dept: '', role: 'EMPLOYEE', roles: [], permissions: [], annualLeave: 15 },
  depts: [], legacyDepts: [], dashboard: null,
  approvals: [], leaves: [], payslips, threads: [],
});

describe('PayrollPage', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => { host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host); });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  it('급여 모듈 전에는 준비 중 안내만 보여 준다', () => {
    data = make(null);
    act(() => root.render(<PayrollPage />));
    const text = host.textContent ?? '';
    expect(text).toContain('준비 중');
    expect(text).not.toContain('지급 내역');
    expect(host.querySelector('.chips')).toBeNull();
  });

  it('데모 샘플이 있으면 지급·공제 내역을 보여 준다', () => {
    data = make([SLIP]);
    act(() => root.render(<PayrollPage />));
    const text = host.textContent ?? '';
    expect(text).toContain('기본급(예시)');
    expect(text).toContain('공제(예시)');
    expect(text).not.toContain('준비 중');
  });
});
