import { useEffect, useRef, useState } from 'react';
import { LuSlidersHorizontal, LuCalendarDays, LuMail, LuBell, LuSearch, LuLogOut } from 'react-icons/lu';
import { NAV, navGroupsFor, type ModuleId, type NavGroup } from '../../nav';
import { useData, useStore } from '../../state/store';
import { useSession } from '../../state/session';
import { Avatar, cx } from '../ui';
import { EnterSwitcher } from './EnterSwitcher';

export function DesktopNav() {
  const { page, go, pendingCount, unreadCount, toast, dept } = useStore();
  const { logout } = useSession();
  const { me } = useData();
  const [menu, setMenu] = useState(false);
  /** 중메뉴 목록이 열린 대메뉴 */
  const [openMod, setOpenMod] = useState<ModuleId | null>(null);
  const closeTimer = useRef<number>(0);
  const dockRef = useRef<HTMLElement>(null);

  const badgeFor = (id: string) => (id === 'approvals' ? pendingCount : id === 'messages' ? unreadCount : 0);
  const groupBadge = (g: NavGroup) => g.items.reduce((sum, n) => sum + badgeFor(n.id), 0);
  const groups = navGroupsFor(me.role, me.permissions);

  // 바깥을 누르거나 Esc 를 누르면 중메뉴 목록을 닫는다
  useEffect(() => {
    if (!openMod) return;
    const onDown = (e: PointerEvent) => { if (!dockRef.current?.contains(e.target as Node)) setOpenMod(null); };
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpenMod(null); };
    document.addEventListener('pointerdown', onDown);
    window.addEventListener('keydown', onKey);
    return () => { document.removeEventListener('pointerdown', onDown); window.removeEventListener('keydown', onKey); };
  }, [openMod]);
  useEffect(() => () => clearTimeout(closeTimer.current), []);

  const hoverOpen = (id: ModuleId) => { clearTimeout(closeTimer.current); setOpenMod(id); };
  const hoverClose = () => { clearTimeout(closeTimer.current); closeTimer.current = window.setTimeout(() => setOpenMod(null), 160); };

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
          <EnterSwitcher className="glass pill" />
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
                <span className="muted sm">{[dept(me.dept)?.name, me.loginId].filter(Boolean).join(' · ')}</span>
                <button onClick={() => logout(false)}><LuLogOut /> 로그아웃</button>
              </div>
            )}
          </div>
        </div>
      </header>

      <nav className="glass dock" aria-label="전체 메뉴" ref={dockRef}>
        {groups.map((g) => {
          const single = g.items.length === 1;
          const active = g.items.some((n) => n.id === page);
          const open = !single && openMod === g.module.id;
          const count = groupBadge(g);
          const sep = g.module.id === 'system' || g.module.id === 'settings';
          return (
            <div
              key={g.module.id}
              className={cx('dock__group', sep && 'sep')}
              onPointerEnter={(e) => { if (!single && e.pointerType === 'mouse') hoverOpen(g.module.id); }}
              onPointerLeave={(e) => { if (!single && e.pointerType === 'mouse') hoverClose(); }}
            >
              <button
                className={cx('dock__btn', active && 'active', open && 'open')}
                onClick={() => (single ? go(g.items[0].id) : setOpenMod((v) => (v === g.module.id ? null : g.module.id)))}
                data-tip={single ? g.module.label : undefined}
                aria-label={g.module.label}
                aria-haspopup={single ? undefined : 'menu'}
                aria-expanded={single ? undefined : open}
                aria-current={single && active ? 'page' : undefined}
              >
                <g.module.icon />
                {count > 0 && <span className="count">{count}</span>}
              </button>
              {open && (
                <div className="dock__flyout" role="menu" aria-label={g.module.label}>
                  <p className="dock__flyout-title">{g.module.label}</p>
                  {g.items.map((n) => (
                    <button
                      key={n.id}
                      role="menuitem"
                      className={cx('dock__sub', page === n.id && 'active')}
                      onClick={() => { go(n.id); setOpenMod(null); }}
                      aria-current={page === n.id ? 'page' : undefined}
                    >
                      <n.icon />
                      <span>{n.label}</span>
                      {badgeFor(n.id) > 0 && <em className="dock__sub-count">{badgeFor(n.id)}</em>}
                    </button>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </nav>
    </>
  );
}
