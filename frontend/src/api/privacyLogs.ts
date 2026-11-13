/**
 * 개인정보 조회 로그 API (#19). 백엔드: backend/ AuthController GET /api/admin/privacy-logs (관리자만, PRIVACY_LOG_READ).
 * 응답 형식은 PrivacyLogService.LogPage 와 같다. 권한은 서버가 막고, 화면은 메뉴만 숨긴다.
 */
import { call } from './http';
import { ApiError } from './types';

/** source: PRIVACY = privacy_access_log, RRN = 주민등록번호 원문 조회 로그(rrn_access_log). purpose 는 RRN 에만 있다. */
export interface PrivacyLogRow {
  source: 'PRIVACY' | 'RRN';
  accessedAt: string;
  accessorId: string;
  accessorRoles: string | null;
  targetEmpId: number | null;
  item: string;
  action: string;
  purpose: string | null;
  result: string;
  clientIp: string | null;
  requestPath: string | null;
}

export interface PrivacyLogPage { from: string; to: string; rows: PrivacyLogRow[]; truncated: boolean }

/** 비운 값은 서버 기본값(오늘부터 30일 전까지, 모든 사람). 날짜는 'YYYY-MM-DD'(Asia/Seoul). */
export interface PrivacyLogQuery { from: string; to: string; accessorId: string; targetEmpId: string }

export interface PrivacyLogApi { search(q: PrivacyLogQuery): Promise<PrivacyLogPage> }

export function toSearchParams(q: PrivacyLogQuery): string {
  const p = new URLSearchParams();
  if (q.from) p.set('from', q.from);
  if (q.to) p.set('to', q.to);
  if (q.accessorId.trim()) p.set('accessorId', q.accessorId.trim());
  if (q.targetEmpId.trim()) p.set('targetEmpId', q.targetEmpId.trim());
  const s = p.toString();
  return s ? `?${s}` : '';
}

/** 서버로 보내기 전 입력 검사(서버도 다시 검사한다). 문제가 없으면 null. */
export function validateQuery(q: PrivacyLogQuery): string | null {
  if (q.from && q.to && q.to < q.from) return '끝 날짜가 시작 날짜보다 빨라요';
  if (q.targetEmpId.trim() && !/^[0-9]+$/.test(q.targetEmpId.trim())) return '대상 사원번호(empId)는 숫자로 입력해 주세요';
  return null;
}

export const httpPrivacyLogApi: PrivacyLogApi = {
  search: (q) => call('GET', `/admin/privacy-logs${toSearchParams(q)}`),
};

/* ---------- 화면 표시 ---------- */

export const ITEM_LABEL: Record<string, string> = {
  RRN: '주민등록번호', EMP_PERSONAL: '인적사항', EMP_LIST: '사원 목록 파일',
};
export const ACTION_LABEL: Record<string, string> = { VIEW: '조회', DOWNLOAD: '다운로드' };
export const RESULT_LABEL: Record<string, string> = { SUCCESS: '성공', DENIED: '거부', NOT_FOUND: '대상 없음' };
export const PURPOSE_LABEL: Record<string, string> = { VIEW: '열람', SOCIAL_INSURANCE: '4대보험 신고', WITHHOLDING: '원천징수' };

export const labelOf = (map: Record<string, string>, code: string | null) => (code ? map[code] ?? code : '');

/* ---------- 데모용 가짜 백엔드 ---------- */

/** 테스트·VITE_API=mock 용. 관리자(ADMIN)가 아니면 서버처럼 403 을 던진다. 데이터는 모두 가짜 값이다. */
export function createMockPrivacyLogApi(rows: PrivacyLogRow[], isAdmin: () => boolean = () => true): PrivacyLogApi & { calls: PrivacyLogQuery[] } {
  const calls: PrivacyLogQuery[] = [];
  return {
    calls,
    async search(q) {
      calls.push(q);
      if (!isAdmin()) throw new ApiError(403, '이 기능을 쓸 권한이 없어요');
      const err = validateQuery(q);
      if (err) throw new ApiError(400, err);
      const to = q.to || '9999-12-31';
      const from = q.from || '0000-01-01';
      const hit = rows.filter((r) => {
        const d = r.accessedAt.slice(0, 10);
        return d >= from && d <= to &&
          (!q.accessorId.trim() || r.accessorId === q.accessorId.trim()) &&
          (!q.targetEmpId.trim() || r.targetEmpId === Number(q.targetEmpId.trim()));
      }).sort((a, b) => (a.accessedAt < b.accessedAt ? 1 : -1));
      return { from: q.from, to: q.to, rows: hit, truncated: false };
    },
  };
}

export const privacyLogApi: PrivacyLogApi = import.meta.env.VITE_API === 'mock' ? createMockPrivacyLogApi([]) : httpPrivacyLogApi;
