import { describe, expect, it, vi } from 'vitest';
import { loadAppData, toDepts } from './bootstrap';
import type { DeptNode, EmployeeSummary } from './employees';
import type { AuthMe, Bootstrap, DashboardSummary, MessageThread } from './types';
import { mockApi } from './mock';

/** #77 로그인 직후 데이터: 사용자·부서·요약은 Spring, 나머지는 Node. 모두 가짜 값. */
const node = (dept: string) => ({
  me: { id: 'E1', loginId: 'x', name: 'Node이름', dept, position: 'p', role: 'HR', email: 'e@example.invalid', annualLeave: 15 },
  depts: [{ id: 'node-dept', name: 'Node부서', color: '#000', desc: '', lead: '' }],
  employees: [], dashboard: { headcount: 999 },
  approvals: [], leaves: [], attendance: { clockIn: null, clockOut: null, week: [] }, payslips: [], threads: [],
}) as unknown as Bootstrap;

const node2 = (deptId: number, name: string, children: DeptNode[] = [], sortOrder = 10): DeptNode => ({
  deptId, deptCode: `D${deptId}`, name, parentDeptId: null, headEmpId: null, sortOrder, validFrom: '2099-01-01', validTo: null, children,
});
const TREE = { baseDate: '2099-03-02', roots: [node2(2, '본부(예시)', [node2(3, '팀(예시)')], 20), node2(1, '대표실(예시)', [], 10)] };
const SELF: EmployeeSummary = {
  empId: 7, empNo: 'S01-2099-007', name: '가짜사원', currentDeptId: 3, jobGradeCode: null, jobTitleCode: null,
  employmentTypeCode: 'REGULAR', hireDate: '2099-01-01', retireDate: null,
};
const SUMMARY: DashboardSummary = { today: '2099-03-02', headcount: 4, hiredThisMonth: 1, presentToday: 2, trend: [] };
const THREADS: MessageThread[] = [{ id: 5, members: [{ empId: 8, name: '가짜상대', deptId: 3 }], preview: '안녕', lastSentAt: '2099-03-02T09:00:00', unread: 1 }];

function fakeGet(auth: AuthMe) {
  return vi.fn(async (path: string): Promise<unknown> => {
    if (path === '/bootstrap') return node('hr');
    if (path === '/auth/me') return auth;
    if (path.startsWith('/org/tree?date=')) return TREE;
    if (path === '/employees') return [SELF];
    if (path === '/dashboard/summary') return SUMMARY;
    if (path === '/threads') return THREADS;
    throw new Error('unexpected ' + path);
  }) as unknown as (<T>(p: string) => Promise<T>) & { mock: { calls: [string][] } };
}

describe('loadAppData', () => {
  it('인사 권한: Spring 사용자·부서·요약을 쓰고 Node 의 me·depts·dashboard 는 버린다', async () => {
    const get = fakeGet({ loginId: 'hr1', empId: 7, roles: ['HR'], permissions: ['EMP_READ_ALL'], managedDeptIds: [] });
    const d = await loadAppData(get);
    expect(d.me).toEqual({
      loginId: 'hr1', empId: 7, name: '가짜사원', dept: '3', role: 'HR', roles: ['HR'], permissions: ['EMP_READ_ALL'], annualLeave: 15,
    });
    expect(d.depts.map((x) => [x.id, x.name])).toEqual([['1', '대표실(예시)'], ['2', '본부(예시)'], ['3', '팀(예시)']]);
    expect(d.legacyDepts).toEqual([{ id: 'node-dept', name: 'Node부서', color: '#000', desc: '', lead: '' }]);
    expect(d.dashboard).toEqual(SUMMARY);
    expect(d).not.toHaveProperty('employees');
    // 메시지 대화방은 Spring /api/threads(#80). Node bootstrap 의 threads 는 버린다.
    expect(d.threads).toEqual(THREADS);
    expect(get.mock.calls.map((c) => c[0])).toContain('/dashboard/summary');
  });

  it('일반 사원은 요약을 부르지 않고(403 방지) dashboard=null', async () => {
    const get = fakeGet({ loginId: 'emp1', empId: 7, roles: ['EMPLOYEE'], permissions: ['EMP_READ_SELF'], managedDeptIds: [] });
    const d = await loadAppData(get);
    expect(d.dashboard).toBeNull();
    expect(d.me.role).toBe('EMPLOYEE');
    expect(get.mock.calls.map((c) => c[0])).not.toContain('/dashboard/summary');
  });

  it('사원과 연결되지 않은 계정은 사원 목록을 부르지 않고 로그인 아이디를 이름으로 쓴다', async () => {
    const get = fakeGet({ loginId: 'admin1', empId: null, roles: ['ADMIN'], permissions: ['EMP_READ_ALL'], managedDeptIds: [] });
    const d = await loadAppData(get);
    expect(d.me).toMatchObject({ name: 'admin1', dept: '', role: 'ADMIN' });
    expect(get.mock.calls.map((c) => c[0])).not.toContain('/employees');
  });
});

describe('toDepts', () => {
  it('정렬 순서대로 펼치고 상위 경로를 desc 로 둔다', () => {
    expect(toDepts(TREE.roots).find((d) => d.id === '3')?.desc).toBe('본부(예시) > 팀(예시)');
  });
});

describe('mockApi.bootstrap (데모)', () => {
  it('Spring 과 같은 모양을 돌려준다', async () => {
    const d = await mockApi.bootstrap();
    expect(d.me.empId).toBe(3);
    expect(d.me.role).toBe('HR');
    expect(d.depts.some((x) => x.id === d.me.dept)).toBe(true);
    expect(d.legacyDepts.some((x) => x.id === d.approvals[0].dept)).toBe(true);
    expect(d.threads[0].members.length).toBeGreaterThan(0);
    expect(d.depts.some((x) => x.id === String(d.threads[0].members[0].deptId))).toBe(true);
    expect(d.dashboard?.trend).toHaveLength(12);
    expect(d.dashboard?.trend[11].month).toMatch(/^\d{4}-\d{2}$/);
  });
});
