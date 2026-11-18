import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { LuRefreshCw, LuSearch, LuShieldAlert } from 'react-icons/lu';
import { Badge, Card, CardHead, Modal, PageHead, cx, vars } from '../components/ui';
import {
  APPT_STATUS_LABEL, APPT_TYPE_LABEL, APPT_TYPES, appointmentApi, BULK_MAX, cancelLabel, REGISTER_TYPES, typeFields, validateAppointment,
  type AppointmentApi, type AppointmentFilter, type AppointmentInput, type AppointmentView, type ApptType, type BulkResult, type Snapshot,
} from '../api/appointments';
import { codeApi, CODE_GROUP, type CodeApi } from '../api/codes';
import { employeeApi, flattenDepts, seoulToday, type EmployeeApi, type EmployeeSummary, type FlatDept } from '../api/employees';
import { useCodes } from '../hooks/useCodes';
import { isEmployedOn } from '../lib/orgTree';
import { canManageAppointments } from '../nav';
import { useData } from '../state/store';

/**
 * 인사발령 (#60, 인사담당자·관리자). 발령 목록(유형·기간·부서·확정 여부)·등록·확정·취소와 여러 사원 일괄 발령.
 * 사유는 근로기준법 제23조 제1항(정당한 이유 없는 전직·휴직 등 금지)에 따라 필수다.
 * 화면은 메뉴만 숨기고, 실제 차단은 서버(EMP_WRITE·EMP_READ_ALL)가 한다. 일반 사원은 인사기록카드에서 본인 확정 이력만 본다.
 */
export function AppointmentsPage() {
  const { me } = useData();
  if (!canManageAppointments(me.role)) {
    return (
      <>
        <PageHead title="인사발령" />
        <Card i={1}>
          <CardHead title="인사담당자만 볼 수 있어요" sub="본인 발령 이력은 인사정보 > 인사기록카드에서 볼 수 있어요" right={<LuShieldAlert className="muted" />} />
        </Card>
      </>
    );
  }
  return <AppointmentWorkspace api={appointmentApi} employees={employeeApi} codes={codeApi} today={seoulToday()} />;
}

const errorOf = (e: unknown, fallback: string) => (e instanceof Error ? e.message : fallback);

type ConfirmedFilter ='ALL' | 'PENDING' | 'CONFIRMED';
const CONFIRMED_FILTERS: Array<{ id: ConfirmedFilter; label: string; value: boolean | null }> = [
  { id: 'ALL', label: '전체', value: null }, { id: 'PENDING', label: '미확정', value: false }, { id: 'CONFIRMED', label: '확정', value: true },
];

/** 화면 본문. 스토어 없이 api 만 받아서 테스트할 수 있다. today 는 Asia/Seoul 'YYYY-MM-DD'. */
export function AppointmentWorkspace({ api, employees, codes, today }: { api: AppointmentApi; employees: EmployeeApi; codes: CodeApi; today: string }) {
  const grade = useCodes(CODE_GROUP.JOB_GRADE, codes);
  const title = useCodes(CODE_GROUP.JOB_TITLE, codes);
  const [emps, setEmps] = useState<EmployeeSummary[]>([]);
  const [depts, setDepts] = useState<FlatDept[]>([]);
  const [baseError, setBaseError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    Promise.all([employees.list(true), employees.orgTree(today)]).then(([l, t]) => {
      if (!alive) return;
      setEmps(l);
      setDepts(flattenDepts(t.roots));
    }, (e: unknown) => { if (alive) setBaseError(errorOf(e, '사원·조직을 불러오지 못했어요')); });
    return () => { alive = false; };
  }, [employees, today]);

  const empName = useCallback((id: number) => {
    const e = emps.find((x) => x.empId === id);
    return e ? `${e.name}(${e.empNo})` : `사원 ${id}`;
  }, [emps]);
  const deptName = useCallback((id: number | null) => (id === null ? '' : depts.find((d) => d.deptId === id)?.name ?? `부서 ${id}`), [depts]);
  const describe = useCallback((s: Snapshot) => [deptName(s.deptId), grade.nameOf(s.jobGradeCode), title.nameOf(s.jobTitleCode), s.status ? APPT_STATUS_LABEL[s.status] : '']
    .filter(Boolean).join(' · '), [deptName, grade, title]);
  const [reload, setReload] = useState(0);

  return (
    <>
      <PageHead title="인사발령" sub="발령을 등록하고 확정해야 사원 정보에 반영돼요. 확정된 발령은 취소 발령으로 되돌려요" />
      {baseError && <p className="badge badge--red" role="alert">{baseError}</p>}
      <RegisterCard api={api} employees={employees} emps={emps} grades={grade.codes} titles={title.codes} today={today} empName={empName} onDone={() => setReload((n) => n + 1)} />
      <ListCard api={api} depts={depts} empName={empName} describe={describe} reload={reload} onChanged={() => setReload((n) => n + 1)} />
    </>
  );
}

// ---------------------------------------------------------------- 목록·확정·취소

function ListCard({ api, depts, empName, describe, reload, onChanged }: {
  api: AppointmentApi; depts: FlatDept[]; empName: (id: number) => string; describe: (s: Snapshot) => string; reload: number; onChanged: () => void;
}) {
  const [type, setType] = useState<ApptType | ''>('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [deptId, setDeptId] = useState<number | null>(null);
  const [confirmed, setConfirmed] = useState<ConfirmedFilter>('ALL');
  const [rows, setRows] = useState<AppointmentView[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<number | null>(null);
  const [cancelling, setCancelling] = useState<AppointmentView | null>(null);
  const [refresh, setRefresh] = useState(0);

  const filter = useMemo<AppointmentFilter>(() => ({
    type, from, to, deptId, confirmed: CONFIRMED_FILTERS.find((c) => c.id === confirmed)!.value,
  }), [type, from, to, deptId, confirmed]);

  useEffect(() => {
    if (from && to && to < from) { setError('조회 종료일은 시작일 이후여야 해요'); return; }
    let alive = true;
    api.search(filter).then((l) => { if (alive) { setRows(l); setError(null); } }, (e: unknown) => { if (alive) { setRows([]); setError(errorOf(e, '발령 목록을 불러오지 못했어요')); } });
    return () => { alive = false; };
  }, [api, filter, from, to, reload, refresh]);

  const act = async (apptId: number, run: () => Promise<unknown>, fallback: string) => {
    setBusy(apptId);
    setError(null);
    try { await run(); onChanged(); setRefresh((n) => n + 1); } catch (e) { setError(errorOf(e, fallback)); } finally { setBusy(null); }
  };

  return (
    <Card i={2} className="emp-list">
      <CardHead title="발령 목록" sub="발령일 순이에요. 부서는 발령 전·후 소속 중 하나로 찾아요" right={
        <button className="glass pill" onClick={() => setRefresh((n) => n + 1)} aria-label="새로 고침"><LuRefreshCw /></button>
      } />
      <div className="toolbar appt-filters" role="search" aria-label="발령 검색">
        <select className="input" value={type} onChange={(e) => setType(e.target.value as ApptType | '')} aria-label="발령 유형">
          <option value="">전체 유형</option>
          {APPT_TYPES.map((t) => <option key={t} value={t}>{APPT_TYPE_LABEL[t]}</option>)}
        </select>
        <input className="input" type="date" value={from} onChange={(e) => setFrom(e.target.value)} aria-label="발령일 시작" />
        <input className="input" type="date" value={to} onChange={(e) => setTo(e.target.value)} aria-label="발령일 끝" />
        <select className="input" value={deptId ?? ''} onChange={(e) => setDeptId(e.target.value ? Number(e.target.value) : null)} aria-label="부서">
          <option value="">전체 부서</option>
          {depts.map((d) => <option key={d.deptId} value={d.deptId}>{'　'.repeat(d.depth)}{d.name}</option>)}
        </select>
        <div className="chips" role="radiogroup" aria-label="확정 여부">
          {CONFIRMED_FILTERS.map((c) => (
            <button key={c.id} role="radio" aria-checked={confirmed === c.id} className={cx('glass pill', confirmed === c.id && 'active')} onClick={() => setConfirmed(c.id)}>{c.label}</button>
          ))}
        </div>
      </div>
      {error && <p className="badge badge--red" role="alert">{error}</p>}
      {rows === null ? (
        <p className="muted code-empty">불러오는 중…</p>
      ) : rows.length === 0 ? (
        <p className="muted code-empty">조건에 맞는 발령이 없어요</p>
      ) : (
        <table className="emp-table appt-table">
          <thead>
            <tr><th>발령일</th><th>사원</th><th>유형</th><th className="hide-sm">발령번호</th><th>발령 후</th><th className="hide-sm">사유</th><th>상태</th><th>처리</th></tr>
          </thead>
          <tbody>
            {rows.map((a) => (
              <tr key={a.apptId} data-appt={a.apptNo}>
                <td className="num emp-table__no">{a.effectiveDate}</td>
                <td className="emp-table__name"><strong>{empName(a.empId)}</strong></td>
                <td className="emp-table__grade">
                  <Badge tone={a.cancelOfApptId !== null ? 'amber' : 'blue'}>{APPT_TYPE_LABEL[a.apptTypeCode] ?? a.apptTypeCode}{a.cancelOfApptId !== null ? ' 취소' : ''}</Badge>
                </td>
                <td className="hide-sm"><code>{a.apptNo}</code></td>
                <td className="emp-table__dept">{describe(a.after)}{a.leaveStartDate ? ` (${a.leaveStartDate} ~ ${a.leaveEndDate ?? ''})` : ''}</td>
                <td className="hide-sm">{a.reason}</td>
                <td className="emp-table__status">
                  {a.cancelledByApptId !== null
                    ? <Badge tone="neutral">{cancelLabel(rows, a.cancelledByApptId)}</Badge>
                    : <Badge tone={a.confirmed ? 'green' : 'amber'}>{a.confirmed ? '확정' : '미확정'}</Badge>}
                </td>
                <td className="appt-actions">
                  {!a.confirmed && (
                    <>
                      <button className="btn btn--primary" disabled={busy === a.apptId} onClick={() => void act(a.apptId, () => api.confirm(a.apptId), '확정하지 못했어요')}>확정</button>
                      <button className="glass pill" disabled={busy === a.apptId} onClick={() => void act(a.apptId, () => api.remove(a.apptId), '삭제하지 못했어요')}>삭제</button>
                    </>
                  )}
                  {a.confirmed && a.cancelOfApptId === null && a.cancelledByApptId === null && (
                    <button className="glass pill" disabled={busy === a.apptId} onClick={() => setCancelling(a)}>취소 발령</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <Modal open={cancelling !== null} onClose={() => setCancelling(null)} title="취소 발령 등록">
        {cancelling && (
          <CancelForm appt={cancelling} empName={empName} onCancel={() => setCancelling(null)} onSubmit={async (reason) => {
            const target = cancelling;
            setCancelling(null);
            await act(target.apptId, () => api.cancel(target.apptId, reason), '취소 발령을 등록하지 못했어요');
          }} />
        )}
      </Modal>
    </Card>
  );
}

function CancelForm({ appt, empName, onSubmit, onCancel }: { appt: AppointmentView; empName: (id: number) => string; onSubmit: (reason: string) => Promise<void>; onCancel: () => void }) {
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!reason.trim()) { setError('취소 사유를 입력해 주세요(근로기준법 제23조)'); return; }
    void onSubmit(reason.trim());
  };
  return (
    <form className="form" onSubmit={submit} noValidate aria-label="취소 발령">
      <p className="muted sm">{empName(appt.empId)} · {appt.effectiveDate} {APPT_TYPE_LABEL[appt.apptTypeCode]} 발령(<code>{appt.apptNo}</code>)을 되돌리는 취소 발령을 만들어요. 취소 발령을 확정해야 반영돼요.</p>
      <label className="field">
        <span className="field__label">취소 사유</span>
        <textarea className="input" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={1000} rows={3} />
      </label>
      {error && <p className="badge badge--red" role="alert">{error}</p>}
      <div className="form__actions">
        <button type="button" className="glass pill" onClick={onCancel}>닫기</button>
        <button type="submit" className="btn btn--primary">취소 발령 등록</button>
      </div>
    </form>
  );
}

// ---------------------------------------------------------------- 등록·일괄 발령

interface CodeOption { code: string; name: string }

function RegisterCard({ api, employees, emps, grades, titles, today, empName, onDone }: {
  api: AppointmentApi; employees: EmployeeApi; emps: EmployeeSummary[]; grades: CodeOption[]; titles: CodeOption[]; today: string;
  empName: (id: number) => string; onDone: () => void;
}) {
  const [q, setQ] = useState('');
  const [picked, setPicked] = useState<number[]>([]);
  const [type, setType] = useState<ApptType | ''>('');
  const [date, setDate] = useState(today);
  const [depts, setDepts] = useState<FlatDept[]>([]);
  const [deptError, setDeptError] = useState<string | null>(null);
  const [dept, setDept] = useState('');
  const [gradeCode, setGradeCode] = useState('');
  const [titleCode, setTitleCode] = useState('');
  const [leaveStart, setLeaveStart] = useState('');
  const [leaveEnd, setLeaveEnd] = useState('');
  const [reason, setReason] = useState('');
  const [confirmNow, setConfirmNow] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // 결과 표시는 제출할 때의 '바로 확정' 값으로 한다(제출 뒤 체크를 바꿔도 결과 문구가 바뀌지 않게)
  const [result, setResult] = useState<{ r: BulkResult; confirmed: boolean } | null>(null);
  const f = typeFields(type);

  useEffect(() => {
    let alive = true;
    employees.orgTree(date).then((tree) => {
      if (!alive) return;
      setDepts(flattenDepts(tree.roots));
      setDeptError(null);
    }, (e: unknown) => {
      if (!alive) return;
      setDepts([]);
      setDeptError(errorOf(e, '발령일 기준 조직을 불러오지 못했어요'));
    });
    return () => { alive = false; };
  }, [date, employees]);

  const eligible = useMemo(() => emps.filter((e) => isEmployedOn(e, date)), [date, emps]);

  const shown = useMemo(() => {
    const s = q.trim();
    return s ? eligible.filter((e) => e.name.includes(s) || e.empNo.includes(s)) : eligible;
  }, [eligible, q]);
  const toggle = (id: number) => setPicked((p) => (p.includes(id) ? p.filter((x) => x !== id) : [...p, id]));
  const allShown = shown.length > 0 && shown.every((e) => picked.includes(e.empId));
  const toggleAll = () => setPicked((p) => (allShown ? p.filter((id) => !shown.some((e) => e.empId === id)) : [...new Set([...p, ...shown.map((e) => e.empId)])]));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (picked.length === 0) { setError('발령할 사원을 선택해 주세요'); return; }
    if (picked.length > BULK_MAX) { setError(`한 번에 ${BULK_MAX}명까지 발령할 수 있어요`); return; }
    const input: AppointmentInput = {
      apptTypeCode: type, effectiveDate: date, afterDeptId: dept ? Number(dept) : null,
      afterJobGradeCode: gradeCode || null, afterJobTitleCode: titleCode || null, reason,
      leaveStartDate: leaveStart || null, leaveEndDate: leaveEnd || null,
    };
    const err = validateAppointment(input);
    if (err) { setError(err); return; }
    setBusy(true);
    setError(null);
    try {
      const r = await api.bulk(picked, input, confirmNow);
      setResult({ r, confirmed: confirmNow });
      // 성공한 사원은 선택을 풀고, 실패한 사원만 남겨 바로 다시 시도할 수 있게 한다
      setPicked(r.items.filter((i) => !i.ok).map((i) => i.empId));
      if (r.succeeded > 0) onDone();
    } catch (ex) {
      setError(errorOf(ex, '발령을 등록하지 못했어요'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card i={1}>
      <CardHead title="발령 등록" sub="여러 사원을 고르면 같은 유형·발령일·사유로 한 번에 등록해요. 한 사원이 실패해도 나머지는 등록돼요" />
      <form className="form appt-form" onSubmit={submit} noValidate aria-label="발령 등록">
        <fieldset className="field appt-pick" aria-label="발령 대상 사원">
          <legend className="field__label">대상 사원 ({picked.length}명 선택)</legend>
          <label className="glass search">
            <LuSearch />
            <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="이름 또는 사번" aria-label="대상 사원 검색" />
          </label>
          <label className="appt-pick__all"><input type="checkbox" checked={allShown} onChange={toggleAll} /> 보이는 사원 모두 선택</label>
          <ul className="appt-pick__list">
            {shown.map((e) => (
              <li key={e.empId}>
                <label><input type="checkbox" checked={picked.includes(e.empId)} onChange={() => toggle(e.empId)} aria-label={`${e.name} 선택`} /> {e.name} <code>{e.empNo}</code></label>
              </li>
            ))}
          </ul>
        </fieldset>
        <label className="field">
          <span className="field__label">발령 유형</span>
          <select className="input" value={type} onChange={(e) => setType(e.target.value as ApptType | '')}>
            <option value="">선택</option>
            {REGISTER_TYPES.map((t) => <option key={t} value={t}>{APPT_TYPE_LABEL[t]}</option>)}
          </select>
        </label>
        <label className="field">
          <span className="field__label">발령일</span>
          <input className="input" type="date" value={date} onChange={(e) => {
            const next = e.target.value;
            setDate(next);
            setPicked((ids) => ids.filter((id) => {
              const employee = emps.find((item) => item.empId === id);
              return employee !== undefined && isEmployedOn(employee, next);
            }));
            setDept('');
          }} />
        </label>
        {deptError && <p className="badge badge--red" role="alert">{deptError}</p>}
        {f.dept && (
          <label className="field">
            <span className="field__label">발령 부서</span>
            <select className="input" value={dept} onChange={(e) => setDept(e.target.value)}>
              <option value="">선택</option>
              {depts.map((d) => <option key={d.deptId} value={d.deptId}>{'　'.repeat(d.depth)}{d.name}</option>)}
            </select>
          </label>
        )}
        {f.grade && (
          <label className="field">
            <span className="field__label">발령 직급</span>
            <select className="input" value={gradeCode} onChange={(e) => setGradeCode(e.target.value)}>
              <option value="">선택</option>
              {grades.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
            </select>
          </label>
        )}
        {f.title && (
          <label className="field">
            <span className="field__label">발령 직책(비우면 해제)</span>
            <select className="input" value={titleCode} onChange={(e) => setTitleCode(e.target.value)}>
              <option value="">없음(해제)</option>
              {titles.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
            </select>
          </label>
        )}
        {f.leave && (
          <>
            <label className="field">
              <span className="field__label">휴직 시작일</span>
              <input className="input" type="date" value={leaveStart} onChange={(e) => setLeaveStart(e.target.value)} />
            </label>
            <label className="field">
              <span className="field__label">휴직 종료 예정일</span>
              <input className="input" type="date" value={leaveEnd} onChange={(e) => setLeaveEnd(e.target.value)} />
            </label>
          </>
        )}
        <label className="field">
          <span className="field__label">발령 사유 (근로기준법 제23조)</span>
          <textarea className="input" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={1000} rows={2} />
        </label>
        <label className="appt-pick__all"><input type="checkbox" checked={confirmNow} onChange={(e) => setConfirmNow(e.target.checked)} /> 등록하면서 바로 확정</label>
        {error && <p className="badge badge--red" role="alert">{error}</p>}
        <div className="form__actions">
          <button type="submit" className="btn btn--primary" disabled={busy}>{picked.length > 1 ? `${picked.length}명 일괄 발령` : '발령 등록'}</button>
        </div>
      </form>
      {result && <BulkResultView result={result.r} empName={empName} confirmed={result.confirmed} />}
    </Card>
  );
}

function BulkResultView({ result, empName, confirmed }: { result: BulkResult; empName: (id: number) => string; confirmed: boolean }) {
  return (
    <section className="appt-result rise" style={vars({ i: 0 })} aria-label="발령 결과">
      <p><Badge tone="green">{confirmed ? '등록·확정' : '등록'} {result.succeeded}명</Badge>{' '}{result.failed > 0 && <Badge tone="red">실패 {result.failed}명</Badge>}</p>
      {result.failed > 0 && <p className="muted sm">실패한 사원은 등록·확정이 모두 되돌려졌고, 선택에 남겨 두었어요. 고친 뒤 다시 등록해 주세요.</p>}
      <ul className="appt-result__list">
        {result.items.map((i) => (
          <li key={i.empId} className={i.ok ? 'ok' : 'fail'}>
            <Badge tone={i.ok ? 'green' : 'red'}>{i.ok ? '성공' : '실패'}</Badge> {empName(i.empId)}
            {i.ok && i.appointment ? <span className="muted sm"> · <code>{i.appointment.apptNo}</code></span> : <span className="sm"> · {i.error}</span>}
          </li>
        ))}
      </ul>
    </section>
  );
}
