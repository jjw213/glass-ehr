import { useEffect, useRef } from 'react';
import { LuBuilding2, LuUsers, LuCrown } from 'react-icons/lu';
import { Card, PageHead, Avatar, cx, vars } from '../components/ui';
import { useData, useStore } from '../state/store';

export function OrgPage() {
  const { orgDept, setOrgDept, dept } = useStore();
  const { employees: EMPLOYEES, depts: DEPTS } = useData();
  const deptById = (id: string) => dept(id) ?? { name: id, color: '#999' };
  const detailRef = useRef<HTMLDivElement>(null);
  const members = orgDept ? EMPLOYEES.filter((e) => e.dept === orgDept).sort((a, b) => Number(b.position === '팀장') - Number(a.position === '팀장')) : [];

  useEffect(() => {
    if (orgDept) detailRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, [orgDept]);

  return (
    <>
      <PageHead title="조직도" sub={`${DEPTS.length}개 부서 · ${EMPLOYEES.length}명`} right={orgDept && <button className="glass pill" onClick={() => setOrgDept(null)}>전체 보기</button>} />

      <Card i={1} className="ceo">
        <span className="chip-icon" style={{ background: '#ff7a2f33', color: '#ffb27a' }}><LuCrown /></span>
        <div className="grow">
          <strong>대표이사 · 윤재석</strong>
          <span className="muted sm">Glass HR 주식회사</span>
        </div>
        <span className="muted sm"><LuBuilding2 /> 본사 · 판교</span>
      </Card>

      <div className="org-line rise" style={vars({ i: 2 })} aria-hidden />

      <div className="grid grid--org">
        {DEPTS.map((d, i) => {
          const people = EMPLOYEES.filter((e) => e.dept === d.id);
          return (
            <Card key={d.id} i={i + 3} className={cx('dept', orgDept === d.id && 'active', orgDept && orgDept !== d.id && 'dim')} onClick={() => setOrgDept(orgDept === d.id ? null : d.id)}>
              <div className="dept__cover" style={{ background: `linear-gradient(150deg, ${d.color}cc, ${d.color}55)` }}>
                <LuUsers />
                <span className="dept__count">{people.length}명</span>
              </div>
              <strong>{d.name}팀</strong>
              <span className="muted sm">{d.desc}</span>
              <div className="dept__foot">
                <div className="stack">
                  {people.slice(0, 4).map((p) => <Avatar key={p.id} name={p.name} dept={p.dept} size={26} />)}
                </div>
                <span className="muted sm">팀장 {d.lead}</span>
              </div>
            </Card>
          );
        })}
      </div>

      {orgDept && (
        <div ref={detailRef} className="org-detail" key={orgDept}>
          <Card i={0}>
            <h3 className="org-detail__title"><span className="s-dot" style={{ background: deptById(orgDept).color }} />{deptById(orgDept).name}팀 구성원</h3>
            <div className="grid grid--people">
              {members.map((e) => (
                <div key={e.id} className="person person--flat">
                  <Avatar name={e.name} dept={e.dept} size={40} />
                  <div className="grow"><strong>{e.name}</strong><span className="muted sm">{e.position} · {e.status}</span></div>
                </div>
              ))}
            </div>
          </Card>
        </div>
      )}
    </>
  );
}
