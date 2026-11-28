import { describe, expect, it } from 'vitest';
import { MODULES, NAV, navById, navFor, navGroupsFor, moduleOf } from './nav';
import { PAGE_IDS } from './state/store';
import type { Role } from './api/types';

const ROLES: Role[] = ['EMPLOYEE', 'HR', 'ADMIN'];

describe('NAV', () => {
  it('메뉴 id가 겹치지 않는다', () => {
    const ids = NAV.map((n) => n.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('navById로 각 메뉴를 찾을 수 있다', () => {
    for (const item of NAV) expect(navById(item.id)).toBe(item);
  });

  it('모든 화면이 메뉴에 있고, 모든 메뉴가 있는 대메뉴에 속한다', () => {
    expect(NAV.map((n) => n.id).sort()).toEqual([...PAGE_IDS].sort());
    const modules = new Set(MODULES.map((m) => m.id));
    for (const n of NAV) expect(modules.has(n.module)).toBe(true);
  });
});

describe('대메뉴 → 중메뉴', () => {
  it('같은 모듈끼리 묶는다 (예: 육아휴직과 근태 · 휴가는 근태관리)', () => {
    expect(moduleOf('familyLeaves')).toBe('attendance');
    expect(moduleOf('attendance')).toBe('attendance');
    expect(moduleOf('appointments')).toBe('hr');
    expect(moduleOf('approvalInbox')).toBe('approval');
  });

  it('중메뉴마다 역할 권한을 적용한다', () => {
    for (const role of ROLES) {
      const inGroups = navGroupsFor(role).flatMap((g) => g.items.map((n) => n.id));
      expect(inGroups.sort()).toEqual(navFor(role).map((n) => n.id).sort());
    }
    const hrAttendance = navGroupsFor('HR').find((g) => g.module.id === 'attendance')!;
    expect(hrAttendance.items.map((n) => n.id)).toContain('familyLeaves');
    const empAttendance = navGroupsFor('EMPLOYEE').find((g) => g.module.id === 'attendance')!;
    expect(empAttendance.items.map((n) => n.id)).not.toContain('familyLeaves');
  });

  it('볼 수 있는 중메뉴가 없는 대메뉴는 숨긴다', () => {
    expect(navGroupsFor('EMPLOYEE').map((g) => g.module.id)).not.toContain('system');
    const hrSystem = navGroupsFor('HR').find((g) => g.module.id === 'system')!;
    expect(hrSystem.items.map((n) => n.id)).toEqual(['codes', 'enterSettings']);
    const adminSystem = navGroupsFor('ADMIN').find((g) => g.module.id === 'system')!;
    expect(adminSystem.items.map((n) => n.id)).toEqual(['codes', 'enterSettings', 'privacyLogs']);
  });

  it('대메뉴 순서는 MODULES 순서를 따른다', () => {
    const order = MODULES.map((m) => m.id);
    const ids = navGroupsFor('ADMIN').map((g) => g.module.id);
    expect(ids).toEqual(order.filter((id) => ids.includes(id)));
  });
});
