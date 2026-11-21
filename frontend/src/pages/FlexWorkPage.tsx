import { useEffect, useState, type FormEvent } from 'react';
import {
  datesBetween, duration, flexWorkApi, rowOf, toPlanDay,
  type FlexAssign, type FlexDetail, type FlexWorkApi, type PlanDay, type PlanRow,
} from '../api/flexWork';
import { Card, PageHead } from '../components/ui';

const errorMessage = (error: unknown) => error instanceof Error ? error.message : '요청에 실패했어요';

export function FlexWorkPage({ api = flexWorkApi }: { api?: FlexWorkApi } = {}) {
  const [assignments, setAssignments] = useState<FlexAssign[] | null>(null);
  const [selected, setSelected] = useState<number | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    api.mine().then((mine) => {
      if (!active) return;
      const selective = mine.filter((assign) => assign.workSystem === 'SELECTIVE').reverse();
      setAssignments(selective);
      setSelected(selective[0]?.flexId ?? null);
    }).catch((e: unknown) => { if (active) setError(errorMessage(e)); });
    return () => { active = false; };
  }, [api]);

  return (
    <>
      <PageHead title="근무계획" sub="선택적 근로시간제" />
      <Card i={1}>
        {error && <p role="alert" className="text-red">{error}</p>}
        {!error && assignments === null && <p role="status">배정을 불러오는 중…</p>}
        {assignments?.length === 0 && <p className="muted">선택적 근로시간제 배정이 없어요.</p>}
        {!!assignments?.length && (
          <label className="field">
            <span className="field__label">정산기간</span>
            <select className="input" value={selected ?? ''} onChange={(event) => setSelected(Number(event.target.value))}>
              {assignments.map((assign) => (
                <option key={assign.flexId} value={assign.flexId}>{assign.periodStart} ~ {assign.periodEnd}</option>
              ))}
            </select>
          </label>
        )}
      </Card>
      {selected !== null && <PlanEditor key={selected} api={api} flexId={selected} />}
    </>
  );
}

function rowsOf(detail: FlexDetail): PlanRow[] {
  const plansByDate = new Map(detail.plan.map((day) => [day.date, day]));
  return datesBetween(detail.assign.periodStart, detail.assign.periodEnd)
    .map((date) => rowOf(plansByDate.get(date), date));
}

function PlanEditor({ api, flexId }: { api: FlexWorkApi; flexId: number }) {
  const [detail, setDetail] = useState<FlexDetail | null>(null);
  const [rows, setRows] = useState<PlanRow[]>([]);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    api.detail(flexId).then((response) => {
      if (!active) return;
      setDetail(response);
      setRows(rowsOf(response));
    }).catch((e: unknown) => { if (active) setError(errorMessage(e)); });
    return () => { active = false; };
  }, [api, flexId]);

  const plan = rows.map(toPlanDay);
  const validDays = plan.filter((day): day is PlanDay => typeof day !== 'string');
  const total = validDays.reduce((sum, day) => sum + day.minutes, 0);

  function change(date: string, field: 'start' | 'endDate' | 'end' | 'breakMinutes', value: string) {
    setSaved(false);
    setError('');
    setRows((previous) => previous.map((row) => row.date === date ? { ...row, [field]: value } : row));
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (saving) return;
    setSaved(false);
    const invalid = plan.find((day) => typeof day === 'string');
    if (invalid) {
      setError(invalid);
      return;
    }
    setError('');
    setSaving(true);
    try {
      const response = await api.savePlan(flexId, validDays.filter((day) => day.start !== null));
      setDetail(response);
      setRows(rowsOf(response));
      setSaved(true);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Card i={2}>
      {detail ? (
        <form className="form" onSubmit={save}>
          <div>
            <p>계획 합계 <strong>{duration(total)}</strong> · 정산기간 법정 총량 <strong>{duration(detail.totalLimitMinutes)}</strong></p>
            {detail.assign.coreStart && detail.assign.coreEnd && (
              <p className="muted sm">의무 근로시간대 {detail.assign.coreStart} ~ {detail.assign.coreEnd}</p>
            )}
            <p className="muted sm">근무하지 않는 날은 시업·종업 시각과 휴게를 비워 두세요.</p>
          </div>
          <div style={{ overflowX: 'auto' }}>
            <table className="code-table">
              <thead>
                <tr><th scope="col">근무일</th><th scope="col">시업</th><th scope="col">종업일</th><th scope="col">종업</th><th scope="col">휴게(분)</th></tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.date}>
                    <td>{row.date}</td>
                    <td><input className="input" type="time" aria-label={`${row.date} 시업`} value={row.start} disabled={saving} onChange={(e) => change(row.date, 'start', e.target.value)} /></td>
                    <td><input className="input" type="date" aria-label={`${row.date} 종업일`} min={row.date} value={row.endDate} disabled={saving} onChange={(e) => change(row.date, 'endDate', e.target.value)} /></td>
                    <td><input className="input" type="time" aria-label={`${row.date} 종업`} value={row.end} disabled={saving} onChange={(e) => change(row.date, 'end', e.target.value)} /></td>
                    <td><input className="input" type="number" min={0} step={1} aria-label={`${row.date} 휴게(분)`} value={row.breakMinutes} disabled={saving} onChange={(e) => change(row.date, 'breakMinutes', e.target.value)} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="form__actions">
            <button type="submit" className="btn btn--primary" disabled={saving}>{saving ? '저장 중…' : '근무계획 저장'}</button>
          </div>
        </form>
      ) : !error && <p role="status">근무계획을 불러오는 중…</p>}
      {error && <p role="alert" className="text-red">{error}</p>}
      {saved && <p role="status">근무계획을 저장했어요.</p>}
    </Card>
  );
}
