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

export const navById = (id: PageId) => NAV.find((n) => n.id === id)!;
