import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, type PointerEvent as RPointerEvent } from 'react';
import { LuChevronRight, LuChevronLeft, LuChevronDown, LuSearch, LuSun, LuMoon, LuLogOut, LuCommand } from 'react-icons/lu';
import { moduleOf, navById, navGroupsFor, type ModuleId, type NavGroup, type NavItem } from '../../nav';

const SettingsIcon = navById('settings').icon;
import { useData, useStore } from '../../state/store';
import { useSession } from '../../state/session';
import { Avatar, Toggle, cx } from '../ui';

/**
 * Glass sidebar for phones (video 3):
 *  - collapsed: floating icon rail with badges and tooltips (long-press on touch)
 *  - expanded: panel that morphs out of the rail, with search, 대메뉴 → 중메뉴 accordions,
 *    a sliding highlight pill, night mode and profile.
 *  - 대메뉴 아래 중메뉴는 역할별로 거른다(nav.ts navGroupsFor). 중메뉴가 하나뿐인 대메뉴는 바로 그 화면으로 간다.
 * Open with the chevron, the search icon, the avatar or a swipe from the left edge.
 */
export function MobileSidebar() {
  const { page, go, pendingCount, unreadCount, night, setNight } = useStore();
  const { logout } = useSession();
  const { me } = useData();
  const ME = me;
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState('');
  /** 펼쳐 둔 대메뉴. 처음에는 지금 화면이 속한 대메뉴만 펼친다. */
  const [expanded, setExpanded] = useState<Set<ModuleId>>(() => new Set([moduleOf(page)]));
  const [hover, setHover] = useState<string | null>(null);
  const [hl, setHl] = useState<{ top: number; height: number } | null>(null);
  const [tip, setTip] = useState<{ label: string; y: number } | null>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  const tipTimer = useRef<number>(0);

  const badgeFor = (id: string) => (id === 'approvals' ? pendingCount : id === 'messages' ? unreadCount : 0);
  const groupBadge = (g: NavGroup) => g.items.reduce((sum, n) => sum + badgeFor(n.id), 0);
  const groups = useMemo(() => navGroupsFor(me.role), [me.role]);
  const activeKey: string = page;

  /* ----- open / close ----- */
  const openSheet = useCallback((focusSearch = false, mod?: ModuleId) => {
    setOpen(true);
    setTip(null);
    // 지금 화면의 대메뉴(그리고 레일에서 누른 대메뉴)는 펼쳐서 연다
    setExpanded((prev) => new Set([...prev, moduleOf(page), ...(mod ? [mod] : [])]));
    if (focusSearch) setTimeout(() => searchRef.current?.focus(), 380);
  }, [page]);
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
    // 접힌 대메뉴의 중메뉴는 보이지 않으므로 대메뉴 머리를 강조한다
    if (!key.startsWith('mod:') && !q) {
      const g = groups.find((x) => x.items.some((n) => n.id === key));
      if (g && g.items.length > 1 && !expanded.has(g.module.id)) key = `mod:${g.module.id}`;
    }
    const el = list.querySelector<HTMLElement>(`[data-key="${key}"]`);
    setHl(el ? { top: el.offsetTop, height: el.offsetHeight } : null);
  }, [hover, activeKey, expanded, q, groups]);
  useLayoutEffect(() => {
    measure();
    // 대메뉴가 펼쳐지고 접히는 애니메이션이 끝난 뒤 한 번 더 잰다
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

  const pick = (id: NavItem['id']) => { go(id); close(); };
  const toggleModule = (id: ModuleId) => setExpanded((prev) => {
    const next = new Set(prev);
    if (next.has(id)) next.delete(id); else next.add(id);
    return next;
  });

  const match = (s: string) => s.toLowerCase().includes(q.trim().toLowerCase());
  // 검색: 대메뉴 이름이 맞으면 그 중메뉴 전부, 아니면 이름이 맞는 중메뉴만
  const shown: NavGroup[] = q.trim()
    ? groups
        .map((g) => ({ ...g, items: match(g.module.label) ? g.items : g.items.filter((n) => match(n.label)) }))
        .filter((g) => g.items.length > 0)
    : groups;

  const hoverKey = (key: string) => ({
    onPointerEnter: (e: RPointerEvent<HTMLElement>) => { if (e.pointerType === 'mouse') setHover(key); },
    onPointerDown: () => setHover(key),
  });

  const renderGroup = (g: NavGroup) => {
    const all = groups.find((x) => x.module.id === g.module.id)!;
    const single = all.items.length === 1;
    const within = g.items.some((n) => n.id === page);
    if (single) {
      const n = g.items[0];
      return (
        <button
          key={g.module.id}
          data-key={n.id}
          className={cx('s-item', activeKey === n.id && 'active')}
          {...hoverKey(n.id)}
          onClick={() => pick(n.id)}
          aria-current={page === n.id ? 'page' : undefined}
        >
          <g.module.icon className="s-item__icon" />
          <span className="s-item__label">{g.module.label}</span>
          {badgeFor(n.id) > 0 && <span className={cx('s-item__badge', n.id === 'messages' && 'hot')}>{badgeFor(n.id)}</span>}
        </button>
      );
    }
    const isOpen = expanded.has(g.module.id) || !!q.trim();
    const count = groupBadge(g);
    return (
      <div key={g.module.id}>
        <button
          data-key={`mod:${g.module.id}`}
          className={cx('s-item', within && 'within')}
          {...hoverKey(`mod:${g.module.id}`)}
          onClick={() => toggleModule(g.module.id)}
          aria-expanded={isOpen}
        >
          <g.module.icon className="s-item__icon" />
          <span className="s-item__label">{g.module.label}</span>
          {!isOpen && count > 0 && <span className="s-item__badge">{count}</span>}
          <LuChevronDown className={cx('s-item__chev', isOpen && 'up')} />
        </button>
        <div className={cx('s-sub', isOpen && 'open')}>
          <div className="s-sub__inner">
            {g.items.map((n) => (
              <button
                key={n.id}
                data-key={n.id}
                className={cx('s-subitem', activeKey === n.id && 'active')}
                {...hoverKey(n.id)}
                onClick={() => pick(n.id)}
                tabIndex={isOpen && open ? 0 : -1}
                aria-current={page === n.id ? 'page' : undefined}
              >
                <n.icon className="s-subitem__icon" />
                <span className="s-item__label">{n.label}</span>
                {badgeFor(n.id) > 0 && <span className="s-item__badge">{badgeFor(n.id)}</span>}
              </button>
            ))}
          </div>
        </div>
      </div>
    );
  };

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
        {groups.filter((g) => g.module.id !== 'settings').map((g) => {
          const single = g.items.length === 1;
          const count = groupBadge(g);
          return (
            <button
              key={g.module.id}
              className={cx('rail__btn', g.items.some((n) => n.id === page) && 'active', g.module.id === 'system' && 'gap')}
              onClick={() => (single ? pick(g.items[0].id) : openSheet(false, g.module.id))}
              aria-label={g.module.label}
              {...railHandlers(g.module.label)}
            >
              <g.module.icon />
              {count > 0 && <span className="rail__badge">{count}</span>}
            </button>
          );
        })}
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

          {shown.map(renderGroup)}
          {shown.length === 0 && <p className="s-empty">'{q}'에 맞는 메뉴가 없어요</p>}
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
              <span>{ME.loginId}</span>
            </div>
            <button onClick={() => logout(false)} aria-label="로그아웃"><LuLogOut /></button>
          </div>
        </div>
      </aside>
    </>
  );
}
