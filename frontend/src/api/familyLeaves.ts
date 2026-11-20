/**
 * 육아휴직·육아기 근로시간 단축·가족돌봄휴직 API (#37). 백엔드: backend/ FamilyLeaveController (Spring).
 * 응답 형식은 backend FamilyLeaveDtos 와 같다. 날짜는 YYYY-MM-DD.
 * 기간·분할 횟수·주 근로시간·자격 판정은 서버가 legal_rate(시작일 기준 적용값)로 한다. 화면은 형식·필수 검사만 한다.
 */
import { call } from './http';

/** 남녀고용평등법 제19조(육아휴직)·제19조의2(육아기 근로시간 단축)·제22조의2(가족돌봄휴직). */
export type LeaveKind = 'PARENTAL_LEAVE' | 'CHILDCARE_REDUCED_HOURS' | 'FAMILY_CARE_LEAVE';
export const KIND_LABEL: Record<LeaveKind, string> = {
  PARENTAL_LEAVE: '육아휴직(제19조)',
  CHILDCARE_REDUCED_HOURS: '육아기 근로시간 단축(제19조의2)',
  FAMILY_CARE_LEAVE: '가족돌봄휴직(제22조의2)',
};

export type LeaveStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
export const STATUS_LABEL: Record<LeaveStatus, string> = {
  REQUESTED: '승인 대기', APPROVED: '승인', REJECTED: '반려', CANCELLED: '취소',
};

export interface FamilyLeaveInput {
  kind: LeaveKind;
  /** 자녀 구분(예: 첫째). 실명은 넣지 않는다. */
  childLabel: string | null;
  childBirthDate: string | null;
  familyRelation: string | null;
  startDate: string;
  endDate: string;
  workPatternId: number | null;
  /** 제19조 제2항 추가 육아휴직 요건을 인사 담당자가 확인했을 때만 true */
  extension: boolean;
  reason: string;
}

export interface FamilyLeave extends FamilyLeaveInput {
  reqId: number; empId: number; status: LeaveStatus; warnings: string[];
  requestedBy: string; requestedAt: string; decidedBy: string | null; decidedAt: string | null; decisionNote: string | null;
  leaveApptId: number | null; returnApptId: number | null; reducedAssignId: number | null; restoreAssignId: number | null;
}

/** 개월 한도 사용 현황. 남은 기간 = remainingMonths개월 - remainingLessDays일. 기준값이 없으면 limit·remaining 이 null. */
export interface MonthUsage {
  limitMonths: number | null; usedMonths: number; usedDays: number;
  remainingMonths: number | null; remainingLessDays: number; uses: number; usesMax: number | null;
}
export interface ChildBalance { childLabel: string; parentalLeave: MonthUsage; reducedHours: MonthUsage }
export interface YearUsage { year: number; limitDays: number | null; usedDays: number; remainingDays: number | null }
export interface Balance { empId: number; asOf: string; children: ChildBalance[]; familyCareLeave: YearUsage; notes: string[] }

export interface FamilyLeaveApi {
  create(empId: number, input: FamilyLeaveInput): Promise<FamilyLeave>;
  listByEmployee(empId: number): Promise<FamilyLeave[]>;
  search(status: LeaveStatus | null): Promise<FamilyLeave[]>;
  balance(empId: number, asOf: string): Promise<Balance>;
  approve(reqId: number): Promise<FamilyLeave>;
  reject(reqId: number, note: string): Promise<FamilyLeave>;
  cancel(reqId: number, note: string | null): Promise<FamilyLeave>;
}

export const familyLeaveApi: FamilyLeaveApi = {
  create: (empId, input) => call('POST', `/employees/${empId}/family-leaves`, input),
  listByEmployee: (empId) => call('GET', `/employees/${empId}/family-leaves`),
  search: (status) => call('GET', `/family-leaves${status ? `?status=${status}` : ''}`),
  balance: (empId, asOf) => call('GET', `/employees/${empId}/family-leave-balance?asOf=${encodeURIComponent(asOf)}`),
  approve: (reqId) => call('POST', `/family-leaves/${reqId}/approve`),
  reject: (reqId, note) => call('POST', `/family-leaves/${reqId}/reject`, { note }),
  cancel: (reqId, note) => call('POST', `/family-leaves/${reqId}/cancel`, { note }),
};

const DATE = /^\d{4}-\d{2}-\d{2}$/;
const blank = (s: string | null) => !s || !s.trim();

/** 신청 입력 형식 검사. 문제가 없으면 null. 한도·자격은 서버가 판정한다. */
export function validateFamilyLeave(empId: number, i: FamilyLeaveInput): string | null {
  if (!Number.isInteger(empId) || empId <= 0) return '사원 ID(숫자)를 입력해 주세요';
  if (!DATE.test(i.startDate) || !DATE.test(i.endDate)) return '시작일과 종료일을 입력해 주세요';
  if (i.endDate < i.startDate) return '종료일은 시작일 이후여야 해요';
  if (i.kind === 'FAMILY_CARE_LEAVE') {
    if (blank(i.familyRelation)) return '돌볼 가족의 관계를 입력해 주세요';
  } else if (blank(i.childLabel)) {
    return '자녀 구분(예: 첫째)을 입력해 주세요. 실명은 넣지 않아요';
  }
  if (i.kind === 'CHILDCARE_REDUCED_HOURS') {
    if (!i.childBirthDate || !DATE.test(i.childBirthDate)) return '자녀 생년월일을 입력해 주세요';
    if (i.workPatternId == null) return '단축 기간에 쓸 근무유형을 골라 주세요';
  }
  if (blank(i.reason)) return '신청 사유를 입력해 주세요';
  return null;
}

/** 서버로 보낼 값: 종류에 맞지 않는 항목은 비운다(서버는 섞인 값을 거부한다). */
export function toFamilyLeaveBody(i: FamilyLeaveInput): FamilyLeaveInput {
  const care = i.kind === 'FAMILY_CARE_LEAVE';
  const reduced = i.kind === 'CHILDCARE_REDUCED_HOURS';
  return {
    ...i,
    childLabel: care ? null : i.childLabel?.trim() || null,
    childBirthDate: care ? null : i.childBirthDate || null,
    familyRelation: care ? i.familyRelation?.trim() || null : null,
    workPatternId: reduced ? i.workPatternId : null,
    extension: i.kind === 'PARENTAL_LEAVE' && i.extension,
    reason: i.reason.trim(),
  };
}

/** 남은 기간 표시. 기준값이 없으면 '확인 불가'. */
export function remainingText(u: MonthUsage): string {
  if (u.remainingMonths == null) return '확인 불가(기준값 없음)';
  return u.remainingLessDays > 0 ? `${u.remainingMonths}개월에서 ${u.remainingLessDays}일 뺀 기간` : `${u.remainingMonths}개월`;
}

export const usedText = (u: MonthUsage) => `${u.usedMonths}개월${u.usedDays > 0 ? ` ${u.usedDays}일` : ''}`;
