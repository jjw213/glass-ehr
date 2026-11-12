import { describe, expect, it } from 'vitest';
import seedSql from '../../../backend/src/main/resources/db/oracle/seed/employee_demo_seed.sql?raw';
import { DEMO_DEPTS, DEMO_EMPLOYEES } from '../data/org';
import { createMockOrgApi, validateDept, within, type DeptChange } from './org';
import { ApiError } from './types';

const sql = seedSql.split('\n').map((l) => l.replace(/^\s*--.*$/, '')).join('\n');
const deptOrder = [...sql.matchAll(/INSERT INTO org_dept \(dept_code, valid_from\) VALUES \('([A-Z0-9_]+)', DATE '([\d-]+)'\)/g)].map((m) => ({ code: m[1], from: m[2] }));
const idOf = (code: string) => deptOrder.findIndex((d) => d.code === code) + 1;
const histRows = [...sql.matchAll(/SELECT [^;]*?DATE '([\d-]+)', '([^']*)', (NULL|p\.dept_id), (\d+), 'CREATE'[^;]*?WHERE (?:d\.)?dept_code = '([A-Z0-9_]+)'(?: AND p\.dept_code = '([A-Z0-9_]+)')?/g)]
  .map((m) => ({ from: m[1], name: m[2], sortOrder: Number(m[4]), code: m[5], parentCode: m[6] ?? null }));
const empRows = [...sql.matchAll(/SELECT '(S\d\d-\d{4}-\d{3})', 'S\d\d', '([^']*)'[^;]*?'[A-Z_]{2,}', DATE '([\d-]+)'[^;]*?'([A-Z_]+)', \d+, '[A-Z_]+', dept_id, '([A-Z_]+)', '([A-Z_]+)' FROM org_dept WHERE dept_code = '([A-Z0-9_]+)'/g)]
  .map((m) => ({ empNo: m[1], name: m[2], hireDate: m[3], employmentTypeCode: m[4], jobGradeCode: m[5], jobTitleCode: m[6], deptCode: m[7] }));

describe('데모 시드는 백엔드 데모 시드(employee_demo_seed.sql)와 같다', () => {
  it('부서', () => {
    expect(deptOrder).toHaveLength(9);
    expect(histRows).toHaveLength(9);
    const expected = deptOrder.map((d, i) => {
      const h = histRows.find((r) => r.code === d.code)!;
      return { deptId: i + 1, deptCode: d.code, validFrom: d.from, name: h.name, parentDeptId: h.parentCode ? idOf(h.parentCode) : null, sortOrder: h.sortOrder };
    });
    expect(DEMO_DEPTS.map((d) => ({ deptId: d.deptId, deptCode: d.deptCode, validFrom: d.validFrom, name: d.hist[0].name, parentDeptId: d.hist[0].parentDeptId, sortOrder: d.hist[0].sortOrder }))).toEqual(expected);
  });

  it('사원', () => {
    expect(empRows).toHaveLength(32);
    expect(DEMO_EMPLOYEES.map((e) => ({ empNo: e.empNo, name: e.name, hireDate: e.hireDate, employmentTypeCode: e.employmentTypeCode, jobGradeCode: e.jobGradeCode, jobTitleCode: e.jobTitleCode, deptId: e.currentDeptId })))
      .toEqual(empRows.map(({ deptCode, ...r }) => ({ ...r, deptId: idOf(deptCode) })));
  });
});

describe('within (반개구간)', () => {
  it('시작일은 포함, 종료일은 제외, 종료 null 은 계속', () => {
    expect(within('2026-01-01', '2026-01-01', '2026-02-01')).toBe(true);
    expect(within('2026-02-01', '2026-01-01', '2026-02-01')).toBe(false);
    expect(within('2025-12-31', '2026-01-01', null)).toBe(false);
    expect(within('2099-01-01', '2026-01-01', null)).toBe(true);
  });
});

describe('validateDept', () => {
  const base: DeptChange = { name: '새팀', parentDeptId: null, headEmpId: null, sortOrder: 10, effectiveDate: '2026-11-12', reason: null };
  it('백엔드 OrgDtos 규칙과 같게 검사한다', () => {
    expect(validateDept({ ...base, deptCode: 'DEV3' })).toBeNull();
    expect(validateDept({ ...base, deptCode: '3RD_TEAM' })).toBeNull();
    expect(validateDept({ ...base, deptCode: '_DEV' })).toMatch(/부서코드/);
    expect(validateDept({ ...base, deptCode: 'dev' })).toMatch(/부서코드/);
    expect(validateDept({ ...base, deptCode: 'A'.repeat(21) })).toMatch(/부서코드/);
    expect(validateDept({ ...base, name: '  ' })).toBe('부서명을 입력해 주세요');
    expect(validateDept({ ...base, sortOrder: 100000 })).toMatch(/정렬 순서/);
    expect(validateDept({ ...base, effectiveDate: '' })).toBe('적용일을 입력해 주세요');
  });
});

describe('createMockOrgApi', () => {
  it('기준일 트리: 신설일 전에는 없고, 이동 전날·당일이 각각 옛·새 상위부서', async () => {
    const api = createMockOrgApi();
    const v = await api.createDept({ deptCode: 'DEV3', name: '개발3팀', parentDeptId: 5, headEmpId: null, sortOrder: 30, effectiveDate: '2026-12-01', reason: null });
    expect((await api.tree('2026-11-30')).roots.find((r) => r.deptId === 5)!.children.map((c) => c.deptCode)).toEqual(['DEMO_DEV1', 'DEMO_DEV2']);
    expect((await api.tree('2026-12-01')).roots.find((r) => r.deptId === 5)!.children.map((c) => c.deptCode)).toEqual(['DEMO_DEV1', 'DEMO_DEV2', 'DEV3']);

    await api.changeDept(v.deptId, { name: '개발3팀', parentDeptId: 2, headEmpId: null, sortOrder: 30, effectiveDate: '2027-01-01', reason: null });
    expect((await api.tree('2026-12-31')).roots.find((r) => r.deptId === 5)!.children.map((c) => c.deptCode)).toContain('DEV3');
    expect((await api.tree('2027-01-01')).roots.find((r) => r.deptId === 2)!.children.map((c) => c.deptCode)).toEqual(['DEMO_HR', 'DEMO_FIN', 'DEV3']);
  });

  it('자기 하위로 옮기기·마지막 변경일 이전 적용·중복 코드를 거부한다', async () => {
    const api = createMockOrgApi();
    await expect(api.changeDept(5, { name: '개발본부(예시)', parentDeptId: 6, headEmpId: null, sortOrder: 30, effectiveDate: '2026-12-01', reason: null }))
      .rejects.toThrow('자기 자신이나 하위부서 아래로는 옮길 수 없어요');
    await expect(api.changeDept(6, { name: '개발1팀(예시)', parentDeptId: 2, headEmpId: null, sortOrder: 10, effectiveDate: '2018-01-01', reason: null }))
      .rejects.toThrow(/마지막 변경일/);
    await expect(api.createDept({ deptCode: 'DEMO_HR', name: '중복', parentDeptId: null, headEmpId: null, sortOrder: 10, effectiveDate: '2026-12-01', reason: null }))
      .rejects.toBeInstanceOf(ApiError);
  });
});
