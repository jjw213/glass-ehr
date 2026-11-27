import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { LuPencil, LuRefreshCw, LuShieldAlert } from 'react-icons/lu';
import { Badge, Card, CardHead, Modal, PageHead } from '../components/ui';
import { enterSettingApi, validateSetting, type EnterSetting, type EnterSettingApi, type SettingChange } from '../api/enterSettings';
import { canManageEnterSettings } from '../nav';
import { useData, useStore } from '../state/store';

/**
 * 시스템관리 > 회사 설정 (#131). 지금 로그인한 법인의 운영 설정(상시근로자 수·알림 일수·주 52시간 근접 기준)을
 * 적용 시작일별로 조회·수정하고 변경 이력을 본다. 인사담당자·관리자만(서버가 EMP_WRITE 로 다시 막는다).
 * 값이 없는 법인은 서버 기본값(application.yml)을 쓴다.
 */
export function EnterSettingsPage() {
  const { me } = useData();
  const { toast } = useStore();
  if (!canManageEnterSettings(me.role)) {
    return (
      <>
        <PageHead title="회사 설정" />
        <Card i={1}>
          <CardHead title="인사담당자만 볼 수 있어요" sub="회사 설정은 인사담당자·관리자 계정으로 관리해요" right={<LuShieldAlert className="muted" />} />
        </Card>
      </>
    );
  }
  return <EnterSettingManager api={enterSettingApi} toast={toast} />;
}

const errText = (e: unknown, fallback: string) => (e instanceof Error ? e.message : fallback);
const todayText = () => {
  // 서울 기준 오늘(YYYY-MM-DD)
  const p = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  return p;
};

/** 화면 본문. 스토어 없이 api·toast 만 받아서 테스트할 수 있다. */
export function EnterSettingManager({ api, toast }: { api: EnterSettingApi; toast: (text: string) => void }) {
  const [settings, setSettings] = useState<EnterSetting[] | null>(null);
  const [history, setHistory] = useState<SettingChange[] | null>(null);
  const [reload, setReload] = useState(0);
  const [editing, setEditing] = useState<EnterSetting | null>(null);
  const [effectiveFrom, setEffectiveFrom] = useState('');
  const [value, setValue] = useState('');
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const fail = useCallback((e: unknown) => toast(errText(e, '알 수 없는 오류가 발생했어요')), [toast]);

  useEffect(() => {
    let alive = true;
    api.list().then((s) => { if (alive) setSettings(s); }, fail);
    api.history().then((h) => { if (alive) setHistory(h); }, fail);
    return () => { alive = false; };
  }, [api, reload, fail]);

  const openEdit = (s: EnterSetting) => {
    setEditing(s); setEffectiveFrom(todayText()); setValue(String(s.value)); setFormError(null);
  };
  const close = useCallback(() => setEditing(null), []);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!editing) return;
    const err = validateSetting(editing, effectiveFrom, value);
    if (err) { setFormError(err); return; }
    setSaving(true);
    try {
      await api.update(editing.key, { effectiveFrom, value: Number(value) });
      setEditing(null);
      setReload((n) => n + 1);
      toast(`${editing.label}을(를) 저장했어요`);
    } catch (ex) {
      setFormError(errText(ex, '저장하지 못했어요'));
    } finally {
      setSaving(false);
    }
  };

  const unitOf = (key: string) => settings?.find((s) => s.key === key)?.unit ?? '';

  return (
    <>
      <PageHead
        title="회사 설정"
        sub="지금 법인의 운영 설정이에요. 상시근로자 수는 규모별 시행 조문(legal_rate 적용 규모 하한)과 비교해요"
        right={<button className="glass pill" onClick={() => setReload((n) => n + 1)} aria-label="새로 고침"><LuRefreshCw /> 새로 고침</button>}
      />

      <Card i={1}>
        <CardHead title="설정" sub="적용 시작일별로 값을 둬요. 과거 날짜로 다시 계산하면 그 날짜의 값을 써요" />
        {settings === null ? (
          <p className="muted code-empty">불러오는 중…</p>
        ) : (
          <table className="code-table">
            <thead>
              <tr><th>설정</th><th>오늘 값</th><th className="hide-sm">적용 시작일별 값</th><th /></tr>
            </thead>
            <tbody>
              {settings.map((s) => (
                <tr key={s.key}>
                  <td>{s.label}</td>
                  <td className="num">
                    {s.value}{s.unit} {s.effectiveFrom === null && <Badge tone="blue">기본값</Badge>}
                  </td>
                  <td className="hide-sm muted sm">
                    {s.entries.length === 0 ? '—' : s.entries.map((e) => `${e.effectiveFrom}부터 ${e.value}${s.unit}`).join(' · ')}
                  </td>
                  <td className="code-table__actions">
                    <button className="btn btn--ghost icon-only" onClick={() => openEdit(s)} aria-label={`${s.label} 수정`}><LuPencil /></button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      <Card i={2}>
        <CardHead title="변경 이력" sub="최근 것부터" />
        {history === null ? (
          <p className="muted code-empty">불러오는 중…</p>
        ) : history.length === 0 ? (
          <p className="muted code-empty">변경 이력이 없어요</p>
        ) : (
          <table className="code-table" aria-label="변경 이력">
            <thead>
              <tr><th>변경 시각</th><th>설정</th><th>적용 시작일</th><th>값</th><th className="hide-sm">변경자</th></tr>
            </thead>
            <tbody>
              {history.map((c) => (
                <tr key={c.histId}>
                  <td className="sm">{c.changedAt.replace('T', ' ').slice(0, 16)}</td>
                  <td>{c.label}</td>
                  <td>{c.effectiveFrom}</td>
                  <td>{c.oldValue === null ? `새로 등록 ${c.newValue}` : `${c.oldValue} → ${c.newValue}`}{unitOf(c.key)}</td>
                  <td className="hide-sm">{c.changedBy}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      <Modal open={editing !== null} onClose={close} title={editing ? `${editing.label} 수정` : '설정 수정'}>
        <form className="form" onSubmit={(e) => void submit(e)} noValidate>
          <label className="field">
            <span className="field__label">적용 시작일 (이 날짜부터 새 값을 써요)</span>
            <input className="input" type="date" aria-label="적용 시작일" value={effectiveFrom} onChange={(e) => setEffectiveFrom(e.target.value)} />
          </label>
          <label className="field">
            <span className="field__label">값 ({editing ? `${editing.min}~${editing.max}${editing.unit}, 기본값 ${editing.defaultValue}${editing.unit}` : ''})</span>
            <input className="input" inputMode="numeric" aria-label="값" value={value} onChange={(e) => setValue(e.target.value.replace(/[^0-9]/g, ''))} />
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
