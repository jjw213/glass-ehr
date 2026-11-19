/**
 * 인사발령 API (#59 백엔드, #60 화면·일괄 발령). 백엔드: backend/ AppointmentController (Spring).
 * 응답 형식은 backend AppointmentDtos 와 같다. 날짜는 YYYY-MM-DD.
 * 사유는 근로기준법 제23조 제1항(정당한 이유 없는 전직·휴직 등 금지)에 따라 반드시 적는다. 실제 검사는 서버가 한다.
 */
import { call } from './http';
import { ApiError } from './types';

/** 발령 유형. 코드값·이름은 백엔드 AppointmentType(공통코드 APPOINTMENT_TYPE)과 같다. */
export type ApptType = 'HIRE' | 'TRANSFER' | 'PROMOTION' | 'TITLE_CHANGE' | 'LEAVE' | 'RETURN' | 'RETIREMENT';
export const APPT_TYPE_LABEL: Record<ApptType, string> = {
  HIRE: '입사', TRANSFER: '전보', PROMOTION: '승진', TITLE_CHANGE: '직책', LEAVE: '휴직', RETURN: '복직', RETIREMENT: '퇴직',
};
export const APPT_TYPES = Object.keys(APPT_TYPE_LABEL) as ApptType[];
/** 화면에서 등록하는 유형. 입사 발령은 사원 등록 때 만들어진다. */
export const REGISTER_TYPES: ApptType[] = ['TRANSFER', 'PROMOTION', 'TITLE_CHANGE', 'LEAVE', 'RETURN', 'RETIREMENT'];

/** 유형마다 입력하는 이후 값(백엔드 AppointmentType.changes 와 같다). */
export const typeFields = (t: ApptType | '') => ({
  dept: t === 'TRANSFER' || t === 'HIRE',
  grade: t === 'PROMOTION' || t === 'HIRE',
  title: t === 'TITLE_CHANGE' || t === 'HIRE',
  leave: t === 'LEAVE',
});

/** 재직상태(백엔드 appointment.EmpStatus). */
export type ApptEmpStatus = 'ACTIVE' | 'ON_LEAVE' | 'RETIRED';
export const APPT_STATUS_LABEL: Record<ApptEmpStatus, string> = { ACTIVE: '재직', ON_LEAVE: '휴직', RETIRED: '퇴직' };
export interface Snapshot { deptId: number | null; jobGradeCode: string | null; jobTitleCode: string | null; status: ApptEmpStatus | null }

export interface AppointmentView {
  apptId: number; apptNo: string; apptTypeCode: ApptType; empId: number; effectiveDate: string;
  before: Snapshot; after: Snapshot; reason: string; leaveStartDate: string | null; leaveEndDate: string | null;
  confirmed: boolean; confirmedAt: string | null; createdAt: string; updatedAt: string;
  /** 이 발령이 취소 발령이면 원 발령 ID */
  cancelOfApptId: number | null;
  /** 이 발령을 되돌리는 취소 발령 ID(미확정 포함) */
  cancelledByApptId: number | null;
}

export interface AppointmentInput {
  apptTypeCode: ApptType | ''; effectiveDate: string;
  afterDeptId: number | null; afterJobGradeCode: string | null; afterJobTitleCode: string | null;
  reason: string; leaveStartDate: string | null; leaveEndDate: string | null;
}

export interface AppointmentFilter { confirmed: boolean | null; type: ApptType | ''; from: string; to: string; deptId: number | null }

export interface BulkItemResult { empId: number; ok: boolean; appointment: AppointmentView | null; error: string | null }
export interface BulkResult { succeeded: number; failed: number; items: BulkItemResult[] }

export interface AppointmentApi {
  search(f: AppointmentFilter): Promise<AppointmentView[]>;
  /** 사원 발령 이력. 일반 사원은 본인 것·확정분만 온다(서버 검사). */
  byEmployee(empId: number): Promise<AppointmentView[]>;
  confirm(apptId: number): Promise<AppointmentView>;
  /** 취소 발령(미확정)을 만든다. 확정해야 반영된다. */
  cancel(apptId: number, reason: string): Promise<AppointmentView>;
  /** 미확정 발령 삭제 */
  remove(apptId: number): Promise<void>;
  /** 등록(한 명이어도 이것을 쓴다). confirm 이면 바로 확정. 실패한 사원만 되돌리고 사원별 결과를 준다. */
  bulk(empIds: number[], input: AppointmentInput, confirm: boolean): Promise<BulkResult>;
}

/** 원 발령의 취소 상태. 취소 발령이 확정돼야 '취소됨'. 조회 조건 때문에 취소 발령이 목록에 없으면 상태를 단정하지 않는다. */
export const cancelLabel = (rows: AppointmentView[], cancelApptId: number) => {
  const c = rows.find((r) => r.apptId === cancelApptId);
  return c === undefined ? '취소 발령 있음' : c.confirmed ? '취소됨' : '취소 대기';
};

/** 일괄 발령 한 번의 최대 인원(백엔드 BulkAppointmentRequest @Size 와 같다). */
export const BULK_MAX = 200;

export function validateAppointment(i: AppointmentInput): string | null {
  if (!i.apptTypeCode) return '발령 유형을 선택해 주세요';
  if (!i.effectiveDate) return '발령일을 입력해 주세요';
  if (!i.reason.trim()) return '발령 사유를 입력해 주세요(근로기준법 제23조)';
  if (i.reason.trim().length > 1000) return '발령 사유는 1000자 이내로 써 주세요';
  const f = typeFields(i.apptTypeCode);
  if (i.apptTypeCode === 'TRANSFER' && i.afterDeptId === null) return '전보할 부서를 선택해 주세요';
  if (i.apptTypeCode === 'PROMOTION' && !i.afterJobGradeCode) return '승진할 직급을 선택해 주세요';
  if (f.leave) {
    if (!i.leaveStartDate || !i.leaveEndDate) return '휴직 발령은 휴직 시작일과 종료 예정일을 입력해 주세요';
    if (i.leaveStartDate < i.effectiveDate) return '휴직 시작일은 발령일 이후여야 해요';
    if (i.leaveEndDate < i.leaveStartDate) return '휴직 종료 예정일은 휴직 시작일 이후여야 해요';
  }
  return null;
}

/** 유형이 바꾸지 않는 항목은 비워서 보낸다(서버가 이전 값을 옮긴다). */
export function toAppointmentBody(i: AppointmentInput) {
  const f = typeFields(i.apptTypeCode);
  return {
    apptTypeCode: i.apptTypeCode,
    effectiveDate: i.effectiveDate,
    afterDeptId: f.dept ? i.afterDeptId : null,
    afterJobGradeCode: f.grade ? i.afterJobGradeCode || null : null,
    afterJobTitleCode: f.title ? i.afterJobTitleCode || null : null,
    reason: i.reason.trim(),
    leaveStartDate: f.leave ? i.leaveStartDate : null,
    leaveEndDate: f.leave ? i.leaveEndDate : null,
  };
}

export function searchQuery(f: AppointmentFilter) {
  const p = new URLSearchParams();
  if (f.confirmed !== null) p.set('confirmed', String(f.confirmed));
  if (f.type) p.set('type', f.type);
  if (f.from) p.set('from', f.from);
  if (f.to) p.set('to', f.to);
  if (f.deptId !== null) p.set('deptId', String(f.deptId));
  const s = p.toString();
  return s ? `?${s}` : '';
}

export const httpAppointmentApi: AppointmentApi = {
  search: (f) => call('GET', `/appointments${searchQuery(f)}`),
  byEmployee: (empId) => call('GET', `/employees/${empId}/appointments`),
  confirm: (apptId) => call('POST', `/appointments/${apptId}/confirm`),
  cancel: (apptId, reason) => call('POST', `/appointments/${apptId}/cancel`, { reason }),
  remove: async (apptId) => { await call('DELETE', `/appointments/${apptId}`); },
  bulk: (empIds, input, confirm) => call('POST', '/appointments/bulk', { empIds, appointment: toAppointmentBody(input), confirm }),
};

/**
 * 데모·테스트용 메모리 구현. 서버 규칙 중 화면 확인에 필요한 것만 흉내 낸다
 * (사원 존재, 취소 대상은 확정분만, 확정분 삭제 불가). 이후 값 계산·소속 반영은 하지 않는다.
 */
export function createMockAppointmentApi(seed: AppointmentView[] = [], knownEmpIds?: number[]): AppointmentApi {
  let rows = structuredClone(seed);
  let seq = rows.reduce((m, r) => Math.max(m, r.apptId), 0);
  const now = () => new Date().toISOString().slice(0, 19);
  const find = (id: number) => {
    const a = rows.find((r) => r.apptId === id);
    if (!a) throw new ApiError(404, '발령이 없어요');
    return a;
  };
  const make = (empId: number, input: AppointmentInput, cancelOf: number | null = null): AppointmentView => {
    if (knownEmpIds && !knownEmpIds.includes(empId)) throw new ApiError(404, `사원이 없어요: ${empId}`);
    const err = cancelOf === null ? validateAppointment(input) : null;
    if (err) throw new ApiError(400, err);
    const b = toAppointmentBody(input);
    const none: Snapshot = { deptId: null, jobGradeCode: null, jobTitleCode: null, status: null };
    const v: AppointmentView = {
      apptId: ++seq, apptNo: `A-${String(seq).padStart(5, '0')}`, apptTypeCode: input.apptTypeCode as ApptType, empId,
      effectiveDate: b.effectiveDate, before: none,
      after: { deptId: b.afterDeptId, jobGradeCode: b.afterJobGradeCode, jobTitleCode: b.afterJobTitleCode, status: null },
      reason: b.reason, leaveStartDate: b.leaveStartDate, leaveEndDate: b.leaveEndDate, confirmed: false, confirmedAt: null,
      createdAt: now(), updatedAt: now(), cancelOfApptId: cancelOf, cancelledByApptId: null,
    };
    rows.push(v);
    return v;
  };
  const confirm = (id: number) => {
    const a = find(id);
    if (a.confirmed) throw new ApiError(409, '이미 확정된 발령이에요');
    a.confirmed = true;
    a.confirmedAt = now();
    return structuredClone(a);
  };
  const sorted = (l: AppointmentView[]) => [...l].sort((x, y) => (x.effectiveDate < y.effectiveDate ? -1 : x.effectiveDate > y.effectiveDate ? 1 : x.apptId - y.apptId));
  return {
    search: async (f) => structuredClone(sorted(rows.filter((a) =>
      (f.confirmed === null || a.confirmed === f.confirmed) && (!f.type || a.apptTypeCode === f.type)
      && (!f.from || a.effectiveDate >= f.from) && (!f.to || a.effectiveDate <= f.to)
      && (f.deptId === null || a.before.deptId === f.deptId || a.after.deptId === f.deptId)))),
    byEmployee: async (empId) => structuredClone(sorted(rows.filter((a) => a.empId === empId))),
    confirm: async (id) => confirm(id),
    cancel: async (id, reason) => {
      const a = find(id);
      if (!a.confirmed) throw new ApiError(409, '미확정 발령은 취소 대신 삭제해 주세요');
      if (a.cancelledByApptId !== null) throw new ApiError(409, '이미 취소 발령이 있어요');
      const c = make(a.empId, { apptTypeCode: a.apptTypeCode, effectiveDate: a.effectiveDate, afterDeptId: null, afterJobGradeCode: null, afterJobTitleCode: null, reason, leaveStartDate: a.leaveStartDate, leaveEndDate: a.leaveEndDate }, a.apptId);
      a.cancelledByApptId = c.apptId;
      return structuredClone(c);
    },
    remove: async (id) => {
      const a = find(id);
      if (a.confirmed) throw new ApiError(409, '확정된 발령은 삭제할 수 없어요. 취소 발령을 등록해 주세요');
      rows = rows.filter((r) => r.apptId !== id);
      for (const r of rows) if (r.cancelledByApptId === id) r.cancelledByApptId = null;
    },
    bulk: async (empIds, input, doConfirm) => {
      const items: BulkItemResult[] = [];
      for (const empId of [...new Set(empIds)]) {
        try {
          const c = make(empId, input);
          items.push({ empId, ok: true, appointment: doConfirm ? confirm(c.apptId) : structuredClone(c), error: null });
        } catch (e) {
          items.push({ empId, ok: false, appointment: null, error: e instanceof Error ? e.message : '처리하지 못했어요' });
        }
      }
      const ok = items.filter((i) => i.ok).length;
      return { succeeded: ok, failed: items.length - ok, items };
    },
  };
}

export const appointmentApi: AppointmentApi = import.meta.env.VITE_API === 'mock' ? createMockAppointmentApi() : httpAppointmentApi;
