import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { initialEnter, readLastEnter, saveLastEnter } from './lastEnter';

/** #120 로그인 화면 회사 선택: 하나면 자동 선택, 마지막 선택 기억 */
const A = { enterCd: 'C001', enterName: '가짜법인A' };
const B = { enterCd: 'C002', enterName: '가짜법인B' };

// Node 22+ 전역 localStorage 가 jsdom 것을 가릴 수 있어 메모리 저장소로 바꿔 끼운다.
beforeEach(() => {
  const m = new Map<string, string>();
  vi.stubGlobal('localStorage', {
    getItem: (k: string) => m.get(k) ?? null,
    setItem: (k: string, v: string) => { m.set(k, v); },
    removeItem: (k: string) => { m.delete(k); },
    clear: () => m.clear(),
  });
});
afterEach(() => vi.unstubAllGlobals());

describe('initialEnter', () => {
  it('법인이 하나면 기억한 값과 상관없이 그 법인', () => {
    expect(initialEnter([A], null)).toBe('C001');
    expect(initialEnter([A], 'C002')).toBe('C001');
  });

  it('여럿이면 마지막 선택이 아직 사용 중일 때만 고른다', () => {
    expect(initialEnter([A, B], 'C002')).toBe('C002');
    expect(initialEnter([A, B], 'C009')).toBe('');
    expect(initialEnter([A, B], null)).toBe('');
  });

  it('목록이 비면 고르지 않는다', () => {
    expect(initialEnter([], 'C001')).toBe('');
  });
});

describe('마지막 선택 기억', () => {
  it('저장한 법인 코드를 다시 읽는다', () => {
    expect(readLastEnter()).toBeNull();
    saveLastEnter('C002');
    expect(readLastEnter()).toBe('C002');
  });

  it('저장소를 못 쓰면 기억하지 않고 오류도 내지 않는다', () => {
    vi.stubGlobal('localStorage', undefined);
    expect(() => saveLastEnter('C002')).not.toThrow();
    expect(readLastEnter()).toBeNull();
  });
});
