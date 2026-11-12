import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { LuChevronDown, LuFolderTree, LuMove, LuPlus, LuUser, LuUsers } from 'react-icons/lu';
import { Badge, Card, CardHead, Modal, PageHead, cx } from '../components/ui';
import { codeApi, CODE_GROUP, type CodeApi } from '../api/codes';
import { MAX_DEPT_SORT_ORDER, orgApi, validateDept, type DeptNode, type DeptView, type EmployeeSummary, type OrgApi, type OrgTree } from '../api/org';
import { useCodes } from '../hooks/useCodes';
import { useIsMobile } from '../hooks/useMediaQuery';
import { findDept, flatten, headcounts, membersByDept, nextDay, seoulToday, selfAndDescendants } from '../lib/orgTree';
import { canManageOrg } from '../nav';
import type { Role } from '../api/types';
import { useData, useStore } from '../state/store';

/** 조직도 (#13). 기준일 조직 트리 + 부서원 목록. 인사담당자·관리자는 부서 추가·이동. */
export function OrgPage() {
  const { me } = useData();
  const { toast } = useStore();
  const mobile = useIsMobile();
  return <OrgChart api={orgApi} codes={codeApi} role={me.role} toast={toast} mobile={mobile} today={seoulToday()} />;
}

type Editing =
  | { mode: 'create'; parentId: number | null }
  | { mode: 'move'; node: DeptNode };

interface Draft { deptCode: string; name: string; parentId: string; sortOrder: string; effectiveDate: string; reason: string }

const ROOT = '';   // 상위부서 선택 목록의 '최상위'
const later = (a: string, b: string) => (a > b ? a : b);

export interface OrgChartProps {
  api: OrgApi;
  codes: CodeApi;
  role: Role;
  toast: (text: string) => void;
  /** 모바일(글래스 사이드바 레이아웃): 한 열 세로 트리, 부서원 목록은 트리 아래 */
  mobile: boolean;
  /** 서울 기준 오늘 'YYYY-MM-DD' */
  today: string;
}

/** 화면 본문. 스토어 없이 api·role·toast 만 받아서 테스트할 수 있다. */
export function OrgChart({ api, codes, role, toast, mobile, today }: OrgChartProps) {
  const canManage = canManageOrg(role);
  const [date, setDate] = useState(today);
  const [tree, setTree] = useState<OrgTree | null>(null);
  const [employees, setEmployees] = useState<EmployeeSummary[] | null>(null);
  const [empFailed, setEmpFailed] = useState(false);
  const [empRetry, setEmpRetry] = useState(0);
  const [collapsed, setCollapsed] = useState<Set<number>>(() => new Set());
  const [selected, setSelected] = useState<number | null>(null);
  const [editing, setEditing] = useState<Editing | null>(null);
  const [draft, setDraft] = useState<Draft>({ deptCode: '', name: '', parentId: ROOT, sortOrder: '10', effectiveDate: today, reason: '' });
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [reload, setReload] = useState(0);
  const membersRef = useRef<HTMLDivElement>(null);
  const grades = useCodes(CODE_GROUP.JOB_GRADE, codes);
  const titles = useCodes(CODE_GROUP.JOB_TITLE, codes);

  const fail = useCallback((e: unknown) => toast(e instanceof Error ? e.message : '알 수 없는 오류가 발생했어요'), [toast]);

  useEffect(() => {
    if (!date) return;
    let alive = true;
    setTree(null);
    api.tree(date).then((t) => { if (alive) setTree(t); }, fail);
    return () => { alive = false; };
  }, [api, date, reload, fail]);

  // 사원 목록을 못 받으면 0명으로 보이지 않게 '불러오지 못함' 상태로 두고 다시 시도할 수 있게 한다
  useEffect(() => {
    let alive = true;
    setEmpFailed(false);
    api.employees().then(
      (l) => { if (alive) setEmployees(l); },
      (e: unknown) => { if (alive) { setEmployees(null); setEmpFailed(true); fail(e); } },
    );
    return () => { alive = false; };
  }, [api, reload, empRetry, fail]);
  const retryEmployees = useCallback(() => setEmpRetry((n) => n + 1), []);
  /** 기준일 변경. 사원 목록을 못 받은 상태였으면 같이 다시 시도한다 */
  const changeDate = (d: string) => { setDate(d); if (empFailed) retryEmployees(); };

  const roots = useMemo(() => tree?.roots ?? [], [tree]);
  const flat = useMemo(() => flatten(roots), [roots]);
  const members = useMemo(() => membersByDept(employees ?? [], date), [employees, date]);
  const counts = useMemo(() => headcounts(roots, members), [roots, members]);
  const empReady = employees !== null;
  const total = useMemo(() => roots.reduce((s, r) => s + (counts.get(r.deptId)?.total ?? 0), 0), [roots, counts]);
  const current = selected !== null ? findDept(roots, selected) : undefined;
  const parentIds = useMemo(() => flat.filter((f) => f.node.children.length > 0).map((f) => f.node.deptId), [flat]);

  // 기준일을 바꿔 선택한 부서가 그날 없으면 선택을 푼다
  useEffect(() => { if (tree && selected !== null && !findDept(tree.roots, selected)) setSelected(null); }, [tree, selected]);

  const pick = (id: number) => {
    setSelected((cur) => (cur === id ? null : id));
    if (mobile) setTimeout(() => membersRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'start' }), 0);
  };
  const toggle = (id: number) => setCollapsed((s) => { const n = new Set(s); if (n.has(id)) n.delete(id); else n.add(id); return n; });

  /* ----- 부서 추가·이동 (인사담당자) ----- */
  const openCreate = (parentId: number | null) => {
    const siblings = parentId === null ? roots : findDept(roots, parentId)?.children ?? [];
    const next = Math.min(MAX_DEPT_SORT_ORDER, siblings.reduce((m, c) => Math.max(m, c.sortOrder), 0) + 10);
    setDraft({ deptCode: '', name: '', parentId: parentId === null ? ROOT : String(parentId), sortOrder: String(next), effectiveDate: date, reason: '' });
    setFormError(null);
    setEditing({ mode: 'create', parentId });
  };
  const openMove = (node: DeptNode) => {
    setDraft({
      deptCode: node.deptCode, name: node.name, parentId: node.parentDeptId === null ? ROOT : String(node.parentDeptId),
      sortOrder: String(node.sortOrder), effectiveDate: later(date, nextDay(node.validFrom)), reason: '',
    });
    setFormError(null);
    setEditing({ mode: 'move', node });
  };
  const close = useCallback(() => setEditing(null), []);

  const blocked = editing?.mode === 'move' ? selfAndDescendants(editing.node) : new Set<number>();
  const parentOptions = flat.filter((f) => !blocked.has(f.node.deptId));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!editing || !canManage) return;
    const parentDeptId = draft.parentId === ROOT ? null : Number(draft.parentId);
    const sortOrder = draft.sortOrder.trim() === '' ? NaN : Number(draft.sortOrder);
    const reason = draft.reason.trim() || null;
    try {
      setSaving(true);
      if (editing.mode === 'create') {
        const input = { deptCode: draft.deptCode, name: draft.name, parentDeptId, headEmpId: null, sortOrder, effectiveDate: draft.effectiveDate, reason };
        const err = validateDept(input);
        if (err) { setFormError(err); return; }
        const v = await api.createDept(input);
        done(v, `${v.name} 부서를 ${v.validFrom}부터 추가했어요`);
      } else {
        const n = editing.node;
        const base = { name: n.name, parentDeptId, headEmpId: n.headEmpId, sortOrder: n.sortOrder, effectiveDate: draft.effectiveDate, reason };
        const err0 = validateDept(base);
        if (err0) { setFormError(err0); return; }
        // 이동은 상위부서만 바꾼다. PUT 은 속성 전체를 받으므로 나머지 속성은 화면의(과거일 수 있는) 노드가 아니라
        // 적용일 기준 조직도에서 다시 읽은 값을 보낸다. 그 사이의 부서명·부서장·정렬 변경을 되돌리지 않기 위해서다.
        const at = findDept((await api.tree(draft.effectiveDate)).roots, n.deptId);
        if (!at) { setFormError(`${draft.effectiveDate}에는 이 부서가 없어요. 적용일을 확인해 주세요`); return; }
        if (parentDeptId === at.parentDeptId) { setFormError('지금과 같은 상위부서예요. 옮길 부서를 골라 주세요'); return; }
        const input = { ...base, name: at.name, headEmpId: at.headEmpId, sortOrder: at.sortOrder };
        const err = validateDept(input);
        if (err) { setFormError(err); return; }
        const v = await api.changeDept(n.deptId, input);
        done(v, `${v.name} 부서를 ${v.validFrom}부터 옮겼어요`);
      }
    } catch (e2) {
      setFormError(e2 instanceof Error ? e2.message : '저장하지 못했어요');
    } finally {
      setSaving(false);
    }
  };
  /** 저장 후: 적용일 조직도로 옮겨 결과를 보여 준다 */
  const done = (v: DeptView, text: string) => {
    setEditing(null);
    setSelected(v.deptId);
    const parent = v.parentDeptId;
    if (parent !== null) setCollapsed((s) => { const n = new Set(s); n.delete(parent); return n; });
    setDate(v.validFrom);
    setReload((r) => r + 1);
    toast(text);
  };

  /* ----- 화면 ----- */
  const when = date < today ? <Badge tone="amber">과거 조직도</Badge> : date > today ? <Badge tone="blue">예정 조직도</Badge> : <Badge tone="green">오늘</Badge>;

  const renderNode = (n: DeptNode) => {
    const hasKids = n.children.length > 0;
    const open = !collapsed.has(n.deptId);
    const c = counts.get(n.deptId) ?? { direct: 0, total: 0 };
    const shown = (v: number) => (empReady ? `${v}명` : empFailed ? '?' : '…');
    return (
      <li key={n.deptId} role="treeitem" aria-expanded={hasKids ? open : undefined} aria-selected={selected === n.deptId} aria-label={n.name}>
        <div className="org-node">
          {hasKids ? (
            <button type="button" className={cx('org-node__toggle', !open && 'closed')} onClick={() => toggle(n.deptId)} aria-label={`${n.name} ${open ? '접기' : '펼치기'}`}>
              <LuChevronDown />
            </button>
          ) : (
            <span className="org-node__toggle org-node__toggle--leaf" aria-hidden><LuUser /></span>
          )}
          <button type="button" className={cx('org-node__main', selected === n.deptId && 'active')} onClick={() => pick(n.deptId)}>
            <span className="org-node__name">
              <strong>{n.name}</strong>
              {hasKids && <span className="muted sm">직속 {shown(c.direct)}</span>}
            </span>
            <span className="org-node__count" data-testid={`count-${n.deptId}`}>{shown(c.total)}</span>
          </button>
          {canManage && (
            <>
              <button type="button" className="btn btn--ghost icon-only" onClick={() => openCreate(n.deptId)} aria-label={`${n.name} 하위 부서 추가`}><LuPlus /></button>
              <button type="button" className="btn btn--ghost icon-only" onClick={() => openMove(n)} aria-label={`${n.name} 이동`}><LuMove /></button>
            </>
          )}
        </div>
        {hasKids && open && <ul role="group">{n.children.map(renderNode)}</ul>}
      </li>
    );
  };

  const list = current ? members.get(current.deptId) ?? [] : [];

  return (
    <>
      <PageHead
        title="조직도"
        sub={tree ? `${date} 기준 · ${flat.length}개 부서 · ${empReady ? `${total}명` : empFailed ? '인원 확인 불가' : '인원 불러오는 중…'}` : '불러오는 중…'}
        right={canManage && <button className="btn btn--primary" onClick={() => openCreate(null)} disabled={!tree}><LuPlus /> 부서 추가</button>}
      />

      <div className={cx('org-layout', mobile && 'org-layout--mobile')} data-layout={mobile ? 'mobile' : 'desktop'}>
        <Card i={1}>
          <CardHead
            title={<span className="org-date">기준일 {when}</span>}
            sub="날짜를 바꾸면 그날의 조직도를 보여 줘요"
            right={<LuFolderTree className="muted" />}
          />
          <div className="org-date">
            <input type="date" className="input" aria-label="기준일" value={date} onChange={(e) => e.target.value && changeDate(e.target.value)} />
            {date !== today && <button type="button" className="glass pill" onClick={() => changeDate(today)}>오늘로</button>}
            <span className="grow" />
            <div className="org-tools">
              <button type="button" className="btn btn--ghost" onClick={() => setCollapsed(new Set())}>모두 펼치기</button>
              <button type="button" className="btn btn--ghost" onClick={() => setCollapsed(new Set(parentIds))}>모두 접기</button>
            </div>
          </div>
          {tree === null ? (
            <p className="muted code-empty">불러오는 중…</p>
          ) : roots.length === 0 ? (
            <p className="muted code-empty">이 날짜에는 부서가 없어요</p>
          ) : (
            <ul className="org-tree" role="tree" aria-label={`${date} 조직도`}>{roots.map(renderNode)}</ul>
          )}
        </Card>

        <div ref={membersRef}>
          <Card i={2}>
            {current ? (
              <>
                <CardHead title={`${current.name} 부서원`} sub={`${current.deptCode}${empReady ? ` · 직속 ${list.length}명` : ''}`} right={<LuUsers className="muted" />} />
                {!empReady ? (
                  !empFailed && <p className="muted code-empty">부서원을 불러오는 중…</p>
                ) : list.length === 0 ? (
                  <p className="muted code-empty">이 부서에 바로 소속된 사원이 없어요</p>
                ) : (
                  <ul className="org-members" aria-label={`${current.name} 부서원`}>
                    {list.map((e) => (
                      <li key={e.empId}>
                        <span className="chip-icon"><LuUser /></span>
                        <div className="grow">
                          <strong>{e.name} {current.headEmpId === e.empId && <Badge tone="blue">부서장</Badge>}</strong>
                          <span className="muted sm">{[grades.nameOf(e.jobGradeCode), titles.nameOf(e.jobTitleCode)].filter(Boolean).join(' · ')} · {e.empNo}</span>
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
              </>
            ) : (
              <CardHead title="부서를 눌러 보세요" sub="부서를 누르면 부서원 목록이 여기에 나와요" right={<LuUsers className="muted" />} />
            )}
            {empFailed && (
              <div className="code-empty" role="alert">
                <p className="muted">사원 목록을 불러오지 못했어요. 인원 수와 부서원은 확인되지 않은 상태예요.</p>
                <button type="button" className="btn btn--ghost" onClick={retryEmployees}>사원 목록 다시 불러오기</button>
              </div>
            )}
            {date !== today && (
              <p className="muted sm org-note">
                인원은 기준일에 재직 중인 사원을 지금 소속 부서로 센 값이에요. 인사발령 이력이 연결되면 그날의 소속으로 바뀌어요.
              </p>
            )}
          </Card>
        </div>
      </div>

      <Modal open={editing !== null} onClose={close} title={editing?.mode === 'move' ? `${editing.node.name} 이동` : '부서 추가'}>
        <form className="form" onSubmit={submit} noValidate>
          {editing?.mode === 'create' && (
            <>
              <label className="field">
                <span className="field__label">부서코드 (영문 대문자·숫자·밑줄, 추가 후 바꿀 수 없어요)</span>
                <input className="input" value={draft.deptCode} maxLength={20} autoComplete="off" placeholder="예: DEV3"
                  onChange={(e) => setDraft({ ...draft, deptCode: e.target.value.toUpperCase() })} />
              </label>
              <label className="field">
                <span className="field__label">부서명</span>
                <input className="input" value={draft.name} maxLength={100} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
              </label>
            </>
          )}
          <label className="field">
            <span className="field__label">{editing?.mode === 'move' ? '옮길 상위부서' : '상위부서'}</span>
            <select className="input" value={draft.parentId} onChange={(e) => setDraft({ ...draft, parentId: e.target.value })}>
              <option value={ROOT}>(최상위)</option>
              {parentOptions.map((f) => <option key={f.node.deptId} value={String(f.node.deptId)}>{f.path}</option>)}
            </select>
          </label>
          <div className="form__row">
            <label className="field">
              <span className="field__label">{editing?.mode === 'move' ? '적용일' : '신설일'}</span>
              <input className="input" type="date" value={draft.effectiveDate} onChange={(e) => setDraft({ ...draft, effectiveDate: e.target.value })} />
            </label>
            {editing?.mode === 'create' && (
              <label className="field">
                <span className="field__label">정렬 순서 (작을수록 위)</span>
                <input className="input" inputMode="numeric" value={draft.sortOrder} onChange={(e) => setDraft({ ...draft, sortOrder: e.target.value.replace(/[^0-9]/g, '') })} />
              </label>
            )}
          </div>
          <label className="field">
            <span className="field__label">사유 (선택)</span>
            <textarea className="input" rows={2} value={draft.reason} maxLength={500} onChange={(e) => setDraft({ ...draft, reason: e.target.value })} />
          </label>
          {formError && <p className="badge badge--red" role="alert">{formError}</p>}
          <div className="form__actions">
            <button type="button" className="btn btn--ghost" onClick={close}>취소</button>
            <button type="submit" className="btn btn--primary" disabled={saving}>{saving ? '저장 중…' : '저장'}</button>
          </div>
        </form>
      </Modal>
    </>
  );
}
