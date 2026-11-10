import { useState } from 'react';
import { LuUsers, LuBriefcase, LuPlane, LuFileCheck, LuArrowUpRight, LuDownload, LuMegaphone, LuPin } from 'react-icons/lu';
import type { IconType } from 'react-icons';
import { Card, CardHead, PageHead, Segmented, Sparkline, Badge, Avatar, vars, cx, ymd } from '../components/ui';
import { useData, useStore } from '../state/store';
import type { TrendPoint } from '../api';

const PERIODS = ['7개월', '1년(분기)'] as const;
type Period = (typeof PERIODS)[number];

/** 12 monthly points → 4 groups of 3 months */
const quarters = (t: TrendPoint[]): TrendPoint[] =>
  [0, 3, 6, 9].map((i) => ({
    m: `${t[i].m.replace('월', '')}~${t[i + 2].m}`,
    hire: t[i].hire + t[i + 1].hire + t[i + 2].hire,
    leave: t[i].leave + t[i + 1].leave + t[i + 2].leave,
  }));

function greeting(h: number) {
  if (h < 5) return '늦은 시간까지 고생 많아요';
  if (h < 12) return '좋은 아침이에요';
  if (h < 18) return '좋은 오후예요';
  return '좋은 저녁이에요';
}

interface Stat { label: string; value: string; change: string; up: boolean; icon: IconType; color: string; spark: number[] }

export function HomePage() {
  const { pendingCount, go, toast } = useStore();
  const { me, dashboard: db, approvals } = useData();
  const [period, setPeriod] = useState<Period>('7개월');
  const t = db.trend;
  // headcount at the end of each of the last 7 months, walking back from today
  const net = t.map((p) => p.hire - p.leave);
  const hc = t.map((_, i) => db.headcount - net.slice(i + 1).reduce((s, x) => s + x, 0)).slice(-7);
  const rate = db.headcount ? Math.round((db.presentToday / db.headcount) * 100) : 0;

  const stats: Stat[] = [
    { label: '재직 인원', value: `${db.headcount}`, change: `+${db.hiredThisMonth} 이번 달`, up: true, icon: LuUsers, color: '#f59e0b', spark: hc },
    { label: '오늘 출근', value: `${db.presentToday}`, change: `출근율 ${rate}%`, up: rate >= 80, icon: LuBriefcase, color: '#4ade80', spark: [rate - 6, rate - 2, rate - 4, rate + 1, rate - 3, rate] },
    { label: '휴가 · 부재', value: `${db.onLeaveToday}`, change: '오늘 기준', up: false, icon: LuPlane, color: '#60a5fa', spark: t.slice(-7).map((p) => p.leave + 1) },
    { label: '내 결재 대기', value: `${pendingCount}`, change: pendingCount ? '처리가 필요해요' : '모두 처리했어요', up: !pendingCount, icon: LuFileCheck, color: '#f472b6', spark: [3, 5, 4, 6, 8, 6, pendingCount] },
  ];

  const data = period === '7개월' ? t.slice(-7) : quarters(t);
  const max = Math.max(...data.map((d) => d.hire)) + 1;
  const pending = approvals.filter((a) => a.canDecide).slice(0, 3);
  const today = ymd();

  return (
    <>
      <PageHead
        title={<>{greeting(new Date().getHours())}, {me.name.slice(1)}님</>}
        sub="오늘 회사에서 일어나는 일을 한눈에 볼 수 있어요"
        right={
          <>
            <Segmented options={PERIODS} value={period} onChange={setPeriod} small />
            <button className="glass pill" onClick={() => toast('데모에서는 내보내기를 지원하지 않아요')}><LuDownload /> 내보내기</button>
          </>
        }
      />

      <div className="grid grid--stats">
        {stats.map((s, i) => (
          <Card key={s.label} i={i + 1} className="stat">
            <div className="stat__top">
              <span className="chip-icon" style={{ background: s.color + '33', color: s.color }}><s.icon /></span>
              <LuArrowUpRight className="stat__arrow" />
            </div>
            <p className="muted sm">{s.label}</p>
            <strong className="stat__value">{s.value}</strong>
            <div className="stat__foot">
              <Badge tone={s.up ? 'green' : 'amber'}>{s.change}</Badge>
              <Sparkline data={s.spark} color={s.color} />
            </div>
          </Card>
        ))}
      </div>

      <div className="grid grid--home">
        <Card i={5} className="span-2">
          <CardHead
            title="입사 · 퇴사 추이"
            sub={period === '7개월' ? '최근 7개월' : '최근 1년, 3개월 단위'}
            right={<div className="legend"><span><i className="lg-hire" />입사</span><span><i className="lg-leave" />퇴사</span></div>}
          />
          <div className="bars" key={period}>
            {data.map((d, i) => (
              <div key={d.m} className={cx('bars__col', i === data.length - 1 && 'now')} style={vars({ i })}>
                <div className="bars__stack">
                  <span className="bars__tip">입사 {d.hire} · 퇴사 {d.leave}</span>
                  <span className="bar bar--hire" style={vars({ h: `${(d.hire / max) * 100}%` })} />
                  <span className="bar bar--leave" style={vars({ h: `${(d.leave / max) * 100}%` })} />
                </div>
                <span className="bars__label">{d.m}</span>
              </div>
            ))}
          </div>
        </Card>

        <Card i={6}>
          <CardHead title="다가오는 일정" sub={new Date().toLocaleDateString('ko-KR', { month: 'long', day: 'numeric', weekday: 'long' })} />
          <ul className="timeline">
            {db.schedule.map((e) => (
              <li key={e.date + e.time + e.title}>
                <span className="timeline__time">{e.date === today ? e.time : `${Number(e.date.slice(5, 7))}/${Number(e.date.slice(8))}`}</span>
                <span className="timeline__bar" style={{ background: e.color }} />
                <div>
                  <strong>{e.title}</strong>
                  <span className="muted sm">{e.tag}{e.date !== today && ` · ${e.time}`}</span>
                </div>
              </li>
            ))}
            {db.schedule.length === 0 && <li className="empty">예정된 일정이 없어요</li>}
          </ul>
        </Card>
      </div>

      <div className="grid grid--home">
        <Card i={7} className="span-2">
          <CardHead title="결재 대기" sub={`${pendingCount}건이 기다리고 있어요`} right={<button className="link" onClick={() => go('approvals')}>전체 보기 →</button>} />
          <ul className="list">
            {pending.map((a) => (
              <li key={a.id} className="list__row" onClick={() => go('approvals')}>
                <Avatar name={a.requester} dept={a.dept} size={34} />
                <div className="grow">
                  <strong>{a.title}</strong>
                  <span className="muted sm">{a.requester} · {a.kind} · {a.date}</span>
                </div>
                <Badge tone="amber">대기</Badge>
              </li>
            ))}
            {pending.length === 0 && <li className="muted">대기 중인 결재가 없어요 🎉</li>}
          </ul>
        </Card>

        <Card i={8}>
          <CardHead title="공지사항" right={<LuMegaphone className="muted" />} />
          <ul className="list">
            {db.notices.map((n) => (
              <li key={n.id} className="list__row">
                {n.pinned ? <LuPin className="pin" /> : <span className="bullet" />}
                <span className="grow">{n.title}</span>
                <span className="muted sm">{n.date}</span>
              </li>
            ))}
          </ul>
        </Card>
      </div>
    </>
  );
}
