/**
 * 연장·휴일근로 사전 신청(#30)과 공통 결재함 API. 백엔드: backend/ OvertimeController·ApprovalController (Spring).
 * 응답 형식은 backend OvertimeDtos·ApprovalDtos 와 같다. 시각은 Asia/Seoul 로컬 시각 'YYYY-MM-DDTHH:mm(:ss)'.
 *
 * - 신청은 시작 시각이 지금 이후인 사전 신청만 받는다(서버가 다시 검사).
 * - 마지막 결재자가 승인할 때 서버가 1주 연장근로 한도(근로기준법 제53조 제1항, legal_rate)를 검사한다.
 *   한도를 넘으면 승인 자체가 되돌려지고 오류 메시지가 온다. 화면은 한도를 계산하지 않는다.
 */
import { call } from './http';

export type WorkKind = 'OVERTIME' | 'HOLIDAY';
export type RequestStatus = 'IN_PROGRESS' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN';
export type StepStatus = 'WAITING' | 'APPROVED' | 'REJECTED' | 'SKIPPED';
export type LogAction = 'SUBMIT' | 'SKIP' | 'APPROVE' | 'REJECT' | 'WITHDRAW';

export const WORK_KIND_LABEL: Record<WorkKind, string> = { OVERTIME: '연장근로', HOLIDAY: '휴일근로' };
export const STATUS_LABEL: Record<RequestStatus, string> = { IN_PROGRESS: '결재 중', APPROVED: '승인', REJECTED: '반려', WITHDRAWN: '회수' };
export const STEP_LABEL: Record<StepStatus, string> = { WAITING: '대기', APPROVED: '승인', REJECTED: '반려', SKIPPED: '건너뜀' };
export const ACTION_LABEL: Record<LogAction, string> = { SUBMIT: '상신', SKIP: '건너뜀', APPROVE: '승인', REJECT: '반려', WITHDRAW: '회수' };
export const STATUS_TONE: Record<RequestStatus, 'blue' | 'green' | 'red' | 'neutral'> = {
  IN_PROGRESS: 'blue', APPROVED: 'green', REJECTED: 'red', WITHDRAWN: 'neutral',
};
export const REASON_MAX = 500;

export interface OvertimeInput {
  workKind: WorkKind;
  /** 'YYYY-MM-DDTHH:mm' (Asia/Seoul) */
  startAt: string;
  endAt: string;
  breakMinutes: number;
  reason: string;
}

export interface OvertimeRequestView {
  requestId: number;
  empId: number;
  workKind: WorkKind;
  workDate: string;
  startAt: string;
  endAt: string;
  breakMinutes: number;
  plannedMinutes: number;
  reason: string;
  status: RequestStatus;
  requestedAt: string;
  decidedAt: string | null;
  approvalId: number | null;
}

export interface PersonView { empId: number; name: string }

export interface DocSummary {
  approvalId: number;
  requestType: string;
  requestTypeLabel: string;
  requestRef: number;
  title: string;
  requester: PersonView;
  status: RequestStatus;
  currentStepNo: number | null;
  stepCount: number;
  submittedAt: string;
  completedAt: string | null;
}

export interface StepView {
  stepNo: number;
  approverKind: string;
  approverRef: string | null;
  status: StepStatus;
  approvers: PersonView[];
  actedBy: PersonView | null;
  actedAt: string | null;
  comment: string | null;
}

export interface LogView {
  actionId: number;
  stepNo: number | null;
  action: LogAction;
  actor: PersonView;
  actedAt: string;
  comment: string | null;
}

export interface DocDetail {
  summary: DocSummary;
  requestDetail: OvertimeRequestView | null;
  steps: StepView[];
  history: LogView[];
  canAct: boolean;
  canWithdraw: boolean;
}

export type Box = 'inbox' | 'processed' | 'mine';
export const BOX_LABEL: Record<Box, string> = { inbox: '결재할 문서', processed: '처리한 문서', mine: '내가 올린 문서' };

export interface OvertimeApi {
  mine(): Promise<OvertimeRequestView[]>;
  create(input: OvertimeInput): Promise<OvertimeRequestView>;
}

export interface ApprovalApi {
  list(box: Box): Promise<DocSummary[]>;
  detail(approvalId: number): Promise<DocDetail>;
  approve(approvalId: number, comment: string): Promise<DocDetail>;
  reject(approvalId: number, comment: string): Promise<DocDetail>;
  withdraw(approvalId: number): Promise<DocDetail>;
}

export const httpOvertimeApi: OvertimeApi = {
  mine: () => call('GET', '/overtime-requests/mine'),
  create: (input) => call('POST', '/overtime-requests', toOvertimeBody(input)),
};

export const httpApprovalApi: ApprovalApi = {
  list: (box) => call('GET', `/approvals/${box}`),
  detail: (id) => call('GET', `/approvals/${id}`),
  approve: (id, comment) => call('POST', `/approvals/${id}/approve`, { comment: comment.trim() || null }),
  reject: (id, comment) => call('POST', `/approvals/${id}/reject`, { comment: comment.trim() }),
  withdraw: (id) => call('POST', `/approvals/${id}/withdraw`),
};

// ---- 입력 검사·표시 ----

/** 두 로컬 시각 'YYYY-MM-DDTHH:mm' 사이 분. 시간대와 무관하게 같은 기준으로 읽는다. */
export const minutesBetween = (from: string, to: string) => Math.round((Date.parse(to + 'Z') - Date.parse(from + 'Z')) / 60000);

/** 예정 근로분 = 종료 − 시작 − 휴게(서버 CHECK 와 같은 식). */
export const plannedMinutes = (i: Pick<OvertimeInput, 'startAt' | 'endAt' | 'breakMinutes'>) =>
  minutesBetween(i.startAt, i.endAt) - i.breakMinutes;

/** 지금 Asia/Seoul 로컬 시각 'YYYY-MM-DDTHH:mm'. */
export function seoulNowLocal(now: Date = new Date()): string {
  const p = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(now).map((x) => [x.type, x.value]));
  return `${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}`;
}

/** 화면 입력 검사. 오류 문구 또는 null. nowLocal 은 seoulNowLocal() 값. */
export function validateOvertime(i: OvertimeInput, nowLocal: string): string | null {
  if (!i.startAt || !i.endAt) return '시작·종료 시각을 입력해 주세요';
  if (i.startAt.slice(0, 16) <= nowLocal) return '사전 신청만 할 수 있어요. 시작 시각을 지금 이후로 정해 주세요';
  if (i.endAt <= i.startAt) return '종료 시각은 시작 시각 뒤여야 해요';
  if (!Number.isInteger(i.breakMinutes) || i.breakMinutes < 0) return '휴게 시간은 0분 이상 정수로 입력해 주세요';
  if (plannedMinutes(i) <= 0) return '휴게 시간을 뺀 근로시간이 없어요';
  if (!i.reason.trim()) return '사유를 입력해 주세요';
  if (i.reason.trim().length > REASON_MAX) return `사유는 ${REASON_MAX}자 이내로 써 주세요`;
  return null;
}

export const toOvertimeBody = (i: OvertimeInput) => ({
  workKind: i.workKind, startAt: i.startAt, endAt: i.endAt, breakMinutes: i.breakMinutes, reason: i.reason.trim(),
});

/** 분 → "2시간 30분" */
export function hm(min: number): string {
  const h = Math.floor(min / 60);
  const m = min % 60;
  return h === 0 ? `${m}분` : m === 0 ? `${h}시간` : `${h}시간 ${m}분`;
}

/** 'YYYY-MM-DDTHH:mm:ss' → 'MM-DD HH:mm' */
export const shortDateTime = (s: string | null) => (s ? `${s.slice(5, 10)} ${s.slice(11, 16)}` : '');

// ---- 메모리 구현(VITE_API=mock 데모·테스트). 신청하면 결재 1단계 문서를 만들고, 같은 사용자가 결재할 수 있다. ----

export function createMockApis(me: PersonView = { empId: 1, name: '데모 사원' }) {
  const requests: OvertimeRequestView[] = [];
  const docs: DocDetail[] = [];
  let seq = 1;
  const stamp = () => seoulNowLocal() + ':00';
  const find = (id: number) => {
    const d = docs.find((x) => x.summary.approvalId === id);
    if (!d) throw new Error('결재 문서를 찾을 수 없어요');
    return d;
  };
  const finish = (d: DocDetail, status: RequestStatus, action: LogAction, comment: string | null) => {
    const at = stamp();
    d.summary = { ...d.summary, status, currentStepNo: null, completedAt: at };
    if (action !== 'WITHDRAW') d.steps[0] = { ...d.steps[0], status: status === 'APPROVED' ? 'APPROVED' : 'REJECTED', actedBy: me, actedAt: at, comment };
    d.history.push({ actionId: seq++, stepNo: action === 'WITHDRAW' ? null : 1, action, actor: me, actedAt: at, comment });
    d.canAct = false;
    d.canWithdraw = false;
    const r = requests.find((x) => x.approvalId === d.summary.approvalId);
    if (r) { r.status = status; r.decidedAt = at; }
    return structuredClone(d);
  };
  const overtime: OvertimeApi = {
    mine: async () => structuredClone(requests).reverse(),
    create: async (input) => {
      const err = validateOvertime(input, seoulNowLocal());
      if (err) throw new Error(err);
      const at = stamp();
      const approvalId = seq++;
      const r: OvertimeRequestView = {
        requestId: seq++, empId: me.empId, workKind: input.workKind, workDate: input.startAt.slice(0, 10), startAt: input.startAt + ':00',
        endAt: input.endAt + ':00', breakMinutes: input.breakMinutes, plannedMinutes: plannedMinutes(input), reason: input.reason.trim(),
        status: 'IN_PROGRESS', requestedAt: at, decidedAt: null, approvalId,
      };
      requests.push(r);
      docs.push({
        summary: {
          approvalId, requestType: 'OVERTIME', requestTypeLabel: '연장·휴일근로', requestRef: r.requestId,
          title: `${WORK_KIND_LABEL[r.workKind]} ${r.workDate} ${hm(r.plannedMinutes)}`, requester: me, status: 'IN_PROGRESS',
          currentStepNo: 1, stepCount: 1, submittedAt: at, completedAt: null,
        },
        requestDetail: structuredClone(r),
        steps: [{ stepNo: 1, approverKind: 'DEPT_HEAD', approverRef: null, status: 'WAITING', approvers: [me], actedBy: null, actedAt: null, comment: null }],
        history: [{ actionId: seq++, stepNo: null, action: 'SUBMIT', actor: me, actedAt: at, comment: null }],
        canAct: true, canWithdraw: true,
      });
      return structuredClone(r);
    },
  };
  const approvals: ApprovalApi = {
    list: async (box) => docs
      .filter((d) => (box === 'inbox' ? d.canAct : box === 'mine' ? d.summary.requester.empId === me.empId : d.history.some((h) => h.actor.empId === me.empId && (h.action === 'APPROVE' || h.action === 'REJECT'))))
      .map((d) => structuredClone(d.summary)).reverse(),
    detail: async (id) => structuredClone(find(id)),
    approve: async (id, comment) => finish(find(id), 'APPROVED', 'APPROVE', comment.trim() || null),
    reject: async (id, comment) => {
      if (!comment.trim()) throw new Error('반려 사유를 입력해 주세요');
      return finish(find(id), 'REJECTED', 'REJECT', comment.trim());
    },
    withdraw: async (id) => finish(find(id), 'WITHDRAWN', 'WITHDRAW', null),
  };
  return { overtime, approvals };
}

const mock = import.meta.env.VITE_API === 'mock' ? createMockApis() : null;
export const overtimeApi: OvertimeApi = mock ? mock.overtime : httpOvertimeApi;
export const approvalApi: ApprovalApi = mock ? mock.approvals : httpApprovalApi;
