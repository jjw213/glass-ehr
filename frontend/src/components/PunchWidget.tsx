import { useCallback, useEffect, useRef, useState } from 'react';
import { LuClock } from 'react-icons/lu';
import { Badge, Card, CardHead, cx } from './ui';
import { elapsedMinutes, formatElapsed, punchApi as defaultApi, seoulTime, type PunchApi, type PunchToday, type PunchType } from '../api/punches';

/**
 * 대시보드 출퇴근 위젯 (#28). 오늘 출근 시각과 근무 경과 시간을 보여 주고 출근·퇴근 버튼으로 기록한다.
 * 기록 시각은 서버 시각이다. 경과 시간은 조회 때 받은 serverNow 로 단말 시계 차이를 보정해 센다.
 * 중복 출근·출근 없는 퇴근도 서버가 저장하고 안내 문구(checkMessage)를 돌려주므로 그대로 보여 준다.
 */
export function PunchWidget({ api = defaultApi, now = Date.now, i = 0 }: { api?: PunchApi; now?: () => number; i?: number }) {
  const [today, setToday] = useState<PunchToday | null>(null);
  const [offset, setOffset] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [, setTick] = useState(0);
  const alive = useRef(true);

  const load = useCallback(async () => {
    try {
      const t = await api.today();
      if (!alive.current) return;
      setOffset(Date.parse(t.serverNow) - now());
      setToday(t);
      setError(null);
    } catch (e) {
      if (alive.current) setError(e instanceof Error ? e.message : '출퇴근 기록을 불러오지 못했어요');
    }
  }, [api, now]);

  useEffect(() => {
    alive.current = true;
    void load();
    const id = setInterval(() => setTick((x) => x + 1), 30_000);
    return () => { alive.current = false; clearInterval(id); };
  }, [load]);

  const punch = async (type: PunchType) => {
    setBusy(true);
    setNotice(null);
    try {
      const p = await api.punch(type);
      if (!alive.current) return;
      setNotice(p.checkMessage ?? `${type === 'IN' ? '출근' : '퇴근'} ${seoulTime(p.punchedAt)} 기록했어요`);
      await load();
    } catch (e) {
      if (alive.current) setError(e instanceof Error ? e.message : '기록하지 못했어요');
    } finally {
      if (alive.current) setBusy(false);
    }
  };

  const elapsed = today ? elapsedMinutes(today, now(), offset) : null;

  return (
    <Card i={i} className="punch-widget">
      <CardHead
        title="출퇴근"
        sub={today ? `${today.workDate} 기준 · 서버 시각으로 기록` : '서버 시각으로 기록'}
        right={today && (today.working ? <Badge tone="green">근무 중</Badge> : today.firstInAt ? <Badge tone="blue">퇴근</Badge> : <Badge>출근 전</Badge>)}
      />
      {error && (
        <p className="muted sm" role="alert">
          {error} <button className="link" onClick={() => void load()}>다시 불러오기</button>
        </p>
      )}
      {today && (
        <dl className="punch-widget__stats">
          <div><dt className="muted sm">오늘 출근</dt><dd aria-label="오늘 출근 시각">{today.firstInAt ? seoulTime(today.firstInAt) : '—'}</dd></div>
          <div>
            <dt className="muted sm"><LuClock /> 근무 경과</dt>
            <dd aria-label="근무 경과 시간">{elapsed === null ? '—' : formatElapsed(elapsed)}</dd>
          </div>
          {today.lastOutAt && <div><dt className="muted sm">퇴근</dt><dd aria-label="퇴근 시각">{seoulTime(today.lastOutAt)}</dd></div>}
        </dl>
      )}
      {notice && <p className="sm" role="status">{notice}</p>}
      <div className="punch-widget__actions">
        <button className={cx('btn', today?.working ? 'btn--ghost' : 'btn--primary')} disabled={busy || !today} onClick={() => void punch('IN')}>출근</button>
        <button className={cx('btn', today?.working ? 'btn--primary' : 'btn--ghost')} disabled={busy || !today} onClick={() => void punch('OUT')}>퇴근</button>
      </div>
      <p className="muted sm">경과 시간은 첫 출근부터의 시간(휴게 미공제)이에요. 남긴 기록은 고칠 수 없고, 정정 기능은 따로 준비 중이에요.</p>
    </Card>
  );
}
