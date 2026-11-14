/**
 * 근로자대표 서면합의서 API (#27). 백엔드: backend/ LaborAgreementController (Spring).
 * 응답 형식은 backend LaborAgreementDtos 와 같다. 날짜는 YYYY-MM-DD, 유효기간은 양 끝 포함.
 * 유효 여부 판정(기간·대상)은 서버 LaborAgreementGuard 가 한다. 화면은 형식 검사만 한다.
 */
import { actorHeader, call } from './http';
import { ApiError } from './types';

export type AgreementType =
  | 'FLEXIBLE_WRITTEN' | 'FLEXIBLE_LONG' | 'SELECTIVE' | 'HOLIDAY_SUBSTITUTION'
  | 'COMPENSATORY_LEAVE' | 'DISCRETIONARY' | 'ANNUAL_LEAVE_SUBSTITUTE';
export type Scope = 'ALL' | 'SITE';
/** 근로기준법 제24조 제3항: 과반수 노동조합 / 근로자 과반수를 대표하는 자 */
export type RepType = 'MAJORITY_UNION' | 'MAJORITY_REP';
export type AgreementStatus = 'UPCOMING' | 'ACTIVE' | 'EXPIRED';

export interface TypeView { code: AgreementType; label: string; article: string }

export interface AgreementInput {
  agreementType: AgreementType;
  title: string;
  signedDate: string;
  validFrom: string;
  validUntil: string | null;
  scopeType: Scope;
  scopeSiteCode: string | null;
  targetDesc: string;
  repType: RepType;
  repUnionName: string | null;
  repEmpId: number | null;
  note: string | null;
  /** 합의서 원본의 문서 번호·보관 위치(필수). 스캔본은 첨부파일로 따로 올린다. */
  documentRef: string;
}
export interface Agreement extends Omit<AgreementInput, 'targetDesc'> {
  /** #26 휴일 달력에서 먼저 등록한 합의서는 비어 있을 수 있다(수정할 때 채워야 한다). */
  targetDesc: string | null;
  agreementId: number;
  typeLabel: string;
  article: string;
  repEmpName: string | null;
  repEmpNo: string | null;
  status: AgreementStatus;
  /** 오늘부터 마지막 유효일까지 남은 날 수. 기간 정함 없으면 null */
  daysUntilExpiry: number | null;
  fileCount: number;
  updatedBy: string;
  updatedAt: string;
}
export interface AgreementFile {
  fileId: number; agreementId: number; fileName: string; contentType: string; fileSize: number;
  sha256: string; uploadedBy: string; uploadedAt: string;
}
export interface AgreementDetail { agreement: Agreement; files: AgreementFile[] }
export interface Expiring { days: number; agreements: Agreement[] }
export interface CheckResult {
  ok: boolean; agreementType: AgreementType; agreementId: number | null; title: string | null;
  validFrom: string | null; validUntil: string | null; message: string | null;
}
export interface CheckQuery { type: AgreementType; from: string; until: string | null; siteCode: string | null }

export interface AgreementApi {
  types(): Promise<TypeView[]>;
  list(): Promise<Agreement[]>;
  expiring(): Promise<Expiring>;
  get(id: number): Promise<AgreementDetail>;
  create(input: AgreementInput): Promise<Agreement>;
  update(id: number, input: AgreementInput): Promise<Agreement>;
  check(q: CheckQuery): Promise<CheckResult>;
  upload(id: number, file: File): Promise<AgreementFile>;
  download(id: number, fileId: number): Promise<{ blob: Blob; filename: string }>;
  removeFile(id: number, fileId: number): Promise<void>;
}

/** 받는 첨부 형식(서버가 파일 내용으로 다시 판정한다). */
export const ACCEPT_FILES = '.pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg';
const DATE = /^\d{4}-\d{2}-\d{2}$/;

/** 형식 검사. 백엔드 LaborAgreementRules.validate·DTO 와 같은 규칙(서버도 다시 검사한다). 문제가 없으면 null. */
export function validateAgreement(i: AgreementInput): string | null {
  if (!i.title.trim()) return '합의서 제목을 입력해 주세요';
  if (i.title.length > 200) return '제목은 200자 이내로 써 주세요';
  if (!DATE.test(i.signedDate)) return '체결일을 입력해 주세요';
  if (!DATE.test(i.validFrom)) return '유효기간 시작일을 입력해 주세요';
  if (i.validUntil !== null && !DATE.test(i.validUntil)) return '유효기간 종료일 형식이 올바르지 않아요';
  if (i.validUntil !== null && i.validUntil < i.validFrom) return '유효기간 종료일은 시작일 이후여야 해요';
  if (i.validUntil !== null && i.signedDate > i.validUntil) return '체결일이 유효기간 종료일보다 늦어요';
  if (i.scopeType === 'SITE' && !i.scopeSiteCode?.trim()) return '대상 사업장을 골라 주세요';
  if (!i.targetDesc.trim()) return '대상 근로자 범위를 입력해 주세요';
  if (i.targetDesc.length > 1000) return '대상 근로자 범위는 1000자 이내로 써 주세요';
  if (i.repType === 'MAJORITY_UNION' && !i.repUnionName?.trim()) return '노동조합 이름을 입력해 주세요';
  if (i.repType === 'MAJORITY_REP' && (i.repEmpId === null || !Number.isInteger(i.repEmpId) || i.repEmpId <= 0)) {
    return '근로자 과반수를 대표하는 사원을 골라 주세요';
  }
  if (i.note && i.note.length > 500) return '비고는 500자 이내로 써 주세요';
  if (!i.documentRef.trim()) return '합의서 문서(파일 번호·보관 위치)를 입력해 주세요';
  if (i.documentRef.length > 500) return '합의서 문서 참조는 500자 이내로 써 주세요';
  return null;
}

export const STATUS_LABEL: Record<AgreementStatus, string> = { UPCOMING: '시작 전', ACTIVE: '유효', EXPIRED: '만료' };
export const REP_LABEL: Record<RepType, string> = {
  MAJORITY_UNION: '과반수 노동조합',
  MAJORITY_REP: '근로자 과반수 대표자',
};

async function send(path: string, init: RequestInit): Promise<Response> {
  let res: Response;
  try {
    res = await fetch('/api' + path, { credentials: 'same-origin', ...init, headers: actorHeader() });
  } catch {
    throw new ApiError(0, '서버에 연결할 수 없어요. 서버가 켜져 있는지 확인해 주세요');
  }
  if (!res.ok) {
    let msg = `요청에 실패했어요 (${res.status})`;
    try { msg = ((await res.json()) as { error?: string }).error ?? msg; } catch { /* not JSON */ }
    throw new ApiError(res.status, msg);
  }
  return res;
}

function filenameOf(disposition: string | null, fallback: string) {
  const m = disposition ? /filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i.exec(disposition) : null;
  if (!m) return fallback;
  try { return decodeURIComponent(m[1] ?? m[2]); } catch { return m[1] ?? m[2]; }
}

const BASE = '/attendance/agreements';
const qs = (q: CheckQuery) => {
  const p = new URLSearchParams({ type: q.type, from: q.from });
  if (q.until) p.set('until', q.until);
  if (q.siteCode) p.set('siteCode', q.siteCode);
  return p.toString();
};

export const httpAgreementApi: AgreementApi = {
  types: () => call('GET', `${BASE}/types`),
  list: () => call('GET', BASE),
  expiring: () => call('GET', `${BASE}/expiring`),
  get: (id) => call('GET', `${BASE}/${id}`),
  create: (input) => call('POST', BASE, input),
  update: (id, input) => call('PUT', `${BASE}/${id}`, input),
  check: (q) => call('GET', `${BASE}/check?${qs(q)}`),
  async upload(id, file) {
    const form = new FormData();
    form.append('file', file);
    const res = await send(`${BASE}/${id}/files`, { method: 'POST', body: form });
    return res.json() as Promise<AgreementFile>;
  },
  async download(id, fileId) {
    const res = await send(`${BASE}/${id}/files/${fileId}`, { method: 'GET' });
    return { blob: await res.blob(), filename: filenameOf(res.headers.get('Content-Disposition'), `agreement-${fileId}`) };
  },
  async removeFile(id, fileId) {
    await send(`${BASE}/${id}/files/${fileId}`, { method: 'DELETE' });
  },
};

const TYPES: TypeView[] = [
  { code: 'FLEXIBLE_WRITTEN', label: '탄력적 근로시간제(3개월 이내)', article: '근로기준법 제51조 제2항' },
  { code: 'FLEXIBLE_LONG', label: '탄력적 근로시간제(3개월 초과)', article: '근로기준법 제51조의2' },
  { code: 'SELECTIVE', label: '선택적 근로시간제', article: '근로기준법 제52조' },
  { code: 'HOLIDAY_SUBSTITUTION', label: '휴일 대체', article: '근로기준법 제55조 제2항 단서' },
  { code: 'COMPENSATORY_LEAVE', label: '보상휴가제', article: '근로기준법 제57조' },
  { code: 'DISCRETIONARY', label: '재량근로', article: '근로기준법 제58조 제3항' },
  { code: 'ANNUAL_LEAVE_SUBSTITUTE', label: '연차 유급휴가 대체', article: '근로기준법 제62조' },
];

const DAY_MS = 86_400_000;
const toDay = (s: string) => Date.UTC(Number(s.slice(0, 4)), Number(s.slice(5, 7)) - 1, Number(s.slice(8, 10)));
/** 서울 기준 오늘 YYYY-MM-DD */
export const seoulToday = (now = new Date()) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(now);

/**
 * 브라우저 안의 가짜 백엔드(데모용). 서버 LaborAgreementRules 와 같은 판정(양 끝 포함, 사업장 한정 우선)을 흉내 낸다.
 * 첨부는 메모리에만 둔다.
 */
export function createMockAgreementApi(today: () => string = () => seoulToday(), expiryDays = 30): AgreementApi {
  type Row = AgreementInput & { agreementId: number; updatedBy: string; updatedAt: string };
  let rows: Row[] = [{
    agreementId: 1, agreementType: 'SELECTIVE', title: '연구소 선택적 근로시간제 합의서(예시)', signedDate: '2026-01-02',
    validFrom: '2026-01-05', validUntil: '2026-12-31', scopeType: 'ALL', scopeSiteCode: null,
    targetDesc: '연구개발 직군 전원(예시)', repType: 'MAJORITY_UNION', repUnionName: '가상노동조합', repEmpId: null,
    note: null, documentRef: '인사팀 문서함 2026-001(예시)', updatedBy: 'hr1', updatedAt: '2026-01-02T09:00:00',
  }];
  let files: Array<AgreementFile & { blob: Blob }> = [];
  let nextId = 2;
  let nextFile = 1;
  const view = (r: Row): Agreement => {
    const t = today();
    const label = TYPES.find((x) => x.code === r.agreementType)!;
    return {
      ...r, typeLabel: label.label, article: label.article, repEmpName: null, repEmpNo: null,
      status: r.validFrom > t ? 'UPCOMING' : r.validUntil !== null && r.validUntil < t ? 'EXPIRED' : 'ACTIVE',
      daysUntilExpiry: r.validUntil === null ? null : Math.round((toDay(r.validUntil) - toDay(t)) / DAY_MS),
      fileCount: files.filter((f) => f.agreementId === r.agreementId).length,
    };
  };
  const find = (id: number) => {
    const r = rows.find((x) => x.agreementId === id);
    if (!r) throw new ApiError(404, `합의서가 없어요: ${id}`);
    return r;
  };
  const clean = (i: AgreementInput): AgreementInput => {
    const err = validateAgreement(i);
    if (err) throw new ApiError(400, err);
    return {
      ...i, title: i.title.trim(), targetDesc: i.targetDesc.trim(), note: i.note?.trim() || null,
      documentRef: i.documentRef.trim(),
      scopeSiteCode: i.scopeType === 'SITE' ? i.scopeSiteCode!.trim() : null,
      repUnionName: i.repType === 'MAJORITY_UNION' ? i.repUnionName!.trim() : null,
      repEmpId: i.repType === 'MAJORITY_REP' ? i.repEmpId : null,
    };
  };
  return {
    async types() { return structuredClone(TYPES); },
    async list() { return rows.map(view).sort((a, b) => (a.validFrom < b.validFrom ? 1 : -1)); },
    async expiring() {
      const t = today();
      const list = rows.map(view).filter((a) => a.daysUntilExpiry !== null && a.validUntil! >= t && a.daysUntilExpiry <= expiryDays);
      return { days: expiryDays, agreements: list.sort((a, b) => (a.validUntil! < b.validUntil! ? -1 : 1)) };
    },
    async get(id) {
      const r = find(id);
      return { agreement: view(r), files: files.filter((f) => f.agreementId === id).map(({ blob: _b, ...f }) => f) };
    },
    async create(i) {
      const row: Row = { ...clean(i), agreementId: nextId++, updatedBy: 'demo', updatedAt: new Date().toISOString() };
      rows = [...rows, row];
      return view(row);
    },
    async update(id, i) {
      const cur = find(id);
      if (cur.agreementType !== i.agreementType) throw new ApiError(400, '합의 유형은 바꿀 수 없어요. 새 합의서로 등록해 주세요');
      const next: Row = { ...cur, ...clean(i), updatedBy: 'demo', updatedAt: new Date().toISOString() };
      rows = rows.map((r) => (r === cur ? next : r));
      return view(next);
    },
    async check(q) {
      if (q.until !== null && q.until < q.from) throw new ApiError(400, '적용 종료일은 시작일 이후여야 해요');
      const t = TYPES.find((x) => x.code === q.type)!;
      const hit = rows
        .filter((a) => a.agreementType === q.type && a.validFrom <= q.from
          && (a.validUntil === null || (q.until !== null && q.until <= a.validUntil))
          && (a.scopeType === 'ALL' || (q.siteCode !== null && a.scopeSiteCode === q.siteCode)))
        .sort((a, b) => (a.scopeType !== b.scopeType ? (a.scopeType === 'SITE' ? -1 : 1) : a.validFrom < b.validFrom ? 1 : -1))[0];
      return hit
        ? { ok: true, agreementType: q.type, agreementId: hit.agreementId, title: hit.title, validFrom: hit.validFrom, validUntil: hit.validUntil, message: null }
        : { ok: false, agreementType: q.type, agreementId: null, title: null, validFrom: null, validUntil: null,
          message: `${t.label}는 근로자대표와의 서면합의가 있어야 해요(${t.article}). 적용 기간과 대상을 모두 덮는 유효한 합의서가 없어요` };
    },
    async upload(id, file) {
      find(id);
      if (file.size === 0) throw new ApiError(400, '빈 파일은 올릴 수 없어요');
      const f = {
        fileId: nextFile++, agreementId: id, fileName: file.name, contentType: file.type || 'application/pdf',
        fileSize: file.size, sha256: '0'.repeat(64), uploadedBy: 'demo', uploadedAt: new Date().toISOString(), blob: file,
      };
      files = [...files, f];
      const { blob: _b, ...meta } = f;
      return meta;
    },
    async download(id, fileId) {
      const f = files.find((x) => x.agreementId === id && x.fileId === fileId);
      if (!f) throw new ApiError(404, `첨부파일이 없어요: ${fileId}`);
      return { blob: f.blob, filename: f.fileName };
    },
    async removeFile(id, fileId) {
      if (!files.some((x) => x.agreementId === id && x.fileId === fileId)) throw new ApiError(404, `첨부파일이 없어요: ${fileId}`);
      files = files.filter((x) => !(x.agreementId === id && x.fileId === fileId));
    },
  };
}

export const agreementApi: AgreementApi = import.meta.env.VITE_API === 'mock' ? createMockAgreementApi() : httpAgreementApi;
