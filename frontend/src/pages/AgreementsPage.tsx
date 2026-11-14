import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { LuBellRing, LuDownload, LuPaperclip, LuPencil, LuPlus, LuRefreshCw, LuShieldAlert, LuTrash2 } from 'react-icons/lu';
import { Badge, Card, CardHead, Modal, PageHead, cx } from '../components/ui';
import {
  ACCEPT_FILES, REP_LABEL, STATUS_LABEL, agreementApi, seoulToday, validateAgreement,
  type Agreement, type AgreementApi, type AgreementDetail, type AgreementInput, type AgreementStatus,
  type AgreementType, type CheckResult, type Expiring, type RepType, type Scope, type TypeView,
} from '../api/agreements';
import { saveFile } from '../api/employeeImport';
import { canManageAgreements } from '../nav';
import { useData, useStore } from '../state/store';

/** 근로자대표 서면합의서 관리 (#27). 인사담당자·관리자만. 서버가 권한을 다시 검사한다. */
export function AgreementsPage() {
  const { me } = useData();
  const { toast } = useStore();
  if (!canManageAgreements(me.role)) {
    return (
      <>
        <PageHead title="서면합의서 관리" />
        <Card i={1}>
          <CardHead title="인사담당자만 볼 수 있어요" sub="근로자대표 서면합의서는 인사담당자·관리자 계정으로 관리해요" right={<LuShieldAlert className="muted" />} />
        </Card>
      </>
    );
  }
  return <AgreementManager api={agreementApi} toast={toast} save={saveFile} />;
}

interface Draft {
  agreementType: AgreementType; title: string; signedDate: string; validFrom: string; validUntil: string;
  scopeType: Scope; scopeSiteCode: string; targetDesc: string; repType: RepType; repUnionName: string;
  repEmpId: string; note: string; documentRef: string;
}
type Editing = { mode: 'create' } | { mode: 'edit'; row: Agreement };

const emptyDraft = (): Draft => {
  const t = seoulToday();
  return {
    agreementType: 'FLEXIBLE_WRITTEN', title: '', signedDate: t, validFrom: t, validUntil: '', scopeType: 'ALL',
    scopeSiteCode: '', targetDesc: '', repType: 'MAJORITY_UNION', repUnionName: '', repEmpId: '', note: '', documentRef: '',
  };
};
const fromRow = (r: Agreement): Draft => ({
  agreementType: r.agreementType, title: r.title, signedDate: r.signedDate, validFrom: r.validFrom,
  validUntil: r.validUntil ?? '', scopeType: r.scopeType, scopeSiteCode: r.scopeSiteCode ?? '', targetDesc: r.targetDesc ?? '',
  repType: r.repType, repUnionName: r.repUnionName ?? '', repEmpId: r.repEmpId === null ? '' : String(r.repEmpId), note: r.note ?? '',
  documentRef: r.documentRef ?? '',
});
export const toInput = (d: Draft): AgreementInput => ({
  agreementType: d.agreementType, title: d.title, signedDate: d.signedDate, validFrom: d.validFrom,
  validUntil: d.validUntil.trim() || null, scopeType: d.scopeType,
  scopeSiteCode: d.scopeType === 'SITE' ? d.scopeSiteCode.trim().toUpperCase() || null : null,
  targetDesc: d.targetDesc, repType: d.repType,
  repUnionName: d.repType === 'MAJORITY_UNION' ? d.repUnionName : null,
  repEmpId: d.repType === 'MAJORITY_REP' && d.repEmpId.trim() ? Number(d.repEmpId) : null,
  note: d.note.trim() || null,
  documentRef: d.documentRef,
});

const STATUS_TONE: Record<AgreementStatus, 'green' | 'blue' | 'neutral'> = { ACTIVE: 'green', UPCOMING: 'blue', EXPIRED: 'neutral' };
const period = (a: { validFrom: string; validUntil: string | null }) => `${a.validFrom} ~ ${a.validUntil ?? '기간 정함 없음'}`;
const repText = (a: Agreement) =>
  a.repType === 'MAJORITY_UNION' ? `${REP_LABEL.MAJORITY_UNION} · ${a.repUnionName ?? ''}`
    : `${REP_LABEL.MAJORITY_REP} · ${a.repEmpName ?? `사원 ${a.repEmpId}`}${a.repEmpNo ? ` (${a.repEmpNo})` : ''}`;
const kb = (n: number) => (n < 1024 ? `${n}B` : n < 1024 * 1024 ? `${Math.round(n / 1024)}KB` : `${(n / 1024 / 1024).toFixed(1)}MB`);

/** 화면 본문. 스토어 없이 api·toast·save 만 받아서 테스트할 수 있다. */
export function AgreementManager({ api, toast, save }: {
  api: AgreementApi; toast: (text: string) => void; save: (f: { blob: Blob; filename: string }) => void;
}) {
  const [rows, setRows] = useState<Agreement[] | null>(null);
  const [types, setTypes] = useState<TypeView[]>([]);
  const [expiring, setExpiring] = useState<Expiring | null>(null);
  const [editing, setEditing] = useState<Editing | null>(null);
  const [draft, setDraft] = useState<Draft>(emptyDraft());
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [detail, setDetail] = useState<AgreementDetail | null>(null);
  const [uploading, setUploading] = useState(false);
  const [reload, setReload] = useState(0);
  const [query, setQuery] = useState({ type: 'FLEXIBLE_WRITTEN' as AgreementType, from: seoulToday(), until: '', siteCode: '' });
  const [check, setCheck] = useState<CheckResult | null>(null);

  const fail = useCallback((e: unknown) => toast(e instanceof Error ? e.message : '알 수 없는 오류가 발생했어요'), [toast]);

  useEffect(() => {
    let alive = true;
    api.list().then((r) => { if (alive) setRows(r); }, fail);
    api.expiring().then((r) => { if (alive) setExpiring(r); }, fail);
    return () => { alive = false; };
  }, [api, reload, fail]);
  useEffect(() => { api.types().then(setTypes, fail); }, [api, fail]);

  const open = (e: Editing, d: Draft) => { setDraft(d); setFormError(null); setEditing(e); };
  const close = useCallback(() => setEditing(null), []);
  const closeDetail = useCallback(() => setDetail(null), []);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!editing) return;
    const input = toInput(draft);
    const err = validateAgreement(input);
    if (err) { setFormError(err); return; }
    setFormError(null);
    setSaving(true);
    try {
      if (editing.mode === 'create') await api.create(input);
      else await api.update(editing.row.agreementId, input);
      toast(editing.mode === 'create' ? '합의서를 등록했어요' : '합의서를 수정했어요');
      setEditing(null);
      setReload((n) => n + 1);
    } catch (e2) {
      setFormError(e2 instanceof Error ? e2.message : '저장하지 못했어요');
    } finally {
      setSaving(false);
    }
  };

  const openDetail = (id: number) => api.get(id).then(setDetail, fail);
  const refreshDetail = async (id: number) => { setDetail(await api.get(id)); setReload((n) => n + 1); };

  const upload = async (file: File | undefined) => {
    if (!file || !detail) return;
    setUploading(true);
    try {
      await api.upload(detail.agreement.agreementId, file);
      toast(`${file.name}을 첨부했어요`);
      await refreshDetail(detail.agreement.agreementId);
    } catch (e) { fail(e); } finally { setUploading(false); }
  };
  const remove = async (fileId: number) => {
    if (!detail) return;
    try {
      await api.removeFile(detail.agreement.agreementId, fileId);
      toast('첨부파일을 지웠어요');
      await refreshDetail(detail.agreement.agreementId);
    } catch (e) { fail(e); }
  };

  const runCheck = async (e: FormEvent) => {
    e.preventDefault();
    try {
      setCheck(await api.check({ type: query.type, from: query.from, until: query.until || null, siteCode: query.siteCode.trim().toUpperCase() || null }));
    } catch (e2) { setCheck(null); fail(e2); }
  };

  const set = <K extends keyof Draft>(k: K, v: Draft[K]) => setDraft((d) => ({ ...d, [k]: v }));
  const typeOptions = types.map((t) => <option key={t.code} value={t.code}>{t.label} ({t.article})</option>);

  return (
    <>
      <PageHead
        title="서면합의서 관리"
        sub="탄력적·선택적 근로시간제, 재량근로, 보상휴가제, 연차·휴일 대체에 필요한 근로자대표 서면합의서를 등록해요. 제도를 켤 때 유효한 합의서가 있는지 서버가 검사해요"
        right={<button className="glass pill" onClick={() => setReload((n) => n + 1)} aria-label="새로 고침"><LuRefreshCw /> 새로 고침</button>}
      />

      {expiring && expiring.agreements.length > 0 && (
        <Card i={1}>
          <CardHead title={`${expiring.days}일 안에 만료되는 합의서 ${expiring.agreements.length}건`} sub="만료 전에 새 합의서를 체결해 등록해 주세요. 만료되면 그 합의가 필요한 제도를 새로 켤 수 없어요" right={<LuBellRing />} />
          <ul className="agreement-expiring" role="alert" aria-label="만료 예정 합의서">
            {expiring.agreements.map((a) => (
              <li key={a.agreementId}>
                <Badge tone="amber">{a.daysUntilExpiry === 0 ? '오늘 만료' : `${a.daysUntilExpiry}일 남음`}</Badge> {a.typeLabel} · {a.title} (~{a.validUntil})
              </li>
            ))}
          </ul>
        </Card>
      )}

      <Card i={2}>
        <CardHead
          title="합의서"
          sub={rows ? `${rows.length}건` : '불러오는 중…'}
          right={<button className="btn btn--primary" onClick={() => open({ mode: 'create' }, emptyDraft())} disabled={rows === null}><LuPlus /> 합의서 등록</button>}
        />
        {rows === null ? (
          <p className="muted code-empty">불러오는 중…</p>
        ) : rows.length === 0 ? (
          <p className="muted code-empty">등록된 합의서가 없어요</p>
        ) : (
          <table className="code-table">
            <thead>
              <tr><th>유형</th><th>제목</th><th>유효기간</th><th className="hide-sm">대상</th><th className="hide-sm">근로자대표</th><th>상태</th><th /></tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.agreementId} className={cx(r.status === 'EXPIRED' && 'off')}>
                  <td>{r.typeLabel}<br /><span className="muted sm">{r.article}</span></td>
                  <td>{r.title} {r.fileCount > 0 && <Badge tone="blue"><LuPaperclip /> {r.fileCount}</Badge>}</td>
                  <td className="num">{period(r)}</td>
                  <td className="hide-sm muted sm">{r.scopeType === 'ALL' ? '회사 전체' : `사업장 ${r.scopeSiteCode}`} · {r.targetDesc ?? '대상 미입력'}</td>
                  <td className="hide-sm muted sm">{repText(r)}</td>
                  <td><Badge tone={STATUS_TONE[r.status]}>{STATUS_LABEL[r.status]}</Badge></td>
                  <td className="code-table__actions">
                    <button className="btn btn--ghost icon-only" onClick={() => openDetail(r.agreementId)} aria-label={`${r.title} 첨부`}><LuPaperclip /></button>
                    <button className="btn btn--ghost icon-only" onClick={() => open({ mode: 'edit', row: r }, fromRow(r))} aria-label={`${r.title} 수정`}><LuPencil /></button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      <Card i={3}>
        <CardHead title="유효한 합의서 확인" sub="제도를 적용할 기간과 사업장에 유효한 합의서가 있는지 확인해요(제도 설정 화면과 같은 검사)" />
        <form className="form" onSubmit={runCheck} noValidate>
          <div className="form__row">
            <label className="field">
              <span className="field__label">제도</span>
              <select className="input" aria-label="확인할 제도" value={query.type} onChange={(e) => setQuery({ ...query, type: e.target.value as AgreementType })}>{typeOptions}</select>
            </label>
            <label className="field">
              <span className="field__label">사업장 코드 (비우면 회사 전체)</span>
              <input className="input" aria-label="확인할 사업장" value={query.siteCode} maxLength={3} placeholder="예: S01" onChange={(e) => setQuery({ ...query, siteCode: e.target.value })} />
            </label>
          </div>
          <div className="form__row">
            <label className="field">
              <span className="field__label">적용 시작일</span>
              <input className="input" type="date" aria-label="적용 시작일" value={query.from} onChange={(e) => setQuery({ ...query, from: e.target.value })} />
            </label>
            <label className="field">
              <span className="field__label">적용 종료일 (비우면 기간 정함 없음)</span>
              <input className="input" type="date" aria-label="적용 종료일" value={query.until} onChange={(e) => setQuery({ ...query, until: e.target.value })} />
            </label>
          </div>
          <div className="form__actions"><button type="submit" className="btn btn--primary">확인</button></div>
          {check && (
            <p className={cx('badge', check.ok ? 'badge--green' : 'badge--red')} role="status" data-testid="check-result">
              {check.ok ? `유효한 합의서가 있어요: ${check.title} (${period({ validFrom: check.validFrom!, validUntil: check.validUntil })})` : check.message}
            </p>
          )}
        </form>
      </Card>

      <Modal open={editing !== null} onClose={close} title={editing?.mode === 'edit' ? '합의서 수정' : '합의서 등록'}>
        <form className="form" onSubmit={submit} noValidate>
          <label className="field">
            <span className="field__label">합의 유형 {editing?.mode === 'edit' && '(등록 후 바꿀 수 없어요)'}</span>
            <select className="input" aria-label="합의 유형" value={draft.agreementType} disabled={editing?.mode === 'edit'}
              onChange={(e) => set('agreementType', e.target.value as AgreementType)}>{typeOptions}</select>
          </label>
          <label className="field">
            <span className="field__label">제목</span>
            <input className="input" aria-label="제목" value={draft.title} maxLength={200} onChange={(e) => set('title', e.target.value)} placeholder="예: 2027년 생산직 탄력적 근로시간제 합의서" />
          </label>
          <div className="form__row">
            <label className="field">
              <span className="field__label">체결일</span>
              <input className="input" type="date" aria-label="체결일" value={draft.signedDate} onChange={(e) => set('signedDate', e.target.value)} />
            </label>
            <label className="field">
              <span className="field__label">유효기간 시작</span>
              <input className="input" type="date" aria-label="유효기간 시작" value={draft.validFrom} onChange={(e) => set('validFrom', e.target.value)} />
            </label>
            <label className="field">
              <span className="field__label">유효기간 끝 (그날 포함, 비우면 정함 없음)</span>
              <input className="input" type="date" aria-label="유효기간 끝" value={draft.validUntil} onChange={(e) => set('validUntil', e.target.value)} />
            </label>
          </div>
          <div className="form__row">
            <label className="field">
              <span className="field__label">대상 범위</span>
              <select className="input" aria-label="대상 범위" value={draft.scopeType} onChange={(e) => set('scopeType', e.target.value as Scope)}>
                <option value="ALL">회사 전체</option>
                <option value="SITE">사업장 한 곳</option>
              </select>
            </label>
            {draft.scopeType === 'SITE' && (
              <label className="field">
                <span className="field__label">사업장 코드</span>
                <input className="input" aria-label="사업장 코드" value={draft.scopeSiteCode} maxLength={3} placeholder="예: S01" onChange={(e) => set('scopeSiteCode', e.target.value)} />
              </label>
            )}
          </div>
          <label className="field">
            <span className="field__label">대상 근로자 범위 (합의서에 적힌 대로)</span>
            <input className="input" aria-label="대상 근로자 범위" value={draft.targetDesc} maxLength={1000} onChange={(e) => set('targetDesc', e.target.value)} placeholder="예: 생산1팀 교대 근무자 전원" />
          </label>
          <div className="form__row">
            <label className="field">
              <span className="field__label">근로자대표 (근로기준법 제24조 제3항)</span>
              <select className="input" aria-label="근로자대표 구분" value={draft.repType} onChange={(e) => set('repType', e.target.value as RepType)}>
                <option value="MAJORITY_UNION">{REP_LABEL.MAJORITY_UNION}</option>
                <option value="MAJORITY_REP">{REP_LABEL.MAJORITY_REP}</option>
              </select>
            </label>
            {draft.repType === 'MAJORITY_UNION' ? (
              <label className="field">
                <span className="field__label">노동조합 이름</span>
                <input className="input" aria-label="노동조합 이름" value={draft.repUnionName} maxLength={200} onChange={(e) => set('repUnionName', e.target.value)} />
              </label>
            ) : (
              <label className="field">
                <span className="field__label">대표자 사원 ID (이름은 사원 정보에서 불러와요)</span>
                <input className="input" inputMode="numeric" aria-label="대표자 사원 ID" value={draft.repEmpId} onChange={(e) => set('repEmpId', e.target.value.replace(/[^0-9]/g, ''))} />
              </label>
            )}
          </div>
          <label className="field">
            <span className="field__label">비고 (선택)</span>
            <input className="input" aria-label="비고" value={draft.note} maxLength={500} onChange={(e) => set('note', e.target.value)} />
          </label>
          <label className="field">
            <span className="field__label">합의서 문서 (문서 번호·보관 위치, 스캔본은 첨부로 따로 올려요)</span>
            <input className="input" aria-label="합의서 문서" value={draft.documentRef} maxLength={500} onChange={(e) => set('documentRef', e.target.value)} placeholder="예: 인사팀 문서함 2027-003" />
          </label>
          {formError && <p className="badge badge--red" role="alert">{formError}</p>}
          <div className="form__actions">
            <button type="button" className="btn btn--ghost" onClick={close}>취소</button>
            <button type="submit" className="btn btn--primary" disabled={saving}>{saving ? '저장 중…' : '저장'}</button>
          </div>
        </form>
      </Modal>

      <Modal open={detail !== null} onClose={closeDetail} title="합의서 첨부파일">
        {detail && (
          <div className="form">
            <p className="sm">{detail.agreement.typeLabel} · {detail.agreement.title}<br /><span className="muted">{period(detail.agreement)}</span></p>
            {detail.files.length === 0 ? (
              <p className="muted sm">첨부된 파일이 없어요. 서명된 합의서를 스캔해 올려 주세요</p>
            ) : (
              <ul className="agreement-files" aria-label="첨부파일 목록">
                {detail.files.map((f) => (
                  <li key={f.fileId}>
                    <span>{f.fileName} <span className="muted sm">{kb(f.fileSize)} · {f.uploadedBy}</span></span>
                    <button type="button" className="btn btn--ghost icon-only" aria-label={`${f.fileName} 내려받기`}
                      onClick={() => api.download(detail.agreement.agreementId, f.fileId).then(save, fail)}><LuDownload /></button>
                    <button type="button" className="btn btn--ghost icon-only" aria-label={`${f.fileName} 삭제`} onClick={() => remove(f.fileId)}><LuTrash2 /></button>
                  </li>
                ))}
              </ul>
            )}
            <label className="field">
              <span className="field__label">파일 첨부 (PDF·PNG·JPG, 10MB 이하)</span>
              <input className="input" type="file" aria-label="첨부할 파일" accept={ACCEPT_FILES} disabled={uploading}
                onChange={(e) => { const f = e.target.files?.[0]; e.target.value = ''; void upload(f); }} />
            </label>
          </div>
        )}
      </Modal>
    </>
  );
}
