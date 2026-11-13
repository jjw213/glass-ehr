import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { LuRefreshCw, LuShieldAlert, LuShieldCheck } from 'react-icons/lu';
import { Badge, Card, CardHead, PageHead } from '../components/ui';
import { seoulToday } from '../api/employees';
import {
  ACTION_LABEL, ITEM_LABEL, labelOf, privacyLogApi, PURPOSE_LABEL, RESULT_LABEL, validateQuery,
  type PrivacyLogApi, type PrivacyLogPage, type PrivacyLogQuery,
} from '../api/privacyLogs';
import { canReadPrivacyLogs } from '../nav';
import { useData } from '../state/store';

/**
 * 개인정보 조회 로그 (#19, 관리자). 누가·언제·누구의·무엇을 조회·다운로드했는지 본다.
 * 개인정보 보호법 제29조, 개인정보의 안전성 확보조치 기준(접속기록의 보관 및 점검).
 * 화면은 메뉴만 숨기고, 실제 차단은 서버(PRIVACY_LOG_READ 권한)가 한다.
 */
export function PrivacyLogsPage() {
  const { me } = useData();
  if (!canReadPrivacyLogs(me.role)) {
    return (
      <>
        <PageHead title="개인정보 조회 로그" />
        <Card i={1}>
          <CardHead title="관리자만 볼 수 있어요" sub="개인정보 조회 로그는 관리자 계정으로 확인해요" right={<LuShieldAlert className="muted" />} />
        </Card>
      </>
    );
  }
  return <PrivacyLogViewer api={privacyLogApi} today={seoulToday()} />;
}

/** 30일 전 'YYYY-MM-DD'(서버 기본 조회 기간과 같음. 화면 기본값이지 법령 기준값이 아님). */
const DEFAULT_DAYS = 30;
export function daysBefore(day: string, n: number) {
  const d = new Date(`${day}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() - n);
  return d.toISOString().slice(0, 10);
}

const fmtTime = (iso: string) => iso.replace('T', ' ').slice(0, 19);

/** 화면 본문. 스토어 없이 api 만 받아서 테스트할 수 있다. */
export function PrivacyLogViewer({ api, today }: { api: PrivacyLogApi; today: string }) {
  const [draft, setDraft] = useState<PrivacyLogQuery>({ from: daysBefore(today, DEFAULT_DAYS), to: today, accessorId: '', targetEmpId: '' });
  const [query, setQuery] = useState<PrivacyLogQuery>(draft);
  const [page, setPage] = useState<PrivacyLogPage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [reload, setReload] = useState(0);

  const load = useCallback(async (q: PrivacyLogQuery, alive: () => boolean) => {
    setLoading(true);
    setError(null);
    try {
      const p = await api.search(q);
      if (alive()) setPage(p);
    } catch (e) {
      if (alive()) { setPage(null); setError(e instanceof Error ? e.message : '로그를 불러오지 못했어요'); }
    } finally {
      if (alive()) setLoading(false);
    }
  }, [api]);

  useEffect(() => {
    let alive = true;
    void load(query, () => alive);
    return () => { alive = false; };
  }, [load, query, reload]);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const err = validateQuery(draft);
    if (err) { setError(err); return; }
    setQuery({ ...draft });
  };

  const set = (k: keyof PrivacyLogQuery) => (e: { target: { value: string } }) => setDraft((d) => ({ ...d, [k]: e.target.value }));

  return (
    <>
      <PageHead
        title="개인정보 조회 로그"
        sub="개인정보 원문 조회·다운로드와 거부된 시도를 기록해요. 기록은 지우거나 고칠 수 없어요"
        right={<button className="glass pill" onClick={() => setReload((n) => n + 1)} aria-label="새로 고침"><LuRefreshCw /> 새로 고침</button>}
      />

      <Card i={1}>
        <CardHead title="조회 조건" sub="기간은 한 번에 1년까지 볼 수 있어요" right={<LuShieldCheck className="muted" />} />
        <form className="form" onSubmit={submit} noValidate aria-label="로그 조회 조건">
          <label className="field">
            <span className="field__label">시작 날짜</span>
            <input className="input" type="date" value={draft.from} onChange={set('from')} />
          </label>
          <label className="field">
            <span className="field__label">끝 날짜</span>
            <input className="input" type="date" value={draft.to} onChange={set('to')} />
          </label>
          <label className="field">
            <span className="field__label">조회한 사람(로그인 아이디)</span>
            <input className="input" value={draft.accessorId} onChange={set('accessorId')} maxLength={50} autoComplete="off" />
          </label>
          <label className="field">
            <span className="field__label">대상 사원번호(empId)</span>
            <input className="input" inputMode="numeric" value={draft.targetEmpId} onChange={set('targetEmpId')} autoComplete="off" />
          </label>
          <button className="btn btn--primary" type="submit" disabled={loading}>조회</button>
        </form>
      </Card>

      <Card i={2}>
        <CardHead
          title="로그"
          sub={loading ? '불러오는 중…' : page ? `${page.rows.length}건${page.truncated ? ' (최근 일부만 표시)' : ''}` : undefined}
        />
        {error && <p className="badge badge--red" role="alert">{error}</p>}
        {page?.truncated && <p className="muted sm" role="status">결과가 많아 최근 기록만 보여요. 기간이나 조건을 좁혀 주세요.</p>}
        {page && page.rows.length === 0 ? (
          <p className="muted code-empty">기록이 없어요</p>
        ) : page ? (
          <table className="code-table">
            <thead>
              <tr><th>일시</th><th>조회한 사람</th><th className="hide-sm">역할</th><th>대상 사원</th><th>항목</th><th>행위</th><th>결과</th><th className="hide-sm">접속 IP</th></tr>
            </thead>
            <tbody>
              {page.rows.map((r, i) => (
                <tr key={`${r.source}-${r.accessedAt}-${i}`}>
                  <td>{fmtTime(r.accessedAt)}</td>
                  <td>{r.accessorId}</td>
                  <td className="hide-sm">{r.accessorRoles ?? ''}</td>
                  <td>{r.targetEmpId ?? '-'}</td>
                  <td>{labelOf(ITEM_LABEL, r.item)}{r.purpose ? ` (${labelOf(PURPOSE_LABEL, r.purpose)})` : ''}</td>
                  <td>{labelOf(ACTION_LABEL, r.action)}</td>
                  <td><Badge tone={r.result === 'SUCCESS' ? 'green' : r.result === 'DENIED' ? 'red' : 'amber'}>{labelOf(RESULT_LABEL, r.result)}</Badge></td>
                  <td className="hide-sm">{r.clientIp ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : null}
      </Card>
    </>
  );
}
