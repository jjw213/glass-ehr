import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { LuExternalLink,LuPencil, LuPlus, LuRefreshCw, LuShieldAlert, LuTrash2 } from 'react-icons/lu';
import { Badge, Card, CardHead, Modal, PageHead, Toggle, cx } from '../components/ui';
import {
  codeApi, MAX_SORT_ORDER, validateCode, validateGroup,
  type CodeApi, type CodeGroup, type CodeListItem, type UsagePage, type UsageSummary,
} from '../api/codes';
import { invalidateCodes } from '../hooks/useCodes';
import { canManageCodes, NAV } from '../nav';
import { useData, useStore } from '../state/store';

/**
 * 공통코드 관리 (#11, #140). 인사담당자·관리자만.
 * 사용자 그룹은 추가·수정·삭제, 시스템 그룹은 이름·설명·순서만 수정한다.
 * 코드는 참조가 없고 기본 코드가 아닐 때만 삭제한다(서버가 다시 검사한다). 다른 화면에서 편집하는 그룹은 보기·사용처만.
 */
export function CodesPage() {
  const { me } = useData();
  const { toast } = useStore();
  if (!canManageCodes(me.role)) {
    return (
      <>
        <PageHead title="공통코드 관리" />
        <Card i={1}>
          <CardHead title="인사담당자만 볼 수 있어요" sub="공통코드는 인사담당자·관리자 계정으로 관리해요" right={<LuShieldAlert className="muted" />} />
        </Card>
      </>
    );
  }
  return <CodeManager api={codeApi} toast={toast} />;
}

interface Draft { key: string; name: string; description: string; sortOrder: string; active: boolean }
type Editing =
  | { kind: 'code'; mode: 'create' } | { kind: 'code'; mode: 'edit'; code: CodeListItem }
  | { kind: 'group'; mode: 'create' } | { kind: 'group'; mode: 'edit'; group: CodeGroup };
type Deleting = { kind: 'code'; code: CodeListItem } | { kind: 'group'; group: CodeGroup };

const emptyDraft = (next: number): Draft => ({ key: '', name: '', description: '', sortOrder: String(next), active: true });
const nextSort = (xs: { sortOrder: number }[]) => Math.min(MAX_SORT_ORDER, xs.reduce((m, c) => Math.max(m, c.sortOrder), 0) + 10);
const errText = (e: unknown, fallback: string) => (e instanceof Error ? e.message : fallback);

/** 삭제 버튼을 막는 이유. null 이면 삭제할 수 있다. */
export function codeDeleteBlock(c: CodeListItem): string | null {
  if (c.system) return '기본 코드라 삭제할 수 없어요. 사용 안 함으로 바꿔 주세요';
  if (c.inUse) return `사용 중(${c.usageCount}건)이라 삭제할 수 없어요. 사용 안 함으로 바꿔 주세요`;
  return null;
}
export function groupDeleteBlock(g: CodeGroup, codes: CodeListItem[] | null): string | null {
  if (g.system) return '시스템 그룹이라 삭제할 수 없어요';
  const used = (codes ?? []).filter((c) => c.inUse || c.system);
  if (used.length) return `사용 중인 코드가 있어 삭제할 수 없어요 (${used.map((c) => `${c.code} ${c.usageCount}건`).join(', ')})`;
  return null;
}

const pageLabel = (page: string) => NAV.find((n) => n.id === page)?.label ?? page;

/** 화면 본문. 스토어 없이 api·toast 만 받아서 테스트할 수 있다. */
export function CodeManager({ api, toast }: { api: CodeApi; toast: (text: string) => void }) {
  const [groups, setGroups] = useState<CodeGroup[] | null>(null);
  const [group, setGroup] = useState<string | null>(null);
  const [codes, setCodes] = useState<CodeListItem[] | null>(null);
  const [showInactive, setShowInactive] = useState(false);
  const [editing, setEditing] = useState<Editing | null>(null);
  const [draft, setDraft] = useState<Draft>(emptyDraft(10));
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState<Deleting | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [usageTarget, setUsageTarget] = useState<CodeListItem | null>(null);
  const [reload, setReload] = useState(0);
  const [groupReload, setGroupReload] = useState(0);

  const fail = useCallback((e: unknown) => toast(errText(e, '알 수 없는 오류가 발생했어요')), [toast]);

  useEffect(() => {
    let alive = true;
    api.groups().then((g) => {
      if (!alive) return;
      setGroups(g);
      setGroup((cur) => (cur && g.some((x) => x.groupCode === cur) ? cur : g[0]?.groupCode ?? null));
    }, fail);
    return () => { alive = false; };
  }, [api, fail, groupReload]);

  useEffect(() => {
    if (!group) return;
    let alive = true;
    api.codes(group, true).then((c) => { if (alive) setCodes(c); }, fail);
    return () => { alive = false; };
  }, [api, group, reload, fail]);

  const current = groups?.find((g) => g.groupCode === group);
  const readOnly = !!current?.editPage;   // 다른 화면에서 편집하는 그룹
  const visible = (codes ?? []).filter((c) => showInactive || c.active);
  const inactiveCount = (codes ?? []).filter((c) => !c.active).length;

  const pickGroup = (g: string) => { if (g !== group) { setCodes(null); setGroup(g); } };

  const openCreate = () => { setDraft(emptyDraft(nextSort(codes ?? []))); setFormError(null); setEditing({ kind: 'code', mode: 'create' }); };
  const openEdit = (c: CodeListItem) => {
    setDraft({ key: c.code, name: c.name, description: c.description ?? '', sortOrder: String(c.sortOrder), active: c.active });
    setFormError(null);
    setEditing({ kind: 'code', mode: 'edit', code: c });
  };
  const openGroupCreate = () => { setDraft(emptyDraft(nextSort(groups ?? []))); setFormError(null); setEditing({ kind: 'group', mode: 'create' }); };
  const openGroupEdit = (g: CodeGroup) => {
    setDraft({ key: g.groupCode, name: g.name, description: g.description ?? '', sortOrder: String(g.sortOrder), active: g.active });
    setFormError(null);
    setEditing({ kind: 'group', mode: 'edit', group: g });
  };
  const close = useCallback(() => setEditing(null), []);
  const closeUsage = useCallback(() => setUsageTarget(null), []);

  const saved = (g: string, text: string) => {
    invalidateCodes(g);   // useCodes 를 쓰는 화면이 새 목록을 받도록
    setReload((n) => n + 1);
    toast(text);
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!editing) return;
    const sortOrder = draft.sortOrder.trim() === '' ? NaN : Number(draft.sortOrder);
    const input = { name: draft.name, description: draft.description.trim() || null, sortOrder, active: draft.active };
    const err = editing.kind === 'group'
      ? validateGroup(editing.mode === 'create' ? { ...input, groupCode: draft.key } : input)
      : validateCode(editing.mode === 'create' ? { ...input, code: draft.key } : input);
    if (err) { setFormError(err); return; }
    setSaving(true);
    try {
      if (editing.kind === 'group') {
        if (editing.mode === 'create') {
          await api.createGroup({ ...input, groupCode: draft.key });
          setCodes(null);
          setGroup(draft.key);
          toast(`코드그룹 ${draft.key}를 추가했어요`);
        } else {
          await api.updateGroup(editing.group.groupCode, input);
          invalidateCodes(editing.group.groupCode);
          toast(`코드그룹 ${editing.group.groupCode}를 수정했어요`);
        }
        setGroupReload((n) => n + 1);
      } else if (group) {
        if (editing.mode === 'create') {
          await api.create(group, { ...input, code: draft.key });
          saved(group, `코드 ${draft.key}를 추가했어요`);
        } else {
          await api.update(group, editing.code.code, input);
          saved(group, `코드 ${editing.code.code}를 수정했어요`);
        }
      }
      setEditing(null);
    } catch (e2) {
      setFormError(errText(e2, '저장하지 못했어요'));
    } finally {
      setSaving(false);
    }
  };

  const toggleActive = async (c: CodeListItem, active: boolean) => {
    try {
      await api.update(c.groupCode, c.code, { name: c.name, description: c.description, sortOrder: c.sortOrder, active });
      saved(c.groupCode, active ? `${c.name} 코드를 다시 사용해요` : `${c.name} 코드를 사용 중지했어요`);
    } catch (e) { fail(e); }
  };

  const askDelete = (d: Deleting) => { setDeleteError(null); setDeleting(d); };
  const confirmDelete = async () => {
    if (!deleting) return;
    setSaving(true);
    try {
      if (deleting.kind === 'code') {
        await api.deleteCode(deleting.code.groupCode, deleting.code.code);
        saved(deleting.code.groupCode, `코드 ${deleting.code.code}를 삭제했어요`);
      } else {
        await api.deleteGroup(deleting.group.groupCode);
        invalidateCodes(deleting.group.groupCode);
        setCodes(null);
        setGroup(null);
        setGroupReload((n) => n + 1);
        toast(`코드그룹 ${deleting.group.groupCode}를 삭제했어요`);
      }
      setDeleting(null);
    } catch (e) {
      setDeleteError(errText(e, '삭제하지 못했어요'));   // 서버의 막힌 이유(사용처 건수 포함)
    } finally {
      setSaving(false);
    }
  };

  const groupBlock = current ? groupDeleteBlock(current, codes) : null;

  return (
    <>
      <PageHead
        title="공통코드 관리"
        sub="직급·직책·직군·고용형태·근무지·퇴직사유 등 코드를 한 곳에서 관리해요"
        right={<button className="glass pill" onClick={() => { setGroupReload((n) => n + 1); setReload((n) => n + 1); }} aria-label="새로 고침"><LuRefreshCw /> 새로 고침</button>}
      />

      <div className="grid grid--codes">
        <Card i={1}>
          <CardHead
            title="코드그룹"
            sub={groups ? `${groups.length}개` : '불러오는 중…'}
            right={<button className="btn btn--ghost" onClick={openGroupCreate} disabled={groups === null}><LuPlus /> 그룹 추가</button>}
          />
          <ul className="code-groups" role="tablist" aria-label="코드그룹">
            {(groups ?? []).map((g) => (
              <li key={g.groupCode}>
                <button role="tab" aria-selected={g.groupCode === group} className={cx(g.groupCode === group && 'active')} onClick={() => pickGroup(g.groupCode)}>
                  <span>{g.name}{g.system && <> <Badge tone="blue">시스템</Badge></>}</span>
                  <span className="muted sm">{g.groupCode}</span>
                </button>
              </li>
            ))}
          </ul>
        </Card>

        <Card i={2}>
          <CardHead
            title={current ? current.name : '코드'}
            sub={current?.description ?? undefined}
            right={current && (
              <div className="code-head-actions">
                {!readOnly && <button className="btn btn--ghost icon-only" onClick={() => openGroupEdit(current)} aria-label={`${current.name} 그룹 수정`}><LuPencil /></button>}
                <span title={groupBlock ?? undefined}>
                  <button className="btn btn--ghost icon-only" onClick={() => askDelete({ kind: 'group', group: current })} disabled={!!groupBlock || codes === null} aria-label={`${current.name} 그룹 삭제`}><LuTrash2 /></button>
                </span>
                {readOnly ? (
                  <a className="btn btn--primary" href={`#${current.editPage}`}><LuExternalLink /> {pageLabel(current.editPage!)}에서 편집</a>
                ) : (
                  <button className="btn btn--primary" onClick={openCreate} disabled={codes === null}><LuPlus /> 코드 추가</button>
                )}
              </div>
            )}
          />
          <ul className="settings-list">
            <li>
              <div className="grow">
                <strong>사용 중지 코드 보기</strong>
                <span className="muted sm">사용 중지한 코드는 새 입력 목록에서 빠지지만, 과거 이력에는 그대로 남아요 ({inactiveCount}개)</span>
              </div>
              <Toggle checked={showInactive} onChange={setShowInactive} label="사용 중지 코드 보기" />
            </li>
          </ul>

          {codes === null ? (
            <p className="muted code-empty">불러오는 중…</p>
          ) : visible.length === 0 ? (
            <p className="muted code-empty">등록된 코드가 없어요</p>
          ) : (
            <table className="code-table">
              <thead>
                <tr><th>순서</th><th>코드값</th><th>코드명</th><th className="hide-sm">설명</th><th>사용 중</th><th>사용</th><th /></tr>
              </thead>
              <tbody>
                {visible.map((c) => {
                  const block = codeDeleteBlock(c);
                  return (
                    <tr key={c.code} className={cx(!c.active && 'off')}>
                      <td className="num">{c.sortOrder}</td>
                      <td><code>{c.code}</code></td>
                      <td>{c.name} {c.system && <Badge tone="blue">기본</Badge>} {!c.active && <Badge tone="amber">사용 중지</Badge>}</td>
                      <td className="hide-sm muted sm">{c.description}</td>
                      <td className="num">
                        {c.inUse
                          ? <button className="btn btn--ghost" onClick={() => setUsageTarget(c)} aria-label={`${c.name} 사용처`}>{c.usageCount}건</button>
                          : <span className="muted">—</span>}
                      </td>
                      <td>
                        {readOnly
                          ? <span className="muted sm">{c.active ? '사용' : '사용 안 함'}</span>
                          : <Toggle checked={c.active} onChange={(v) => void toggleActive(c, v)} label={`${c.name} 사용`} />}
                      </td>
                      <td className="code-table__actions">
                        {!readOnly && <>
                          <button className="btn btn--ghost icon-only" onClick={() => openEdit(c)} aria-label={`${c.name} 수정`}><LuPencil /></button>
                          <span title={block ?? undefined}>
                            <button className="btn btn--ghost icon-only" onClick={() => askDelete({ kind: 'code', code: c })} disabled={!!block} aria-label={`${c.name} 삭제`}><LuTrash2 /></button>
                          </span>
                        </>}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </Card>
      </div>

      <Modal
        open={editing !== null}
        onClose={close}
        title={editing?.kind === 'group'
          ? (editing.mode === 'edit' ? '코드그룹 수정' : '코드그룹 추가')
          : (editing?.mode === 'edit' ? '코드 수정' : `${current?.name ?? ''} 코드 추가`)}
      >
        <form className="form" onSubmit={submit} noValidate>
          <label className="field">
            <span className="field__label">{editing?.kind === 'group' ? '그룹코드' : '코드값'} (영문 대문자·숫자·밑줄, 추가 후 바꿀 수 없어요)</span>
            <input
              className="input" value={draft.key} disabled={editing?.mode === 'edit'} maxLength={30} autoComplete="off"
              onChange={(e) => setDraft({ ...draft, key: e.target.value.toUpperCase() })} placeholder={editing?.kind === 'group' ? '예: SKILL_LEVEL' : '예: INTERN'}
            />
          </label>
          <label className="field">
            <span className="field__label">{editing?.kind === 'group' ? '그룹명' : '코드명'}</span>
            <input className="input" value={draft.name} maxLength={100} onChange={(e) => setDraft({ ...draft, name: e.target.value })} placeholder="화면에 보일 이름" />
          </label>
          <label className="field">
            <span className="field__label">설명 (선택)</span>
            <textarea className="input" rows={2} value={draft.description} maxLength={500} onChange={(e) => setDraft({ ...draft, description: e.target.value })} />
          </label>
          <div className="form__row">
            <label className="field">
              <span className="field__label">정렬 순서 (작을수록 위)</span>
              <input className="input" inputMode="numeric" value={draft.sortOrder} onChange={(e) => setDraft({ ...draft, sortOrder: e.target.value.replace(/[^0-9]/g, '') })} />
            </label>
            <div className="field">
              <span className="field__label">사용 여부</span>
              {editing?.kind === 'group' && editing.mode === 'edit' && editing.group.system
                ? <span className="muted sm">시스템 그룹은 사용 여부를 바꿀 수 없어요</span>
                : <Toggle checked={draft.active} onChange={(v) => setDraft({ ...draft, active: v })} label="사용 여부" />}
            </div>
          </div>
          {formError && <p className="badge badge--red" role="alert">{formError}</p>}
          <div className="form__actions">
            <button type="button" className="btn btn--ghost" onClick={close}>취소</button>
            <button type="submit" className="btn btn--primary" disabled={saving}>{saving ? '저장 중…' : '저장'}</button>
          </div>
        </form>
      </Modal>

      <Modal open={deleting !== null} onClose={() => setDeleting(null)} title={deleting?.kind === 'group' ? '코드그룹 삭제' : '코드 삭제'}>
        <div className="form">
          <p>
            {deleting?.kind === 'group'
              ? `코드그룹 ${deleting.group.name}(${deleting.group.groupCode})과 그 안의 코드를 모두 삭제할까요? 되돌릴 수 없어요.`
              : deleting && `코드 ${deleting.code.name}(${deleting.code.code})를 삭제할까요? 되돌릴 수 없어요.`}
          </p>
          {deleteError && <p className="badge badge--red" role="alert">{deleteError}</p>}
          <div className="form__actions">
            <button type="button" className="btn btn--ghost" onClick={() => setDeleting(null)}>취소</button>
            <button type="button" className="btn btn--primary" onClick={() => void confirmDelete()} disabled={saving}>{saving ? '삭제 중…' : '삭제'}</button>
          </div>
        </div>
      </Modal>

      <CodeUsageModal api={api} target={usageTarget} onClose={closeUsage} onError={fail} />
    </>
  );
}

type UsageTarget = Pick<CodeListItem, 'groupCode' | 'code' | 'name'>;

/** 코드 사용처(테이블별 건수 → 행 목록). 근태코드 관리 화면도 쓴다(#143). */
export function CodeUsageModal({ api, target, onClose, onError }: {
  api: Pick<CodeApi, 'usages' | 'usageRows'>; target: UsageTarget | null; onClose: () => void; onError: (e: unknown) => void;
}) {
  const [usage, setUsage] = useState<{ code: UsageTarget; summary: UsageSummary | null; page: UsagePage | null } | null>(null);

  useEffect(() => {
    if (!target) { setUsage(null); return; }
    let alive = true;
    setUsage({ code: target, summary: null, page: null });
    api.usages(target.groupCode, target.code).then(
      (summary) => { if (alive) setUsage({ code: target, summary, page: null }); },
      (e) => { if (alive) { onClose(); onError(e); } },
    );
    return () => { alive = false; };
  }, [api, target, onClose, onError]);

  const openUsageRows = async (table: string, page: number) => {
    if (!usage) return;
    try {
      const rows = await api.usageRows(usage.code.groupCode, usage.code.code, table, page);
      setUsage({ ...usage, page: rows });
    } catch (e) { onError(e); }
  };

  return (
      <Modal open={usage !== null} onClose={onClose} title={usage ? `${usage.code.name}(${usage.code.code}) 사용처` : '사용처'}>
        {usage && (usage.summary === null ? <p className="muted">불러오는 중…</p> : (
          <div className="form code-usage">
            <ul className="settings-list">
              {usage.summary.tables.map((t) => (
                <li key={t.table}>
                  <div className="grow"><strong>{t.label}</strong></div>
                  {usage.summary!.rowsVisible
                    ? <button className="btn btn--ghost" onClick={() => void openUsageRows(t.table, 0)} aria-label={`${t.label} 목록`}>{t.count}건</button>
                    : <span>{t.count}건</span>}
                </li>
              ))}
            </ul>
            {!usage.summary.rowsVisible && <p className="muted sm">사원 목록은 전체 사원 조회 권한이 있어야 볼 수 있어요. 건수만 보여 드려요.</p>}
            {usage.page && (
              <>
                <table className="code-table">
                  <caption className="muted sm">{usage.page.label} {usage.page.total}건</caption>
                  <thead><tr><th>사번</th><th>이름</th><th>{usage.page.dateLabel}</th></tr></thead>
                  <tbody>
                    {usage.page.rows.map((r, i) => (
                      <tr key={`${r.empNo}-${i}`}><td><code>{r.empNo}</code></td><td>{r.name}</td><td className="num">{r.date ?? '—'}</td></tr>
                    ))}
                  </tbody>
                </table>
                <div className="form__actions">
                  <button type="button" className="btn btn--ghost" disabled={usage.page.page === 0} onClick={() => void openUsageRows(usage.page!.table, usage.page!.page - 1)}>이전</button>
                  <span className="muted sm">{usage.page.page + 1} / {Math.max(1, Math.ceil(usage.page.total / usage.page.size))}</span>
                  <button type="button" className="btn btn--ghost" disabled={(usage.page.page + 1) * usage.page.size >= usage.page.total} onClick={() => void openUsageRows(usage.page!.table, usage.page!.page + 1)}>다음</button>
                </div>
              </>
            )}
          </div>
        ))}
      </Modal>
  );
}
