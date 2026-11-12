import { describe, expect, it } from 'vitest';
import { EMP_DEPT_TREE, EMP_SEED } from '../data/employees';
import {
  createMockEmployeeApi, filterEmployees, flattenDepts, maskRrn, seoulToday, statusOf, toCreateBody, validateEmployee,
  type EmployeeCreate, type EmployeeSummary,
} from './employees';

const TODAY = '2026-11-12';

const s = (over: Partial<EmployeeSummary>): EmployeeSummary => ({
  empId: 1, empNo: 'S01-2020-001', name: '예시사원01', currentDeptId: 6, jobGradeCode: 'STAFF', jobTitleCode: 'MEMBER',
  employmentTypeCode: 'REGULAR', hireDate: '2020-01-02', retireDate: null, ...over,
});

const input = (over: Partial<EmployeeCreate> = {}): EmployeeCreate => ({
  siteCode: 'S01', name: '예시신규', gender: 'F', birthDate: '2000-01-01', address: '가상시 예시구 1', careerHistory: null,
  jobDescription: '예시 업무', jobFamilyCode: null, hireDate: '2026-12-01', renewalDate: null, contractStartDate: null, contractEndDate: null,
  employmentTypeCode: 'REGULAR', weeklyContractMinutes: null, workLocationCode: null, employmentNote: null, otherNote: null,
  deptId: 6, jobGradeCode: 'STAFF', jobTitleCode: 'MEMBER', rrn: null, ...over,
});

describe('seoulToday', () => {
  it('Asia/Seoul 날짜를 쓴다 (UTC 15시 = 서울 다음 날 0시)', () => {
    expect(seoulToday(new Date('2026-11-12T14:59:00Z'))).toBe('2026-11-12');
    expect(seoulToday(new Date('2026-11-12T15:00:00Z'))).toBe('2026-11-13');
  });
});

describe('statusOf', () => {
  it('입사일 전은 입사예정, 퇴직일 당일까지 재직(퇴직예정), 다음 날부터 퇴직', () => {
    expect(statusOf({ hireDate: '2026-11-13', retireDate: null }, TODAY)).toBe('PENDING');
    expect(statusOf({ hireDate: '2026-11-12', retireDate: null }, TODAY)).toBe('ACTIVE');
    expect(statusOf({ hireDate: '2020-01-01', retireDate: '2026-11-12' }, TODAY)).toBe('RETIRING');
    expect(statusOf({ hireDate: '2020-01-01', retireDate: '2026-11-11' }, TODAY)).toBe('RETIRED');
  });
});

describe('flattenDepts', () => {
  it('정렬 순서대로 펼치고 상위부서 경로를 단다', () => {
    const flat = flattenDepts(EMP_DEPT_TREE);
    expect(flat.map((d) => d.deptId)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9]);
    const dev1 = flat.find((d) => d.deptId === 6)!;
    expect(dev1).toMatchObject({ depth: 1, path: '개발본부(예시) > 개발1팀(예시)', ancestors: [5] });
  });
});

describe('filterEmployees', () => {
  const depts = flattenDepts(EMP_DEPT_TREE);
  const list = [
    s({ empId: 1, empNo: 'S01-2020-001', name: '예시사원01', currentDeptId: 6 }),
    s({ empId: 2, empNo: 'S01-2021-001', name: '예시사원02', currentDeptId: 7, jobGradeCode: 'MANAGER' }),
    s({ empId: 3, empNo: 'S01-2022-001', name: '예시사원03', currentDeptId: 8, retireDate: '2025-01-01' }),
    s({ empId: 4, empNo: 'S01-2027-001', name: '예시사원04', currentDeptId: 3, hireDate: '2027-01-04' }),
  ];
  const all = { q: '', deptId: null, jobGradeCode: '', status: 'ALL' as const };
  const ids = (r: EmployeeSummary[]) => r.map((e) => e.empId);

  it('이름·사번 부분 검색 (사번은 대소문자 무시)', () => {
    expect(ids(filterEmployees(list, { ...all, q: '사원02' }, depts, TODAY))).toEqual([2]);
    expect(ids(filterEmployees(list, { ...all, q: 's01-2022' }, depts, TODAY))).toEqual([3]);
    expect(ids(filterEmployees(list, { ...all, q: '  ' }, depts, TODAY))).toEqual([1, 2, 3, 4]);
  });

  it('상위부서를 고르면 하위부서 사원도 나온다', () => {
    expect(ids(filterEmployees(list, { ...all, deptId: 5 }, depts, TODAY))).toEqual([1, 2]);
    expect(ids(filterEmployees(list, { ...all, deptId: 6 }, depts, TODAY))).toEqual([1]);
  });

  it('직급과 재직상태로 거른다', () => {
    expect(ids(filterEmployees(list, { ...all, jobGradeCode: 'MANAGER' }, depts, TODAY))).toEqual([2]);
    expect(ids(filterEmployees(list, { ...all, status: 'ACTIVE' }, depts, TODAY))).toEqual([1, 2]);
    expect(ids(filterEmployees(list, { ...all, status: 'RETIRED' }, depts, TODAY))).toEqual([3]);
    expect(ids(filterEmployees(list, { ...all, status: 'PENDING' }, depts, TODAY))).toEqual([4]);
  });
});

describe('validateEmployee', () => {
  it('정상 입력은 통과', () => expect(validateEmployee(input())).toBeNull());
  it.each([
    [{ siteCode: 'X1' }, '사업장코드는 S01 형식이에요'],
    [{ name: ' ' }, '성명을 입력해 주세요'],
    [{ gender: '' as const }, '성별을 선택해 주세요'],
    [{ birthDate: '' }, '생년월일을 입력해 주세요'],
    [{ address: '' }, '주소를 입력해 주세요'],
    [{ jobDescription: '' }, '종사 업무를 입력해 주세요'],
    [{ employmentTypeCode: '' }, '고용형태를 선택해 주세요'],
    [{ contractStartDate: '2027-01-01', contractEndDate: '2026-12-31' }, '계약 종료일이 시작일보다 빨라요'],
    [{ weeklyContractMinutes: 0 }, '1주 소정근로시간은 0보다 커야 해요'],
    [{ weeklyContractMinutes: 7 * 24 * 60 + 1 }, '1주 소정근로시간(분)이 너무 커요'],
    [{ rrn: '12345' }, '주민등록번호 형식이 올바르지 않아요(숫자 6자리-숫자 7자리)'],
  ])('%j → %s', (over, msg) => expect(validateEmployee(input(over))).toBe(msg));

  it('오류 메시지에 주민등록번호 입력값을 넣지 않는다', () => {
    expect(validateEmployee(input({ rrn: '000000-00000' }))).not.toContain('000000');
  });
});

describe('toCreateBody', () => {
  it('빈 선택 항목은 null 로 보낸다', () => {
    const b = toCreateBody(input({ careerHistory: '  ', jobFamilyCode: '', rrn: ' ' }));
    expect(b.careerHistory).toBeNull();
    expect(b.jobFamilyCode).toBeNull();
    expect(b.rrn).toBeNull();
  });
});

describe('createMockEmployeeApi', () => {
  it('사번은 사업장·입사연도별 다음 순번', async () => {
    const api = createMockEmployeeApi();
    const v = await api.create(input({ hireDate: '2026-12-01' }), { id: 'hr', role: 'HR' });
    const same = EMP_SEED.filter((e) => e.empNo.startsWith('S01-2026-')).length;
    expect(v.empNo).toBe(`S01-2026-${String(same + 1).padStart(3, '0')}`);
    expect((await api.create(input({ hireDate: '2030-03-02' }), { id: 'hr', role: 'HR' })).empNo).toBe('S01-2030-001');
  });

  it('주민등록번호는 마스킹 값만 돌려주고, 원문은 인사·급여 담당만 받는다', async () => {
    const api = createMockEmployeeApi();
    const v = await api.create(input({ rrn: '0000000000001' }), { id: 'hr', role: 'HR' });
    expect(v.rrnMasked).toBe('000000-*******');
    expect(JSON.stringify(v)).not.toContain('0000001');
    await expect(api.revealRrn(v.empId, 'VIEW', { id: 'admin', role: 'ADMIN' })).rejects.toMatchObject({ status: 403 });
    expect(await api.revealRrn(v.empId, 'VIEW', { id: 'pay', role: 'PAYROLL' })).toBe('000000-0000001');
    expect(api.revealLog.map((l) => [l.role, l.ok])).toEqual([['ADMIN', false], ['PAYROLL', true]]);
  });

  it('includeRetired=false 는 퇴직일이 정해진 사람을 뺀다 (백엔드와 같음)', async () => {
    const api = createMockEmployeeApi();
    const active = await api.list(false);
    expect(active.every((e) => e.retireDate === null)).toBe(true);
    expect((await api.list(true)).length).toBe(EMP_SEED.length);
  });

  it('maskRrn', () => expect(maskRrn('0000000000001')).toBe('000000-*******'));
});

describe('가짜 시드', () => {
  it('성명·주소가 명백한 가짜 값이고 주민등록번호가 없다', () => {
    for (const e of EMP_SEED) {
      expect(e.name).toMatch(/^예시사원\d{2}$/);
      expect(e.address.startsWith('가상시 ')).toBe(true);
      expect(e.rrnMasked).toBeNull();
    }
    expect(new Set(EMP_SEED.map((e) => e.empNo)).size).toBe(EMP_SEED.length);
  });
});

it('statusOf 는 실제 오늘로도 돈다', () => {
  expect(['PENDING', 'ACTIVE', 'RETIRING', 'RETIRED']).toContain(statusOf(EMP_SEED[0], seoulToday()));
});
