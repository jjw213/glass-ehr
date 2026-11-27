import { describe, expect, it } from 'vitest';
import seedSql from '../../../backend/src/main/resources/db/oracle/V007__common_code_seed.sql?raw';
import { CODE_GROUPS, CODES } from '../data/codes';
import { CODE_GROUP, createMockCodeApi, validateCode } from './codes';
import { ApiError } from './types';

import systemSql from '../../../backend/src/main/resources/db/oracle/V037__common_code_system_edit_page.sql?raw';
import sysTypeSeedSql from '../../../backend/src/main/resources/db/oracle/V050__common_code_sys_type.sql?raw';

/** V050 이 채우는 처리방식: 그룹 → (코드값 → 처리방식, 나머지) */
const sysTypeSql = sysTypeSeedSql.split('\n').map((l) => l.replace(/--.*$/, '')).join('\n');
const sysTypeRules = new Map([...sysTypeSql.matchAll(/SET process_type = CASE code([^;]*?)ELSE '(\w+)' END\s+WHERE group_code = '(\w+)'/g)]
  .map((m) => [m[3], { cases: new Map([...m[1].matchAll(/WHEN '(\w+)' THEN '(\w+)'/g)].map((w) => [w[1], w[2]])), other: m[2] }]));
const seedSysType = (group: string, code: string) => {
  const r = sysTypeRules.get(group);
  return r ? r.cases.get(code) ?? r.other : null;
};
const sql = seedSql.split('\n').map((l) => l.replace(/--.*$/, '')).join('\n');
const sysSql = systemSql.split('\n').map((l) => l.replace(/--.*$/, '')).join('\n');
const quoted = (s: string) => [...s.matchAll(/'([^']*)'/g)].map((m) => m[1]);
/** V037 이 system_yn='Y' 로 바꾸는 그룹·코드 */
const systemGroups = new Set(quoted(/UPDATE common_code_group SET system_yn = 'Y'\s+WHERE group_code IN \(([^)]*)\)/.exec(sysSql)![1]));
const codeUpdate = /UPDATE common_code SET system_yn = 'Y'([^;]*);/.exec(sysSql)![1];
const wholeSystemGroups = new Set([...codeUpdate.matchAll(/group_code = '(\w+)'(?!\s+AND)/g)].map((m) => m[1]));
const systemCodes = new Set([...codeUpdate.matchAll(/group_code = '(\w+)'\s+AND code IN \(([^)]*)\)/g)]
  .flatMap((m) => quoted(m[2]).map((code) => `${m[1]}/${code}`)));
const groupRows = [...sql.matchAll(/INSERT INTO common_code_group \([^)]*\) VALUES\s*\('([^']*)',\s*'([^']*)',\s*'([^']*)',\s*(\d+)\)/g)]
  .map((m) => ({ groupCode: m[1], name: m[2], description: m[3], sortOrder: Number(m[4]) }));
const codeRows = [...sql.matchAll(/INSERT INTO common_code \(([^)]*)\) VALUES\s*\(([^;]*)\);/g)].map((m) => {
  const cols = m[1].split(',').map((s) => s.trim());
  const vals = [...m[2].matchAll(/'([^']*)'|(\d+)/g)].map((v) => (v[1] !== undefined ? v[1] : Number(v[2])));
  const row = Object.fromEntries(cols.map((c, i) => [c, vals[i]]));
  return {
    groupCode: row.group_code, code: row.code, name: row.code_name, description: row.description ?? null, sortOrder: row.sort_order, active: true,
    system: wholeSystemGroups.has(String(row.group_code)) || systemCodes.has(`${row.group_code}/${row.code}`),
    sysType: seedSysType(String(row.group_code), String(row.code)),
  };
});

describe('데모 시드는 백엔드 시드(V007)와 같다', () => {
  it('그룹', () => {
    expect(groupRows.length).toBeGreaterThan(0);
    expect(CODE_GROUPS.map(({ groupCode, name, description, sortOrder }) => ({ groupCode, name, description, sortOrder }))).toEqual(groupRows);
    expect(Object.values(CODE_GROUP)).toEqual(groupRows.map((g) => g.groupCode));
    expect(CODE_GROUPS.map((g) => g.system)).toEqual(groupRows.map((g) => systemGroups.has(g.groupCode)));
  });

  it('코드', () => {
    expect(codeRows.length).toBeGreaterThan(0);
    expect(CODES).toEqual(codeRows);
  });
});

describe('validateCode', () => {
  const ok = { code: 'INTERN', name: '인턴', description: null, sortOrder: 10, active: true };
  it('정상 입력은 통과', () => expect(validateCode(ok)).toBeNull());
  it('코드값 형식', () => {
    expect(validateCode({ ...ok, code: 'intern' })).not.toBeNull();
    expect(validateCode({ ...ok, code: '1ST' })).not.toBeNull();
    expect(validateCode({ ...ok, code: 'A'.repeat(31) })).not.toBeNull();
    expect(validateCode({ ...ok, code: 'A'.repeat(30) })).toBeNull();
  });
  it('코드명·정렬 순서', () => {
    expect(validateCode({ ...ok, name: '  ' })).not.toBeNull();
    expect(validateCode({ ...ok, sortOrder: -1 })).not.toBeNull();
    expect(validateCode({ ...ok, sortOrder: 1.5 })).not.toBeNull();
    expect(validateCode({ ...ok, sortOrder: NaN })).not.toBeNull();
  });
});

describe('mock 공통코드 API', () => {
  it('추가하면 정렬 순서대로 조회되고, 중복 코드값은 409', async () => {
    const api = createMockCodeApi();
    await api.create('EMPLOYMENT_TYPE', { code: 'INTERN', name: ' 인턴 ', description: ' ', sortOrder: 15, active: true, sysType: 'FIXED_TERM' });
    const list = await api.activeCodes(['EMPLOYMENT_TYPE']);
    expect(list.EMPLOYMENT_TYPE.map((c) => c.code)).toEqual(['REGULAR', 'INTERN', 'FIXED_TERM', 'PART_TIME']);
    expect(list.EMPLOYMENT_TYPE[1]).toMatchObject({ name: '인턴', description: null, sysType: 'FIXED_TERM' });
    await expect(api.create('EMPLOYMENT_TYPE', { code: 'INTERN', name: '인턴', description: null, sortOrder: 1, active: true, sysType: 'REGULAR' }))
      .rejects.toMatchObject({ status: 409 });
  });

  it('처리방식(#141): 있는 그룹은 필수·목록 안의 값만, 없는 그룹은 둘 수 없고, 기본 코드는 바꿀 수 없다', async () => {
    const api = createMockCodeApi();
    const base = { code: 'X1', name: '테스트', description: null, sortOrder: 99, active: true };
    expect((await api.sysTypes('RETIREMENT_REASON')).map((t) => t.code)).toContain('DISMISSAL');
    expect(await api.sysTypes('JOB_GRADE')).toEqual([]);
    await expect(api.create('EMPLOYMENT_TYPE', base)).rejects.toMatchObject({ status: 400 });
    await expect(api.create('EMPLOYMENT_TYPE', { ...base, sysType: 'DISMISSAL' })).rejects.toMatchObject({ status: 400 });
    await expect(api.create('JOB_GRADE', { ...base, sysType: 'REGULAR' })).rejects.toMatchObject({ status: 400 });
    await expect(api.update('RETIREMENT_REASON', 'DISMISSAL', { name: '해고', description: null, sortOrder: 50, active: true, sysType: 'OTHER' }))
      .rejects.toMatchObject({ status: 409 });
    expect(await api.create('RETIREMENT_REASON', { ...base, code: 'MGMT_DISMISSAL', sysType: 'DISMISSAL' })).toMatchObject({ sysType: 'DISMISSAL' });
  });

  it('사용 중지하면 훅용 조회에서 빠지고 관리 조회에는 남는다', async () => {
    const api = createMockCodeApi();
    await api.update('WORK_LOCATION', 'HQ', { name: '본사', description: null, sortOrder: 10, active: false });
    expect((await api.activeCodes(['WORK_LOCATION'])).WORK_LOCATION.map((c) => c.code)).not.toContain('HQ');
    expect((await api.codes('WORK_LOCATION', true)).find((c) => c.code === 'HQ')?.active).toBe(false);
    expect((await api.codes('WORK_LOCATION', false)).map((c) => c.code)).not.toContain('HQ');
  });

  it('없는 그룹·코드는 404, 잘못된 입력은 400', async () => {
    const api = createMockCodeApi();
    await expect(api.codes('NOPE', true)).rejects.toBeInstanceOf(ApiError);
    await expect(api.activeCodes(['JOB_GRADE', 'NOPE'])).rejects.toMatchObject({ status: 404 });
    await expect(api.update('JOB_GRADE', 'NOPE', { name: 'x', description: null, sortOrder: 1, active: true })).rejects.toMatchObject({ status: 404 });
    await expect(api.create('JOB_GRADE', { code: 'bad', name: 'x', description: null, sortOrder: 1, active: true })).rejects.toMatchObject({ status: 400 });
  });

  it('인스턴스마다 데이터가 따로다', async () => {
    const a = createMockCodeApi();
    await a.create('JOB_GRADE', { code: 'INTERN', name: '인턴', description: null, sortOrder: 1, active: true });
    expect((await createMockCodeApi().codes('JOB_GRADE', true)).map((c) => c.code)).not.toContain('INTERN');
  });
});
