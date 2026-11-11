import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createMockCodeApi, type CodeApi } from '../api/codes';
import { invalidateCodes, loadCodes, useCodes } from './useCodes';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

/** activeCodes 호출을 세는 mock api */
function countingApi() {
  const api = createMockCodeApi();
  const spy = vi.fn(api.activeCodes);
  const wrapped: CodeApi = { ...api, activeCodes: spy };
  return { api: wrapped, spy };
}

describe('loadCodes', () => {
  it('캐시에 없는 그룹만 한 요청으로 받아 오고, 이후에는 캐시를 쓴다', async () => {
    const { api, spy } = countingApi();
    const [a, b] = await Promise.all([loadCodes(['JOB_GRADE'], api), loadCodes(['JOB_GRADE', 'EMPLOYMENT_TYPE'], api)]);
    expect(a.JOB_GRADE[0].code).toBe('STAFF');
    expect(b.EMPLOYMENT_TYPE.map((c) => c.code)).toEqual(['REGULAR', 'FIXED_TERM', 'PART_TIME']);
    expect(spy).toHaveBeenCalledTimes(2);
    expect(spy.mock.calls).toEqual([[['JOB_GRADE']], [['EMPLOYMENT_TYPE']]]);
    await loadCodes(['EMPLOYMENT_TYPE', 'JOB_GRADE'], api);
    expect(spy).toHaveBeenCalledTimes(2);
  });

  it('실패한 그룹은 캐시에 남기지 않아 다시 시도할 수 있다', async () => {
    const { api, spy } = countingApi();
    await expect(loadCodes(['NOPE'], api)).rejects.toMatchObject({ status: 404 });
    await expect(loadCodes(['NOPE'], api)).rejects.toMatchObject({ status: 404 });
    expect(spy).toHaveBeenCalledTimes(2);
  });

  it('invalidateCodes 뒤에는 다시 받아 온다', async () => {
    const { api, spy } = countingApi();
    await loadCodes(['JOB_TITLE'], api);
    invalidateCodes('JOB_TITLE', api);
    await loadCodes(['JOB_TITLE'], api);
    expect(spy).toHaveBeenCalledTimes(2);
  });
});

describe('useCodes', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => { host = document.createElement('div'); root = createRoot(host); });
  afterEach(() => { act(() => root.unmount()); });

  function Probe({ group, api, code }: { group: string; api: CodeApi; code: string }) {
    const { codes, nameOf, loading, error } = useCodes(group, api);
    if (loading) return <p>loading</p>;
    if (error) return <p>error:{error}</p>;
    return <p>{codes.map((c) => c.name).join(',')}|{nameOf(code)}</p>;
  }

  const flush = () => act(async () => { await new Promise((r) => setTimeout(r, 0)); });

  it('사용 중인 코드를 정렬 순서대로 주고, nameOf 는 모르는 코드값을 그대로 돌려준다', async () => {
    const { api } = countingApi();
    await act(async () => { root.render(<Probe group="EMPLOYMENT_TYPE" api={api} code="FIXED_TERM" />); });
    await flush();
    expect(host.textContent).toBe('정규직,기간제,단시간|기간제');

    await act(async () => { root.render(<Probe group="EMPLOYMENT_TYPE" api={api} code="RETIRED_OLD" />); });
    expect(host.textContent).toBe('정규직,기간제,단시간|RETIRED_OLD');
  });

  it('관리 화면에서 고친 뒤 invalidateCodes 하면 새 목록을 보인다', async () => {
    const { api } = countingApi();
    await act(async () => { root.render(<Probe group="WORK_LOCATION" api={api} code="HQ" />); });
    await flush();
    expect(host.textContent).toBe('본사,A지사,A공장|본사');

    await api.update('WORK_LOCATION', 'HQ', { name: '본사', description: null, sortOrder: 10, active: false });
    await act(async () => { invalidateCodes('WORK_LOCATION', api); });
    await flush();
    // 사용 중지된 코드는 목록에서 빠지고, 이름 조회는 코드값으로 대신한다
    expect(host.textContent).toBe('A지사,A공장|HQ');
  });

  it('없는 그룹이면 오류 메시지', async () => {
    const { api } = countingApi();
    await act(async () => { root.render(<Probe group="NOPE" api={api} code="X" />); });
    await flush();
    expect(host.textContent).toBe('error:코드그룹이 없어요: NOPE');
  });
});
