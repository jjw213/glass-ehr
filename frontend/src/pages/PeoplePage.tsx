import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { LuSearch, LuX, LuPlus, LuRefreshCw, LuEye, LuEyeOff, LuLock, LuIdCard, LuUpload } from 'react-icons/lu';
import { Badge, Card, Modal, PageHead, cx, vars } from '../components/ui';
import { codeApi, CODE_GROUP, type CodeApi } from '../api/codes';
import {
  employeeApi, filterEmployees, flattenDepts, seoulToday, statusOf, STATUS_LABEL, validateEmployee, MINUTES_PER_WEEK,
  type Actor, type EmployeeApi, type EmployeeCreate, type EmployeeSummary, type EmployeeView, type EmpStatus, type FlatDept, type StatusFilter,
} from '../api/employees';
import { useCodes } from '../hooks/useCodes';
import { httpEmployeeImportApi, type EmployeeImportApi, type ImportConfirmed } from '../api/employeeImport';
import { EmployeeExportButton, EmployeeImportDialog } from '../components/EmployeeImport';
import { canExportEmployees, canRegisterEmployee, canRevealRrn, canViewPersonal, HIDDEN, shownPersonal, type ViewerRole } from '../lib/privacy';
import { useData, useStore } from '../state/store';

/** 인사정보 = 사원 목록 + 인사기록카드 + 신규 등록 (#15). 데이터는 Spring 백엔드 /api/employees·/api/org. */
export function PeoplePage() {
  const { me } = useData();
  const { toast } = useStore();
  const viewer = useMemo<Viewer>(() => ({ id: me.loginId, role: me.role }), [me.loginId, me.role]);
  return <EmployeeDirectory api={employeeApi} codes={codeApi} viewer={viewer} toast={toast} />;
}

export interface Viewer extends Actor { role: ViewerRole }

const STATUS_FILTERS: Array<{ id: StatusFilter; label: string }> = [
  { id: 'ACTIVE', label: '재직' },
  { id: 'PENDING', label: '입사예정' },
  { id: 'RETIRED', label: '퇴직' },
  { id: 'ALL', label: '전체' },
];

const statusTone = (s: EmpStatus) => (s === 'ACTIVE' ? 'green' : s === 'RETIRING' ? 'amber' : s === 'PENDING' ? 'blue' : 'neutral');
const errText = (e: unknown, fallback: string) => (e instanceof Error ? e.message : fallback);

/** 코드값 → 이름. 화면 전체에서 쓰는 코드그룹을 한 번에 불러온다. */
function useNames(api: CodeApi) {
  const grade = useCodes(CODE_GROUP.JOB_GRADE, api);
  const title = useCodes(CODE_GROUP.JOB_TITLE, api);
  const family = useCodes(CODE_GROUP.JOB_FAMILY, api);
  const type = useCodes(CODE_GROUP.EMPLOYMENT_TYPE, api);
  const location = useCodes(CODE_GROUP.WORK_LOCATION, api);
  const reason = useCodes(CODE_GROUP.RETIREMENT_REASON, api);
  return { grade, title, family, type, location, reason };
}
type Names = ReturnType<typeof useNames>;

/** 화면 본문. 스토어 없이 api·viewer·toast 만 받아서 테스트할 수 있다. today 는 Asia/Seoul 'YYYY-MM-DD'. */
export function EmployeeDirectory({ api, codes, viewer, toast, today: todayProp, importApi = httpEmployeeImportApi }: {
  api: EmployeeApi; codes: CodeApi; viewer: Viewer; toast: (text: string) => void; today?: string; importApi?: EmployeeImportApi;
}) {
  const today = todayProp ?? seoulToday();
  const names = useNames(codes);
  const [list, setList] = useState<EmployeeSummary[] | null>(null);
  const [depts, setDepts] = useState<FlatDept[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  const [q, setQ] = useState('');
  const [deptId, setDeptId] = useState<number | null>(null);
  const [grade, setGrade] = useState('');
  const [status, setStatus] = useState<StatusFilter>('ACTIVE');
  const [sel, setSel] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);
  const [importing, setImporting] = useState(false);

  useEffect(() => {
    let alive = true;
    Promise.all([api.list(true), api.orgTree(today)]).then(([l, t]) => {
      if (!alive) return;
      setList(l);
      setDepts(flattenDepts(t.roots));
      setLoadError(null);
    }, (e: unknown) => { if (alive) { setList([]); setLoadError(errText(e, '사원 목록을 불러오지 못했어요')); } });
    return () => { alive = false; };
  }, [api, today, reload]);

  const deptName = useCallback((id: number | null) => (id === null ? '' : depts.find((d) => d.deptId === id)?.name ?? `부서 ${id}`), [depts]);
  const shown = useMemo(() => filterEmployees(list ?? [], { q, deptId, jobGradeCode: grade, status }, depts, today), [list, q, deptId, grade, status, depts, today]);
  const total = (list ?? []).filter((e) => statusOf(e, today) !== 'RETIRED').length;

  const created = (v: EmployeeView) => {
    setCreating(false);
    setReload((n) => n + 1);
    toast(`${v.name} 님을 사번 ${v.empNo}로 등록했어요`);
    setSel(v.empId);
  };

  const imported = (r: ImportConfirmed) => {
    setImporting(false);
    setReload((n) => n + 1);
    toast(`${r.created}명을 등록했어요`);
  };

  return (
    <>
      {canRegisterEmployee(viewer.role) && (
        <EmployeeImportDialog api={importApi} open={importing} onClose={() => setImporting(false)} onDone={imported} />
      )}
      <PageHead
        title="인사정보"
        sub={list ? `재직 ${total}명 · 검색 결과 ${shown.length}명` : '불러오는 중…'}
        right={
          <>
            <button className="glass pill" onClick={() => setReload((n) => n + 1)} aria-label="새로 고침"><LuRefreshCw /> 새로 고침</button>
            {canExportEmployees(viewer.role) && <EmployeeExportButton api={importApi} toast={toast} />}
            {canRegisterEmployee(viewer.role) && (
              <button className="glass pill" onClick={() => setImporting(true)}><LuUpload /> 엑셀 일괄 등록</button>
            )}
            {canRegisterEmployee(viewer.role) && (
              <button className="btn btn--primary" onClick={() => setCreating(true)}><LuPlus /> 신규 등록</button>
            )}
          </>
        }
      />

      <div className="toolbar rise" style={vars({ i: 1 })}>
        <label className="glass search">
          <LuSearch />
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="이름 또는 사번으로 검색" aria-label="이름 또는 사번 검색" />
          {q && <button onClick={() => setQ('')} aria-label="검색어 지우기"><LuX /></button>}
        </label>
        <div className="emp-filters">
          <select className="input" value={deptId ?? ''} onChange={(e) => setDeptId(e.target.value ? Number(e.target.value) : null)} aria-label="부서">
            <option value="">전체 부서</option>
            {depts.map((d) => <option key={d.deptId} value={d.deptId}>{'　'.repeat(d.depth)}{d.name}</option>)}
          </select>
          <select className="input" value={grade} onChange={(e) => setGrade(e.target.value)} aria-label="직급">
            <option value="">전체 직급</option>
            {names.grade.codes.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
          </select>
        </div>
        <div className="chips" role="radiogroup" aria-label="재직상태">
          {STATUS_FILTERS.map((s) => (
            <button key={s.id} role="radio" aria-checked={status === s.id} className={cx('glass pill', status === s.id && 'active')} onClick={() => setStatus(s.id)}>
              {s.label}
            </button>
          ))}
        </div>
      </div>

      <Card i={2} className="emp-list">
        {loadError && <p className="badge badge--red" role="alert">{loadError}</p>}
        {list === null ? (
          <p className="muted code-empty">불러오는 중…</p>
        ) : shown.length === 0 ? (
          <p className="muted code-empty">검색 결과가 없어요</p>
        ) : (
          <table className="emp-table">
            <thead>
              <tr><th>사번</th><th>성명</th><th>부서</th><th>직급</th><th className="hide-sm">직책</th><th className="hide-sm">고용형태</th><th className="hide-sm">입사일</th><th>상태</th></tr>
            </thead>
            <tbody>
              {shown.map((e) => {
                const st = statusOf(e, today);
                return (
                  <tr key={e.empId} tabIndex={0} onClick={() => setSel(e.empId)} onKeyDown={(k) => { if (k.key === 'Enter' || k.key === ' ') { k.preventDefault(); setSel(e.empId); } }} aria-label={`${e.name} 인사기록카드 열기`}>
                    <td className="emp-table__no"><code>{e.empNo}</code></td>
                    <td className="emp-table__name"><strong>{e.name}</strong></td>
                    <td className="emp-table__dept">{deptName(e.currentDeptId)}</td>
                    <td className="emp-table__grade">{names.grade.nameOf(e.jobGradeCode)}</td>
                    <td className="hide-sm">{names.title.nameOf(e.jobTitleCode)}</td>
                    <td className="hide-sm">{names.type.nameOf(e.employmentTypeCode)}</td>
                    <td className="hide-sm num">{e.hireDate}</td>
                    <td className="emp-table__status"><Badge tone={statusTone(st)}>{STATUS_LABEL[st]}</Badge></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </Card>

      {sel !== null && (
        <EmployeeCard key={sel} api={api} empId={sel} viewer={viewer} names={names} deptName={deptName} today={today} onClose={() => setSel(null)} />
      )}

      <Modal open={creating} onClose={() => setCreating(false)} title="신규 사원 등록">
        {creating && <EmployeeCreateForm api={api} viewer={viewer} names={names} depts={depts} today={today} onCreated={created} onCancel={() => setCreating(false)} />}
      </Modal>
    </>
  );
}

/* ---------------- 인사기록카드 ---------------- */

const TABS = ['인적사항', '발령 이력', '근로계약', '근태 요약'] as const;
type Tab = typeof TABS[number];

/** 원문을 화면에 띄워 두는 시간(ms). 지나면 다시 가린다. */
const RRN_SHOW_MS = 30_000;

const hm = (m: number | null) => (m === null ? '' : `${Math.floor(m / 60)}시간${m % 60 ? ` ${m % 60}분` : ''}`);

function EmployeeCard({ api, empId, viewer, names, deptName, today, onClose }: {
  api: EmployeeApi; empId: number; viewer: Viewer; names: Names; deptName: (id: number | null) => string; today: string; onClose: () => void;
}) {
  const [emp, setEmp] = useState<EmployeeView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>('인적사항');
  const [rrnPlain, setRrnPlain] = useState<string | null>(null);
  const [rrnError, setRrnError] = useState<string | null>(null);
  const [revealing, setRevealing] = useState(false);

  useEffect(() => {
    let alive = true;
    api.get(empId).then((v) => { if (alive) setEmp(v); }, (e: unknown) => { if (alive) setError(errText(e, '인사기록카드를 불러오지 못했어요')); });
    return () => { alive = false; };
  }, [api, empId]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  // 원문은 잠깐만 보이고 다시 가린다
  useEffect(() => {
    if (rrnPlain === null) return;
    const t = setTimeout(() => setRrnPlain(null), RRN_SHOW_MS);
    return () => clearTimeout(t);
  }, [rrnPlain]);

  const reveal = async () => {
    setRevealing(true);
    setRrnError(null);
    try { setRrnPlain(await api.revealRrn(empId, 'VIEW', viewer)); } catch (e) { setRrnError(errText(e, '원문을 불러오지 못했어요')); } finally { setRevealing(false); }
  };

  const personal = canViewPersonal(viewer.role);
  const st = emp ? statusOf(emp, today) : null;

  return (
    <div className="drawer" onClick={onClose}>
      <aside className="glass drawer__panel emp-card" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true" aria-label={emp ? `${emp.name} 인사기록카드` : '인사기록카드'}>
        <div className="modal__grab" />
        <button className="iconbtn drawer__close" onClick={onClose} aria-label="닫기"><LuX /></button>
        {error ? (
          <p className="badge badge--red" role="alert">{error}</p>
        ) : !emp ? (
          <p className="muted code-empty">불러오는 중…</p>
        ) : (
          <>
            <header className="emp-card__head">
              <span className="emp-card__mark" aria-hidden><LuIdCard /></span>
              <div>
                <h2>{emp.name}</h2>
                <p className="muted sm"><code>{emp.empNo}</code> · {deptName(emp.currentDeptId)} · {names.grade.nameOf(emp.jobGradeCode)}{emp.jobTitleCode ? ` · ${names.title.nameOf(emp.jobTitleCode)}` : ''}</p>
              </div>
              {st && <Badge tone={statusTone(st)}>{STATUS_LABEL[st]}</Badge>}
            </header>

            <div className="emp-tabs" role="tablist" aria-label="인사기록카드 항목">
              {TABS.map((t) => (
                <button key={t} role="tab" aria-selected={tab === t} className={cx(tab === t && 'on')} onClick={() => setTab(t)}>{t}</button>
              ))}
            </div>

            <div role="tabpanel" aria-label={tab} className="emp-panel">
              {tab === '인적사항' && <PersonalTab emp={emp} viewer={viewer} names={names} rrnPlain={rrnPlain} rrnError={rrnError} revealing={revealing} onReveal={() => void reveal()} onHide={() => setRrnPlain(null)} />}
              {tab === '발령 이력' && <AssignmentTab emp={emp} names={names} deptName={deptName} />}
              {tab === '근로계약' && (personal ? <ContractTab emp={emp} names={names} /> : <Locked />)}
              {tab === '근태 요약' && (personal ? <AttendanceTab /> : <Locked />)}
            </div>
          </>
        )}
      </aside>
    </div>
  );
}

function Locked() {
  return <p className="emp-locked muted"><LuLock /> 인사·급여 담당자만 볼 수 있어요</p>;
}

function Rows({ rows }: { rows: Array<[string, ReactNode]> }) {
  return (
    <dl className="emp-rows">
      {rows.map(([k, v]) => (
        <div key={k}><dt>{k}</dt><dd>{v === '' || v === null || v === undefined ? <span className="muted">-</span> : v}</dd></div>
      ))}
    </dl>
  );
}

function PersonalTab({ emp, viewer, names, rrnPlain, rrnError, revealing, onReveal, onHide }: {
  emp: EmployeeView; viewer: Viewer; names: Names; rrnPlain: string | null; rrnError: string | null; revealing: boolean; onReveal: () => void; onHide: () => void;
}) {
  const p = shownPersonal(viewer.role, emp);
  const rrnCell = (
    <span className="emp-rrn">
      <code data-testid="rrn">{rrnPlain ?? p.rrn}</code>
      {canRevealRrn(viewer.role) && emp.rrnMasked && (
        rrnPlain === null
          ? <button type="button" className="link" onClick={onReveal} disabled={revealing}><LuEye /> 원문 보기</button>
          : <button type="button" className="link" onClick={onHide}><LuEyeOff /> 가리기</button>
      )}
    </span>
  );
  return (
    <>
      <Rows rows={[
        ['사번', <code key="no">{emp.empNo}</code>],
        ['성명', emp.name],
        ['성별', emp.gender === 'M' ? '남' : '여'],
        ['생년월일', p.birthDate],
        ['주민등록번호', rrnCell],
        ['주소', p.address],
        ['이력', p.careerHistory],
        ['종사 업무', canViewPersonal(viewer.role) ? emp.jobDescription : HIDDEN],
        ['직군', names.family.nameOf(emp.jobFamilyCode)],
        ['사업장', emp.siteCode],
        ['근무지', names.location.nameOf(emp.workLocationCode)],
        ['기타', canViewPersonal(viewer.role) ? emp.otherNote ?? '' : HIDDEN],
      ]} />
      {rrnPlain !== null && <p className="muted sm emp-note">원문 조회 기록이 남았어요. 30초 뒤 다시 가려져요.</p>}
      {rrnError && <p className="badge badge--red" role="alert">{rrnError}</p>}
    </>
  );
}

function AssignmentTab({ emp, names, deptName }: { emp: EmployeeView; names: Names; deptName: (id: number | null) => string }) {
  const rows: Array<{ date: string; kind: string; detail: string }> = [
    { date: emp.hireDate, kind: '입사', detail: [deptName(emp.currentDeptId), names.grade.nameOf(emp.jobGradeCode), names.title.nameOf(emp.jobTitleCode)].filter(Boolean).join(' · ') },
  ];
  if (emp.retireDate) rows.push({ date: emp.retireDate, kind: '퇴직', detail: names.reason.nameOf(emp.retireReasonCode) });
  return (
    <>
      <ol className="emp-timeline">
        {[...rows].reverse().map((r) => (
          <li key={r.kind + r.date}><span className="num">{r.date}</span><Badge tone={r.kind === '퇴직' ? 'amber' : 'blue'}>{r.kind}</Badge><span>{r.detail}</span></li>
        ))}
      </ol>
      <p className="muted sm emp-note">부서이동·승진·휴직·복직 이력은 인사발령 기능이 생기면 여기에 쌓여요. 부서명은 오늘 기준 조직명이에요.</p>
    </>
  );
}

function ContractTab({ emp, names }: { emp: EmployeeView; names: Names }) {
  const period = emp.contractStartDate || emp.contractEndDate ? `${emp.contractStartDate ?? ''} ~ ${emp.contractEndDate ?? ''}` : '기간의 정함 없음';
  return (
    <Rows rows={[
      ['고용형태', names.type.nameOf(emp.employmentTypeCode)],
      ['입사일', emp.hireDate],
      ['고용갱신일', emp.renewalDate ?? ''],
      ['계약기간', period],
      ['1주 소정근로시간', hm(emp.weeklyContractMinutes)],
      ['근무지', names.location.nameOf(emp.workLocationCode)],
      ['고용 관련 메모', emp.employmentNote ?? ''],
      ['퇴직일', emp.retireDate ?? ''],
      ['퇴직 사유', emp.retireReasonCode ? [names.reason.nameOf(emp.retireReasonCode), emp.retireReasonDetail].filter(Boolean).join(' · ') : ''],
      ['명부 보존기한', emp.recordRetentionUntil ?? ''],
    ]} />
  );
}

function AttendanceTab() {
  return <p className="emp-locked muted">근태 모듈이 만들어지면 이번 달 근무시간·연장근로·연차 사용 현황을 여기에서 보여 줘요.</p>;
}

/* ---------------- 신규 등록 ---------------- */

interface Draft {
  siteCode: string; name: string; gender: '' | 'M' | 'F'; birthDate: string; address: string; careerHistory: string;
  jobDescription: string; jobFamilyCode: string; hireDate: string; contractStartDate: string; contractEndDate: string;
  employmentTypeCode: string; weeklyHours: string; weeklyMinutes: string; workLocationCode: string; employmentNote: string;
  deptId: string; jobGradeCode: string; jobTitleCode: string; rrn: string;
}

const emptyDraft = (today: string): Draft => ({
  siteCode: 'S01', name: '', gender: '', birthDate: '', address: '', careerHistory: '', jobDescription: '', jobFamilyCode: '',
  hireDate: today, contractStartDate: '', contractEndDate: '', employmentTypeCode: '', weeklyHours: '', weeklyMinutes: '',
  workLocationCode: '', employmentNote: '', deptId: '', jobGradeCode: '', jobTitleCode: '', rrn: '',
});

/** 시간·분 입력을 분 단위 정수로. 둘 다 비면 null(미입력). */
function weeklyMinutes(h: string, m: string): number | null {
  if (!h.trim() && !m.trim()) return null;
  const hh = h.trim() ? Number(h) : 0, mm = m.trim() ? Number(m) : 0;
  if (!Number.isInteger(hh) || !Number.isInteger(mm) || mm < 0 || mm > 59) return NaN;
  return hh * 60 + mm;
}

function EmployeeCreateForm({ api, viewer, names, depts, today, onCreated, onCancel }: {
  api: EmployeeApi; viewer: Viewer; names: Names; depts: FlatDept[]; today: string; onCreated: (v: EmployeeView) => void; onCancel: () => void;
}) {
  const [d, setD] = useState<Draft>(() => emptyDraft(today));
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const set = <K extends keyof Draft>(k: K) => (v: Draft[K]) => setD((cur) => ({ ...cur, [k]: v }));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const minutes = weeklyMinutes(d.weeklyHours, d.weeklyMinutes);
    if (Number.isNaN(minutes)) { setError('1주 소정근로시간은 시간·분(0~59)을 숫자로 써 주세요'); return; }
    const input: EmployeeCreate = {
      siteCode: d.siteCode.trim().toUpperCase(), name: d.name, gender: d.gender, birthDate: d.birthDate, address: d.address,
      careerHistory: d.careerHistory || null, jobDescription: d.jobDescription, jobFamilyCode: d.jobFamilyCode || null,
      hireDate: d.hireDate, renewalDate: null, contractStartDate: d.contractStartDate || null, contractEndDate: d.contractEndDate || null,
      employmentTypeCode: d.employmentTypeCode, weeklyContractMinutes: minutes, workLocationCode: d.workLocationCode || null,
      employmentNote: d.employmentNote || null, otherNote: null, deptId: d.deptId ? Number(d.deptId) : null,
      jobGradeCode: d.jobGradeCode || null, jobTitleCode: d.jobTitleCode || null, rrn: d.rrn.trim() || null,
    };
    const err = validateEmployee(input);
    if (err) { setError(err); return; }
    if (minutes !== null && minutes > MINUTES_PER_WEEK) { setError('1주 소정근로시간(분)이 너무 커요'); return; }
    setSaving(true);
    setError(null);
    try {
      const v = await api.create(input, viewer);
      setD((cur) => ({ ...cur, rrn: '' }));   // 원문을 화면 상태에 남기지 않는다
      onCreated(v);
    } catch (e2) {
      setError(errText(e2, '등록하지 못했어요'));
    } finally {
      setSaving(false);
    }
  };

  const text = (label: string, k: keyof Draft, opts: { max?: number; type?: string; placeholder?: string } = {}) => (
    <label className="field">
      <span className="field__label">{label}</span>
      <input className="input" type={opts.type ?? 'text'} value={d[k]} maxLength={opts.max} placeholder={opts.placeholder} autoComplete="off" onChange={(e) => set(k)(e.target.value as never)} />
    </label>
  );
  const pick = (label: string, k: keyof Draft, options: Array<{ value: string; label: string }>, emptyLabel = '선택 안 함') => (
    <label className="field">
      <span className="field__label">{label}</span>
      <select className="input" value={d[k]} onChange={(e) => set(k)(e.target.value as never)}>
        <option value="">{emptyLabel}</option>
        {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
    </label>
  );
  const codeOpts = (c: Names[keyof Names]) => c.codes.map((x) => ({ value: x.code, label: x.name }));

  return (
    <form className="form emp-form" onSubmit={submit} noValidate>
      <p className="muted sm">사번은 저장할 때 사업장·입사연도 순번으로 자동으로 매겨져요. 소속·직급·직책은 입사 때 값이고, 이후에는 인사발령으로 바꿔요.</p>
      <div className="form__row">
        {text('성명', 'name', { max: 100 })}
        {pick('성별', 'gender', [{ value: 'M', label: '남' }, { value: 'F', label: '여' }], '선택')}
      </div>
      <div className="form__row">
        {text('생년월일', 'birthDate', { type: 'date' })}
        {text('주민등록번호 (선택)', 'rrn', { max: 14, type: 'password', placeholder: '000000-0000000' })}
      </div>
      {text('주소', 'address', { max: 300 })}
      <div className="form__row">
        {text('사업장코드', 'siteCode', { max: 3, placeholder: 'S01' })}
        {text('입사일', 'hireDate', { type: 'date' })}
      </div>
      <div className="form__row">
        {pick('부서', 'deptId', depts.map((x) => ({ value: String(x.deptId), label: x.path })))}
        {pick('고용형태', 'employmentTypeCode', codeOpts(names.type), '선택')}
      </div>
      <div className="form__row">
        {pick('직급', 'jobGradeCode', codeOpts(names.grade))}
        {pick('직책', 'jobTitleCode', codeOpts(names.title))}
      </div>
      <div className="form__row">
        {pick('직군', 'jobFamilyCode', codeOpts(names.family))}
        {pick('근무지', 'workLocationCode', codeOpts(names.location))}
      </div>
      {text('종사 업무', 'jobDescription', { max: 500 })}
      <div className="form__row">
        {text('계약 시작일', 'contractStartDate', { type: 'date' })}
        {text('계약 종료일', 'contractEndDate', { type: 'date' })}
      </div>
      <div className="form__row">
        <label className="field">
          <span className="field__label">1주 소정근로시간 (시간)</span>
          <input className="input" inputMode="numeric" value={d.weeklyHours} onChange={(e) => set('weeklyHours')(e.target.value.replace(/[^0-9]/g, ''))} />
        </label>
        <label className="field">
          <span className="field__label">(분)</span>
          <input className="input" inputMode="numeric" value={d.weeklyMinutes} maxLength={2} onChange={(e) => set('weeklyMinutes')(e.target.value.replace(/[^0-9]/g, ''))} />
        </label>
      </div>
      <label className="field">
        <span className="field__label">이력 (선택)</span>
        <textarea className="input" rows={2} value={d.careerHistory} maxLength={4000} onChange={(e) => set('careerHistory')(e.target.value)} />
      </label>
      <label className="field">
        <span className="field__label">고용 관련 메모 (선택)</span>
        <textarea className="input" rows={2} value={d.employmentNote} maxLength={1000} onChange={(e) => set('employmentNote')(e.target.value)} />
      </label>
      {error && <p className="badge badge--red" role="alert">{error}</p>}
      <div className="form__actions">
        <button type="button" className="btn btn--ghost" onClick={onCancel}>취소</button>
        <button type="submit" className="btn btn--primary" disabled={saving}>{saving ? '등록 중…' : '등록'}</button>
      </div>
    </form>
  );
}
