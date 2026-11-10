import { useState } from 'react';
import { LuLogOut, LuPlay, LuMoon, LuBell, LuMessageCircle, LuPalette } from 'react-icons/lu';
import { Card, CardHead, PageHead, Toggle, Avatar, cx } from '../components/ui';
import { useData, useStore, type SceneId } from '../state/store';
import { useSession } from '../state/session';

const SCENES: { id: SceneId; name: string }[] = [
  { id: 'sunset', name: '노을' },
  { id: 'dawn', name: '새벽' },
  { id: 'night', name: '밤' },
];

export function SettingsPage() {
  const { night, setNight, scene, setScene, toast, dept } = useStore();
  const { me: ME } = useData();
  const { logout } = useSession();
  const [notiApproval, setNotiApproval] = useState(true);
  const [notiMsg, setNotiMsg] = useState(true);

  return (
    <>
      <PageHead title="설정" sub="화면과 알림, 계정을 관리하세요" />
      <div className="grid grid--settings">
        <Card i={1} className="span-2">
          <CardHead title="배경 장면" sub="유리 카드 뒤로 보이는 풍경을 골라요" right={<LuPalette className="muted" />} />
          <div className="scenes">
            {SCENES.map((s) => (
              <button key={s.id} className={cx('scene-thumb', `thumb--${s.id}`, (night ? 'night' : scene) === s.id && 'active')} onClick={() => { if (s.id === 'night') setNight(true); else { setNight(false); setScene(s.id); } }}>
                <span className="scene-thumb__img" />
                <span>{s.name}</span>
              </button>
            ))}
          </div>
        </Card>

        <Card i={2}>
          <CardHead title="내 계정" />
          <div className="me">
            <Avatar name={ME.name} dept={ME.dept} size={56} ring />
            <div>
              <strong>{ME.name}</strong>
              <span className="muted sm">{dept(ME.dept)?.name}팀 · {ME.position}</span>
              <span className="muted sm">{ME.email}</span>
            </div>
          </div>
          <div className="form__actions">
            <button className="btn btn--ghost" onClick={() => logout(true)}><LuPlay /> 인트로 다시 보기</button>
            <button className="btn btn--primary" onClick={() => logout(false)}><LuLogOut /> 로그아웃</button>
          </div>
        </Card>

        <Card i={3} className="span-3">
          <CardHead title="환경" />
          <ul className="settings-list">
            <li><LuMoon /><div className="grow"><strong>야간 모드</strong><span className="muted sm">유리를 어둡게, 배경을 밤 장면으로 바꿔요</span></div><Toggle checked={night} onChange={setNight} label="야간 모드" /></li>
            <li><LuBell /><div className="grow"><strong>결재 알림</strong><span className="muted sm">새 결재 요청이 오면 알려줘요</span></div><Toggle checked={notiApproval} onChange={(v) => { setNotiApproval(v); toast(v ? '결재 알림을 켰어요' : '결재 알림을 껐어요'); }} label="결재 알림" /></li>
            <li><LuMessageCircle /><div className="grow"><strong>메시지 알림</strong><span className="muted sm">새 메시지를 받으면 알려줘요</span></div><Toggle checked={notiMsg} onChange={(v) => { setNotiMsg(v); toast(v ? '메시지 알림을 켰어요' : '메시지 알림을 껐어요'); }} label="메시지 알림" /></li>
          </ul>
        </Card>
      </div>
    </>
  );
}
