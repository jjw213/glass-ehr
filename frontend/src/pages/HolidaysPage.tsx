import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import { LuChevronLeft, LuChevronRight, LuPlus, LuRepeat, LuTrash2, LuUpload } from 'react-icons/lu';
import { Badge, Card, CardHead, Modal, PageHead, Toggle, cx } from '../components/ui';
import {
  KIND_LABEL, REP_TYPE_LABEL, holidayApi, isIsoDate, isStatutory, parseHolidayCsv, seoulToday,
  type Agreement, type AgreementInput, type Holiday, type HolidayApi, type HolidayKind, type RepType,
} from '../api/holidays';
import { canManageHolidays } from '../nav';
import { useData, useStore } from '../state/store';

/** 휴일 달력 (#26). 모두 볼 수 있고, 등록·대체·불러오기는 인사담당자·관리자만(서버도 EMP_WRITE 로 막는다). */
export function HolidaysPage() {
  const { me } = useData();
  const { toast } = useStore();
  return <HolidayCalendar api={holidayApi} toast={toast} canManage={canManageHolidays(me.role)} today={seoulToday()} />;
}

const WEEK = ['일', '월', '화', '수', '목', '금', '토'];
const TONE: Record<HolidayKind, 'red' | 'amber' | 'blue' | 'green'> = { PUBLIC: 'red', SUBSTITUTE: 'amber', COMPANY: 'blue', REPLACEMENT: 'green' };
const pad = (n: number) => String(n).padStart(2, '0');
const iso = (y: number, m: number, d: number) => `${y}-${pad(m)}-${pad(d)}`;
/** YYYY-MM-DD 의 요일(0=일). 달력 날짜만 다루므로 UTC 로 계산한다(시간대 영향 없음). */
const weekday = (s: string) => new Date(`${s}T00:00:00Z`).getUTCDay();
const dayText = (s: string) => `${s.slice(5).replace('-', '.')}(${WEEK[weekday(s)]})`;

type Dialog = 'company' | 'substitute' | null;
interface AgreementDraft { title: string; repType: RepType; signedDate: string; effectiveFrom: string; effectiveTo: string; documentRef: string }
const emptyAgreement = (): AgreementDraft => ({ title: '', repType: 'MAJORITY_UNION', signedDate: '', effectiveFrom: '', effectiveTo: '', documentRef: '' });

/** 화면 본문. 스토어 없이 api·toast 만 받아서 테스트할 수 있다. */
export function HolidayCalendar({ api, toast, canManage, today }: { api: HolidayApi; toast: (t: string) => void; canManage: boolean; today: string }) {
  const [year, setYear] = useState(Number(today.slice(0, 4)));
  const [rows, setRows] = useState<Holiday[] | null>(null);
  const [agreements, setAgreements] = useState<Agreement[]>([]);
  const [reload, setReload] = useState(0);
  const [dialog, setDialog] = useState<Dialog>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  // 회사휴일
  const [company, setCompany] = useState({ date: '', name: '', paid: true, note: '' });
  // 휴일 대체
  const [subst, setSubst] = useState({ originalId: '', date: '', agreementId: '', note: '' });
  const [newAgreement, setNewAgreement] = useState<AgreementDraft | null>(null);

  const fail = useCallback((e: unknown) => toast(e instanceof Error ? e.message : '알 수 없는 오류가 발생했어요'), [toast]);

  useEffect(() => {
    let alive = true;
    setRows(null);
    api.year(year).then((r) => { if (alive) setRows(r); }, fail);
    return () => { alive = false; };
  }, [api, year, reload, fail]);

  useEffect(() => {
    if (!canManage) return;
    let alive = true;
    api.agreements().then((a) => { if (alive) setAgreements(a); }, fail);
    return () => { alive = false; };
  }, [api, canManage, reload, fail]);

  const byDate = useMemo(() => {
    const m = new Map<string, Holiday[]>();
    for (const r of rows ?? []) m.set(r.date, [...(m.get(r.date) ?? []), r]);
    return m;
  }, [rows]);
  const replaceable = (rows ?? []).filter((r) => isStatutory(r.kind) && r.effective);

  const open = (d: Dialog) => {
    setError(null);
    if (d === 'company') setCompany({ date: '', name: '', paid: true, note: '' });
    if (d === 'substitute') { setSubst({ originalId: '', date: '', agreementId: '', note: '' }); setNewAgreement(null); }
    setDialog(d);
  };
  const close = useCallback(() => setDialog(null), []);
  const done = (msg: string) => { toast(msg); setDialog(null); setReload((n) => n + 1); };

  const run = async (f: () => Promise<void>) => {
    setSaving(true);
    try { await f(); } catch (e) { setError(e instanceof Error ? e.message : '저장하지 못했어요'); } finally { setSaving(false); }
  };

  const submitCompany = (e: FormEvent) => {
    e.preventDefault();
    if (!isIsoDate(company.date)) { setError('날짜를 입력해 주세요'); return; }
    if (!company.name.trim()) { setError('휴일 이름을 입력해 주세요'); return; }
    void run(async () => {
      await api.createCompany({ date: company.date, name: company.name, paid: company.paid, note: company.note.trim() || null });
      done(`회사휴일 ${company.name.trim()}을 추가했어요`);
    });
  };

  const submitAgreement = () => {
    if (!newAgreement) return;
    const a = newAgreement;
    if (!a.title.trim()) { setError('합의서 제목을 입력해 주세요'); return; }
    if (!isIsoDate(a.signedDate) || !isIsoDate(a.effectiveFrom)) { setError('체결일·효력 시작일을 입력해 주세요'); return; }
    if (!a.documentRef.trim()) { setError('합의서 문서(파일 번호·보관 위치)를 입력해 주세요'); return; }
    const input: AgreementInput = { ...a, effectiveTo: a.effectiveTo || null, note: null };
    void run(async () => {
      const created = await api.createAgreement(input);
      setAgreements((l) => [created, ...l]);
      setSubst((s) => ({ ...s, agreementId: String(created.agreementId) }));
      setNewAgreement(null);
      setError(null);
    });
  };

  const submitSubst = (e: FormEvent) => {
    e.preventDefault();
    if (!subst.originalId) { setError('대체할 공휴일을 골라 주세요'); return; }
    if (!isIsoDate(subst.date)) { setError('대신 쉴 근로일을 입력해 주세요'); return; }
    if (!subst.agreementId) { setError('근로자대표 서면합의 문서를 연결해 주세요(근로기준법 제55조 제2항 단서)'); return; }
    void run(async () => {
      const r = await api.substitute({ originalHolidayId: Number(subst.originalId), substituteDate: subst.date, agreementId: Number(subst.agreementId), note: subst.note.trim() || null });
      done(`${r.replaces?.name ?? '공휴일'}을 ${dayText(r.date)}로 대체했어요`);
    });
  };

  const remove = async (r: Holiday) => {
    try {
      await api.remove(r.holidayId);
      toast(r.kind === 'REPLACEMENT' ? `휴일 대체를 취소했어요: ${r.name}` : `회사휴일을 지웠어요: ${r.name}`);
      setReload((n) => n + 1);
    } catch (e) { fail(e); }
  };

  const onFile = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    const text = await file.text();
    const parsed = parseHolidayCsv(text);
    if (typeof parsed === 'string') { toast(parsed); return; }
    try {
      const r = await api.importCsv(text);
      toast(`공휴일 ${r.inserted}건을 불러왔어요${r.skipped ? ` (이미 있는 ${r.skipped}건은 건너뜀)` : ''}`);
      setReload((n) => n + 1);
    } catch (err) { fail(err); }
  };

  const canRemove = (r: Holiday) => {
    if (r.kind === 'COMPANY') return r.date > today;
    if (r.kind === 'REPLACEMENT') return r.date > today && (r.replaces ? r.replaces.date > today : false);
    return false;
  };

  return (
    <>
      <PageHead
        title="휴일 달력"
        sub="관공서 공휴일·대체공휴일은 유급휴일이에요(근로기준법 제55조 제2항). 근로자대표와 서면합의하면 다른 근로일로 대체할 수 있어요"
        right={
          <div className="holiday-year" role="group" aria-label="연도">
            <button className="glass pill" onClick={() => setYear((y) => y - 1)} aria-label="이전 해"><LuChevronLeft /></button>
            <strong className="num" data-testid="year">{year}년</strong>
            <button className="glass pill" onClick={() => setYear((y) => y + 1)} aria-label="다음 해"><LuChevronRight /></button>
          </div>
        }
      />

      {canManage && (
        <Card i={1}>
          <CardHead
            title="휴일 관리"
            sub="공휴일은 사람이 확인한 자료(CSV: 날짜,이름,종류,출처,검증일)를 불러와서만 등록해요"
            right={
              <div className="holiday-actions">
                <button className="btn btn--ghost" onClick={() => fileRef.current?.click()}><LuUpload /> 공휴일 자료 불러오기</button>
                <button className="btn btn--ghost" onClick={() => open('company')}><LuPlus /> 회사휴일 추가</button>
                <button className="btn btn--primary" onClick={() => open('substitute')} disabled={rows === null}><LuRepeat /> 휴일 대체</button>
              </div>
            }
          />
          <input ref={fileRef} type="file" accept=".csv,text/csv" hidden aria-label="공휴일 CSV 파일" onChange={onFile} />
        </Card>
      )}

      <Card i={2}>
        <CardHead
          title={`${year}년 달력`}
          sub={rows ? `휴일 ${rows.filter((r) => r.effective).length}일` : '불러오는 중…'}
          right={<div className="holiday-legend">{(Object.keys(KIND_LABEL) as HolidayKind[]).map((k) => <Badge key={k} tone={TONE[k]}>{KIND_LABEL[k]}</Badge>)}</div>}
        />
        <div className="holiday-months">
          {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => {
            const days = new Date(Date.UTC(year, m, 0)).getUTCDate();
            const lead = weekday(iso(year, m, 1));
            return (
              <section key={m} className="holiday-month" aria-label={`${m}월`}>
                <h4>{m}월</h4>
                <div className="holiday-grid">
                  {WEEK.map((w) => <span key={w} className="holiday-grid__wd">{w}</span>)}
                  {Array.from({ length: lead }, (_, i) => <span key={`b${i}`} />)}
                  {Array.from({ length: days }, (_, i) => {
                    const d = iso(year, m, i + 1);
                    const hs = byDate.get(d) ?? [];
                    const on = hs.find((h) => h.effective);
                    const off = hs.find((h) => !h.effective);
                    const title = hs.map((h) => `${h.name}(${h.effective ? h.kindLabel : `${h.kindLabel} → 근로일`})`).join(', ');
                    return (
                      <span key={d} title={title || undefined} data-date={d}
                        className={cx('holiday-day', on && `holiday-day--${on.kind}`, !on && off && 'holiday-day--moved', d === today && 'holiday-day--today')}>
                        {i + 1}
                      </span>
                    );
                  })}
                </div>
              </section>
            );
          })}
        </div>
      </Card>

      <Card i={3}>
        <CardHead title="휴일 목록" sub={rows ? `${rows.length}건` : ''} />
        {rows === null ? (
          <p className="muted code-empty">불러오는 중…</p>
        ) : rows.length === 0 ? (
          <p className="muted code-empty">{year}년 휴일이 없어요. {canManage ? '공휴일 자료를 불러와 주세요' : '인사담당자가 공휴일 자료를 등록하면 보여요'}</p>
        ) : (
          <table className="code-table">
            <thead><tr><th>날짜</th><th>이름</th><th>종류</th><th className="hide-sm">유급</th><th className="hide-sm">내용</th><th /></tr></thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.holidayId} className={cx(!r.effective && 'off')}>
                  <td className="num">{dayText(r.date)}</td>
                  <td>{r.name}{!r.effective && <> <Badge tone="neutral">근로일로 대체됨</Badge></>}</td>
                  <td><Badge tone={TONE[r.kind]}>{r.kindLabel}</Badge></td>
                  <td className="hide-sm">{r.paid ? '유급' : '무급'}</td>
                  <td className="hide-sm muted sm">
                    {r.replacedBy && <>→ {dayText(r.replacedBy.date)}에 대신 쉼</>}
                    {r.replaces && <>{dayText(r.replaces.date)} {r.replaces.name} 대신 · 서면합의: {r.agreementTitle ?? `#${r.agreementId}`}</>}
                    {!r.replacedBy && !r.replaces && (r.sourceRef ? `출처: ${r.sourceRef} (검증 ${r.verifiedDate})` : r.note ?? '')}
                  </td>
                  <td className="code-table__actions">
                    {canManage && canRemove(r) && (
                      <button className="btn btn--ghost icon-only" onClick={() => void remove(r)} aria-label={`${r.name} ${r.kind === 'REPLACEMENT' ? '대체 취소' : '삭제'}`}><LuTrash2 /></button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      <Modal open={dialog === 'company'} onClose={close} title="회사휴일 추가">
        <form className="form" onSubmit={submitCompany} noValidate>
          <div className="form__row">
            <label className="field"><span className="field__label">날짜</span>
              <input className="input" type="date" aria-label="회사휴일 날짜" value={company.date} onChange={(e) => setCompany({ ...company, date: e.target.value })} /></label>
            <label className="field"><span className="field__label">이름</span>
              <input className="input" aria-label="회사휴일 이름" maxLength={100} value={company.name} placeholder="예: 창립기념일" onChange={(e) => setCompany({ ...company, name: e.target.value })} /></label>
          </div>
          <div className="form__row">
            <div className="field"><span className="field__label">유급 (취업규칙·단체협약에 따라)</span>
              <Toggle checked={company.paid} onChange={(v) => setCompany({ ...company, paid: v })} label="유급" /></div>
            <label className="field"><span className="field__label">비고 (선택)</span>
              <input className="input" maxLength={500} value={company.note} onChange={(e) => setCompany({ ...company, note: e.target.value })} /></label>
          </div>
          {error && <p className="badge badge--red" role="alert">{error}</p>}
          <div className="form__actions">
            <button type="button" className="btn btn--ghost" onClick={close}>취소</button>
            <button type="submit" className="btn btn--primary" disabled={saving}>저장</button>
          </div>
        </form>
      </Modal>

      <Modal open={dialog === 'substitute'} onClose={close} title="휴일 대체">
        <form className="form" onSubmit={submitSubst} noValidate>
          <p className="muted sm">근로자대표와 서면으로 합의한 경우에만 공휴일을 특정한 근로일로 대체할 수 있어요(근로기준법 제55조 제2항 단서). 원래 공휴일은 근로일이 되고, 대신 쉬는 날이 유급휴일이 돼요.</p>
          <div className="form__row">
            <label className="field"><span className="field__label">대체할 공휴일</span>
              <select className="input" aria-label="대체할 공휴일" value={subst.originalId} onChange={(e) => setSubst({ ...subst, originalId: e.target.value })}>
                <option value="">골라 주세요</option>
                {replaceable.map((r) => <option key={r.holidayId} value={r.holidayId}>{dayText(r.date)} {r.name}</option>)}
              </select></label>
            <label className="field"><span className="field__label">대신 쉴 근로일</span>
              <input className="input" type="date" aria-label="대신 쉴 근로일" value={subst.date} onChange={(e) => setSubst({ ...subst, date: e.target.value })} /></label>
          </div>
          <label className="field"><span className="field__label">근로자대표 서면합의 (필수)</span>
            <select className="input" aria-label="서면합의" value={subst.agreementId} onChange={(e) => setSubst({ ...subst, agreementId: e.target.value })}>
              <option value="">골라 주세요</option>
              {agreements.map((a) => <option key={a.agreementId} value={a.agreementId}>{a.title} ({a.signedDate} 체결, {a.effectiveFrom}~{a.effectiveTo ?? ''})</option>)}
            </select></label>
          {newAgreement === null ? (
            <button type="button" className="btn btn--ghost" onClick={() => setNewAgreement(emptyAgreement())}><LuPlus /> 서면합의 등록</button>
          ) : (
            <fieldset className="holiday-agreement" aria-label="서면합의 등록">
              <div className="form__row">
                <label className="field"><span className="field__label">합의서 제목</span>
                  <input className="input" aria-label="합의서 제목" maxLength={200} value={newAgreement.title} onChange={(e) => setNewAgreement({ ...newAgreement, title: e.target.value })} /></label>
                <label className="field"><span className="field__label">근로자대표</span>
                  <select className="input" aria-label="근로자대표 구분" value={newAgreement.repType} onChange={(e) => setNewAgreement({ ...newAgreement, repType: e.target.value as RepType })}>
                    {(Object.keys(REP_TYPE_LABEL) as RepType[]).map((k) => <option key={k} value={k}>{REP_TYPE_LABEL[k]}</option>)}
                  </select></label>
              </div>
              <div className="form__row">
                <label className="field"><span className="field__label">체결일</span>
                  <input className="input" type="date" aria-label="체결일" value={newAgreement.signedDate} onChange={(e) => setNewAgreement({ ...newAgreement, signedDate: e.target.value })} /></label>
                <label className="field"><span className="field__label">효력 시작일</span>
                  <input className="input" type="date" aria-label="효력 시작일" value={newAgreement.effectiveFrom} onChange={(e) => setNewAgreement({ ...newAgreement, effectiveFrom: e.target.value })} /></label>
                <label className="field"><span className="field__label">효력 종료일(그날 전까지, 선택)</span>
                  <input className="input" type="date" aria-label="효력 종료일" value={newAgreement.effectiveTo} onChange={(e) => setNewAgreement({ ...newAgreement, effectiveTo: e.target.value })} /></label>
              </div>
              <label className="field"><span className="field__label">합의서 문서 (파일 번호·보관 위치, 필수)</span>
                <input className="input" aria-label="합의서 문서" maxLength={500} value={newAgreement.documentRef} onChange={(e) => setNewAgreement({ ...newAgreement, documentRef: e.target.value })} /></label>
              <div className="form__actions">
                <button type="button" className="btn btn--ghost" onClick={() => setNewAgreement(null)}>닫기</button>
                <button type="button" className="btn btn--primary" onClick={submitAgreement} disabled={saving}>합의 등록</button>
              </div>
            </fieldset>
          )}
          <label className="field"><span className="field__label">비고 (선택)</span>
            <input className="input" maxLength={500} value={subst.note} onChange={(e) => setSubst({ ...subst, note: e.target.value })} /></label>
          {error && <p className="badge badge--red" role="alert">{error}</p>}
          <div className="form__actions">
            <button type="button" className="btn btn--ghost" onClick={close}>취소</button>
            <button type="submit" className="btn btn--primary" disabled={saving}>대체 등록</button>
          </div>
        </form>
      </Modal>
    </>
  );
}
