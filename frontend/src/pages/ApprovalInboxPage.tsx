import { useEffect, useState } from 'react';
import { LuRefreshCw } from 'react-icons/lu';
import { Badge, Card, CardHead, Modal, PageHead, Segmented } from '../components/ui';
import {
  ACTION_LABEL, approvalApi, BOX_LABEL, hm, shortDateTime, STATUS_LABEL, STATUS_TONE, STEP_LABEL, WORK_KIND_LABEL,
  type ApprovalApi, type Box, type DocDetail, type DocSummary,
} from '../api/overtime';

/**
 * 결재함 (#30 공통 결재 모듈, 모든 사원). 결재할 문서·처리한 문서·내가 올린 문서를 보고 승인·반려·회수한다.
 * 차례·본인 여부는 서버가 검사하고(canAct·canWithdraw), 화면은 그 값으로 버튼만 보인다.
 * 연장·휴일근로 신청은 마지막 승인 때 서버가 1주 연장근로 한도(근로기준법 제53조 제1항)를 검사해, 넘으면 승인되지 않고 오류가 보인다.
 */
export function ApprovalInboxPage() {
  return <ApprovalInbox api={approvalApi} />;
}

const errorOf = (e: unknown, fallback: string) => (e instanceof Error ? e.message : fallback);
const BOXES = Object.values(BOX_LABEL);
const boxOf = (label: string) => (Object.keys(BOX_LABEL) as Box[]).find((b) => BOX_LABEL[b] === label)!;

export function ApprovalInbox({ api }: { api: ApprovalApi }) {
  const [box, setBox] = useState<Box>('inbox');
  const [rows, setRows] = useState<DocSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [openId, setOpenId] = useState<number | null>(null);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let alive = true;
    setRows(null);
    api.list(box).then((l) => { if (alive) { setRows(l); setError(null); } }, (e: unknown) => {
      if (alive) { setRows([]); setError(errorOf(e, '결재 문서를 불러오지 못했어요')); }
    });
    return () => { alive = false; };
  }, [api, box, reload]);

  return (
    <>
      <PageHead title="결재함" sub="결재선 순서대로 한 단계씩 승인해요. 회수는 첫 승인 전까지만 할 수 있어요" />
      <Card i={1} className="emp-list">
        <CardHead title={BOX_LABEL[box]} right={
          <div className="toolbar">
            <Segmented small options={BOXES} value={BOX_LABEL[box]} onChange={(v) => setBox(boxOf(v))} />
            <button className="glass pill" onClick={() => setReload((n) => n + 1)} aria-label="새로 고침"><LuRefreshCw /></button>
          </div>
        } />
        {error && <p className="badge badge--red" role="alert">{error}</p>}
        {rows === null ? (
          <p className="muted code-empty">불러오는 중…</p>
        ) : rows.length === 0 ? (
          <p className="muted code-empty">문서가 없어요</p>
        ) : (
          <table className="emp-table">
            <thead><tr><th>종류</th><th>제목</th><th>신청자</th><th>단계</th><th>상신</th><th>상태</th></tr></thead>
            <tbody>
              {rows.map((d) => (
                <tr key={d.approvalId} tabIndex={0} onClick={() => setOpenId(d.approvalId)}
                  onKeyDown={(e) => { if (e.key === 'Enter') setOpenId(d.approvalId); }}>
                  <td>{d.requestTypeLabel}</td>
                  <td>{d.title}</td>
                  <td>{d.requester.name}</td>
                  <td className="num">{d.currentStepNo ? `${d.currentStepNo}/${d.stepCount}` : `-/${d.stepCount}`}</td>
                  <td className="num">{shortDateTime(d.submittedAt)}</td>
                  <td><Badge tone={STATUS_TONE[d.status]}>{STATUS_LABEL[d.status]}</Badge></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
      {openId !== null && (
        <DocModal api={api} approvalId={openId} onClose={() => setOpenId(null)} onChanged={() => setReload((n) => n + 1)} />
      )}
    </>
  );
}

function DocModal({ api, approvalId, onClose, onChanged }: { api: ApprovalApi; approvalId: number; onClose: () => void; onChanged: () => void }) {
  const [doc, setDoc] = useState<DocDetail | null>(null);
  const [comment, setComment] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let alive = true;
    api.detail(approvalId).then((d) => { if (alive) setDoc(d); }, (e: unknown) => { if (alive) setError(errorOf(e, '문서를 불러오지 못했어요')); });
    return () => { alive = false; };
  }, [api, approvalId]);

  const act = async (run: () => Promise<DocDetail>, fallback: string) => {
    setBusy(true);
    setError(null);
    try { setDoc(await run()); setComment(''); onChanged(); } catch (e) { setError(errorOf(e, fallback)); } finally { setBusy(false); }
  };
  const reject = () => {
    if (!comment.trim()) { setError('반려 사유를 입력해 주세요'); return; }
    void act(() => api.reject(approvalId, comment), '반려하지 못했어요');
  };

  return (
    <Modal open onClose={onClose} title={doc ? doc.summary.title : '결재 문서'}>
      {!doc ? (
        error ? <p className="badge badge--red" role="alert">{error}</p> : <p className="muted">불러오는 중…</p>
      ) : (
        <div className="approval-doc">
          <p className="muted">{doc.summary.requestTypeLabel} · {doc.summary.requester.name} · 상신 {shortDateTime(doc.summary.submittedAt)}{' '}
            <Badge tone={STATUS_TONE[doc.summary.status]}>{STATUS_LABEL[doc.summary.status]}</Badge></p>
          {doc.requestDetail && (
            <section aria-label="신청 내용">
              <h4>신청 내용</h4>
              <dl>
                <dt>근로 구분</dt><dd>{WORK_KIND_LABEL[doc.requestDetail.workKind]}</dd>
                <dt>예정 시간</dt><dd>{shortDateTime(doc.requestDetail.startAt)}–{shortDateTime(doc.requestDetail.endAt)} ({hm(doc.requestDetail.plannedMinutes)})</dd>
                <dt>휴게 시간</dt><dd>{hm(doc.requestDetail.breakMinutes)}</dd>
                <dt>신청 사유</dt><dd>{doc.requestDetail.reason}</dd>
              </dl>
            </section>
          )}
          <h4>결재선</h4>
          <ol className="approval-steps">
            {doc.steps.map((s) => (
              <li key={s.stepNo}>
                <strong>{s.stepNo}단계</strong> {s.actedBy ? s.actedBy.name : s.approvers.map((p) => p.name).join(', ') || '결재자 없음'}{' '}
                <Badge tone={s.status === 'APPROVED' ? 'green' : s.status === 'REJECTED' ? 'red' : 'neutral'}>{STEP_LABEL[s.status]}</Badge>
                {s.actedAt && <span className="muted"> {shortDateTime(s.actedAt)}</span>}
                {s.comment && <div className="muted">“{s.comment}”</div>}
              </li>
            ))}
          </ol>
          <h4>이력</h4>
          <ul className="approval-history">
            {doc.history.map((h) => (
              <li key={h.actionId}>{shortDateTime(h.actedAt)} {h.actor.name} {ACTION_LABEL[h.action]}{h.comment ? ` — ${h.comment}` : ''}</li>
            ))}
          </ul>
          {(doc.canAct || doc.canWithdraw) && (
            <>
              {doc.canAct && (
                <label className="field">
                  <span className="field__label">의견 (반려 시 필수)</span>
                  <textarea className="input" rows={2} maxLength={500} value={comment} onChange={(e) => setComment(e.target.value)} />
                </label>
              )}
              <div className="toolbar">
                {doc.canAct && <button className="glass pill" disabled={busy} onClick={() => void act(() => api.approve(approvalId, comment), '승인하지 못했어요')}>승인</button>}
                {doc.canAct && <button className="glass pill" disabled={busy} onClick={reject}>반려</button>}
                {doc.canWithdraw && <button className="glass pill" disabled={busy} onClick={() => void act(() => api.withdraw(approvalId), '회수하지 못했어요')}>회수</button>}
              </div>
            </>
          )}
          {error && <p className="badge badge--red" role="alert">{error}</p>}
        </div>
      )}
    </Modal>
  );
}
