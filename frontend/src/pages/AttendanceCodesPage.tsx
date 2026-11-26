import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { LuPencil, LuPlus, LuRefreshCw, LuShieldAlert, LuTrash2 } from 'react-icons/lu';
import { Badge, Card, CardHead, Modal, PageHead, Toggle, cx } from '../components/ui';
import {
  attendanceCodeApi, ATTENDANCE_GROUPS, PROCESS_TYPES, USER_LEAVE_PROCESS_TYPES, processLabel, validateLeave,
  type AttendanceCode, type AttendanceCodeApi, type AttendanceGroup, type LeaveDetail, type LeaveDetailInput, type LegalValue,
} from '../api/attendanceCodes';
import { codeApi, MAX_SORT_ORDER, validateCode, type CodeApi } from '../api/codes';
import { invalidateCodes } from '../hooks/useCodes';
import { canManageAttendanceCodes } from '../nav';
import { useData, useStore } from '../state/store';
import { CodeUsageModal, codeDeleteBlock } from './CodesPage';

/**
 * 근태관리 > 근태코드 관리 (#143). 인사담당자·관리자만. 설계: docs/attendance-code-design.md
 * 휴가 종류·근태 구분은 공통코드에 저장하고 삭제·사용처 규칙도 공통코드와 같다(서버가 다시 검사한다).
 * 연차·법정휴가는 기본 코드라 삭제할 수 없고, 일수·유급 기간은 법정 기준값(legal_rate)을 보여 주기만 한다.
 */
export function AttendanceCodesPage() {
  const { me } = useData();
  const { toast } = useStore();
  if (!canManageAttendanceCodes(me.role)) {
    return (
      <>
        <PageHead title="근태코드 관리" />
        <Card i={1}>
          <CardHead title="인사담당자만 볼 수 있어요" sub="근태코드는 인사담당자·관리자 계정으로 관리해요" right={<LuShieldAlert className="muted" />} />
        </Card>
      </>
    );
  }
  return <AttendanceCodeManager api={attendanceCodeApi} usageApi={codeApi} toast={toast} />;
}

interface Draft {
  code: string; name: string; description: string; sortOrder: string; active: boolean; processType: string;
  unitDay: boolean; unitHalfDay: boolean; unitHour: boolean; paid: boolean | null; proofRequired: boolean; annualLimit: string;
}
type Editing = { mode: 'create' } | { mode: 'edit'; code: AttendanceCode };

const errText = (e: unknown, fallback: string) => (e instanceof Error ? e.message : fallback);
const nextSort = (xs: { sortOrder: number }[]) => Math.min(MAX_SORT_ORDER, xs.reduce((m, c) => Math.max(m, c.sortOrder), 0) + 10);

const emptyDraft = (group: AttendanceGroup, sortOrder: number): Draft => ({
  code: '', name: '', description: '', sortOrder: String(sortOrder), active: true,
  processType: group === 'LEAVE_TYPE' ? 'COMPANY' : PROCESS_TYPES.ATTENDANCE_TYPE[0].value,
  unitDay: true, unitHalfDay: false, unitHour: false, paid: true, proofRequired: false, annualLimit: '',
});
const draftOf = (c: AttendanceCode): Draft => ({
  code: c.code, name: c.name, description: c.description ?? '', sortOrder: String(c.sortOrder), active: c.active, processType: c.processType,
  unitDay: c.leave?.unitDay ?? true, unitHalfDay: c.leave?.unitHalfDay ?? false, unitHour: c.leave?.unitHour ?? false,
  paid: c.leave?.paid ?? null, proofRequired: c.leave?.proofRequired ?? false,
  annualLimit: c.leave?.annualLimitDays == null ? '' : String(c.leave.annualLimitDays),
});

/** 사용 단위. 연차 차감량: 종일 1일, 반차 0.5일, 시간 단위 1/소정근로시간(차감 계산은 휴가 신청에서 한다). */
const UNITS: { key: 'unitDay' | 'unitHalfDay' | 'unitHour'; label: string; hint: string }[] = [
  { key: 'unitDay', label: '종일', hint: '연차 1일 차감' },
  { key: 'unitHalfDay', label: '오전·오후 반차', hint: '연차 0.5일 차감' },
  { key: 'unitHour', label: '시간 단위', hint: '1시간 = 연차 1/소정근로시간일 차감' },
];

const paidText = (d: LeaveDetail) => (d.paid === null ? '법정 기준' : d.paid ? '유급' : '무급');
const legalText = (v: LegalValue) => (v.value === null
  ? `${v.label}: 기준값 미등록`
  : `${v.label}: ${v.value}${v.unit === 'DAYS' ? '일' : ` ${v.unit}`}${v.effectiveFrom ? ` (${v.effectiveFrom}부터)` : ''}`);

/** 화면 본문. 스토어 없이 api·toast 만 받아서 테스트할 수 있다. */
export function AttendanceCodeManager({ api, usageApi, toast }: {
  api: AttendanceCodeApi; usageApi: Pick<CodeApi, 'usages' | 'usageRows'>; toast: (text: string) => void;
}) {
  const [group, setGroup] = useState<AttendanceGroup>('LEAVE_TYPE');
  const [codes, setCodes] = useState<AttendanceCode[] | null>(null);
  const [reload, setReload] = useState(0);
  const [editing, setEditing] = useState<Editing | null>(null);
  const [draft, setDraft] = useState<Draft>(emptyDraft('LEAVE_TYPE', 10));
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState<AttendanceCode | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [usageTarget, setUsageTarget] = useState<AttendanceCode | null>(null);

  const fail = useCallback((e: unknown) => toast(errText(e, '알 수 없는 오류가 발생했어요')), [toast]);
  const closeUsage = useCallback(() => setUsageTarget(null), []);

  useEffect(() => {
    let alive = true;
    api.list(group).then((c) => { if (alive) setCodes(c); }, fail);
    return () => { alive = false; };
  }, [api, group, reload, fail]);

  const isLeave = group === 'LEAVE_TYPE';
  const editingSystem = editing?.mode === 'edit' && editing.code.system;
  const pickGroup = (g: AttendanceGroup) => { if (g !== group) { setCodes(null); setGroup(g); } };
  const saved = (text: string) => { invalidateCodes(group); setReload((n) => n + 1); toast(text); };

  const openCreate = () => { setDraft(emptyDraft(group, nextSort(codes ?? []))); setFormError(null); setEditing({ mode: 'create' }); };
  const openEdit = (c: AttendanceCode) => { setDraft(draftOf(c)); setFormError(null); setEditing({ mode: 'edit', code: c }); };
  const close = useCallback(() => setEditing(null), []);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!editing) return;
    const sortOrder = draft.sortOrder.trim() === '' ? NaN : Number(draft.sortOrder);
    const leave: LeaveDetailInput | null = isLeave ? {
      unitDay: draft.unitDay, unitHalfDay: draft.unitHalfDay, unitHour: draft.unitHour, paid: draft.paid, proofRequired: draft.proofRequired,
      annualLimitDays: draft.annualLimit.trim() === '' ? null : Number(draft.annualLimit),
    } : null;
    const input = { name: draft.name, description: draft.description.trim() || null, sortOrder, active: draft.active, processType: draft.processType, leave };
    const err = validateCode(editing.mode === 'create' ? { ...input, code: draft.code } : input)
      ?? (leave && validateLeave(draft.processType, leave, !!editingSystem));
    if (err) { setFormError(err); return; }
    setSaving(true);
    try {
      if (editing.mode === 'create') {
        await api.create(group, { ...input, code: draft.code });
        saved(`근태코드 ${draft.code}를 추가했어요`);
      } else {
        await api.update(group, editing.code.code, input);
        saved(`근태코드 ${editing.code.code}를 수정했어요`);
      }
      setEditing(null);
    } catch (e2) {
      setFormError(errText(e2, '저장하지 못했어요'));
    } finally {
      setSaving(false);
    }
  };

  const confirmDelete = async () => {
    if (!deleting) return;
    setSaving(true);
    try {
      await api.remove(group, deleting.code);
      saved(`근태코드 ${deleting.code}를 삭제했어요`);
      setDeleting(null);
    } catch (e) {
      setDeleteError(errText(e, '삭제하지 못했어요'));   // 서버의 막힌 이유(사용처 건수 포함)
    } finally {
      setSaving(false);
    }
  };

  const processOptions = PROCESS_TYPES[group].filter((p) =>
    !isLeave || editingSystem || USER_LEAVE_PROCESS_TYPES.includes(p.value));
  const legalValues = editing?.mode === 'edit' ? editing.code.leave?.legalValues ?? [] : [];

  return (
    <>
      <PageHead
        title="근태코드 관리"
        sub="휴가 종류와 근태 구분(출장·외근·재택·교육)을 관리해요. 연차·법정휴가는 법령 기준값을 따라요"
        right={<button className="glass pill" onClick={() => setReload((n) => n + 1)} aria-label="새로 고침"><LuRefreshCw /> 새로 고침</button>}
      />

      <Card i={1}>
        <CardHead
          title={ATTENDANCE_GROUPS.find((g) => g.group === group)!.label}
          sub={isLeave ? '연차·법정휴가는 기본 코드라 삭제할 수 없어요. 회사 약정휴가·무급휴가를 추가할 수 있어요' : '근무로 인정되는(근로시간에 넣는) 구분이에요'}
          right={<button className="btn btn--primary" onClick={openCreate} disabled={codes === null}><LuPlus /> 코드 추가</button>}
        />
        <div className="emp-tabs" role="tablist" aria-label="근태코드 그룹">
          {ATTENDANCE_GROUPS.map((g) => (
            <button key={g.group} role="tab" aria-selected={g.group === group} className={cx(g.group === group && 'on')} onClick={() => pickGroup(g.group)}>{g.label}</button>
          ))}
        </div>

        {codes === null ? (
          <p className="muted code-empty">불러오는 중…</p>
        ) : codes.length === 0 ? (
          <p className="muted code-empty">등록된 코드가 없어요</p>
        ) : (
          <table className="code-table">
            <thead>
              <tr><th>순서</th><th>코드값</th><th>코드명</th><th>처리방식</th>{isLeave && <th className="hide-sm">설정</th>}<th>사용 중</th><th /></tr>
            </thead>
            <tbody>
              {codes.map((c) => {
                const block = codeDeleteBlock(c);
                return (
                  <tr key={c.code} className={cx(!c.active && 'off')}>
                    <td className="num">{c.sortOrder}</td>
                    <td><code>{c.code}</code></td>
                    <td>{c.name} {c.system && <Badge tone="blue">기본</Badge>} {!c.active && <Badge tone="amber">사용 중지</Badge>}</td>
                    <td>{processLabel(group, c.processType)}</td>
                    {isLeave && (
                      <td className="hide-sm muted sm">
                        {c.leave && [
                          UNITS.filter((u) => c.leave![u.key]).map((u) => u.label).join('·'),
                          paidText(c.leave),
                          c.leave.proofRequired ? '증빙 필요' : null,
                          c.leave.annualLimitDays !== null ? `연간 ${c.leave.annualLimitDays}일` : null,
                          c.leave.genderLimit === 'F' ? '여성' : c.leave.genderLimit === 'M' ? '남성' : null,
                        ].filter(Boolean).join(' · ')}
                        {c.leave && c.leave.legalValues.length > 0 && <><br />{c.leave.legalValues.map(legalText).join(' · ')}</>}
                      </td>
                    )}
                    <td className="num">
                      {c.inUse
                        ? <button className="btn btn--ghost" onClick={() => setUsageTarget(c)} aria-label={`${c.name} 사용처`}>{c.usageCount}건</button>
                        : <span className="muted">—</span>}
                    </td>
                    <td className="code-table__actions">
                      <button className="btn btn--ghost icon-only" onClick={() => openEdit(c)} aria-label={`${c.name} 수정`}><LuPencil /></button>
                      <span title={block ?? undefined}>
                        <button className="btn btn--ghost icon-only" onClick={() => { setDeleteError(null); setDeleting(c); }} disabled={!!block} aria-label={`${c.name} 삭제`}><LuTrash2 /></button>
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </Card>

      <Modal open={editing !== null} onClose={close} title={editing?.mode === 'edit' ? '근태코드 수정' : `${isLeave ? '휴가 종류' : '근태 구분'} 추가`}>
        <form className="form" onSubmit={submit} noValidate>
          <label className="field">
            <span className="field__label">코드값 (영문 대문자·숫자·밑줄, 추가 후 바꿀 수 없어요)</span>
            <input className="input" value={draft.code} disabled={editing?.mode === 'edit'} maxLength={30} autoComplete="off"
              onChange={(e) => setDraft({ ...draft, code: e.target.value.toUpperCase() })} placeholder={isLeave ? '예: REFRESH' : '예: FIELD_WORK'} />
          </label>
          <label className="field">
            <span className="field__label">코드명</span>
            <input className="input" value={draft.name} maxLength={100} onChange={(e) => setDraft({ ...draft, name: e.target.value })} placeholder="화면에 보일 이름" />
          </label>
          <label className="field">
            <span className="field__label">처리방식{editingSystem ? ' (기본 코드는 바꿀 수 없어요)' : ''}</span>
            <select className="input" aria-label="처리방식" value={draft.processType} disabled={!!editingSystem}
              onChange={(e) => setDraft({ ...draft, processType: e.target.value, paid: e.target.value === 'UNPAID' ? false : draft.paid })}>
              {processOptions.map((p) => <option key={p.value} value={p.value}>{p.label}</option>)}
            </select>
          </label>
          <label className="field">
            <span className="field__label">설명 (선택)</span>
            <textarea className="input" rows={2} value={draft.description} maxLength={500} onChange={(e) => setDraft({ ...draft, description: e.target.value })} />
          </label>

          {isLeave && (
            <>
              <div className="field">
                <span className="field__label">사용 단위 (하나 이상)</span>
                <ul className="settings-list">
                  {UNITS.map((u) => (
                    <li key={u.key}>
                      <div className="grow"><strong>{u.label}</strong> <span className="muted sm">{u.hint}</span></div>
                      <Toggle checked={draft[u.key]} onChange={(v) => setDraft({ ...draft, [u.key]: v })} label={`${u.label} 사용`} />
                    </li>
                  ))}
                  <li>
                    <div className="grow"><strong>증빙 필요</strong></div>
                    <Toggle checked={draft.proofRequired} onChange={(v) => setDraft({ ...draft, proofRequired: v })} label="증빙 필요" />
                  </li>
                </ul>
              </div>
              {editingSystem ? (
                <div className="field">
                  <span className="field__label">유급·일수 (법령 기준값, 이 화면에서 바꿀 수 없어요)</span>
                  <p className="muted sm">
                    {editing.code.leave && paidText(editing.code.leave)}
                    {editing.code.leave?.legalBasis && ` · 근거: ${editing.code.leave.legalBasis}`}
                  </p>
                  <ul className="muted sm" aria-label="법정 기준값">
                    {legalValues.map((v) => <li key={v.itemCode}>{legalText(v)}</li>)}
                  </ul>
                </div>
              ) : (
                <div className="form__row">
                  <div className="field">
                    <span className="field__label">유급 여부</span>
                    {draft.processType === 'UNPAID'
                      ? <span className="muted sm">무급휴가는 무급이에요</span>
                      : <Toggle checked={draft.paid === true} onChange={(v) => setDraft({ ...draft, paid: v })} label="유급" />}
                  </div>
                  <label className="field">
                    <span className="field__label">연간 한도 일수 (비우면 한도 없음)</span>
                    <input className="input" inputMode="decimal" aria-label="연간 한도 일수" value={draft.annualLimit}
                      onChange={(e) => setDraft({ ...draft, annualLimit: e.target.value.replace(/[^0-9.]/g, '') })} />
                  </label>
                </div>
              )}
            </>
          )}

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

      <Modal open={deleting !== null} onClose={() => setDeleting(null)} title="근태코드 삭제">
        <div className="form">
          <p>{deleting && `근태코드 ${deleting.name}(${deleting.code})를 삭제할까요? 되돌릴 수 없어요.`}</p>
          {deleteError && <p className="badge badge--red" role="alert">{deleteError}</p>}
          <div className="form__actions">
            <button type="button" className="btn btn--ghost" onClick={() => setDeleting(null)}>취소</button>
            <button type="button" className="btn btn--primary" onClick={() => void confirmDelete()} disabled={saving}>{saving ? '삭제 중…' : '삭제'}</button>
          </div>
        </div>
      </Modal>

      <CodeUsageModal api={usageApi} target={usageTarget} onClose={closeUsage} onError={fail} />
    </>
  );
}
