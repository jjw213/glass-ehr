import { useCallback, useEffect, useLayoutEffect, useRef, useState, type PointerEvent as RPointerEvent, type ReactNode } from 'react';
import { LuChevronRight, LuChevronLeft, LuChevronDown, LuSearch, LuSun, LuMoon, LuLogOut, LuCommand } from 'react-icons/lu';
import { NAV, navById, type NavItem } from '../../nav';

const SettingsIcon = navById('settings').icon;
import { useData, useStore } from '../../state/store';
import { useSession } from '../../state/session';
import { Avatar, Toggle, cx } from '../ui';

/**
 * Glass sidebar for phones (video 3):
 *  - collapsed: floating icon rail with badges and tooltips (long-press on touch)
 *  - expanded: panel that morphs out of the rail, with search, sections,
 *    a sliding highlight pill, a collapsible 조직도 sub-menu, night mode and profile.
 * Open with the chevron, the search icon, the avatar or a swipe from the left edge.
 */
export function MobileSidebar() {
  const { page, go, pendingCount, unreadCount, night, setNight, orgDept, setOrgDept } = useStore();
  const { logout } = useSession();
  const { me, depts: DEPTS } = useData();
  const ME = me;
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState('');
  const [orgOpen, setOrgOpen] = useState(true);
  const [hover, setHover] = useState<string | null>(null);
  const [hl, setHl] = useState<{ top: number; height: number } | null>(null);
  const [tip, setTip] = useState<{ label: string; y: number } | null>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  const tipTimer = useRef<number>(0);

  const badgeFor = (id: string) => (id === 'approvals' ? pendingCount : id === 'messages' ? unreadCount : 0);
  const activeKey = page === 'org' && orgDept ? `org:${orgDept}` : page;

  /* ----- open / close ----- */
  const openSheet = useCallback((focusSearch = false) => {
    setOpen(true);
    setTip(null);
    if (focusSearch) setTimeout(() => searchRef.current?.focus(), 380);
  }, []);
  const close = useCallback(() => { setOpen(false); setQ(''); setHover(null); }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') close();
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); openSheet(true); }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [close, openSheet]);

  // swipe from the left edge to open, swipe left on the panel to close
  useEffect(() => {
    let sx = -1, sy = 0;
    const start = (e: TouchEvent) => { const t = e.touches[0]; sx = t.clientX; sy = t.clientY; };
    const move = (e: TouchEvent) => {
      if (sx < 0) return;
      const t = e.touches[0], dx = t.clientX - sx, dy = Math.abs(t.clientY - sy);
      if (dy > 40) { sx = -1; return; }
      if (!open && sx < 28 && dx > 50) { openSheet(); sx = -1; }
      if (open && dx < -60) { close(); sx = -1; }
    };
    const end = () => { sx = -1; };
    window.addEventListener('touchstart', start, { passive: true });
    window.addEventListener('touchmove', move, { passive: true });
    window.addEventListener('touchend', end);
    return () => {
      window.removeEventListener('touchstart', start);
      window.removeEventListener('touchmove', move);
      window.removeEventListener('touchend', end);
    };
  }, [open, openSheet, close]);

  /* ----- sliding highlight pill ----- */
  const measure = useCallback(() => {
    const list = listRef.current;
    if (!list) return;
    let key = hover ?? activeKey;
    // sub-items are clipped while 조직도 is collapsed → highlight its header instead
    if (!orgOpen && !q && key.startsWith('org') && key !== 'org-head') key = 'org-head';
    const el = list.querySelector<HTMLElement>(`[data-key="${key}"]`);
    setHl(el ? { top: el.offsetTop, height: el.offsetHeight } : null);
  }, [hover, activeKey, orgOpen, q]);
  useLayoutEffect(() => {
    measure();
    // the 조직도 sub-menu animates open/closed; measure again once it settles
    const t = window.setTimeout(measure, 380);
    return () => clearTimeout(t);
  }, [measure, open]);

  /* ----- rail tooltips ----- */
  const showTip = (label: string, target: HTMLElement) => {
    const r = target.getBoundingClientRect();
    setTip({ label, y: r.top + r.height / 2 });
  };
  const railHandlers = (label: string) => ({
    onPointerEnter: (e: RPointerEvent<HTMLElement>) => { if (e.pointerType === 'mouse') showTip(label, e.currentTarget); },
    onPointerLeave: () => { clearTimeout(tipTimer.current); setTip(null); },
    onPointerDown: (e: RPointerEvent<HTMLElement>) => {
      if (e.pointerType === 'mouse') return;
      const t = e.currentTarget;
      tipTimer.current = window.setTimeout(() => showTip(label, t), 320);
    },
    onPointerUp: () => { clearTimeout(tipTimer.current); window.setTimeout(() => setTip(null), 700); },
  });

  const pick = (id: NavItem['id']) => { if (id === 'org') setOrgDept(null); go(id); close(); };
  const pickDept = (d: string) => { setOrgDept(d); go('org'); close(); };

  const match = (s: string) => s.toLowerCase().includes(q.trim().toLowerCase());
  const items = NAV.filter((n) => match(n.label) || (n.id === 'org' && DEPTS.some((d) => match(d.name))));
  const section = (s: NavItem['section']) => items.filter((n) => n.section === s);

  const item = (n: NavItem, children?: ReactNode) => (
    <button
      key={n.id}
      data-key={n.id === 'org' ? 'org-head' : n.id}
      className={cx('s-item', activeKey === n.id && n.id !== 'org' && 'active', n.id === 'org' && page === 'org' && 'within')}
      onPointerEnter={(e) => e.pointerType === 'mouse' && setHover(n.id === 'org' ? 'org-head' : n.id)}
      onPointerDown={() => setHover(n.id === 'org' ? 'org-head' : n.id)}
      onClick={() => (n.id === 'org' ? setOrgOpen((v) => !v) : pick(n.id))}
      aria-current={page === n.id ? 'page' : undefined}
    >
      <n.icon className="s-item__icon" />
      <span className="s-item__label">{n.label}</span>
      {badgeFor(n.id) > 0 && <span className={cx('s-item__badge', n.id === 'messages' && 'hot')}>{badgeFor(n.id)}</span>}
      {children}
    </button>
  );

  return (
    <>
      {/* ---------- collapsed rail ---------- */}
      <aside className={cx('glass rail', open && 'rail--hidden')} aria-label="메뉴">
        <div className="rail__logo">
          <span className="logo-mark">H</span>
          <button className="rail__expand" onClick={() => openSheet()} aria-label="메뉴 펼치기"><LuChevronRight /></button>
        </div>
        <button className="rail__btn" onClick={() => openSheet(true)} aria-label="메뉴 검색" {...railHandlers('검색')}><LuSearch /></button>
        <span className="rail__sep" />
        {NAV.filter((n) => n.section !== 'system').map((n, i) => (
          <button
            key={n.id}
            className={cx('rail__btn', page === n.id && 'active', i === 4 && 'gap')}
            onClick={() => pick(n.id)}
            aria-label={n.label}
            {...railHandlers(n.label)}
          >
            <n.icon />
            {badgeFor(n.id) > 0 && <span className="rail__badge">{badgeFor(n.id)}</span>}
          </button>
        ))}
        <span className="rail__sep" />
        <button className={cx('rail__btn', page === 'settings' && 'active')} onClick={() => pick('settings')} aria-label="설정" {...railHandlers('설정')}>
          <SettingsIcon />
        </button>
        <button className="rail__btn" onClick={() => setNight(!night)} aria-label="야간 모드" {...railHandlers(night ? '주간 모드' : '야간 모드')}>
          {night ? <LuMoon /> : <LuSun />}
        </button>
        <span className="rail__spacer" />
        <button className="rail__me" onClick={() => openSheet()} aria-label="내 계정">
          <Avatar name={ME.name} dept={ME.dept} size={34} />
          <span className="online" />
        </button>
      </aside>

      {tip && !open && <div className="glass rail-tip" style={{ top: tip.y }}>{tip.label}</div>}

      {/* ---------- expanded panel ---------- */}
      <div className={cx('scrim', open && 'show')} onClick={close} aria-hidden />
      <aside className={cx('glass sheet', open && 'open')} aria-hidden={!open} aria-label="전체 메뉴">
        <div className="sheet__head">
          <span className="logo-mark">H</span>
          <div className="sheet__title">
            <strong>Glass HR</strong>
            <span>인사 워크스페이스</span>
          </div>
          <button className="sheet__collapse" onClick={close} aria-label="메뉴 접기"><LuChevronLeft /></button>
        </div>

        <label className="sheet__search">
          <LuSearch />
          <input ref={searchRef} value={q} onChange={(e) => setQ(e.target.value)} placeholder="메뉴 검색" tabIndex={open ? 0 : -1} />
          <kbd><LuCommand />K</kbd>
        </label>

        <div className="sheet__list" ref={listRef} onPointerLeave={() => setHover(null)}>
          {hl && <span className="s-hl" style={{ transform: `translateY(${hl.top}px)`, height: hl.height }} />}

          {section('main').length > 0 && <p className="s-section">메인</p>}
          {section('main').map((n) => item(n))}

          {section('workspace').length > 0 && <p className="s-section">워크스페이스</p>}
          {section('workspace').map((n) =>
            n.id === 'org' ? (
              <div key={n.id}>
                {item(n, <LuChevronDown className={cx('s-item__chev', orgOpen && 'up')} />)}
                <div className={cx('s-sub', (orgOpen || q) && 'open')}>
                  <div className="s-sub__inner">
                  <button data-key="org" className={cx('s-subitem', activeKey === 'org' && 'active')} onPointerEnter={(e) => e.pointerType === 'mouse' && setHover('org')} onPointerDown={() => setHover('org')} onClick={() => pick('org')}>
                    <span className="s-dot" style={{ background: '#fff' }} /> 전체 조직도
                  </button>
                  {DEPTS.filter((d) => !q || match(d.name) || match(n.label)).map((d) => (
                    <button
                      key={d.id}
                      data-key={`org:${d.id}`}
                      className={cx('s-subitem', activeKey === `org:${d.id}` && 'active')}
                      onPointerEnter={(e) => e.pointerType === 'mouse' && setHover(`org:${d.id}`)}
                      onPointerDown={() => setHover(`org:${d.id}`)}
                      onClick={() => pickDept(d.id)}
                    >
                      <span className="s-dot" style={{ background: d.color }} /> {d.name}
                    </button>
                  ))}
                  </div>
                </div>
              </div>
            ) : (
              item(n)
            ),
          )}

          {section('system').map((n) => item(n))}
          {items.length === 0 && <p className="s-empty">'{q}'에 맞는 메뉴가 없어요</p>}
        </div>

        <div className="sheet__foot">
          <div className="s-night">
            {night ? <LuMoon /> : <LuSun />}
            <span>야간 모드</span>
            <Toggle checked={night} onChange={setNight} label="야간 모드" />
          </div>
          <div className="s-me">
            <Avatar name={ME.name} dept={ME.dept} size={36} />
            <div>
              <strong>{ME.name}</strong>
              <span>{ME.email}</span>
            </div>
            <button onClick={() => logout(false)} aria-label="로그아웃"><LuLogOut /></button>
          </div>
        </div>
      </aside>
    </>
  );
}
