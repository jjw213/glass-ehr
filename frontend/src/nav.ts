import type { IconType } from 'react-icons';
import { LuHouse, LuClock, LuWallet, LuFileCheck, LuMessageCircle, LuUsers, LuNetwork, LuSettings, LuListTree, LuCalendarClock, LuCalendarDays, LuShieldCheck, LuSignature, LuDoorOpen, LuArrowLeftRight, LuTimer, LuInbox, LuBaby, LuCalendarCheck, LuBriefcase, LuWrench, LuSlidersHorizontal, LuBuilding2 } from 'react-icons/lu';
import type { Role } from './api/types';
import type { PageId } from './state/store';

/** 대메뉴(모듈). 사이드바·독은 이 순서로 대메뉴를 보여 주고, 그 아래에 중메뉴(NavItem)를 묶는다. */
export type ModuleId = 'home' | 'hr' | 'attendance' | 'payroll' | 'approval' | 'messages' | 'system' | 'settings';

export interface NavModule {
  id: ModuleId;
  label: string;
  icon: IconType;
}

export const MODULES: NavModule[] = [
  { id: 'home', label: '홈', icon: LuHouse },
  { id: 'hr', label: '인사관리', icon: LuBriefcase },
  { id: 'attendance', label: '근태관리', icon: LuClock },
  { id: 'payroll', label: '급여관리', icon: LuWallet },
  { id: 'approval', label: '전자결재', icon: LuFileCheck },
  { id: 'messages', label: '메시지', icon: LuMessageCircle },
  { id: 'system', label: '시스템관리', icon: LuWrench },
  { id: 'settings', label: '설정', icon: LuSettings },
];

export interface NavItem {
  id: PageId;
  label: string;
  short: string;
  icon: IconType;
  /** 이 중메뉴가 속한 대메뉴 */
  module: ModuleId;
  /** shown as a pill in the desktop top bar */
  top?: boolean;
  /** only these roles see the menu (unset = everyone) */
  roles?: Role[];
  /** 이 서버 권한이 있는 사용자만 메뉴를 본다(roles 와 함께 적용). 예: 그룹 관리자 ENTER_COMPANY_MANAGE */
  permission?: string;
}

/** Single source of truth for desktop top bar, desktop dock and mobile sidebar. 같은 대메뉴 안에서는 이 순서대로 보인다. */
export const NAV: NavItem[] = [
  { id: 'home', label: '홈', short: '홈', icon: LuHouse, module: 'home', top: true },
  // 인사관리
  { id: 'people', label: '인사정보', short: '인사', icon: LuUsers, module: 'hr' },
  { id: 'org', label: '조직도', short: '조직', icon: LuNetwork, module: 'hr' },
  { id: 'appointments', label: '인사발령', short: '발령', icon: LuArrowLeftRight, module: 'hr', roles: ['HR', 'ADMIN'] },
  { id: 'retirements', label: '퇴직 처리', short: '퇴직', icon: LuDoorOpen, module: 'hr', roles: ['HR', 'ADMIN'] },
  // 근태관리
  { id: 'attendance', label: '근태 · 휴가', short: '근태', icon: LuClock, module: 'attendance', top: true },
  { id: 'overtime', label: '연장·휴일근로 신청', short: '연장근로', icon: LuTimer, module: 'attendance' },
  { id: 'flexWork', label: '근무계획', short: '근무계획', icon: LuCalendarCheck, module: 'attendance' },
  { id: 'holidays', label: '휴일 달력', short: '휴일', icon: LuCalendarDays, module: 'attendance' },
  { id: 'familyLeaves', label: '육아·가족돌봄 휴직', short: '육아휴직', icon: LuBaby, module: 'attendance', roles: ['HR', 'ADMIN'] },
  { id: 'workPatterns', label: '근무유형 관리', short: '근무유형', icon: LuCalendarClock, module: 'attendance', roles: ['HR', 'ADMIN'] },
  { id: 'agreements', label: '서면합의서 관리', short: '합의서', icon: LuSignature, module: 'attendance', roles: ['HR', 'ADMIN'] },
  { id: 'attendanceCodes', label: '근태코드 관리', short: '근태코드', icon: LuListTree, module: 'attendance', roles: ['HR', 'ADMIN'] },
  // 급여관리
  { id: 'payroll', label: '급여명세서', short: '급여', icon: LuWallet, module: 'payroll', top: true },
  // 전자결재
  { id: 'approvals', label: '전자결재', short: '결재', icon: LuFileCheck, module: 'approval', top: true },
  { id: 'approvalInbox', label: '결재함', short: '결재함', icon: LuInbox, module: 'approval' },
  // 단독 메뉴
  { id: 'messages', label: '메시지', short: '메시지', icon: LuMessageCircle, module: 'messages' },
  // 시스템관리
  { id: 'codes', label: '공통코드 관리', short: '코드', icon: LuListTree, module: 'system', roles: ['HR', 'ADMIN'] },
  { id: 'enterSettings', label: '회사 설정', short: '회사설정', icon: LuSlidersHorizontal, module: 'system', roles: ['HR', 'ADMIN'] },
  { id: 'enterCompanies', label: '법인 관리', short: '법인', icon: LuBuilding2, module: 'system', permission: 'ENTER_COMPANY_MANAGE' },
  { id: 'privacyLogs', label: '개인정보 조회 로그', short: '로그', icon: LuShieldCheck, module: 'system', roles: ['ADMIN'] },
  { id: 'settings', label: '설정', short: '설정', icon: LuSettings, module: 'settings' },
];

/** Menus the signed-in role (and server permissions) may see. */
export const navFor = (role: Role, permissions: readonly string[] = []) =>
  NAV.filter((n) => (!n.roles || n.roles.includes(role)) && (!n.permission || permissions.includes(n.permission)));

/** 법인 관리(목록·추가·사용 중지)를 하는 그룹 관리자(#130). 메뉴 표시용이며 실제 차단은 서버 ENTER_COMPANY_MANAGE 검사가 한다. */
export const canManageEnterCompanies = (permissions: readonly string[]) => permissions.includes('ENTER_COMPANY_MANAGE');

/** 공통코드를 관리하는 역할(인사담당자·관리자). */
export const canManageCodes = (role: Role) => navFor(role).some((n) => n.id === 'codes');

/** 근태코드를 관리하는 역할(인사담당자·관리자, #143). 쓰기 API 는 서버가 CODE_WRITE 로 다시 막는다. */
export const canManageAttendanceCodes = (role: Role) => navFor(role).some((n) => n.id === 'attendanceCodes');

/** 그 법인의 회사 설정(상시근로자 수 등)을 관리하는 역할(인사담당자·관리자, #131). 실제 차단은 서버 EMP_WRITE 검사가 한다. */
export const canManageEnterSettings = (role: Role) => navFor(role).some((n) => n.id === 'enterSettings');

/** 근무유형을 관리하는 역할(인사담당자·관리자, #25). 화면에서만 막는다. 백엔드 권한 검사는 인증·권한 이슈에서 붙인다. */
export const canManageWorkPatterns = (role: Role) => navFor(role).some((n) => n.id === 'workPatterns');

/** 근로자대표 서면합의서를 관리하는 역할(인사담당자·관리자, #27). 메뉴 표시용이며 실제 차단은 서버 EMP_WRITE·EMP_READ_ALL 검사가 한다. */
export const canManageAgreements = (role: Role) => navFor(role).some((n) => n.id === 'agreements');

/**
 * 휴일 달력을 관리(공휴일 자료 불러오기·회사휴일·휴일 대체)하는 역할(#26). 달력 보기는 모두 가능.
 * 화면 표시용이며 실제 차단은 서버 EMP_WRITE 권한 검사가 한다. 근무유형 관리와 같은 역할(인사담당자·관리자).
 */
export const canManageHolidays = (role: Role) => canManageWorkPatterns(role);

/** 퇴직 처리·사용증명서·보존기한 목록을 다루는 역할(인사담당자·관리자, #18). 메뉴 표시용이며 실제 차단은 서버 EMP_WRITE·EMP_READ_ALL 검사가 한다. */
export const canManageRetirements = (role: Role) => navFor(role).some((n) => n.id === 'retirements');

/** 육아휴직·근로시간 단축·가족돌봄휴직 신청을 입력·승인하는 역할(인사담당자·관리자, #37). 메뉴 표시용이며 실제 차단은 서버 EMP_WRITE·EMP_READ_ALL 검사가 한다. */
export const canManageFamilyLeaves = (role: Role) => navFor(role).some((n) => n.id === 'familyLeaves');

/** 인사발령 등록·확정·취소·일괄 발령을 하는 역할(인사담당자·관리자, #60). 메뉴 표시용이며 실제 차단은 서버 EMP_WRITE·EMP_READ_ALL 검사가 한다. */
export const canManageAppointments = (role: Role) => navFor(role).some((n) => n.id === 'appointments');

/** 개인정보 조회 로그를 보는 역할(관리자, #19). 메뉴 표시용이며 실제 차단은 서버 PRIVACY_LOG_READ 권한 검사가 한다. */
export const canReadPrivacyLogs = (role: Role) => navFor(role).some((n) => n.id === 'privacyLogs');

/**
 * 조직도에서 부서를 추가·이동할 수 있는 역할(인사담당자·관리자, #13). 일반 사원은 조회만.
 * 화면에서만 막는 것이다. 백엔드 쓰기 권한 검사는 인증·권한 이슈에서 붙인다(docs/org-history-design.md 6장).
 */
export const ORG_MANAGER_ROLES: readonly Role[] = ['HR', 'ADMIN'];
export const canManageOrg = (role: Role) => ORG_MANAGER_ROLES.includes(role);

export const navById = (id: PageId) => NAV.find((n) => n.id === id)!;

export interface NavGroup {
  module: NavModule;
  /** 이 역할이 볼 수 있는 중메뉴만. 하나뿐이면 대메뉴를 누르면 바로 그 화면으로 간다. */
  items: NavItem[];
}

/** 역할별 대메뉴 → 중메뉴 묶음. 중메뉴마다 roles 를 적용하고, 볼 수 있는 중메뉴가 없는 대메뉴는 뺀다. */
export const navGroupsFor = (role: Role, permissions: readonly string[] = []): NavGroup[] => {
  const items = navFor(role, permissions);
  return MODULES
    .map((module) => ({ module, items: items.filter((n) => n.module === module.id) }))
    .filter((g) => g.items.length > 0);
};

/** 화면이 속한 대메뉴 */
export const moduleOf = (id: PageId): ModuleId => navById(id).module;
