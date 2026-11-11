import { describe, expect, it } from 'vitest';
import seedSql from '../../../backend/src/main/resources/db/oracle/V007__common_code_seed.sql?raw';
import { CODE_GROUPS, CODES } from '../data/codes';
import { CODE_GROUP, createMockCodeApi, validateCode } from './codes';
import { ApiError } from './types';

const sql = seedSql.split('\n').map((l) => l.replace(/--.*$/, '')).join('\n');
const groupRows = [...sql.matchAll(/INSERT INTO common_code_group \([^)]*\) VALUES\s*\('([^']*)',\s*'([^']*)',\s*'([^']*)',\s*(\d+)\)/g)]
  .map((m) => ({ groupCode: m[1], name: m[2], description: m[3], sortOrder: Number(m[4]) }));
const codeRows = [...sql.matchAll(/INSERT INTO common_code \(([^)]*)\) VALUES\s*\(([^;]*)\);/g)].map((m) => {
  const cols = m[1].split(',').map((s) => s.trim());
  const vals = [...m[2].matchAll(/'([^']*)'|(\d+)/g)].map((v) => (v[1] !== undefined ? v[1] : Number(v[2])));
  const row = Object.fromEntries(cols.map((c, i) => [c, vals[i]]));
  return { groupCode: row.group_code, code: row.code, name: row.code_name, description: row.description ?? null, sortOrder: row.sort_order, active: true };
});

describe('데모 시드는 백엔드 시드(V007)와 같다', () => {
  it('그룹', () => {
    expect(groupRows.length).toBeGreaterThan(0);
    expect(CODE_GROUPS.map(({ groupCode, name, description, sortOrder }) => ({ groupCode, name, description, sortOrder }))).toEqual(groupRows);
    expect(Object.values(CODE_GROUP)).toEqual(groupRows.map((g) => g.groupCode));
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
    await api.create('EMPLOYMENT_TYPE', { code: 'INTERN', name: ' 인턴 ', description: ' ', sortOrder: 15, active: true });
    const list = await api.activeCodes(['EMPLOYMENT_TYPE']);
    expect(list.EMPLOYMENT_TYPE.map((c) => c.code)).toEqual(['REGULAR', 'INTERN', 'FIXED_TERM', 'PART_TIME']);
    expect(list.EMPLOYMENT_TYPE[1]).toMatchObject({ name: '인턴', description: null });
    await expect(api.create('EMPLOYMENT_TYPE', { code: 'INTERN', name: '인턴', description: null, sortOrder: 1, active: true }))
      .rejects.toMatchObject({ status: 409 });
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
