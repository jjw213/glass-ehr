import { useEffect, useState, type CSSProperties, type ReactNode } from 'react';
import { useStore } from '../../state/store';

/** CSS custom properties helper: vars({ i: 2 }) → { '--i': 2 } */
export const vars = (o: Record<string, string | number>) =>
  Object.fromEntries(Object.entries(o).map(([k, v]) => ['--' + k, v])) as CSSProperties;

export const cx = (...c: Array<string | false | null | undefined>) => c.filter(Boolean).join(' ');

/* ---------- Glass card (rises in with a stagger index) ---------- */
export function Card({ i = 0, className, children, onClick }: { i?: number; className?: string; children: ReactNode; onClick?: () => void }) {
  return (
    <section className={cx('glass card rise', onClick && 'card--click', className)} style={vars({ i })} onClick={onClick}>
      {children}
    </section>
  );
}

export function CardHead({ title, sub, right }: { title: ReactNode; sub?: ReactNode; right?: ReactNode }) {
  return (
    <div className="card-head">
      <div>
        <h3>{title}</h3>
        {sub && <p className="muted sm">{sub}</p>}
      </div>
      {right}
    </div>
  );
}

export function PageHead({ title, sub, right }: { title: ReactNode; sub?: ReactNode; right?: ReactNode }) {
  return (
    <header className="page-head rise" style={vars({ i: 0 })}>
      <div>
        <h1>{title}</h1>
        {sub && <p className="muted">{sub}</p>}
      </div>
      {right && <div className="page-head__right">{right}</div>}
    </header>
  );
}

/* ---------- Avatar ---------- */
export function Avatar({ name, dept, size = 36, ring }: { name: string; dept?: string; size?: number; ring?: boolean }) {
  const store = useStore();
  const color = (dept && store.dept(dept)?.color) || '#ff7a2f';
  return (
    <span
      className={cx('avatar', ring && 'avatar--ring')}
      style={{ width: size, height: size, fontSize: size * 0.36, background: `linear-gradient(140deg, ${color}, ${color}99)` }}
      aria-hidden
    >
      {name.slice(1, 3)}
    </span>
  );
}

/* ---------- small bits ---------- */
export function Badge({ children, tone = 'neutral' }: { children: ReactNode; tone?: 'neutral' | 'green' | 'red' | 'amber' | 'blue' }) {
  return <span className={`badge badge--${tone}`}>{children}</span>;
}

export function Toggle({ checked, onChange, label }: { checked: boolean; onChange: (v: boolean) => void; label: string }) {
  return (
    <button type="button" role="switch" aria-checked={checked} aria-label={label} className={cx('toggle', checked && 'on')} onClick={() => onChange(!checked)}>
      <span />
    </button>
  );
}

export function Segmented<T extends string>({ options, value, onChange, small }: { options: readonly T[]; value: T; onChange: (v: T) => void; small?: boolean }) {
  const idx = Math.max(0, options.indexOf(value));
  return (
    <div className={cx('seg', small && 'seg--sm')} style={vars({ n: options.length, idx })} role="tablist">
      <span className="seg__thumb" />
      {options.map((o) => (
        <button key={o} type="button" role="tab" aria-selected={o === value} className={cx(o === value && 'on')} onClick={() => onChange(o)}>
          {o}
        </button>
      ))}
    </div>
  );
}

export function Sparkline({ data, color = '#fff', height = 34 }: { data: number[]; color?: string; height?: number }) {
  const w = 100, h = 30;
  const min = Math.min(...data), max = Math.max(...data);
  const pts = data.map((v, i) => [(i / (data.length - 1)) * w, h - ((v - min) / (max - min || 1)) * (h - 4) - 2]);
  const d = pts.map((p, i) => (i ? 'L' : 'M') + p[0].toFixed(1) + ' ' + p[1].toFixed(1)).join(' ');
  return (
    <svg className="spark" viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none" style={{ height }} aria-hidden>
      <path d={`${d} L${w} ${h} L0 ${h}Z`} fill={color} opacity=".12" />
      <path d={d} fill="none" stroke={color} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" vectorEffect="non-scaling-stroke" className="spark__line" />
    </svg>
  );
}

/* ---------- Modal / sheet ---------- */
export function Modal({ open, onClose, title, children }: { open: boolean; onClose: () => void; title: string; children: ReactNode }) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);
  if (!open) return null;
  return (
    <div className="modal" onClick={onClose}>
      <div className="glass modal__panel" role="dialog" aria-modal="true" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <div className="modal__grab" />
        <h3 className="modal__title">{title}</h3>
        {children}
      </div>
    </div>
  );
}

/* ---------- Toasts ---------- */
export function Toasts() {
  const { toasts } = useStore();
  return (
    <div className="toasts" aria-live="polite">
      {toasts.map((t) => (
        <div key={t.id} className="glass toast">{t.text}</div>
      ))}
    </div>
  );
}

/* ---------- live clock ---------- */
export function useNow(intervalMs = 1000) {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs]);
  return now;
}

export const hhmm = (d: Date) => d.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false });

/** local calendar date 'YYYY-MM-DD' (toISOString would give the UTC date) */
export const ymd = (d = new Date()) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

export const won = (n: number) => n.toLocaleString('ko-KR') + '원';
export const sum = (l: { amount: number }[]) => l.reduce((s, x) => s + x.amount, 0);
