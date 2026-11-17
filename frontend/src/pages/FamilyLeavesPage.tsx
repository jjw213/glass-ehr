import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { LuShieldAlert } from 'react-icons/lu';
import { Badge, Card, CardHead, PageHead } from '../components/ui';
import { seoulToday } from '../api/agreements';
import {
  familyLeaveApi, KIND_LABEL, remainingText, STATUS_LABEL, toFamilyLeaveBody, usedText, validateFamilyLeave,
  type Balance, type FamilyLeave, type FamilyLeaveApi, type LeaveKind,
} from '../api/familyLeaves';
import { duration, workPatternApi, type WorkPattern } from '../api/workPatterns';
import { canManageFamilyLeaves } from '../nav';
import { useData } from '../state/store';

/**
 * 육아휴직·육아기 근로시간 단축·가족돌봄휴직 (#37, 인사담당자·관리자). 서면 신청을 입력하고 승인·반려한다.
 * 승인하면 서버가 휴직 발령·복직 예정 발령 또는 단축 근무유형 배정을 만든다. 남은 사용 가능 기간을 보여 준다.
 * 화면은 메뉴만 숨기고, 실제 차단은 서버(EMP_WRITE·EMP_READ_ALL)가 한다.
 */
export function FamilyLeavesPage() {
  const { me } = useData();
  const [patterns, setPatterns] = useState<WorkPattern[]>([]);
  const allowed = canManageFamilyLeaves(me.role);
  useEffect(() => {
    if (!allowed) return;
    let alive = true;
    workPatternApi.list().then((p) => { if (alive) setPatterns(p.filter((x) => x.active)); }).catch(() => undefined);
    return () => { alive = false; };
  }, [allowed]);
  if (!allowed) {
    return (
      <>
        <PageHead title="육아·가족돌봄 휴직" />
        <Card i={1}>
          <CardHead title="인사담당자만 볼 수 있어요" sub="신청 입력·승인은 인사·관리자 계정으로 해요" right={<LuShieldAlert className="muted" />} />
        </Card>
      </>
    );
  }
  return <FamilyLeaveWorkspace api={familyLeaveApi} patterns={patterns} today={seoulToday()} />;
}

const errorOf = (e: unknown, fallback: string) => (e instanceof Error ? e.message : fallback);
const KINDS = Object.keys(KIND_LABEL) as LeaveKind[];

/** 화면 본문. 스토어 없이 api 만 받아서 테스트할 수 있다. */
export function FamilyLeaveWorkspace({ api, patterns, today }: { api: FamilyLeaveApi; patterns: WorkPattern[]; today: string }) {
  const [version, setVersion] = useState(0);
  const bump = () => setVersion((v) => v + 1);
  return (
    <>
      <PageHead title="육아·가족돌봄 휴직" sub="육아휴직·육아기 근로시간 단축·가족돌봄휴직 신청을 입력하고 승인해요. 승인하면 인사발령·근무유형이 자동으로 바뀌어요" />
      <RequestCard api={api} patterns={patterns} today={today} onDone={bump} />
      <PendingCard api={api} patterns={patterns} version={version} onDone={bump} />
      <BalanceCard api={api} today={today} version={version} />
    </>
  );
}

// ---------------------------------------------------------------- 신청 입력

function RequestCard({ api, patterns, today, onDone }: {
  api: FamilyLeaveApi; patterns: WorkPattern[]; today: string; onDone: () => void;
}) {
  const [empId, setEmpId] = useState('');
  const [kind, setKind] = useState<LeaveKind>('PARENTAL_LEAVE');
  const [childLabel, setChildLabel] = useState('');
  const [childBirthDate, setChildBirthDate] = useState('');
  const [familyRelation, setFamilyRelation] = useState('');
  const [startDate, setStartDate] = useState(today);
  const [endDate, setEndDate] = useState('');
  const [patternId, setPatternId] = useState('');
  const [extension, setExtension] = useState(false);
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<FamilyLeave | null>(null);
  const [busy, setBusy] = useState(false);
  const care = kind === 'FAMILY_CARE_LEAVE';

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const id = Number(empId.trim());
    const input = {
      kind, childLabel, childBirthDate: childBirthDate || null, familyRelation, startDate, endDate,
      workPatternId: patternId ? Number(patternId) : null, extension, reason,
    };
    const err = validateFamilyLeave(id, input);
    if (err) { setError(err); return; }
    setBusy(true);
    setError(null);
    try {
      setSaved(await api.create(id, toFamilyLeaveBody(input)));
      onDone();
    } catch (ex) {
      setError(errorOf(ex, '신청을 저장하지 못했어요'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card i={1}>
      <CardHead title="신청 입력" sub="근로자가 낸 서면 신청을 입력해요. 기간·분할 횟수·주 근로시간 한도는 서버가 법령 기준값으로 확인해요" />
      <form className="form" onSubmit={submit} noValidate aria-label="휴직 신청 입력">
        <label className="field">
          <span className="field__label">사원 ID(empId)</span>
          <input className="input" inputMode="numeric" value={empId} onChange={(e) => setEmpId(e.target.value)} autoComplete="off" />
        </label>
        <label className="field">
          <span className="field__label">종류</span>
          <select className="input" value={kind} onChange={(e) => setKind(e.target.value as LeaveKind)} aria-label="종류">
            {KINDS.map((k) => <option key={k} value={k}>{KIND_LABEL[k]}</option>)}
          </select>
        </label>
        {care ? (
          <label className="field">
            <span className="field__label">돌볼 가족 관계(예: 부모)</span>
            <input className="input" value={familyRelation} onChange={(e) => setFamilyRelation(e.target.value)} maxLength={30} aria-label="가족 관계" />
          </label>
        ) : (
          <>
            <label className="field">
              <span className="field__label">자녀 구분(예: 첫째, 실명 금지)</span>
              <input className="input" value={childLabel} onChange={(e) => setChildLabel(e.target.value)} maxLength={30} aria-label="자녀 구분" />
            </label>
            <label className="field">
              <span className="field__label">자녀 생년월일{kind === 'PARENTAL_LEAVE' ? '(출산 전이면 비움)' : ''}</span>
              <input className="input" type="date" value={childBirthDate} onChange={(e) => setChildBirthDate(e.target.value)} aria-label="자녀 생년월일" />
            </label>
          </>
        )}
        <label className="field">
          <span className="field__label">시작일</span>
          <input className="input" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} aria-label="시작일" />
        </label>
        <label className="field">
          <span className="field__label">종료일</span>
          <input className="input" type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} aria-label="종료일" />
        </label>
        {kind === 'CHILDCARE_REDUCED_HOURS' && (
          <label className="field">
            <span className="field__label">단축 기간 근무유형(제19조의2 제3항)</span>
            <select className="input" value={patternId} onChange={(e) => setPatternId(e.target.value)} aria-label="단축 근무유형">
              <option value="">선택</option>
              {patterns.map((p) => (
                <option key={p.patternId} value={p.patternId}>{p.patternName} · 주 {duration(p.weeklyContractMinutes)}</option>
              ))}
            </select>
          </label>
        )}
        {kind === 'PARENTAL_LEAVE' && (
          <label>
            <input type="checkbox" checked={extension} onChange={(e) => setExtension(e.target.checked)} />{' '}
            추가 육아휴직 요건 확인함(제19조 제2항)
          </label>
        )}
        <label className="field">
          <span className="field__label">신청 사유</span>
          <input className="input" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} aria-label="신청 사유" />
        </label>
        <button className="btn btn--primary" type="submit" disabled={busy}>신청 저장</button>
      </form>
      {error && <p className="badge badge--red" role="alert">{error}</p>}
      {saved && (
        <div aria-label="저장된 신청">
          <p>신청 {saved.reqId} · {KIND_LABEL[saved.kind]} · {saved.startDate} ~ {saved.endDate} 저장했어요</p>
          {saved.warnings.map((w) => <p key={w} className="badge badge--amber" role="status">{w}</p>)}
        </div>
      )}
    </Card>
  );
}

// ---------------------------------------------------------------- 승인 대기

function PendingCard({ api, patterns, version, onDone }: {
  api: FamilyLeaveApi; patterns: WorkPattern[]; version: number; onDone: () => void;
}) {
  const [rows, setRows] = useState<FamilyLeave[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<number | null>(null);
  const patternName = (id: number | null) => patterns.find((p) => p.patternId === id)?.patternName ?? (id == null ? '' : `근무유형 ${id}`);

  useEffect(() => {
    let alive = true;
    api.search('REQUESTED')
      .then((r) => { if (alive) setRows(r); })
      .catch((e) => { if (alive) { setRows(null); setError(errorOf(e, '목록을 불러오지 못했어요')); } });
    return () => { alive = false; };
  }, [api, version]);

  const act = async (r: FamilyLeave, kind: 'approve' | 'reject' | 'cancel') => {
    let note: string | null = null;
    if (kind === 'reject') {
      note = window.prompt('반려 사유를 입력해 주세요');
      if (!note || !note.trim()) return;
    }
    setBusy(r.reqId);
    setError(null);
    try {
      if (kind === 'approve') await api.approve(r.reqId);
      else if (kind === 'reject') await api.reject(r.reqId, note!.trim());
      else await api.cancel(r.reqId, null);
      onDone();
    } catch (ex) {
      setError(errorOf(ex, '처리하지 못했어요'));
    } finally {
      setBusy(null);
    }
  };

  return (
    <Card i={2}>
      <CardHead title="승인 대기" sub="휴직은 승인 시 휴직 발령(확정)과 종료일 다음 날 복직 발령(미확정)을, 단축은 단축·원래 근무유형 배정을 만들어요" />
      {error && <p className="badge badge--red" role="alert">{error}</p>}
      {rows && rows.length === 0 && <p className="muted code-empty">승인 대기 중인 신청이 없어요</p>}
      {rows && rows.length > 0 && (
        <table className="code-table" aria-label="승인 대기 목록">
          <thead><tr><th>신청</th><th>사원 ID</th><th>종류</th><th>기간</th><th className="hide-sm">대상</th><th>확인할 점</th><th /></tr></thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.reqId}>
                <td>{r.reqId}</td><td>{r.empId}</td><td>{KIND_LABEL[r.kind]}</td><td>{r.startDate} ~ {r.endDate}</td>
                <td className="hide-sm">{r.familyRelation ?? r.childLabel}{r.workPatternId != null ? ` · ${patternName(r.workPatternId)}` : ''}</td>
                <td>{r.warnings.length === 0 ? <Badge tone="green">없음</Badge> : r.warnings.map((w) => <p key={w} className="muted sm">{w}</p>)}</td>
                <td>
                  <button className="btn btn--primary" type="button" disabled={busy === r.reqId} onClick={() => void act(r, 'approve')}>승인</button>{' '}
                  <button className="btn" type="button" disabled={busy === r.reqId} onClick={() => void act(r, 'reject')}>반려</button>{' '}
                  <button className="btn" type="button" disabled={busy === r.reqId} onClick={() => void act(r, 'cancel')}>취소</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Card>
  );
}

// ---------------------------------------------------------------- 남은 기간

function BalanceCard({ api, today, version }: { api: FamilyLeaveApi; today: string; version: number }) {
  const [empId, setEmpId] = useState('');
  const [asOf, setAsOf] = useState(today);
  const [shown, setShown] = useState<number | null>(null);
  const [balance, setBalance] = useState<Balance | null>(null);
  const [history, setHistory] = useState<FamilyLeave[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (id: number, date: string) => {
    setError(null);
    try {
      const [b, h] = await Promise.all([api.balance(id, date), api.listByEmployee(id)]);
      setBalance(b);
      setHistory(h);
    } catch (ex) {
      setBalance(null);
      setHistory([]);
      setError(errorOf(ex, '남은 기간을 불러오지 못했어요'));
    }
  }, [api]);

  useEffect(() => { if (shown != null) void load(shown, asOf); }, [load, shown, asOf, version]);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const id = Number(empId.trim());
    if (!Number.isInteger(id) || id <= 0) { setError('사원 ID(숫자)를 입력해 주세요'); return; }
    setShown(id);
  };

  const care = balance?.familyCareLeave;
  return (
    <Card i={3}>
      <CardHead title="남은 사용 가능 기간" sub="승인된 신청과 실제 휴직 발령(조기 복직 반영)으로 계산해요" />
      <form className="form" onSubmit={submit} noValidate aria-label="남은 기간 조회">
        <label className="field">
          <span className="field__label">사원 ID(empId)</span>
          <input className="input" inputMode="numeric" value={empId} onChange={(e) => setEmpId(e.target.value)} autoComplete="off" />
        </label>
        <label className="field">
          <span className="field__label">기준일</span>
          <input className="input" type="date" value={asOf} onChange={(e) => setAsOf(e.target.value)} aria-label="기준일" />
        </label>
        <button className="btn" type="submit">조회</button>
      </form>
      {error && <p className="badge badge--red" role="alert">{error}</p>}
      {balance && care && (
        <div aria-label="남은 기간">
          {balance.children.length === 0 && <p className="muted">승인된 육아휴직·단축이 없어요</p>}
          {balance.children.length > 0 && (
            <table className="code-table" aria-label="자녀별 남은 기간">
              <thead><tr><th>자녀</th><th>구분</th><th>한도</th><th>사용</th><th>남은 기간</th><th>사용 횟수</th></tr></thead>
              <tbody>
                {balance.children.flatMap((c) => [
                  { key: `${c.childLabel}-p`, child: c.childLabel, label: '육아휴직', u: c.parentalLeave },
                  { key: `${c.childLabel}-r`, child: c.childLabel, label: '근로시간 단축', u: c.reducedHours },
                ]).map((r) => (
                  <tr key={r.key}>
                    <td>{r.child}</td><td>{r.label}</td>
                    <td>{r.u.limitMonths == null ? '확인 불가' : `${r.u.limitMonths}개월`}</td>
                    <td>{usedText(r.u)}</td><td>{remainingText(r.u)}</td>
                    <td>{r.u.uses}{r.u.usesMax != null ? ` / ${r.u.usesMax}번` : '번'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <p>
            {care.year}년 가족돌봄휴직: 사용 {care.usedDays}일
            {care.limitDays != null ? ` / ${care.limitDays}일 · 남은 ${care.remainingDays}일` : ' · 한도 확인 불가(기준값 없음)'}
          </p>
          {balance.notes.map((n) => <p key={n} className="muted sm" role="note">{n}</p>)}
          {history.length > 0 && (
            <ul aria-label="신청 이력">
              {history.map((h) => (
                <li key={h.reqId}>
                  {KIND_LABEL[h.kind]} · {h.startDate} ~ {h.endDate} · {STATUS_LABEL[h.status]}
                  {h.decisionNote ? <span className="muted sm"> ({h.decisionNote})</span> : null}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </Card>
  );
}
