import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { LuPencil, LuPlus, LuRefreshCw, LuShieldAlert } from 'react-icons/lu';
import { Badge, Card, CardHead, Modal, PageHead, Toggle, cx } from '../components/ui';
import {
  DAY_TYPE_LABEL, WEEKDAYS, contractMinutes, duration, parseHm, toHm, validateWorkPattern, workPatternApi,
  type DayType, type Violation, type WorkPattern, type WorkPatternApi, type WorkPatternInput,
} from '../api/workPatterns';
import { canManageWorkPatterns } from '../nav';
import { useData, useStore } from '../state/store';

/** 근무유형 관리 (#25). 인사담당자·관리자만. 저장 전에 서버가 법정 기준(legal_rate)으로 검사한다. */
export function WorkPatternsPage() {
  const { me } = useData();
  const { toast } = useStore();
  if (!canManageWorkPatterns(me.role)) {
    return (
      <>
        <PageHead title="근무유형 관리" />
        <Card i={1}>
          <CardHead title="인사담당자만 볼 수 있어요" sub="근무유형은 인사담당자·관리자 계정으로 관리해요" right={<LuShieldAlert className="muted" />} />
        </Card>
      </>
    );
  }
  return <WorkPatternManager api={workPatternApi} toast={toast} />;
}

interface Draft {
  patternCode: string; patternName: string; start: string; end: string;
  breakStart: string; breakMinutes: string; dayTypes: DayType[]; active: boolean; note: string;
}
type Editing = { mode: 'create' } | { mode: 'edit'; row: WorkPattern };

const emptyDraft = (): Draft => ({
  patternCode: '', patternName: '', start: '09:00', end: '18:00', breakStart: '12:00', breakMinutes: '60',
  dayTypes: ['W', 'W', 'W', 'W', 'W', 'O', 'H'], active: true, note: '',
});
const NEXT: Record<DayType, DayType> = { W: 'O', O: 'H', H: 'W' };

/** 입력값 → API 입력. 시각 형식이 틀리면 문자열 오류. */
function toInput(d: Draft): WorkPatternInput | string {
  const start = parseHm(d.start);
  const end = parseHm(d.end);
  if (start === null || end === null) return '시업·종업 시각을 HH:MM 으로 입력해 주세요';
  const breakMinutes = d.breakMinutes.trim() === '' ? 0 : Number(d.breakMinutes);
  const breakStart = d.breakStart.trim() === '' || breakMinutes === 0 ? null : parseHm(d.breakStart);
  if (breakMinutes > 0 && breakStart === null) return '휴게 시작 시각을 HH:MM 으로 입력해 주세요';
  return {
    patternCode: d.patternCode, patternName: d.patternName, workStartMin: start, workEndMin: end,
    breakStartMin: breakStart, breakMinutes, dayTypes: d.dayTypes.join(''), active: d.active, note: d.note.trim() || null,
  };
}

const dayTypesText = (s: string) =>
  [...s].map((c, i) => (c === 'W' ? null : `${WEEKDAYS[i]} ${DAY_TYPE_LABEL[c as DayType]}`)).filter(Boolean).join(' · ');

/** 화면 본문. 스토어 없이 api·toast 만 받아서 테스트할 수 있다. */
export function WorkPatternManager({ api, toast }: { api: WorkPatternApi; toast: (text: string) => void }) {
  const [rows, setRows] = useState<WorkPattern[] | null>(null);
  const [editing, setEditing] = useState<Editing | null>(null);
  const [draft, setDraft] = useState<Draft>(emptyDraft());
  const [formError, setFormError] = useState<string | null>(null);
  const [violations, setViolations] = useState<Violation[]>([]);
  const [saving, setSaving] = useState(false);
  const [reload, setReload] = useState(0);

  const fail = useCallback((e: unknown) => toast(e instanceof Error ? e.message : '알 수 없는 오류가 발생했어요'), [toast]);

  useEffect(() => {
    let alive = true;
    api.list().then((r) => { if (alive) setRows(r); }, fail);
    return () => { alive = false; };
  }, [api, reload, fail]);

  const input = useMemo(() => toInput(draft), [draft]);
  const preview = typeof input === 'string' ? null : contractMinutes(input);
  const locked = editing?.mode === 'edit' && editing.row.assigned;

  const open = (e: Editing, d: Draft) => { setDraft(d); setFormError(null); setViolations([]); setEditing(e); };
  const openCreate = () => open({ mode: 'create' }, emptyDraft());
  const openEdit = (r: WorkPattern) => open({ mode: 'edit', row: r }, {
    patternCode: r.patternCode, patternName: r.patternName, start: toHm(r.workStartMin), end: toHm(r.workEndMin),
    breakStart: r.breakStartMin === null ? '' : toHm(r.breakStartMin), breakMinutes: String(r.breakMinutes),
    dayTypes: [...r.dayTypes] as DayType[], active: r.active, note: r.note ?? '',
  });
  const close = useCallback(() => setEditing(null), []);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!editing) return;
    setViolations([]);
    if (typeof input === 'string') { setFormError(input); return; }
    const err = validateWorkPattern(input);
    if (err) { setFormError(err); return; }
    setFormError(null);
    setSaving(true);
    try {
      // 서버 검사(법정 기준 포함)를 먼저 받아 위반을 모두 보인다. 저장 때 서버가 다시 검사한다.
      if (!locked) {
        const r = await api.check(input);
        if (!r.ok) { setViolations(r.violations); return; }
      }
      if (editing.mode === 'create') {
        await api.create(input);
        toast(`근무유형 ${input.patternCode}를 추가했어요`);
      } else {
        await api.update(editing.row.patternId, input);
        toast(`근무유형 ${input.patternCode}를 수정했어요`);
      }
      setEditing(null);
      setReload((n) => n + 1);
    } catch (e2) {
      setFormError(e2 instanceof Error ? e2.message : '저장하지 못했어요');
    } finally {
      setSaving(false);
    }
  };

  const setDay = (i: number) => setDraft((d) => ({ ...d, dayTypes: d.dayTypes.map((t, j) => (j === i ? NEXT[t] : t)) }));

  return (
    <>
      <PageHead
        title="근무유형 관리"
        sub="시업·종업 시각, 휴게시간, 근무일·주휴일을 정해요. 저장할 때 근로기준법 기준(1일·1주 근로시간, 휴게, 주휴일)을 검사해요"
        right={<button className="glass pill" onClick={() => setReload((n) => n + 1)} aria-label="새로 고침"><LuRefreshCw /> 새로 고침</button>}
      />

      <Card i={1}>
        <CardHead
          title="근무유형"
          sub={rows ? `${rows.length}개` : '불러오는 중…'}
          right={<button className="btn btn--primary" onClick={openCreate} disabled={rows === null}><LuPlus /> 근무유형 추가</button>}
        />
        {rows === null ? (
          <p className="muted code-empty">불러오는 중…</p>
        ) : rows.length === 0 ? (
          <p className="muted code-empty">등록된 근무유형이 없어요</p>
        ) : (
          <table className="code-table">
            <thead>
              <tr><th>코드</th><th>이름</th><th>근무시간</th><th className="hide-sm">휴게</th><th>소정근로(1일/1주)</th><th className="hide-sm">휴무·주휴</th><th /></tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.patternId} className={cx(!r.active && 'off')}>
                  <td><code>{r.patternCode}</code></td>
                  <td>
                    {r.patternName} {!r.active && <Badge tone="amber">사용 중지</Badge>} {r.assigned && <Badge tone="blue">배정됨</Badge>}
                  </td>
                  <td className="num">{toHm(r.workStartMin)}~{r.workEndMin <= r.workStartMin ? '다음 날 ' : ''}{toHm(r.workEndMin)}</td>
                  <td className="hide-sm muted sm">{r.breakMinutes === 0 || r.breakStartMin === null ? '없음' : `${toHm(r.breakStartMin)}부터 ${duration(r.breakMinutes)}`}</td>
                  <td className="num">{duration(r.dailyContractMinutes)} / {duration(r.weeklyContractMinutes)}</td>
                  <td className="hide-sm muted sm">{dayTypesText(r.dayTypes)}</td>
                  <td className="code-table__actions">
                    <button className="btn btn--ghost icon-only" onClick={() => openEdit(r)} aria-label={`${r.patternName} 수정`}><LuPencil /></button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      <Modal open={editing !== null} onClose={close} title={editing?.mode === 'edit' ? '근무유형 수정' : '근무유형 추가'}>
        <form className="form" onSubmit={submit} noValidate>
          {locked && (
            <p className="badge badge--amber" role="note">
              사원에게 배정된 근무유형이라 이름·사용 여부·비고만 바꿀 수 있어요. 시각을 바꾸려면 새 근무유형을 만들어 다시 배정해 주세요
            </p>
          )}
          <div className="form__row">
            <label className="field">
              <span className="field__label">코드 (추가 후 바꿀 수 없어요)</span>
              <input className="input" value={draft.patternCode} disabled={editing?.mode === 'edit'} maxLength={20} autoComplete="off"
                onChange={(e) => setDraft({ ...draft, patternCode: e.target.value.toUpperCase() })} placeholder="예: DAY" />
            </label>
            <label className="field">
              <span className="field__label">이름</span>
              <input className="input" value={draft.patternName} maxLength={100} onChange={(e) => setDraft({ ...draft, patternName: e.target.value })} placeholder="예: 주간 근무" />
            </label>
          </div>
          <div className="form__row">
            <label className="field">
              <span className="field__label">시업 시각</span>
              <input className="input" type="time" aria-label="시업 시각" value={draft.start} disabled={locked} onChange={(e) => setDraft({ ...draft, start: e.target.value })} />
            </label>
            <label className="field">
              <span className="field__label">종업 시각 (시업보다 이르면 다음 날)</span>
              <input className="input" type="time" aria-label="종업 시각" value={draft.end} disabled={locked} onChange={(e) => setDraft({ ...draft, end: e.target.value })} />
            </label>
          </div>
          <div className="form__row">
            <label className="field">
              <span className="field__label">휴게 시작</span>
              <input className="input" type="time" aria-label="휴게 시작" value={draft.breakStart} disabled={locked} onChange={(e) => setDraft({ ...draft, breakStart: e.target.value })} />
            </label>
            <label className="field">
              <span className="field__label">휴게시간 (분)</span>
              <input className="input" inputMode="numeric" aria-label="휴게시간" value={draft.breakMinutes} disabled={locked}
                onChange={(e) => setDraft({ ...draft, breakMinutes: e.target.value.replace(/[^0-9]/g, '') })} />
            </label>
          </div>
          <div className="field">
            <span className="field__label">요일 구분 (누를 때마다 근무 → 휴무 → 주휴)</span>
            <div className="day-types" role="group" aria-label="요일 구분">
              {draft.dayTypes.map((t, i) => (
                <button key={WEEKDAYS[i]} type="button" disabled={locked} aria-label={`${WEEKDAYS[i]}요일 ${DAY_TYPE_LABEL[t]}`}
                  className={cx('btn', t === 'W' ? 'btn--primary' : 'btn--ghost', `day-type--${t}`)} onClick={() => setDay(i)}>
                  {WEEKDAYS[i]}<br /><span className="sm">{DAY_TYPE_LABEL[t]}</span>
                </button>
              ))}
            </div>
          </div>
          <div className="form__row">
            <div className="field">
              <span className="field__label">사용 여부</span>
              <Toggle checked={draft.active} onChange={(v) => setDraft({ ...draft, active: v })} label="사용 여부" />
            </div>
            <label className="field">
              <span className="field__label">비고 (선택)</span>
              <input className="input" value={draft.note} maxLength={500} onChange={(e) => setDraft({ ...draft, note: e.target.value })} />
            </label>
          </div>
          <p className="muted sm" aria-live="polite" data-testid="preview">
            {preview ? `소정근로시간 1일 ${duration(Math.max(0, preview.daily))} · 1주 ${duration(Math.max(0, preview.weekly))} (휴게 제외)` : ''}
          </p>
          {violations.length > 0 && (
            <ul className="violations" role="alert" aria-label="법정 기준 위반">
              {violations.map((v) => <li key={v.code} className="badge badge--red">{v.message}</li>)}
            </ul>
          )}
          {formError && <p className="badge badge--red" role="alert">{formError}</p>}
          <div className="form__actions">
            <button type="button" className="btn btn--ghost" onClick={close}>취소</button>
            <button type="submit" className="btn btn--primary" disabled={saving}>{saving ? '검사·저장 중…' : '검사 후 저장'}</button>
          </div>
        </form>
      </Modal>
    </>
  );
}
