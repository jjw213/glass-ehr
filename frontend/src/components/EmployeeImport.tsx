import { useRef, useState } from 'react';
import { LuDownload, LuUpload } from 'react-icons/lu';
import { Badge, Modal } from './ui';
import { ImportRejected, saveFile, type DownloadedFile, type EmployeeImportApi, type ImportConfirmed, type ImportPreview } from '../api/employeeImport';

const errText = (e: unknown, fallback: string) => (e instanceof Error ? e.message : fallback);

/**
 * 사원 엑셀(CSV) 일괄 등록 (#20): 양식 내려받기 → 파일 선택 → 미리보기(행별 오류) → 오류가 없을 때만 확정.
 * 확정할 때 서버가 같은 파일을 다시 검사하므로, 그 사이 바뀐 것이 있으면 다시 오류 목록을 보여 준다.
 */
export function EmployeeImportDialog({ api, open, onClose, onDone, save = saveFile }: {
  api: EmployeeImportApi; open: boolean; onClose: () => void; onDone: (r: ImportConfirmed) => void;
  save?: (f: DownloadedFile) => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 응답이 늦게 도착해도 지금 선택된 파일의 것만 반영한다. 파일을 다시 고르거나 닫으면 번호가 바뀌어 이전 응답은 버린다.
  const seq = useRef(0);

  const reset = () => { seq.current += 1; setFile(null); setPreview(null); setError(null); };
  const close = () => { reset(); onClose(); };

  const run = async (f: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try { await f(); } finally { setBusy(false); }
  };

  const template = () => run(async () => {
    try { save(await api.template()); } catch (e) { setError(errText(e, '양식을 내려받지 못했어요')); }
  });

  const check = (f: File) => run(async () => {
    const my = ++seq.current;
    setFile(f);
    setPreview(null);
    try {
      const p = await api.preview(f);
      if (my === seq.current) setPreview(p);
    } catch (e) {
      if (my === seq.current) setError(errText(e, '파일을 검사하지 못했어요'));
    }
  });

  const confirm = () => run(async () => {
    if (!file || !preview?.confirmable) return;
    const my = seq.current;
    try {
      const r = await api.confirm(file);
      if (my === seq.current) reset();
      onDone(r);
    } catch (e) {
      if (my !== seq.current) return;
      if (e instanceof ImportRejected) setPreview(e.preview);
      setError(errText(e, '등록하지 못했어요'));
    }
  });

  const errorRows = preview?.rows.filter((r) => r.errors.length > 0) ?? [];

  return (
    <Modal open={open} onClose={close} title="엑셀 일괄 등록">
      <ol className="import-steps">
        <li>
          양식을 내려받아 엑셀에서 채운 뒤 <strong>CSV</strong>로 저장해 주세요. 머리글에 *가 붙은 열은 필수예요.
          날짜는 YYYY-MM-DD, 코드는 공통코드 값을 써요.
          <div><button type="button" className="glass pill" onClick={template} disabled={busy}><LuDownload /> 양식 내려받기</button></div>
        </li>
        <li>
          <label className="glass pill">
            <LuUpload /> 파일 선택
            <input
              type="file" accept=".csv,text/csv" hidden aria-label="일괄 등록 파일" disabled={busy}
              onChange={(e) => { const f = e.target.files?.[0]; e.target.value = ''; if (f) void check(f); }}
            />
          </label>
          {file && <span className="muted"> {file.name}</span>}
        </li>
      </ol>

      {error && <p className="badge badge--red" role="alert">{error}</p>}

      {preview && (
        <section aria-label="미리보기">
          {preview.fileErrors.length > 0 ? (
            <ul className="import-errors">{preview.fileErrors.map((m) => <li key={m}>{m}</li>)}</ul>
          ) : (
            <p>
              전체 {preview.totalRows}행 · 오류 {preview.errorRows}행{' '}
              {preview.confirmable ? <Badge tone="green">등록 가능</Badge> : <Badge tone="red">오류를 고쳐 다시 올려 주세요</Badge>}
            </p>
          )}
          {preview.rows.length > 0 && (
            <table className="emp-table import-table">
              <thead><tr><th>행</th><th>성명</th><th>사업장</th><th>입사일</th><th>결과</th></tr></thead>
              <tbody>
                {(errorRows.length > 0 ? errorRows : preview.rows).map((r) => (
                  <tr key={r.rowNo}>
                    <td className="num">{r.rowNo}</td>
                    <td>{r.name ?? ''}</td>
                    <td>{r.siteCode ?? ''}</td>
                    <td className="num">{r.hireDate ?? ''}</td>
                    <td>
                      {r.errors.length === 0 ? <Badge tone="green">정상</Badge> : (
                        <ul className="import-errors">{r.errors.map((m) => <li key={m}>{m}</li>)}</ul>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      )}

      <div className="form__actions">
        <button type="button" className="glass pill" onClick={close}>닫기</button>
        <button type="button" className="btn btn--primary" onClick={confirm} disabled={busy || !preview?.confirmable}>
          {preview?.confirmable ? `${preview.totalRows}명 등록` : '등록'}
        </button>
      </div>
    </Modal>
  );
}

/** 사원 목록 내려받기. 서버가 권한에 맞는 범위·항목만 넣고 사원마다 다운로드 로그를 남긴다. */
export function EmployeeExportButton({ api, toast, save = saveFile }: {
  api: EmployeeImportApi; toast: (text: string) => void; save?: (f: DownloadedFile) => void;
}) {
  const [busy, setBusy] = useState(false);
  const run = async () => {
    setBusy(true);
    try { save(await api.exportList(true)); } catch (e) { toast(errText(e, '목록을 내려받지 못했어요')); } finally { setBusy(false); }
  };
  return <button className="glass pill" onClick={() => void run()} disabled={busy}><LuDownload /> 목록 내려받기</button>;
}
