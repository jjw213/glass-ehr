import type { IconType } from 'react-icons';
import { LuHouse, LuClock, LuWallet, LuFileCheck, LuMessageCircle, LuUsers, LuNetwork, LuSettings } from 'react-icons/lu';
import type { PageId } from './state/store';

export interface NavItem {
  id: PageId;
  label: string;
  short: string;
  icon: IconType;
  section: 'main' | 'workspace' | 'system';
  /** shown as a pill in the desktop top bar */
  top?: boolean;
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
];

export const navById = (id: PageId) => NAV.find((n) => n.id === id)!;
