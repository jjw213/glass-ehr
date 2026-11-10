import { useMemo, useState } from 'react';
import { LuSearch, LuMail, LuPhone, LuMapPin, LuX, LuMessageCircle, LuIdCard, LuCalendarDays, LuBriefcase } from 'react-icons/lu';
import { Card, PageHead, Avatar, Badge, cx, vars } from '../components/ui';
import type { Employee } from '../api';
import { useData, useStore } from '../state/store';

const statusTone = (s: Employee['status']) => (s === '근무중' ? 'green' : s === '휴가' ? 'amber' : 'blue');

function tenure(joined: string) {
  const d = new Date(joined), now = new Date();
  let m = (now.getFullYear() - d.getFullYear()) * 12 + now.getMonth() - d.getMonth();
  if (now.getDate() < d.getDate()) m--;
  return m >= 12 ? `${Math.floor(m / 12)}년 ${m % 12}개월` : `${Math.max(0, m)}개월`;
}

export function PeoplePage() {
  const { go, toast, dept: deptOf } = useStore();
  const { employees: EMPLOYEES, depts: DEPTS } = useData();
  const deptById = (id: string) => deptOf(id) ?? { name: id, color: '#999' };
  const [q, setQ] = useState('');
  const [dept, setDept] = useState<string>('all');
  const [sel, setSel] = useState<Employee | null>(null);

  const list = useMemo(() => EMPLOYEES.filter((e) =>
    (dept === 'all' || e.dept === dept) &&
    (!q.trim() || [e.name, e.position, e.email, deptById(e.dept).name].some((v) => v.includes(q.trim()))),
  ), [q, dept, EMPLOYEES, deptOf]);

  return (
    <>
      <PageHead title="인사정보" sub={`전체 ${EMPLOYEES.length}명 · 검색 결과 ${list.length}명`} />

      <div className="toolbar rise" style={vars({ i: 1 })}>
        <label className="glass search">
          <LuSearch />
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="이름, 부서, 직급, 이메일로 검색" />
          {q && <button onClick={() => setQ('')} aria-label="지우기"><LuX /></button>}
        </label>
        <div className="chips">
          <button className={cx('glass pill', dept === 'all' && 'active')} onClick={() => setDept('all')}>전체</button>
          {DEPTS.map((d) => (
            <button key={d.id} className={cx('glass pill', dept === d.id && 'active')} onClick={() => setDept(d.id)}>
              <span className="s-dot" style={{ background: d.color }} />{d.name}
            </button>
          ))}
        </div>
      </div>

      <div className="grid grid--people">
        {list.map((e, i) => (
          <Card key={e.id} i={Math.min(i + 2, 12)} className="person" onClick={() => setSel(e)}>
            <Avatar name={e.name} dept={e.dept} size={48} />
            <div className="grow">
              <strong>{e.name} <span className="muted sm">{e.position}</span></strong>
              <span className="muted sm"><span className="s-dot" style={{ background: deptById(e.dept).color }} />{deptById(e.dept).name}팀</span>
            </div>
            <Badge tone={statusTone(e.status)}>{e.status}</Badge>
          </Card>
        ))}
        {list.length === 0 && <Card i={2} className="empty">검색 결과가 없어요</Card>}
      </div>

      {sel && (
        <div className="drawer" onClick={() => setSel(null)}>
          <aside className="glass drawer__panel" onClick={(e) => e.stopPropagation()} role="dialog" aria-label={`${sel.name} 프로필`}>
            <div className="modal__grab" />
            <button className="iconbtn drawer__close" onClick={() => setSel(null)} aria-label="닫기"><LuX /></button>
            <div className="profile-hero" style={{ background: `linear-gradient(140deg, ${deptById(sel.dept).color}66, transparent)` }}>
              <Avatar name={sel.name} dept={sel.dept} size={72} ring />
              <h2>{sel.name}</h2>
              <p className="muted">{deptById(sel.dept).name}팀 · {sel.position}</p>
              <Badge tone={statusTone(sel.status)}>{sel.status}</Badge>
            </div>
            <ul className="info">
              <li><LuIdCard /><span className="muted">사번</span><strong>{sel.id}</strong></li>
              <li><LuCalendarDays /><span className="muted">입사일</span><strong>{sel.joined}</strong></li>
              <li><LuBriefcase /><span className="muted">근속</span><strong>{tenure(sel.joined)}</strong></li>
              <li><LuMail /><span className="muted">이메일</span><strong>{sel.email}</strong></li>
              <li><LuPhone /><span className="muted">연락처</span><strong>{sel.phone}</strong></li>
              <li><LuMapPin /><span className="muted">근무지</span><strong>{sel.location}</strong></li>
            </ul>
            <div className="form__actions">
              <button className="btn btn--ghost" onClick={() => toast(`${sel.phone} (데모에서는 전화가 연결되지 않아요)`)}><LuPhone /> 전화</button>
              <button className="btn btn--primary" onClick={() => go('messages')}><LuMessageCircle /> 메시지</button>
            </div>
          </aside>
        </div>
      )}
    </>
  );
}
