import type { IconType } from 'react-icons';
import { LuHouse, LuClock, LuWallet, LuFileCheck, LuMessageCircle, LuUsers, LuNetwork, LuSettings, LuListTree } from 'react-icons/lu';
import type { Role } from './api/types';
import type { PageId } from './state/store';

export interface NavItem {
  id: PageId;
  label: string;
  short: string;
  icon: IconType;
  section: 'main' | 'workspace' | 'system';
  /** shown as a pill in the desktop top bar */
  top?: boolean;
  /** only these roles see the menu (unset = everyone) */
  roles?: Role[];
}

/** Single source of truth for desktop top bar, desktop dock and mobile sidebar. */
export const NAV: NavItem[] = [
  { id: 'home', label: '홈', short: '홈', icon: LuHouse, section: 'main', top: true },
  { id: 'attendance', label: '근태 · 휴가', short: '근태', icon: LuClock, section: 'main', top: true },
  { id: 'payroll', label: '급여명세서', short: '급여', icon: LuWallet, section: 'main', top: true },
  { id: 'approvals', label: '전자결재', short: '결재', icon: LuFileCheck, section: 'main', top: true },
  { id: 'messages', label: '메시지', short: '메시지', icon: LuMessageCircle, section: 'workspace' },
  { id: 'people', label: '인사정보', short: '인사', icon: LuUsers, section: 'workspace' },
  { id: 'org', label: '조직도', short: '조직', icon: LuNetwork, section: 'workspace' },
  { id: 'settings', label: '설정', short: '설정', icon: LuSettings, section: 'system' },
  { id: 'codes', label: '공통코드 관리', short: '코드', icon: LuListTree, section: 'system', roles: ['HR', 'ADMIN'] },
];

/** Menus the signed-in role may see. */
export const navFor = (role: Role) => NAV.filter((n) => !n.roles || n.roles.includes(role));

/** 공통코드를 관리하는 역할(인사담당자·관리자). */
export const canManageCodes = (role: Role) => navFor(role).some((n) => n.id === 'codes');

/**
 * 조직도에서 부서를 추가·이동할 수 있는 역할(인사담당자·관리자, #13). 일반 사원은 조회만.
 * 화면에서만 막는 것이다. 백엔드 쓰기 권한 검사는 인증·권한 이슈에서 붙인다(docs/org-history-design.md 6장).
 */
export const ORG_MANAGER_ROLES: readonly Role[] = ['HR', 'ADMIN'];
export const canManageOrg = (role: Role) => ORG_MANAGER_ROLES.includes(role);

export const navById = (id: PageId) => NAV.find((n) => n.id === id)!;
