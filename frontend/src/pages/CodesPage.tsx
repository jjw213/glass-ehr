import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { LuListTree, LuPencil, LuPlus, LuRefreshCw, LuShieldAlert } from 'react-icons/lu';
import { Badge, Card, CardHead, Modal, PageHead, Toggle, cx } from '../components/ui';
import { codeApi, MAX_SORT_ORDER, validateCode, type Code, type CodeApi, type CodeGroup } from '../api/codes';
import { invalidateCodes } from '../hooks/useCodes';
import { canManageCodes } from '../nav';
import { useData, useStore } from '../state/store';

/** 공통코드 관리 (#11). 인사담당자·관리자만. 코드그룹은 마이그레이션으로만 추가하고, 여기서는 코드만 추가·수정한다. */
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

interface Draft { code: string; name: string; description: string; sortOrder: string; active: boolean }
type Editing = { mode: 'create' } | { mode: 'edit'; code: Code };

const emptyDraft = (next: number): Draft => ({ code: '', name: '', description: '', sortOrder: String(next), active: true });
const nextSort = (codes: Code[]) => Math.min(MAX_SORT_ORDER, codes.reduce((m, c) => Math.max(m, c.sortOrder), 0) + 10);

/** 화면 본문. 스토어 없이 api·toast 만 받아서 테스트할 수 있다. */
export function CodeManager({ api, toast }: { api: CodeApi; toast: (text: string) => void }) {
  const [groups, setGroups] = useState<CodeGroup[] | null>(null);
  const [group, setGroup] = useState<string | null>(null);
  const [codes, setCodes] = useState<Code[] | null>(null);
  const [showInactive, setShowInactive] = useState(false);
  const [editing, setEditing] = useState<Editing | null>(null);
  const [draft, setDraft] = useState<Draft>(emptyDraft(10));
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [reload, setReload] = useState(0);

  const fail = useCallback((e: unknown) => toast(e instanceof Error ? e.message : '알 수 없는 오류가 발생했어요'), [toast]);

  useEffect(() => {
    let alive = true;
    api.groups().then((g) => {
      if (!alive) return;
      setGroups(g);
      setGroup((cur) => cur ?? g[0]?.groupCode ?? null);
    }, fail);
    return () => { alive = false; };
  }, [api, fail]);

  useEffect(() => {
    if (!group) return;
    let alive = true;
    api.codes(group, true).then((c) => { if (alive) setCodes(c); }, fail);
    return () => { alive = false; };
  }, [api, group, reload, fail]);

  const current = groups?.find((g) => g.groupCode === group);
  const visible = (codes ?? []).filter((c) => showInactive || c.active);
  const inactiveCount = (codes ?? []).filter((c) => !c.active).length;

  const pickGroup = (g: string) => { if (g !== group) { setCodes(null); setGroup(g); } };

  const openCreate = () => { setDraft(emptyDraft(nextSort(codes ?? []))); setFormError(null); setEditing({ mode: 'create' }); };
  const openEdit = (c: Code) => {
    setDraft({ code: c.code, name: c.name, description: c.description ?? '', sortOrder: String(c.sortOrder), active: c.active });
    setFormError(null);
    setEditing({ mode: 'edit', code: c });
  };
  const close = useCallback(() => setEditing(null), []);

  const saved = (g: string, text: string) => {
    invalidateCodes(g);   // useCodes 를 쓰는 화면이 새 목록을 받도록
    setReload((n) => n + 1);
    toast(text);
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!group || !editing) return;
    const sortOrder = draft.sortOrder.trim() === '' ? NaN : Number(draft.sortOrder);
    const input = { name: draft.name, description: draft.description.trim() || null, sortOrder, active: draft.active };
    const err = validateCode(editing.mode === 'create' ? { ...input, code: draft.code } : input);
    if (err) { setFormError(err); return; }
    setSaving(true);
    try {
      if (editing.mode === 'create') {
        await api.create(group, { ...input, code: draft.code });
        saved(group, `코드 ${draft.code}를 추가했어요`);
      } else {
        await api.update(group, editing.code.code, input);
        saved(group, `코드 ${editing.code.code}를 수정했어요`);
      }
      setEditing(null);
    } catch (e2) {
      setFormError(e2 instanceof Error ? e2.message : '저장하지 못했어요');
    } finally {
      setSaving(false);
    }
  };

  const toggleActive = async (c: Code, active: boolean) => {
    try {
      await api.update(c.groupCode, c.code, { name: c.name, description: c.description, sortOrder: c.sortOrder, active });
      saved(c.groupCode, active ? `${c.name} 코드를 다시 사용해요` : `${c.name} 코드를 사용 중지했어요`);
    } catch (e) { fail(e); }
  };

  return (
    <>
      <PageHead
        title="공통코드 관리"
        sub="직급·직책·직군·고용형태·근무지·퇴직사유 코드를 한 곳에서 관리해요"
        right={<button className="glass pill" onClick={() => setReload((n) => n + 1)} aria-label="새로 고침"><LuRefreshCw /> 새로 고침</button>}
      />

      <div className="grid grid--codes">
        <Card i={1}>
          <CardHead title="코드그룹" sub={groups ? `${groups.length}개` : '불러오는 중…'} right={<LuListTree className="muted" />} />
          <ul className="code-groups" role="tablist" aria-label="코드그룹">
            {(groups ?? []).map((g) => (
              <li key={g.groupCode}>
                <button role="tab" aria-selected={g.groupCode === group} className={cx(g.groupCode === group && 'active')} onClick={() => pickGroup(g.groupCode)}>
                  <span>{g.name}</span>
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
            right={
              <button className="btn btn--primary" onClick={openCreate} disabled={!group || codes === null}>
                <LuPlus /> 코드 추가
              </button>
            }
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
                <tr><th>순서</th><th>코드값</th><th>코드명</th><th className="hide-sm">설명</th><th>사용</th><th /></tr>
              </thead>
              <tbody>
                {visible.map((c) => (
                  <tr key={c.code} className={cx(!c.active && 'off')}>
                    <td className="num">{c.sortOrder}</td>
                    <td><code>{c.code}</code></td>
                    <td>{c.name} {!c.active && <Badge tone="amber">사용 중지</Badge>}</td>
                    <td className="hide-sm muted sm">{c.description}</td>
                    <td><Toggle checked={c.active} onChange={(v) => void toggleActive(c, v)} label={`${c.name} 사용`} /></td>
                    <td className="code-table__actions">
                      <button className="btn btn--ghost icon-only" onClick={() => openEdit(c)} aria-label={`${c.name} 수정`}><LuPencil /></button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Card>
      </div>

      <Modal open={editing !== null} onClose={close} title={editing?.mode === 'edit' ? '코드 수정' : `${current?.name ?? ''} 코드 추가`}>
        <form className="form" onSubmit={submit} noValidate>
          <label className="field">
            <span className="field__label">코드값 (영문 대문자·숫자·밑줄, 추가 후 바꿀 수 없어요)</span>
            <input
              className="input" value={draft.code} disabled={editing?.mode === 'edit'} maxLength={30} autoComplete="off"
              onChange={(e) => setDraft({ ...draft, code: e.target.value.toUpperCase() })} placeholder="예: INTERN"
            />
          </label>
          <label className="field">
            <span className="field__label">코드명</span>
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
              <Toggle checked={draft.active} onChange={(v) => setDraft({ ...draft, active: v })} label="사용 여부" />
            </div>
          </div>
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
