import { describe, expect, it } from 'vitest';
import v038 from '../../../backend/src/main/resources/db/oracle/V038__attendance_code.sql?raw';
import { ATTENDANCE_CODE_GROUPS, ATTENDANCE_CODES } from '../data/attendanceCodes';
import { createMockAttendanceCodeApi, PROCESS_TYPES, validateLeave, type AttendanceCodeCreate } from './attendanceCodes';
import { createMockCodeApi } from './codes';
import { ApiError } from './types';

const sql = v038.split('\n').map((l) => l.replace(/--.*$/, '')).join('\n');
type Val = string | number | null;
/** `INSERT INTO <table> (enter_cd, ...) SELECT enter_cd, ... FROM enter_company;` 행을 열 이름 → 값으로 */
function rows(table: string): Record<string, Val>[] {
  const re = new RegExp(`INSERT INTO ${table} \\(([^)]*)\\)\\s+SELECT enter_cd, ([^;]*?)\\s+FROM enter_company;`, 'g');
  return [...sql.matchAll(re)].map((m) => {
    const cols = m[1].split(',').map((s) => s.trim()).slice(1);
    const vals: Val[] = [...m[2].matchAll(/'([^']*)'|(\d+(?:\.\d+)?)|(NULL)/g)].map((v) => (v[1] !== undefined ? v[1] : v[2] !== undefined ? Number(v[2]) : null));
    return Object.fromEntries(cols.map((c, i) => [c, vals[i]]));
  });
}
const yn = (v: Val) => (v === null ? null : v === 'Y');

describe('데모 근태코드는 백엔드 V038 과 같다', () => {
  it('그룹', () => {
    const groups = rows('common_code_group');
    expect(groups.length).toBe(2);
    expect(ATTENDANCE_CODE_GROUPS).toEqual(groups.map((g) => ({
      groupCode: g.group_code, name: g.group_name, description: g.description, sortOrder: g.sort_order,
      active: yn(g.use_yn), system: yn(g.system_yn), editPage: g.edit_page,
    })));
  });

  it('코드와 휴가 상세 설정', () => {
    const details = new Map(rows('attendance_leave_type').map((d) => [d.code, d]));
    const codes = rows('common_code');
    expect(codes.length).toBe(13);
    expect(ATTENDANCE_CODES.map(({ inUse: _i, usageCount: _u, leave, ...c }) => ({
      ...c, leave: leave && { ...leave, legalValues: undefined },
    }))).toEqual(codes.map((c) => {
      const d = details.get(c.code);
      return {
        groupCode: c.group_code, code: c.code, name: c.code_name, description: c.description, sortOrder: c.sort_order,
        active: yn(c.use_yn), system: yn(c.system_yn), processType: c.process_type,
        leave: d ? {
          unitDay: yn(d.unit_day_yn), unitHalfDay: yn(d.unit_half_day_yn), unitHour: yn(d.unit_hour_yn), paid: yn(d.paid_yn),
          proofRequired: yn(d.proof_required_yn), annualLimitDays: d.annual_limit_days, genderLimit: d.gender_limit, legalBasis: d.legal_basis,
          legalValues: undefined,
        } : null,
      };
    }));
  });

  it('처리방식 목록은 DB CHECK 와 같다', () => {
    const check = /ck_common_code_process CHECK \(([\s\S]*?)\);/.exec(sql)![1];
    for (const g of ['LEAVE_TYPE', 'ATTENDANCE_TYPE'] as const) {
      const list = new RegExp(`group_code = '${g}' AND process_type IN \\(([^)]*)\\)`).exec(check)![1];
      expect([...list.matchAll(/'(\w+)'/g)].map((m) => m[1])).toEqual(PROCESS_TYPES[g].map((p) => p.value));
    }
  });
});

const refresh: AttendanceCodeCreate = {
  code: 'REFRESH', name: '리프레시 휴가', description: null, sortOrder: 140, active: true, processType: 'COMPANY',
  leave: { unitDay: true, unitHalfDay: false, unitHour: false, paid: true, proofRequired: false, annualLimitDays: 5 },
};
const status = async (p: Promise<unknown>) => { try { await p; return 0; } catch (e) { return (e as ApiError).status; } };

describe('validateLeave', () => {
  const d = refresh.leave!;
  it('사용 단위는 하나 이상', () => expect(validateLeave('COMPANY', { ...d, unitDay: false }, false)).not.toBeNull());
  it('무급휴가는 유급일 수 없다', () => expect(validateLeave('UNPAID', d, false)).not.toBeNull());
  it('연간 한도 0.5~366, 소수 첫째 자리', () => {
    expect(validateLeave('COMPANY', { ...d, annualLimitDays: 0.5 }, false)).toBeNull();
    expect(validateLeave('COMPANY', { ...d, annualLimitDays: 0.4 }, false)).not.toBeNull();
    expect(validateLeave('COMPANY', { ...d, annualLimitDays: 366 }, false)).toBeNull();
    expect(validateLeave('COMPANY', { ...d, annualLimitDays: 367 }, false)).not.toBeNull();
    expect(validateLeave('COMPANY', { ...d, annualLimitDays: 1.25 }, false)).not.toBeNull();
    expect(validateLeave('COMPANY', { ...d, annualLimitDays: null }, false)).toBeNull();
  });
});

describe('createMockAttendanceCodeApi', () => {
  it('약정휴가 추가·수정·삭제', async () => {
    const api = createMockAttendanceCodeApi();
    const created = await api.create('LEAVE_TYPE', refresh);
    expect(created.leave).toMatchObject({ unitDay: true, unitHalfDay: false, paid: true, annualLimitDays: 5, legalValues: [] });
    const updated = await api.update('LEAVE_TYPE', 'REFRESH', { ...refresh, leave: { ...refresh.leave!, annualLimitDays: 3 } });
    expect(updated.leave?.annualLimitDays).toBe(3);
    await api.remove('LEAVE_TYPE', 'REFRESH');
    expect((await api.list('LEAVE_TYPE')).map((c) => c.code)).not.toContain('REFRESH');
  });

  it('연차·법정휴가는 추가할 수 없고, 기본 코드는 삭제·유급·한도 변경 불가', async () => {
    const api = createMockAttendanceCodeApi();
    expect(await status(api.create('LEAVE_TYPE', { ...refresh, processType: 'STATUTORY' }))).toBe(400);
    expect(await status(api.remove('LEAVE_TYPE', 'MATERNITY'))).toBe(409);
    const m = (await api.list('LEAVE_TYPE')).find((c) => c.code === 'MATERNITY')!;
    const base = { name: m.name, description: m.description, sortOrder: m.sortOrder, active: m.active, processType: m.processType };
    const leave = { unitDay: true, unitHalfDay: false, unitHour: false, paid: m.leave!.paid, proofRequired: true, annualLimitDays: null };
    expect(await status(api.update('LEAVE_TYPE', 'MATERNITY', { ...base, leave: { ...leave, annualLimitDays: 100 } }))).toBe(409);
    expect(await status(api.update('LEAVE_TYPE', 'MATERNITY', { ...base, processType: 'COMPANY', leave }))).toBe(409);
    const ok = await api.update('LEAVE_TYPE', 'MATERNITY', { ...base, active: false, leave: { ...leave, proofRequired: false } });
    expect(ok).toMatchObject({ active: false, leave: { proofRequired: false, genderLimit: 'F' } });
  });

  it('사용 중이면 409', async () => {
    const api = createMockAttendanceCodeApi({ usages: { 'LEAVE_TYPE/SICK': [{ table: 'leave_request', label: '휴가 신청', count: 2 }] } });
    expect((await api.list('LEAVE_TYPE')).find((c) => c.code === 'SICK')).toMatchObject({ inUse: true, usageCount: 2 });
    expect(await status(api.remove('LEAVE_TYPE', 'SICK'))).toBe(409);
  });

  it('근태 구분에는 휴가 상세 설정을 두지 않는다', async () => {
    const api = createMockAttendanceCodeApi();
    expect(await status(api.create('ATTENDANCE_TYPE', { ...refresh, code: 'FIELD', processType: 'OUTSIDE_WORK' }))).toBe(400);
    const c = await api.create('ATTENDANCE_TYPE', { ...refresh, code: 'FIELD', processType: 'OUTSIDE_WORK', leave: null });
    expect(c.leave).toBeNull();
  });
});

describe('공통코드 API 는 editPage 그룹을 고치지 않는다', () => {
  it('추가·수정·삭제·그룹 수정 409', async () => {
    const api = createMockCodeApi({ groups: ATTENDANCE_CODE_GROUPS, codes: [{ groupCode: 'LEAVE_TYPE', code: 'SICK', name: '병가', description: null, sortOrder: 1, active: true, system: false }] });
    const input = { name: '병가', description: null, sortOrder: 1, active: false };
    expect(await status(api.create('LEAVE_TYPE', { ...input, code: 'X' }))).toBe(409);
    expect(await status(api.update('LEAVE_TYPE', 'SICK', input))).toBe(409);
    expect(await status(api.deleteCode('LEAVE_TYPE', 'SICK'))).toBe(409);
    expect(await status(api.updateGroup('LEAVE_TYPE', { name: '휴가', description: null, sortOrder: 70, active: true }))).toBe(409);
    expect((await api.codes('LEAVE_TYPE', true)).map((c) => c.code)).toEqual(['SICK']);
  });
});
