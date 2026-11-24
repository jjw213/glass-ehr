import { afterEach, describe, expect, it } from 'vitest';
import { DEMO_COMPANIES, mockApi } from './mock';

afterEach(async () => { await mockApi.logout(); });

describe('mockApi.login (데모 계정)', () => {
  it('hr / 1234 로 로그인된다', async () => {
    await expect(mockApi.login('C001', 'hr', '1234')).resolves.toBeUndefined();
  });

  it('비밀번호가 틀리면 거부한다', async () => {
    await expect(mockApi.login('C001', 'hr', 'wrong')).rejects.toThrow();
  });

  it('소속이 아닌 회사로는 같은 문구로 거부한다(#120)', async () => {
    await expect(mockApi.login('C002', 'hr', '1234')).rejects.toMatchObject({ status: 401, message: '아이디 또는 비밀번호가 맞지 않아요' });
  });
});

describe('mockApi 법인 (#120 데모도 Spring 과 같은 모양)', () => {
  it('회사 목록은 둘 이상이라 로그인 화면에 선택 칸이 보인다', async () => {
    await expect(mockApi.enterCompanies()).resolves.toEqual(DEMO_COMPANIES);
    expect(DEMO_COMPANIES.length).toBeGreaterThan(1);
  });

  it('데모 계정은 그룹 관리자라 전환하면 현재 법인이 바뀌고 주체 법인은 그대로다', async () => {
    await mockApi.login('C001', 'hr', '1234');
    const before = await mockApi.bootstrap();
    expect(before.me).toMatchObject({ enterCd: 'C001', homeEnterCd: 'C001', enterName: DEMO_COMPANIES[0].enterName });
    expect(before.me.permissions).toContain('ENTER_SWITCH');
    expect(before.companies).toEqual(DEMO_COMPANIES);

    await mockApi.switchEnter('C002');
    const after = await mockApi.bootstrap();
    expect(after.me).toMatchObject({ enterCd: 'C002', homeEnterCd: 'C001', enterName: DEMO_COMPANIES[1].enterName });
  });

  it('없는 법인으로는 전환되지 않고 404', async () => {
    await mockApi.login('C001', 'hr', '1234');
    await expect(mockApi.switchEnter('ZZ99')).rejects.toMatchObject({ status: 404 });
    expect((await mockApi.bootstrap()).me.enterCd).toBe('C001');
  });
});
