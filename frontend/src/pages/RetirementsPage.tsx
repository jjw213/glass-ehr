import { useEffect, useState, type FormEvent } from 'react';
import { LuPrinter, LuShieldAlert } from 'react-icons/lu';
import { Badge, Card, CardHead, PageHead } from '../components/ui';
import { seoulToday } from '../api/agreements';
import { CODE_GROUP } from '../api/codes';
import {
  CERT_ITEM_LABEL, CERT_ITEMS, DISMISSAL_SYS_TYPE, EXEMPT_LABEL, NOTICE_LABEL, retirementApi, toRetirementBody, validateRetirement,
  WAGE_UNIT_LABEL, won,
  type Certificate, type CertificateItem, type ChecklistTemplateItem, type NoticeExempt, type RetentionExpiredRow,
  type Retirement, type RetirementApi,
} from '../api/retirements';
import { useCodes } from '../hooks/useCodes';
import { canManageRetirements } from '../nav';
import { useData } from '../state/store';

/**
 * 퇴직 처리 (#18, 인사담당자·관리자). 퇴직 유형별 체크리스트, 해고 예고·서면통지 확인(근로기준법 제26조·제27조),
 * 사용증명서 발급·인쇄(제39조), 보존기한이 지난 퇴직자 서류 목록(제42조). 퇴직금 계산은 급여 모듈에서 한다.
 * 화면은 메뉴만 숨기고, 실제 차단은 서버(EMP_WRITE·EMP_READ_ALL)가 한다.
 */
export function RetirementsPage() {
  const { me } = useData();
  const { codes } = useCodes(CODE_GROUP.RETIREMENT_REASON);
  if (!canManageRetirements(me.role)) {
    return (
      <>
        <PageHead title="퇴직 처리" />
        <Card i={1}>
          <CardHead title="인사담당자만 볼 수 있어요" sub="퇴직 처리는 인사·관리자 계정으로 해요" right={<LuShieldAlert className="muted" />} />
        </Card>
      </>
    );
  }
  return <RetirementWorkspace api={retirementApi} reasons={codes.map((c) => ({ code: c.code, name: c.name, sysType: c.sysType }))} today={seoulToday()} />;
}

/** @property sysType 퇴직사유 처리방식(#141). 해고 여부는 코드값이 아니라 이것으로 판단한다 */
export interface Reason { code: string; name: string; sysType: string | null }

const errorOf = (e: unknown, fallback: string) => (e instanceof Error ? e.message : fallback);

/** 화면 본문. 스토어 없이 api 만 받아서 테스트할 수 있다. */
export function RetirementWorkspace({ api, reasons, today }: { api: RetirementApi; reasons: Reason[]; today: string }) {
  const [current, setCurrent] = useState<Retirement | null>(null);
  const reasonName = (code: string) => reasons.find((r) => r.code === code)?.name ?? code;
  return (
    <>
      <PageHead title="퇴직 처리" sub="퇴직 기록·체크리스트·사용증명서·서류 보존기한을 관리해요. 퇴직금은 급여 모듈에서 계산해요" />
      <RegisterCard api={api} reasons={reasons} today={today} onDone={setCurrent} />
      <CaseCard api={api} current={current} onChange={setCurrent} reasonName={reasonName} />
      <CertificateCard api={api} today={today} />
      <RetentionCard api={api} today={today} reasonName={reasonName} />
    </>
  );
}

// ---------------------------------------------------------------- 등록

function RegisterCard({ api, reasons, today, onDone }: {
  api: RetirementApi; reasons: Reason[]; today: string; onDone: (r: Retirement) => void;
}) {
  const [empId, setEmpId] = useState('');
  const [retireDate, setRetireDate] = useState(today);
  const [reasonCode, setReasonCode] = useState('');
  const [reasonDetail, setReasonDetail] = useState('');
  const [noticeDate, setNoticeDate] = useState('');
  const [writtenDate, setWrittenDate] = useState('');
  const [exempt, setExempt] = useState<NoticeExempt | ''>('');
  const [template, setTemplate] = useState<ChecklistTemplateItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const dismissal = reasons.find((r) => r.code === reasonCode)?.sysType === DISMISSAL_SYS_TYPE;

  useEffect(() => {
    if (!reasonCode) { setTemplate([]); return; }
    let alive = true;
    api.template(reasonCode).then((t) => { if (alive) setTemplate(t); }).catch(() => { if (alive) setTemplate([]); });
    return () => { alive = false; };
  }, [api, reasonCode]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const input = {
      empId: Number(empId.trim()), retireDate, reasonCode, reasonDetail,
      dismissalNoticeDate: noticeDate || null, writtenNoticeDate: writtenDate || null, noticeExempt: exempt || null,
    };
    const err = validateRetirement(input, dismissal);
    if (err) { setError(err); return; }
    setBusy(true);
    setError(null);
    try {
      onDone(await api.register(toRetirementBody(input, dismissal)));
    } catch (ex) {
      setError(errorOf(ex, '퇴직 처리를 하지 못했어요'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card i={1}>
      <CardHead title="퇴직 처리 등록" sub="근로자 명부에 퇴직일·사유·보존기한을 기록하고 유형별 체크리스트를 만들어요" />
      <form className="form" onSubmit={submit} noValidate aria-label="퇴직 처리 등록">
        <label className="field">
          <span className="field__label">사원 ID(empId)</span>
          <input className="input" inputMode="numeric" value={empId} onChange={(e) => setEmpId(e.target.value)} autoComplete="off" />
        </label>
        <label className="field">
          <span className="field__label">퇴직일(해고일)</span>
          <input className="input" type="date" value={retireDate} onChange={(e) => setRetireDate(e.target.value)} />
        </label>
        <label className="field">
          <span className="field__label">퇴직 유형</span>
          <select className="input" value={reasonCode} onChange={(e) => setReasonCode(e.target.value)}>
            <option value="">선택</option>
            {reasons.map((r) => <option key={r.code} value={r.code}>{r.name}</option>)}
          </select>
        </label>
        <label className="field">
          <span className="field__label">사유 상세(선택)</span>
          <input className="input" value={reasonDetail} onChange={(e) => setReasonDetail(e.target.value)} maxLength={1000} />
        </label>
        {dismissal && (
          <fieldset className="field" aria-label="해고 예고·서면통지">
            <legend className="field__label">해고 예고·서면통지 (필수)</legend>
            <label className="field">
              <span className="field__label">해고 예고일 (근로기준법 제26조)</span>
              <input className="input" type="date" value={noticeDate} onChange={(e) => setNoticeDate(e.target.value)} />
            </label>
            <label className="field">
              <span className="field__label">해고사유·시기 서면통지일 (제27조)</span>
              <input className="input" type="date" value={writtenDate} onChange={(e) => setWrittenDate(e.target.value)} />
            </label>
            <label className="field">
              <span className="field__label">예고 적용 제외 사유(해당할 때만)</span>
              <select className="input" value={exempt} onChange={(e) => setExempt(e.target.value as NoticeExempt | '')}>
                <option value="">해당 없음</option>
                {(Object.keys(EXEMPT_LABEL) as NoticeExempt[]).map((k) => <option key={k} value={k}>{EXEMPT_LABEL[k]}</option>)}
              </select>
            </label>
          </fieldset>
        )}
        <button className="btn btn--primary" type="submit" disabled={busy}>퇴직 처리</button>
      </form>
      {error && <p className="badge badge--red" role="alert">{error}</p>}
      {template.length > 0 && (
        <div aria-label="체크리스트 미리보기">
          <p className="muted sm">이 유형의 체크리스트</p>
          <ul>{template.map((t) => <li key={t.itemCode}>{t.label}{t.legalBasis ? ` · ${t.legalBasis}` : ''}</li>)}</ul>
        </div>
      )}
    </Card>
  );
}

// ---------------------------------------------------------------- 퇴직 처리 1건

function CaseCard({ api, current, onChange, reasonName }: {
  api: RetirementApi; current: Retirement | null; onChange: (r: Retirement) => void; reasonName: (c: string) => string;
}) {
  const [empId, setEmpId] = useState('');
  const [error, setError] = useState<string | null>(null);

  const load = async (e: FormEvent) => {
    e.preventDefault();
    const id = Number(empId.trim());
    if (!Number.isInteger(id) || id <= 0) { setError('사원 ID(숫자)를 입력해 주세요'); return; }
    setError(null);
    try { onChange(await api.get(id)); } catch (ex) { setError(errorOf(ex, '퇴직 처리 기록을 불러오지 못했어요')); }
  };

  const toggle = async (itemCode: string, done: boolean) => {
    if (!current) return;
    setError(null);
    try { onChange(await api.mark(current.empId, itemCode, done, null)); } catch (ex) { setError(errorOf(ex, '저장하지 못했어요')); }
  };

  const n = current?.notice;
  return (
    <Card i={2}>
      <CardHead title="퇴직 처리 체크리스트" sub="등록한 퇴직 처리를 불러와 항목을 완료 표시해요" />
      <form className="form" onSubmit={load} noValidate aria-label="퇴직 처리 조회">
        <label className="field">
          <span className="field__label">사원 ID(empId)</span>
          <input className="input" inputMode="numeric" value={empId} onChange={(e) => setEmpId(e.target.value)} autoComplete="off" />
        </label>
        <button className="btn" type="submit">불러오기</button>
      </form>
      {error && <p className="badge badge--red" role="alert">{error}</p>}
      {current && n && (
        <div aria-label="퇴직 처리 내용">
          <p>
            <strong>{current.empName}</strong> ({current.empNo}) · {reasonName(current.reasonCode)} · 퇴직일 {current.retireDate}
            {current.recordRetentionUntil && <> · 서류 보존기한 {current.recordRetentionUntil}</>}
          </p>
          {n.check !== 'NOT_APPLICABLE' && (
            <p>
              <Badge tone={n.check === 'OK' || n.check === 'EXEMPT' ? 'green' : n.check === 'SHORT' ? 'red' : 'amber'}>{NOTICE_LABEL[n.check]}</Badge>{' '}
              예고일 {n.dismissalNoticeDate} · 서면통지일 {n.writtenNoticeDate}
              {n.noticeDays != null && <> · 예고 {n.noticeDays}일</>}
              {n.shortfallDays != null && <> · {n.shortfallDays}일 부족</>}
              {n.noticeExempt && <> · {EXEMPT_LABEL[n.noticeExempt]}</>}
            </p>
          )}
          {current.warnings.map((w) => <p key={w} className="badge badge--amber" role="status">{w}</p>)}
          <ul className="checklist">
            {current.checklist.map((i) => (
              <li key={i.itemCode}>
                <label>
                  <input type="checkbox" checked={i.done} onChange={(e) => void toggle(i.itemCode, e.target.checked)} />{' '}
                  {i.label}{i.legalBasis ? <span className="muted sm"> · {i.legalBasis}</span> : null}
                  {i.done && i.doneBy ? <span className="muted sm"> ({i.doneBy})</span> : null}
                </label>
              </li>
            ))}
          </ul>
        </div>
      )}
    </Card>
  );
}

// ---------------------------------------------------------------- 사용증명서

function CertificateCard({ api, today }: { api: RetirementApi; today: string }) {
  const [empId, setEmpId] = useState('');
  const [requestedOn, setRequestedOn] = useState(today);
  const [items, setItems] = useState<CertificateItem[]>([]);
  const [purpose, setPurpose] = useState('');
  const [cert, setCert] = useState<Certificate | null>(null);
  const [error, setError] = useState<string | null>(null);

  const flip = (k: CertificateItem) => setItems((s) => (s.includes(k) ? s.filter((x) => x !== k) : [...s, k]));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const id = Number(empId.trim());
    if (!Number.isInteger(id) || id <= 0) { setError('사원 ID(숫자)를 입력해 주세요'); return; }
    if (items.length === 0) { setError('근로자가 요구한 기재 항목을 하나 이상 골라 주세요'); return; }
    setError(null);
    try {
      setCert(await api.issueCertificate(id, { requestedOn, items, purpose: purpose.trim() || null }));
    } catch (ex) {
      setError(errorOf(ex, '사용증명서를 발급하지 못했어요'));
    }
  };

  return (
    <Card i={3}>
      <CardHead title="사용증명서 발급" sub="근로자가 요구한 사항만 적어요(근로기준법 제39조 제2항)" />
      <form className="form" onSubmit={submit} noValidate aria-label="사용증명서 발급">
        <label className="field">
          <span className="field__label">사원 ID(empId)</span>
          <input className="input" inputMode="numeric" value={empId} onChange={(e) => setEmpId(e.target.value)} autoComplete="off" />
        </label>
        <label className="field">
          <span className="field__label">청구일</span>
          <input className="input" type="date" value={requestedOn} onChange={(e) => setRequestedOn(e.target.value)} />
        </label>
        <fieldset className="field" aria-label="근로자가 요구한 기재 항목">
          <legend className="field__label">근로자가 요구한 기재 항목</legend>
          {CERT_ITEMS.map((k) => (
            <label key={k}><input type="checkbox" checked={items.includes(k)} onChange={() => flip(k)} /> {CERT_ITEM_LABEL[k]}</label>
          ))}
        </fieldset>
        <label className="field">
          <span className="field__label">용도(선택)</span>
          <input className="input" value={purpose} onChange={(e) => setPurpose(e.target.value)} maxLength={200} />
        </label>
        <button className="btn btn--primary" type="submit">발급</button>
      </form>
      {error && <p className="badge badge--red" role="alert">{error}</p>}
      {cert && <CertificatePrint cert={cert} />}
    </Card>
  );
}

/** 인쇄 화면. 브라우저 인쇄에서 'PDF로 저장'을 고르면 PDF가 된다. 요구하지 않은 항목은 줄 자체를 그리지 않는다. */
export function CertificatePrint({ cert }: { cert: Certificate }) {
  const has = (k: CertificateItem) => cert.items.includes(k);
  return (
    <article className="certificate" aria-label="사용증명서">
      <h2>사 용 증 명 서</h2>
      <table className="code-table">
        <tbody>
          <tr><th>성명</th><td>{cert.empName}</td></tr>
          {has('USE_PERIOD') && <tr><th>사용 기간</th><td>{cert.useFrom} ~ {cert.useTo ?? '현재 재직 중'}</td></tr>}
          {has('JOB_TYPE') && <tr><th>업무 종류</th><td>{cert.jobType}</td></tr>}
          {has('POSITION') && <tr><th>지위</th><td>{cert.positionText}</td></tr>}
          {has('WAGE') && cert.wageAmount != null && (
            <tr><th>임금</th><td>{cert.wageUnitCode ? `${WAGE_UNIT_LABEL[cert.wageUnitCode] ?? cert.wageUnitCode} ` : ''}{won(cert.wageAmount)}</td></tr>
          )}
          {has('RETIRE_REASON') && <tr><th>퇴직 사유</th><td>{cert.retireReason}</td></tr>}
          {cert.purpose && <tr><th>용도</th><td>{cert.purpose}</td></tr>}
        </tbody>
      </table>
      <p>위 사실을 증명합니다.</p>
      <p className="muted sm">발급번호 {cert.certId} · 청구일 {cert.requestedOn} · 발급 {cert.issuedAt.slice(0, 10)} ({cert.issuedBy})</p>
      <button className="btn no-print" type="button" onClick={() => window.print()}><LuPrinter /> 인쇄 · PDF 저장</button>
    </article>
  );
}

// ---------------------------------------------------------------- 보존기한

function RetentionCard({ api, today, reasonName }: { api: RetirementApi; today: string; reasonName: (c: string) => string }) {
  const [asOf, setAsOf] = useState(today);
  const [rows, setRows] = useState<RetentionExpiredRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    setError(null);
    api.retentionExpired(asOf)
      .then((r) => { if (alive) setRows(r); })
      .catch((e) => { if (alive) { setRows(null); setError(errorOf(e, '목록을 불러오지 못했어요')); } });
    return () => { alive = false; };
  }, [api, asOf]);

  return (
    <Card i={4}>
      <CardHead title="보존기한이 지난 퇴직자 서류" sub="근로기준법 제42조 보존기한(퇴직일 기준)이 지난 사람이에요. 파기는 확인 후 직접 해요" />
      <label className="field">
        <span className="field__label">기준일</span>
        <input className="input" type="date" value={asOf} onChange={(e) => setAsOf(e.target.value)} aria-label="보존기한 기준일" />
      </label>
      {error && <p className="badge badge--red" role="alert">{error}</p>}
      {rows && rows.length === 0 && <p className="muted code-empty">보존기한이 지난 서류가 없어요</p>}
      {rows && rows.length > 0 && (
        <table className="code-table" aria-label="보존기한 경과 목록">
          <thead><tr><th>사원번호</th><th>이름</th><th>퇴직일</th><th className="hide-sm">퇴직 유형</th><th>보존기한</th><th>경과</th></tr></thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.empId}>
                <td>{r.empNo}</td><td>{r.empName}</td><td>{r.retireDate}</td>
                <td className="hide-sm">{reasonName(r.retireReasonCode)}</td><td>{r.recordRetentionUntil}</td><td>{r.daysOverdue}일</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Card>
  );
}
