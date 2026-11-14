/**
 * 사원 엑셀(CSV) 일괄 등록·목록 내려받기 (#20). 백엔드: EmployeeImportController, EmployeeController.export.
 * 서버가 권한을 다시 검사하고(등록 = 관리자·인사, 내려받기 = 관리자·인사·급여), 내려받을 때 사원마다 다운로드 로그를 남긴다.
 */
import { actorHeader } from './http';
import { ApiError } from './types';

export interface ImportRow { rowNo: number; name: string | null; siteCode: string | null; hireDate: string | null; errors: string[] }
export interface ImportPreview { totalRows: number; errorRows: number; confirmable: boolean; fileErrors: string[]; rows: ImportRow[] }
export interface ImportConfirmed { created: number; empNos: string[] }

/** 확정 거부(422). 서버가 다시 검사한 행별 오류를 함께 준다. */
export class ImportRejected extends Error {
  constructor(message: string, readonly preview: ImportPreview) { super(message); }
}

export interface DownloadedFile { blob: Blob; filename: string }

export interface EmployeeImportApi {
  template(): Promise<DownloadedFile>;
  exportList(includeRetired: boolean): Promise<DownloadedFile>;
  preview(file: File): Promise<ImportPreview>;
  confirm(file: File): Promise<ImportConfirmed>;
}

const headers = actorHeader;

async function fail(res: Response): Promise<never> {
  let msg = `요청에 실패했어요 (${res.status})`;
  let body: { error?: string; preview?: ImportPreview } | null = null;
  try { body = (await res.json()) as { error?: string; preview?: ImportPreview }; } catch { /* not JSON */ }
  if (body?.error) msg = body.error;
  if (res.status === 422 && body?.preview) throw new ImportRejected(msg, body.preview);
  throw new ApiError(res.status, msg);
}

async function send(path: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch('/api' + path, { credentials: 'same-origin', ...init, headers: { ...headers(), ...(init.headers as Record<string, string> | undefined) } });
  } catch {
    throw new ApiError(0, '서버에 연결할 수 없어요. 서버가 켜져 있는지 확인해 주세요');
  }
}

/** Content-Disposition 의 filename 을 꺼낸다. 없으면 fallback. */
export function filenameOf(disposition: string | null, fallback: string) {
  const m = disposition ? /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition) : null;
  return m ? decodeURIComponent(m[1]) : fallback;
}

async function download(path: string, fallback: string): Promise<DownloadedFile> {
  const res = await send(path, { method: 'GET' });
  if (!res.ok) return fail(res);
  return { blob: await res.blob(), filename: filenameOf(res.headers.get('Content-Disposition'), fallback) };
}

async function upload<T>(path: string, file: File): Promise<T> {
  const form = new FormData();
  form.append('file', file);
  const res = await send(path, { method: 'POST', body: form });
  if (!res.ok) return fail(res);
  return res.json() as Promise<T>;
}

export const httpEmployeeImportApi: EmployeeImportApi = {
  template: () => download('/employees/import/template', 'employee-import-template.csv'),
  exportList: (includeRetired) => download(`/employees/export?includeRetired=${includeRetired}`, 'employees.csv'),
  preview: (file) => upload('/employees/import/preview', file),
  confirm: (file) => upload('/employees/import/confirm', file),
};

/** 브라우저에서 파일 저장. 테스트에서는 바꿔 끼운다. */
export function saveFile(f: DownloadedFile) {
  const url = URL.createObjectURL(f.blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = f.filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
