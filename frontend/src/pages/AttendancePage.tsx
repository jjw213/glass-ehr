import { useMemo, useState } from 'react';
import { LuPlus, LuCoffee } from 'react-icons/lu';
import { Card, CardHead, PageHead, Badge, Modal, Segmented, cx, ymd } from '../components/ui';
import { PunchWidget } from '../components/PunchWidget';
import { WeeklyHoursWidget } from '../components/WeeklyHoursWidget';
import type { NewLeave } from '../api';
import { useData, useStore } from '../state/store';

const TYPES = ['연차', '반차', '병가', '경조사'] as const;

/** business days between two yyyy-mm-dd dates (inclusive) */
function businessDays(from: string, to: string) {
  const a = new Date(from), b = new Date(to);
  if (isNaN(+a) || isNaN(+b) || b < a) return 0;
  let n = 0;
  for (const d = new Date(a); d <= b; d.setDate(d.getDate() + 1)) if (d.getDay() % 6 !== 0) n++;
  return n;
}

export function AttendancePage() {
  const { addLeave } = useStore();
  const { leaves, me } = useData();
  const [open, setOpen] = useState(false);
  const [weeklyRefreshKey, setWeeklyRefreshKey] = useState(0);
  const LEAVE_TOTAL = me.annualLeave;

  const annual = leaves.filter((l) => l.type === '연차' || l.type === '반차');
  const used = annual.filter((l) => l.status === '승인').reduce((s, l) => s + l.days, 0);
  const pending = annual.filter((l) => l.status === '대기').reduce((s, l) => s + l.days, 0);
  const left = LEAVE_TOTAL - used;
  const R = 52, C = 2 * Math.PI * R;

  return (
    <>
      <PageHead
        title="근태 · 휴가"
        sub="출퇴근 기록과 남은 연차를 확인하고 휴가를 신청하세요"
        right={<button className="btn btn--primary" onClick={() => setOpen(true)}><LuPlus /> 휴가 신청</button>}
      />

      <div className="grid grid--att">
        {/* 출퇴근(#78): Node /api/attendance/toggle 대신 Spring 출퇴근 기록(#28)과 주간 근로시간(#31) 위젯을 쓴다. */}
        <PunchWidget i={1} onPunched={() => setWeeklyRefreshKey((k) => k + 1)} />
        <WeeklyHoursWidget i={2} refreshKey={weeklyRefreshKey} />

        <Card i={3} className="leave-card">
          <CardHead title="연차 현황" sub={`올해 부여 ${LEAVE_TOTAL}일`} right={<LuCoffee className="muted" />} />
          <div className="ring">
            <svg viewBox="0 0 128 128">
              <circle cx="64" cy="64" r={R} className="ring__track" />
              <circle cx="64" cy="64" r={R} className="ring__pend" strokeDasharray={`${((used + pending) / LEAVE_TOTAL) * C} ${C}`} />
              <circle cx="64" cy="64" r={R} className="ring__used" strokeDasharray={`${(used / LEAVE_TOTAL) * C} ${C}`} />
            </svg>
            <div className="ring__center"><strong>{left}</strong><span className="muted sm">일 남음</span></div>
          </div>
          <div className="leave-card__legend">
            <span><i className="lg-used" />사용 {used}일</span>
            <span><i className="lg-pend" />승인 대기 {pending}일</span>
          </div>
        </Card>
      </div>

      <Card i={4}>
        <CardHead title="휴가 신청 내역" sub={`${leaves.length}건`} />
        <ul className="list">
          {leaves.map((l) => (
            <li key={l.id} className="list__row">
              <span className="chip-icon sm" style={{ background: '#ffffff22' }}>{l.type.slice(0, 1)}</span>
              <div className="grow">
                <strong>{l.type} · {l.days}일</strong>
                <span className="muted sm">{l.from === l.to ? l.from : `${l.from} ~ ${l.to}`} · {l.reason}</span>
              </div>
              <Badge tone={l.status === '승인' ? 'green' : l.status === '대기' ? 'amber' : 'red'}>{l.status}</Badge>
            </li>
          ))}
        </ul>
      </Card>

      <LeaveModal open={open} onClose={() => setOpen(false)} onSubmit={async (l) => { if (await addLeave(l)) setOpen(false); }} left={left - pending} />
    </>
  );
}

function LeaveModal({ open, onClose, onSubmit, left }: { open: boolean; onClose: () => void; onSubmit: (l: NewLeave) => Promise<void>; left: number }) {
  const today = ymd();
  const [sending, setSending] = useState(false);
  const [type, setType] = useState<(typeof TYPES)[number]>('연차');
  const [from, setFrom] = useState(today);
  const [to, setTo] = useState(today);
  const [reason, setReason] = useState('');
  const days = useMemo(() => (type === '반차' ? 0.5 : businessDays(from, to)), [type, from, to]);
  const over = type === '연차' || type === '반차' ? days > left : false;
  const invalid = days <= 0 || over;

  return (
    <Modal open={open} onClose={onClose} title="휴가 신청">
      <div className="form">
        <Segmented options={TYPES} value={type} onChange={setType} />
        <div className="form__row">
          <label className="field"><span className="field__label">시작일</span><input className="input" type="date" value={from} onChange={(e) => { setFrom(e.target.value); if (e.target.value > to) setTo(e.target.value); }} /></label>
          {type !== '반차' && <label className="field"><span className="field__label">종료일</span><input className="input" type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} /></label>}
        </div>
        <label className="field"><span className="field__label">사유</span><textarea className="input" rows={3} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="예: 가족 행사" /></label>
        <p className={cx('sm', over ? 'text-red' : 'muted')}>
          {over ? `남은 연차(${left}일)보다 많아요` : `사용 일수 ${days}일 · 신청 후 남은 연차 ${type === '연차' || type === '반차' ? left - days : left}일`}
        </p>
        <div className="form__actions">
          <button className="btn btn--ghost" onClick={onClose}>취소</button>
          <button className="btn btn--primary" disabled={invalid || sending} onClick={async () => { setSending(true); await onSubmit({ type, from, to: type === '반차' ? from : to, reason: reason.trim() }); setSending(false); }}>{sending ? '보내는 중…' : '결재 올리기'}</button>
        </div>
      </div>
    </Modal>
  );
}
