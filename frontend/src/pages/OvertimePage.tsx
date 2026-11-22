import { useEffect, useState, type FormEvent } from 'react';
import { LuRefreshCw } from 'react-icons/lu';
import { Badge, Card, CardHead, PageHead } from '../components/ui';
import {
  hm, overtimeApi, plannedMinutes, REASON_MAX, seoulNowLocal, shortDateTime, STATUS_LABEL, STATUS_TONE, validateOvertime, WORK_KIND_LABEL,
  type OvertimeApi, type OvertimeInput, type OvertimeRequestView, type WorkKind,
} from '../api/overtime';

/**
 * 연장·휴일근로 사전 신청 (#30, 모든 사원). 신청하면 공통 결재로 올라가고, 결재는 '결재함' 화면에서 한다.
 * 1주 연장근로 한도(근로기준법 제53조 제1항)는 마지막 승인 때 서버가 legal_rate 기준으로 검사한다. 화면은 계산하지 않는다.
 */
export function OvertimePage() {
  return <OvertimeWorkspace api={overtimeApi} now={seoulNowLocal} />;
}

const errorOf = (e: unknown, fallback: string) => (e instanceof Error ? e.message : fallback);
const EMPTY: OvertimeInput = { workKind: 'OVERTIME', startAt: '', endAt: '', breakMinutes: 0, reason: '' };

/** 화면 본문. now 는 Asia/Seoul 'YYYY-MM-DDTHH:mm' 을 돌려준다(테스트에서 고정). */
export function OvertimeWorkspace({ api, now }: { api: OvertimeApi; now: () => string }) {
  const [rows, setRows] = useState<OvertimeRequestView[] | null>(null);
  const [listError, setListError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let alive = true;
    api.mine().then((l) => { if (alive) { setRows(l); setListError(null); } }, (e: unknown) => {
      if (alive) { setRows([]); setListError(errorOf(e, '신청 목록을 불러오지 못했어요')); }
    });
    return () => { alive = false; };
  }, [api, reload]);

  return (
    <>
      <PageHead title="연장·휴일근로 신청" sub="근로 전에 미리 신청해 승인을 받아요. 결재 진행은 결재함에서 볼 수 있어요" />
      <RequestForm api={api} now={now} onDone={() => setReload((n) => n + 1)} />
      <Card i={2} className="emp-list">
        <CardHead title="내 신청" sub="최근 신청 순이에요" right={
          <button className="glass pill" onClick={() => setReload((n) => n + 1)} aria-label="새로 고침"><LuRefreshCw /></button>
        } />
        {listError && <p className="badge badge--red" role="alert">{listError}</p>}
        {rows === null ? (
          <p className="muted code-empty">불러오는 중…</p>
        ) : rows.length === 0 ? (
          <p className="muted code-empty">신청한 내역이 없어요</p>
        ) : (
          <table className="emp-table ot-table">
            <thead><tr><th>구분</th><th>근로일</th><th>시간</th><th>예정</th><th>사유</th><th>상태</th></tr></thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.requestId}>
                  <td>{WORK_KIND_LABEL[r.workKind]}</td>
                  <td>{r.workDate}</td>
                  <td className="num">{shortDateTime(r.startAt)} ~ {shortDateTime(r.endAt)}{r.breakMinutes > 0 && <span className="muted"> (휴게 {r.breakMinutes}분)</span>}</td>
                  <td className="num">{hm(r.plannedMinutes)}</td>
                  <td>{r.reason}</td>
                  <td><Badge tone={STATUS_TONE[r.status]}>{STATUS_LABEL[r.status]}</Badge></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </>
  );
}

function RequestForm({ api, now, onDone }: { api: OvertimeApi; now: () => string; onDone: () => void }) {
  const [input, setInput] = useState<OvertimeInput>(EMPTY);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const set = <K extends keyof OvertimeInput>(k: K, v: OvertimeInput[K]) => setInput((p) => ({ ...p, [k]: v }));
  const planned = input.startAt && input.endAt && input.endAt > input.startAt ? plannedMinutes(input) : null;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setNotice(null);
    const err = validateOvertime(input, now());
    if (err) { setError(err); return; }
    setBusy(true);
    setError(null);
    try {
      await api.create(input);
      setInput(EMPTY);
      setNotice('신청했어요. 결재가 끝나면 상태가 바뀌어요');
      onDone();
    } catch (ex) {
      setError(errorOf(ex, '신청하지 못했어요'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card i={1}>
      <CardHead title="새 신청" sub="1주 연장근로 한도(근로기준법 제53조 제1항)는 승인할 때 휴일근로를 포함해 검사해요" />
      <form className="ot-form" onSubmit={submit} aria-label="연장·휴일근로 신청">
        <label className="field">
          <span className="field__label">근로 구분</span>
          <select className="input" value={input.workKind} onChange={(e) => set('workKind', e.target.value as WorkKind)}>
            {(Object.keys(WORK_KIND_LABEL) as WorkKind[]).map((k) => <option key={k} value={k}>{WORK_KIND_LABEL[k]}</option>)}
          </select>
        </label>
        <label className="field">
          <span className="field__label">시작</span>
          <input className="input" type="datetime-local" value={input.startAt} onChange={(e) => set('startAt', e.target.value)} />
        </label>
        <label className="field">
          <span className="field__label">종료</span>
          <input className="input" type="datetime-local" value={input.endAt} onChange={(e) => set('endAt', e.target.value)} />
        </label>
        <label className="field">
          <span className="field__label">휴게(분)</span>
          <input className="input" type="number" min={0} step={1} value={input.breakMinutes}
            onChange={(e) => set('breakMinutes', e.target.value === '' ? 0 : Number(e.target.value))} />
        </label>
        <label className="field ot-form__wide">
          <span className="field__label">사유</span>
          <textarea className="input" rows={2} maxLength={REASON_MAX} value={input.reason} onChange={(e) => set('reason', e.target.value)} />
        </label>
        <div className="ot-form__foot ot-form__wide">
          <span className="muted">{planned !== null && planned > 0 ? `예정 근로 ${hm(planned)}` : ''}</span>
          <button className="glass pill" type="submit" disabled={busy}>{busy ? '신청 중…' : '신청'}</button>
        </div>
      </form>
      {error && <p className="badge badge--red" role="alert">{error}</p>}
      {notice && <p className="badge badge--green" role="status">{notice}</p>}
    </Card>
  );
}
