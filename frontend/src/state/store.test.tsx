import { act, useEffect } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../api';
import { mockApi } from '../api/mock';
import { StoreProvider, useStore } from './store';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

describe('StoreProvider 법인 전환', () => {
  let host: HTMLDivElement;
  let root: Root;

  beforeEach(() => {
    host = document.createElement('div');
    root = createRoot(host);
  });

  afterEach(() => {
    act(() => root.unmount());
    vi.restoreAllMocks();
  });

  function Probe({ onLost }: { onLost: () => void }) {
    const { data, enterSwitching, login, switchEnter, onSessionLost } = useStore();
    useEffect(() => onSessionLost(onLost), [onLost, onSessionLost]);
    return (
      <>
        <p>{data?.me.enterCd ?? 'blocked'}|{enterSwitching ? 'switching' : 'idle'}</p>
        <button onClick={() => void login('C001', 'hr', '1234')}>login</button>
        <button onClick={() => void switchEnter('C002')}>switch</button>
      </>
    );
  }

  it('서버 전환 뒤 재조회가 실패하면 이전 법인 화면을 막고 세션을 종료한다', async () => {
    await mockApi.login('C001', 'hr', '1234');
    const initial = await mockApi.bootstrap();
    let rejectBootstrap: (reason: Error) => void = () => {};

    vi.spyOn(api, 'login').mockResolvedValue();
    vi.spyOn(api, 'bootstrap')
      .mockResolvedValueOnce(initial)
      .mockImplementationOnce(() => new Promise((_, reject) => { rejectBootstrap = reject; }));
    vi.spyOn(api, 'switchEnter').mockResolvedValue();
    const logout = vi.spyOn(api, 'logout').mockResolvedValue();
    const onLost = vi.fn();

    await act(async () => { root.render(<StoreProvider><Probe onLost={onLost} /></StoreProvider>); });
    await act(async () => { host.querySelectorAll('button')[0].click(); });
    expect(host.querySelector('p')?.textContent).toBe('C001|idle');

    await act(async () => { host.querySelectorAll('button')[1].click(); });
    expect(host.querySelector('p')?.textContent).toBe('blocked|switching');

    await act(async () => { rejectBootstrap(new Error('bootstrap 실패')); });
    expect(host.querySelector('p')?.textContent).toBe('blocked|idle');
    expect(logout).toHaveBeenCalledOnce();
    expect(onLost).toHaveBeenCalledOnce();
  });
});
