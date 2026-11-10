import { describe, expect, it } from 'vitest';
import { NAV, navById } from './nav';

describe('NAV', () => {
  it('메뉴 id가 겹치지 않는다', () => {
    const ids = NAV.map((n) => n.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('navById로 각 메뉴를 찾을 수 있다', () => {
    for (const item of NAV) expect(navById(item.id)).toBe(item);
  });
});
