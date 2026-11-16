/**
 * 퇴직 처리·사용증명서·보존기한 API (#18). 백엔드: backend/ RetirementController (Spring).
 * 응답 형식은 backend RetirementDtos 와 같다. 날짜는 YYYY-MM-DD.
 * 해고 예고 기간 판정은 서버가 legal_rate(해고일 기준 적용값)로 한다. 화면은 형식·필수 검사만 한다.
 */
import { call } from './http';

/** 해고 사유 코드(공통코드 RETIREMENT_REASON). 해고면 예고일·서면통지일이 필수다(근로기준법 제26조·제27조). */
export const DISMISSAL = 'DISMISSAL';

/** 근로기준법 제26조 단서 각 호. */
export type NoticeExempt = 'SHORT_SERVICE' | 'UNAVOIDABLE' | 'EMPLOYEE_FAULT';
export const EXEMPT_LABEL: Record<NoticeExempt, string> = {
  SHORT_SERVICE: '계속 근로 기간이 짧음(제26조 단서 제1호)',
  UNAVOIDABLE: '천재·사변 등 부득이한 사유(제2호)',
  EMPLOYEE_FAULT: '근로자의 고의로 막대한 지장·손해(제3호)',
};

export type NoticeCheck = 'NOT_APPLICABLE' | 'OK' | 'SHORT' | 'EXEMPT' | 'UNKNOWN';
export const NOTICE_LABEL: Record<NoticeCheck, string> = {
  NOT_APPLICABLE: '해당 없음', OK: '예고 기간 충족', SHORT: '예고 기간 미달', EXEMPT: '예고 적용 제외', UNKNOWN: '확인 불가(기준값 없음)',
};

export interface RetirementInput {
  empId: number;
  retireDate: string;
  reasonCode: string;
  reasonDetail: string | null;
  dismissalNoticeDate: string | null;
  writtenNoticeDate: string | null;
  noticeExempt: NoticeExempt | null;
}

export interface ChecklistItem {
  itemCode: string; label: string; legalBasis: string | null; done: boolean;
  doneAt: string | null; doneBy: string | null; note: string | null;
}
export interface ChecklistTemplateItem { itemCode: string; label: string; legalBasis: string | null }
export interface NoticeView {
  check: NoticeCheck; dismissalNoticeDate: string | null; writtenNoticeDate: string | null;
  noticeExempt: NoticeExempt | null; noticeDays: number | null; shortfallDays: number | null;
}
export interface Retirement {
  caseId: number; empId: number; empNo: string; empName: string; reasonCode: string; retireDate: string;
  recordRetentionUntil: string | null; notice: NoticeView; checklist: ChecklistItem[];
  /** 등록 응답에만 담긴다(예고 미달·기준값 없음 등). */
  warnings: string[]; createdBy: string; createdAt: string;
}
export interface RetentionExpiredRow {
  empId: number; empNo: string; empName: string; retireDate: string; retireReasonCode: string;
  recordRetentionUntil: string; daysOverdue: number;
}

/** 근로기준법 제39조 제1항 기재 사항. 퇴직 사유는 '그 밖에 필요한 사항'. */
export type CertificateItem = 'USE_PERIOD' | 'JOB_TYPE' | 'POSITION' | 'WAGE' | 'RETIRE_REASON';
export const CERT_ITEMS: CertificateItem[] = ['USE_PERIOD', 'JOB_TYPE', 'POSITION', 'WAGE', 'RETIRE_REASON'];
export const CERT_ITEM_LABEL: Record<CertificateItem, string> = {
  USE_PERIOD: '사용 기간', JOB_TYPE: '업무 종류', POSITION: '지위(직급·직책)', WAGE: '임금', RETIRE_REASON: '퇴직 사유',
};
export const WAGE_UNIT_LABEL: Record<string, string> = { HOURLY: '시급', DAILY: '일급', WEEKLY: '주급', MONTHLY: '월급', ANNUAL: '연봉' };

export interface CertificateInput { requestedOn: string; items: CertificateItem[]; purpose: string | null }
export interface Certificate {
  certId: number; empId: number; empNo: string; requestedOn: string; items: CertificateItem[]; purpose: string | null;
  empName: string; useFrom: string | null; useTo: string | null; jobType: string | null; positionText: string | null;
  wageAmount: number | null; wageUnitCode: string | null; retireReason: string | null; issuedBy: string; issuedAt: string;
}

export interface RetirementApi {
  template(reasonCode: string): Promise<ChecklistTemplateItem[]>;
  register(input: RetirementInput): Promise<Retirement>;
  get(empId: number): Promise<Retirement>;
  mark(empId: number, itemCode: string, done: boolean, note: string | null): Promise<Retirement>;
  retentionExpired(asOf: string): Promise<RetentionExpiredRow[]>;
  issueCertificate(empId: number, input: CertificateInput): Promise<Certificate>;
}

export const retirementApi: RetirementApi = {
  template: (reasonCode) => call('GET', `/retirements/checklist-template?reasonCode=${encodeURIComponent(reasonCode)}`),
  register: (input) => call('POST', '/retirements', input),
  get: (empId) => call('GET', `/retirements/${empId}`),
  mark: (empId, itemCode, done, note) =>
    call('PUT', `/retirements/${empId}/checklist/${encodeURIComponent(itemCode)}`, { done, note }),
  retentionExpired: (asOf) => call('GET', `/retirements/retention-expired?asOf=${encodeURIComponent(asOf)}`),
  issueCertificate: (empId, input) => call('POST', `/employees/${empId}/employment-certificates`, input),
};

const DATE = /^\d{4}-\d{2}-\d{2}$/;

/** 퇴직 처리 입력 형식 검사. 문제가 없으면 null. 예고 기간 판정은 서버가 한다. */
export function validateRetirement(i: RetirementInput): string | null {
  if (!Number.isInteger(i.empId) || i.empId <= 0) return '사원 ID(숫자)를 입력해 주세요';
  if (!DATE.test(i.retireDate)) return '퇴직일을 입력해 주세요';
  if (!i.reasonCode) return '퇴직 유형을 선택해 주세요';
  if (i.reasonCode === DISMISSAL) {
    if (!i.dismissalNoticeDate) return '해고는 해고 예고일을 입력해야 해요(근로기준법 제26조)';
    if (!i.writtenNoticeDate) return '해고는 해고사유·해고시기 서면통지일을 입력해야 해요(근로기준법 제27조)';
    if (i.dismissalNoticeDate > i.retireDate) return '해고 예고일은 해고일(퇴직일)보다 늦을 수 없어요';
    if (i.writtenNoticeDate > i.retireDate) return '서면통지일은 해고일(퇴직일)보다 늦을 수 없어요';
  }
  return null;
}

/** 서버로 보낼 값: 해고가 아니면 예고 항목을 비운다. */
export function toRetirementBody(i: RetirementInput): RetirementInput {
  const dismissal = i.reasonCode === DISMISSAL;
  return {
    ...i,
    reasonDetail: i.reasonDetail?.trim() ? i.reasonDetail.trim() : null,
    dismissalNoticeDate: dismissal ? i.dismissalNoticeDate : null,
    writtenNoticeDate: dismissal ? i.writtenNoticeDate : null,
    noticeExempt: dismissal ? i.noticeExempt : null,
  };
}

/** 금액(원 단위 정수) 표시. */
export const won = (n: number) => `${n.toLocaleString('ko-KR')}원`;
