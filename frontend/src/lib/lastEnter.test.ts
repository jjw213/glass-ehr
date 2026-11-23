import { afterEach, describe, expect, it } from 'vitest';
import { initialEnter, readLastEnter, saveLastEnter } from './lastEnter';

/** #120 로그인 화면 회사 선택: 하나면 자동 선택, 마지막 선택 기억 */
const A = { enterCd: 'C001', enterName: '가짜법인A' };
const B = { enterCd: 'C002', enterName: '가짜법인B' };

afterEach(() => window.localStorage.clear());

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
});
