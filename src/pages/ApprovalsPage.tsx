import { useState } from 'react';
import { LuArrowLeft, LuCheck, LuX, LuPaperclip } from 'react-icons/lu';
import { Card, PageHead, Segmented, Badge, Avatar, cx, won } from '../components/ui';
import type { Approval } from '../api';
import { useData, useStore } from '../state/store';
import { useIsMobile } from '../hooks/useMediaQuery';

const TABS = ['대기', '승인', '반려', '전체'] as const;
type Tab = (typeof TABS)[number];
const KIND_COLOR: Record<Approval['kind'], string> = { 휴가: '#60a5fa', 지출: '#f59e0b', 출장: '#4ade80', 채용: '#a78bfa', 교육: '#f472b6' };
const tone = (s: Approval['status']) => (s === '승인' ? 'green' : s === '반려' ? 'red' : 'amber');

export function ApprovalsPage() {
  const { decide, pendingCount, dept } = useStore();
  const { approvals } = useData();
  const [busy, setBusy] = useState(false);
  const act = async (id: string, a: 'approve' | 'reject') => { setBusy(true); await decide(id, a); setBusy(false); };
  const mobile = useIsMobile();
  const [tab, setTab] = useState<Tab>('대기');
  const list = approvals.filter((a) => tab === '전체' || a.status === tab);
  const [selId, setSelId] = useState<string | null>(mobile ? null : list[0]?.id ?? null);
  const sel = approvals.find((a) => a.id === selId) ?? null;

  const showList = !mobile || !sel;
  const showDetail = !mobile || !!sel;

  return (
    <>
      <PageHead title="전자결재" sub={`승인을 기다리는 문서가 ${pendingCount}건 있어요`} right={<Segmented options={TABS} value={tab} onChange={(t) => { setTab(t); setSelId(mobile ? null : approvals.find((a) => t === '전체' || a.status === t)?.id ?? null); }} small />} />

      <div className="split-view">
        {showList && (
          <Card i={1} className="split-view__list">
            <ul className="list">
              {list.map((a) => (
                <li key={a.id} className={cx('list__row doc-row', a.id === selId && 'active')} onClick={() => setSelId(a.id)}>
                  <span className="kind" style={{ background: KIND_COLOR[a.kind] + '33', color: KIND_COLOR[a.kind] }}>{a.kind}</span>
                  <div className="grow">
                    <strong>{a.title}</strong>
                    <span className="muted sm">{a.requester} · {dept(a.dept)?.name} · {a.date}</span>
                  </div>
                  <Badge tone={tone(a.status)}>{a.status}</Badge>
                </li>
              ))}
              {list.length === 0 && <li className="empty">이 탭에는 문서가 없어요</li>}
            </ul>
          </Card>
        )}

        {showDetail && (
          <Card i={2} className="split-view__detail">
            {sel ? (
              <div className="doc" key={sel.id}>
                {mobile && <button className="link back" onClick={() => setSelId(null)}><LuArrowLeft /> 목록</button>}
                <div className="doc__meta">
                  <span className="kind" style={{ background: KIND_COLOR[sel.kind] + '33', color: KIND_COLOR[sel.kind] }}>{sel.kind}</span>
                  <span className="muted sm">{sel.id}</span>
                  <Badge tone={tone(sel.status)}>{sel.status}</Badge>
                </div>
                <h2>{sel.title}</h2>
                <div className="doc__who">
                  <Avatar name={sel.requester} dept={sel.dept} size={36} />
                  <div><strong>{sel.requester}</strong><span className="muted sm">{dept(sel.dept)?.name} · {sel.date} 상신</span></div>
                </div>
                {sel.amount != null && <div className="doc__amount"><span className="muted sm">금액</span><strong>{won(sel.amount)}</strong></div>}
                <p className="doc__body">{sel.body}</p>
                <div className="doc__file"><LuPaperclip /> 첨부파일 1개</div>

                <p className="muted sm">결재선</p>
                <ol className="steps">
                  {sel.line.map((s, i) => {
                    const firstWait = sel.line.findIndex((x) => x.status === 'WAIT');
                    const current = sel.status === '대기' && i === firstWait;
                    return (
                      <li key={s.name + i} className={cx(s.status === 'APPROVED' && 'done', current && 'current', s.status === 'REJECTED' && 'rejected')}>
                        <span className="steps__dot">{s.status === 'APPROVED' ? <LuCheck /> : s.status === 'REJECTED' ? <LuX /> : i + 1}</span>
                        <span>{s.name}</span>
                      </li>
                    );
                  })}
                </ol>

                {sel.canDecide ? (
                  <div className="doc__actions">
                    <button className="btn btn--ghost" disabled={busy} onClick={() => act(sel.id, 'reject')}><LuX /> 반려</button>
                    <button className="btn btn--primary" disabled={busy} onClick={() => act(sel.id, 'approve')}><LuCheck /> 승인</button>
                  </div>
                ) : sel.status === '대기' && (
                  <p className="muted sm doc__wait">다른 결재자의 차례예요</p>
                )}
              </div>
            ) : (
              <p className="empty">왼쪽에서 문서를 선택하세요</p>
            )}
          </Card>
        )}
      </div>
    </>
  );
}
