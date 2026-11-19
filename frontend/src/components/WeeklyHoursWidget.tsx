import { useEffect, useState } from 'react';
import { Badge, Card, CardHead, cx, vars } from './ui';
import { ApiError } from '../api/types';
import {
  formatHours, gaugePercent, STATUS_LABEL, weeklyHoursApi as defaultApi,
  type MyWeek, type TeamWeek, type WeeklyHoursApi, type WeeklyStatus,
} from '../api/weeklyHours';

const TONE: Record<WeeklyStatus, 'green' | 'amber' | 'red'> = { NORMAL: 'green', NEAR: 'amber', OVER: 'red' };

/**
 * 대시보드 주 52시간 위젯 (#31).
 * - 개인: 이번 주 누적 근로시간 게이지(한도 = 근로기준법 제50조 제1항 + 제53조 제1항, 서버가 legal_rate 에서 읽음).
 *   한도 근접·초과면 본인에게 경고 문구를 보여 준다.
 * - 부서장·인사: 부서별 표에서 한도 근접·초과자를 위로 올리고 강조한다. /team 이 403 이면 표를 숨긴다.
 * 모바일(≤899px)에서는 표가 사원별 카드로 바뀐다(global.css).
 */
export function WeeklyHoursWidget({ api = defaultApi, i = 0, refreshKey = 0 }: { api?: WeeklyHoursApi; i?: number; refreshKey?: number }) {
  const [mine, setMine] = useState<MyWeek | null>(null);
  const [mineError, setMineError] = useState<string | null>(null);
  const [team, setTeam] = useState<TeamWeek | null>(null);
  const [teamError, setTeamError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    api.mine().then(
      (m) => { if (alive) { setMine(m); setMineError(null); } },
      (e: unknown) => {
        // 사원과 연결되지 않은 계정(관리자 등)은 403 — 개인 게이지만 숨긴다.
        if (alive) setMineError(e instanceof ApiError && e.status === 403 ? null : e instanceof Error ? e.message : '근로시간을 불러오지 못했어요');
      },
    );
    api.team().then(
      (t) => { if (alive) { setTeam(t); setTeamError(null); } },
      (e: unknown) => {
        if (alive) setTeamError(e instanceof ApiError && e.status === 403 ? null : e instanceof Error ? e.message : '부서별 근로시간을 불러오지 못했어요');
      },
    );
    return () => { alive = false; };
  }, [api, refreshKey]);

  const w = mine?.me.week;
  const range = mine ?? team;
  return (
    <Card i={i} className="span-2 weekly-hours">
      <CardHead
        title="이번 주 근로시간"
        sub={range ? `${range.weekStart} ~ ${range.weekEnd} · 소정 + 연장 + 휴일` : '소정 + 연장 + 휴일'}
        right={w && <Badge tone={TONE[w.status]}>{STATUS_LABEL[w.status]}</Badge>}
      />
      {mineError && <p className="muted sm" role="alert">{mineError}</p>}
      {w && (
        <div className="weekly-hours__me">
          <div
            className={cx('gauge', `gauge--${w.status.toLowerCase()}`)}
            role="meter"
            aria-label="이번 주 누적 근로시간"
            aria-valuemin={0}
            aria-valuemax={w.limitMinutes}
            aria-valuenow={Math.min(w.workedMinutes, w.limitMinutes)}
            aria-valuetext={`${formatHours(w.workedMinutes)} / ${formatHours(w.limitMinutes)}`}
          >
            <span className="gauge__fill" style={vars({ p: `${gaugePercent(w)}%` })} />
          </div>
          <dl className="weekly-hours__stats">
            <div><dt className="muted sm">누적</dt><dd aria-label="누적 근로시간">{formatHours(w.workedMinutes)}</dd></div>
            <div><dt className="muted sm">주간 한도</dt><dd>{formatHours(w.limitMinutes)}</dd></div>
            <div><dt className="muted sm">연장(한도 대상)</dt><dd>{formatHours(w.limitOvertimeMinutes)} / {formatHours(w.overtimeLimitMinutes)}</dd></div>
          </dl>
          {w.status !== 'NORMAL' && (
            <p className={cx('sm', 'weekly-hours__warn', `weekly-hours__warn--${w.status.toLowerCase()}`)} role="alert">
              {w.status === 'OVER'
                ? '이번 주 근로시간 한도를 넘었어요. 부서장과 근무 일정을 조정해 주세요.'
                : `한도까지 ${formatHours(Math.min(w.remainingMinutes, w.overtimeRemainingMinutes))} 남았어요. 추가 근무 전에 부서장과 상의해 주세요.`}
            </p>
          )}
          {w.missingOut && <p className="muted sm">퇴근 기록이 없는 날이 있어 일부 근무가 빠졌을 수 있어요.</p>}
        </div>
      )}
      {teamError && <p className="muted sm" role="alert">{teamError}</p>}
      {team && (
        <section className="weekly-hours__team" aria-label="부서별 근로시간">
          <p className="sm">
            부서별 현황 · 한도 근접 <strong>{team.nearCount}</strong>명 · 초과 <strong>{team.overCount}</strong>명
          </p>
          {team.departments.length === 0 && <p className="muted sm">볼 수 있는 사원이 없어요</p>}
          {team.departments.map((d) => (
            <div key={d.deptId ?? 'none'} className="weekly-hours__dept">
              <h4 className="sm">{d.deptName} <span className="muted">근접 {d.nearCount} · 초과 {d.overCount}</span></h4>
              <table className="weekly-table">
                <thead>
                  <tr><th>사원</th><th>누적</th><th>연장(한도 대상)</th><th>남은 시간</th><th>상태</th></tr>
                </thead>
                <tbody>
                  {d.employees.map((e) => (
                    <tr key={e.empId} className={cx(e.week.status !== 'NORMAL' && `weekly-table__row--${e.week.status.toLowerCase()}`)}>
                      <td data-label="사원">{e.name} <span className="muted sm">{e.empNo}</span></td>
                      <td data-label="누적">{formatHours(e.week.workedMinutes)}</td>
                      <td data-label="연장">{formatHours(e.week.limitOvertimeMinutes)}</td>
                      <td data-label="남은 시간">{formatHours(Math.min(e.week.remainingMinutes, e.week.overtimeRemainingMinutes))}</td>
                      <td data-label="상태"><Badge tone={TONE[e.week.status]}>{STATUS_LABEL[e.week.status]}</Badge></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ))}
        </section>
      )}
    </Card>
  );
}
