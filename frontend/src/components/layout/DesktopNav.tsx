import { useState } from 'react';
import { LuSlidersHorizontal, LuCalendarDays, LuMail, LuBell, LuSearch, LuLogOut } from 'react-icons/lu';
import { NAV, navFor } from '../../nav';
import { useData, useStore } from '../../state/store';
import { useSession } from '../../state/session';
import { Avatar, cx } from '../ui';

export function DesktopNav() {
  const { page, go, pendingCount, unreadCount, toast } = useStore();
  const { logout } = useSession();
  const { me } = useData();
  const [menu, setMenu] = useState(false);

  const badgeFor = (id: string) => (id === 'approvals' ? pendingCount : id === 'messages' ? unreadCount : 0);

  return (
    <>
      <header className="topbar">
        <div className="topbar__group">
          <button className="glass iconbtn" aria-label="필터" onClick={() => toast('대시보드 위젯 편집은 준비 중이에요')}><LuSlidersHorizontal /></button>
          <button className="glass iconbtn" aria-label="근태 · 휴가" onClick={() => go('attendance')}><LuCalendarDays /></button>
        </div>

        <nav className="topbar__pills" aria-label="주요 메뉴">
          {NAV.filter((n) => n.top).map((n) => (
            <button key={n.id} className={cx('glass pill', page === n.id && 'active')} onClick={() => go(n.id)} aria-current={page === n.id ? 'page' : undefined}>
              <n.icon /> {n.short}
              {badgeFor(n.id) > 0 && <span className="pill__count">{badgeFor(n.id)}</span>}
            </button>
          ))}
          <button className={cx('glass pill', page === 'settings' && 'active')} onClick={() => go('settings')}>설정</button>
        </nav>

        <div className="topbar__group">
          <button className="glass iconbtn" aria-label="메시지" onClick={() => go('messages')}>
            <LuMail />{unreadCount > 0 && <span className="dot" />}
          </button>
          <button className="glass iconbtn" aria-label="알림" onClick={() => go('approvals')}>
            <LuBell />{pendingCount > 0 && <span className="dot" />}
          </button>
          <button className="glass iconbtn" aria-label="직원 검색" onClick={() => go('people')}><LuSearch /></button>
          <div className="profile">
            <button className="profile__btn" onClick={() => setMenu((v) => !v)} aria-label="내 계정">
              <Avatar name={me.name} dept={me.dept} size={38} ring />
              <span className="online" />
            </button>
            {menu && (
              <div className="glass profile__menu" onMouseLeave={() => setMenu(false)}>
                <strong>{me.name}</strong>
                <span className="muted sm">{me.email} · {me.position}</span>
                <button onClick={() => logout(false)}><LuLogOut /> 로그아웃</button>
              </div>
            )}
          </div>
        </div>
      </header>

      <nav className="glass dock" aria-label="전체 메뉴">
        {navFor(me.role).map((n, i) => (
          <button
            key={n.id}
            className={cx('dock__btn', page === n.id && 'active', i === 4 && 'sep', n.id === 'settings' && 'sep')}
            onClick={() => go(n.id)}
            data-tip={n.label}
            aria-label={n.label}
          >
            <n.icon />
            {badgeFor(n.id) > 0 && <span className="count">{badgeFor(n.id)}</span>}
          </button>
        ))}
      </nav>
    </>
  );
}
