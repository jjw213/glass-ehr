import { describe, expect, it } from 'vitest';
import { mockApi } from './mock';

describe('mockApi.login (데모 계정)', () => {
  it('hr / 1234 로 로그인된다', async () => {
    await expect(mockApi.login('hr', '1234')).resolves.toBeUndefined();
  });

  it('비밀번호가 틀리면 거부한다', async () => {
    await expect(mockApi.login('hr', 'wrong')).rejects.toThrow();
  });
});
