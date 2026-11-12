import { describe, expect, it } from 'vitest';
import type { DeptNode, EmployeeSummary } from '../api/org';
import { findDept, flatten, headcounts, isEmployedOn, membersByDept, nextDay, seoulToday, selfAndDescendants } from './orgTree';

const node = (deptId: number, name: string, children: DeptNode[] = []): DeptNode => ({
  deptId, deptCode: `D${deptId}`, name, parentDeptId: null, headEmpId: null, sortOrder: 10, validFrom: '2020-01-01', validTo: null, children,
});
const emp = (empId: number, deptId: number, hireDate: string, retireDate: string | null = null): EmployeeSummary => ({
  empId, empNo: `S01-2020-${String(empId).padStart(3, '0')}`, name: `예시${empId}`, currentDeptId: deptId, jobGradeCode: 'STAFF', jobTitleCode: 'MEMBER',
  employmentTypeCode: 'REGULAR', hireDate, retireDate,
});

const roots = [node(1, '본부', [node(2, '1팀', [node(4, '1파트')]), node(3, '2팀')]), node(5, '영업')];

describe('isEmployedOn', () => {
  it('입사일 당일부터 퇴직일 당일까지 재직', () => {
    const e = emp(1, 1, '2024-03-01', '2025-06-30');
    expect(isEmployedOn(e, '2024-02-29')).toBe(false);
    expect(isEmployedOn(e, '2024-03-01')).toBe(true);
    expect(isEmployedOn(e, '2025-06-30')).toBe(true);
    expect(isEmployedOn(e, '2025-07-01')).toBe(false);
  });
});

describe('headcounts', () => {
  it('직속 인원과 하위부서 포함 인원을 센다', () => {
    const members = membersByDept([emp(1, 1, '2020-01-01'), emp(2, 2, '2020-01-01'), emp(3, 4, '2020-01-01'), emp(4, 4, '2020-01-01'), emp(5, 3, '2030-01-01')], '2026-11-12');
    const c = headcounts(roots, members);
    expect(c.get(1)).toEqual({ direct: 1, total: 4 });
    expect(c.get(2)).toEqual({ direct: 1, total: 3 });
    expect(c.get(4)).toEqual({ direct: 2, total: 2 });
    expect(c.get(3)).toEqual({ direct: 0, total: 0 });   // 입사 예정자는 세지 않음
    expect(c.get(5)).toEqual({ direct: 0, total: 0 });
  });
});

describe('트리 도우미', () => {
  it('flatten 은 위에서 아래 순서와 경로를 준다', () => {
    expect(flatten(roots).map((f) => [f.node.deptId, f.depth, f.path])).toEqual([
      [1, 0, '본부'], [2, 1, '본부 › 1팀'], [4, 2, '본부 › 1팀 › 1파트'], [3, 1, '본부 › 2팀'], [5, 0, '영업'],
    ]);
  });
  it('findDept·selfAndDescendants', () => {
    expect(findDept(roots, 4)?.name).toBe('1파트');
    expect(findDept(roots, 99)).toBeUndefined();
    expect([...selfAndDescendants(findDept(roots, 2)!)].sort()).toEqual([2, 4]);
  });
  it('nextDay 는 월말·윤년을 넘긴다', () => {
    expect(nextDay('2028-02-28')).toBe('2028-02-29');
    expect(nextDay('2026-12-31')).toBe('2027-01-01');
  });
  it('seoulToday 는 서울 날짜(UTC 15시 이후는 다음 날)', () => {
    expect(seoulToday(new Date('2026-11-12T14:59:00Z'))).toBe('2026-11-12');
    expect(seoulToday(new Date('2026-11-12T15:00:00Z'))).toBe('2026-11-13');
  });
});
