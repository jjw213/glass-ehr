/**
 * CursorBuilder — a swarm of coloured cursors that "builds" the UI.
 *
 * Markup contract (see components/intro/BuildText.tsx):
 *  - data-build="block": element is hidden until a cursor drags a box over it.
 *  - data-build="type":  element contains [data-typed], [data-caret], [data-ghost]
 *                        spans; the cursor types the text in character by character.
 *
 * Everything (cursors, selection boxes, guides) is drawn on one fixed <canvas>.
 */

type Shape = 'arrow' | 'plus' | 'ibeam';
type Phase = 'wait' | 'approach' | 'drag' | 'hold' | 'fade' | 'done';

interface Cursor {
  x: number; y: number; vx: number; vy: number; tx: number; ty: number;
  color: string; shape: Shape; base: Shape; job: Job | null;
  trail: Array<[number, number]>; wanderAt: number;
}

interface Job {
  el: HTMLElement; kind: 'type' | 'block'; phase: Phase; startAt: number;
  c: Cursor | null; t0: number; n: number; dur: number; text: string;
  typed?: HTMLElement; ghost?: HTMLElement; caret?: HTMLElement;
}

interface Guide { kind: 'h' | 'v'; el: HTMLElement; color: string; born: number; life: number }
interface Spark { x: number; y: number; vx: number; vy: number; born: number; life: number; color: string }

export interface CursorBuilderOptions {
  /** number of cursors in the swarm */
  count?: number;
  /** delay between consecutive elements (ms) */
  stagger?: number;
  /** delay before the first element starts (ms) */
  delay?: number;
  /** fired once every element is built */
  onComplete?: () => void;
}

const COLORS = ['#ff4d4d', '#ffcc00', '#36d1ff', '#4ade80', '#ff6bd6', '#a78bfa', '#ffffff', '#ff9f43', '#60a5fa', '#f472b6'];
const BLUE = '#2f6bff';
const rand = (a: number, b: number) => a + Math.random() * (b - a);
const ease = (t: number) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);

export class CursorBuilder {
  private ctx: CanvasRenderingContext2D;
  private W = 0;
  private H = 0;
  private cursors: Cursor[] = [];
  private jobs: Job[] = [];
  private guides: Guide[] = [];
  private sparks: Spark[] = [];
  private raf = 0;
  private state: 'idle' | 'building' | 'exiting' | 'stopped' = 'idle';
  private exitAt = 0;
  private completed = false;
  private readonly opts: Required<CursorBuilderOptions>;

  constructor(private canvas: HTMLCanvasElement, private root: HTMLElement, opts: CursorBuilderOptions = {}) {
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('2D canvas not supported');
    this.ctx = ctx;
    this.opts = {
      count: opts.count ?? (window.innerWidth < 700 ? 12 : 20),
      stagger: opts.stagger ?? 120,
      delay: opts.delay ?? 500,
      onComplete: opts.onComplete ?? (() => {}),
    };
    this.resize = this.resize.bind(this);
    this.tick = this.tick.bind(this);
  }

  /* ---------- public API ---------- */

  start() {
    window.addEventListener('resize', this.resize);
    this.resize();
    for (let i = 0; i < this.opts.count; i++) {
      const shape: Shape = i % 5 === 4 ? 'plus' : 'arrow';
      const c: Cursor = { x: 0, y: 0, vx: 0, vy: 0, tx: 0, ty: 0, color: COLORS[i % COLORS.length], shape, base: shape, job: null, trail: [], wanderAt: 0 };
      this.spawnOffscreen(c);
      this.cursors.push(c);
    }
    const now = performance.now();
    const els = Array.from(this.root.querySelectorAll<HTMLElement>('[data-build]'));
    els.forEach((el, i) => {
      el.classList.remove('built');
      const kind = el.dataset.build === 'type' ? 'type' : 'block';
      const job: Job = { el, kind, phase: 'wait', startAt: now + this.opts.delay + i * this.opts.stagger, c: null, t0: 0, n: 0, dur: 0, text: el.dataset.text ?? '' };
      if (kind === 'type') {
        job.typed = el.querySelector<HTMLElement>('[data-typed]') ?? undefined;
        job.ghost = el.querySelector<HTMLElement>('[data-ghost]') ?? undefined;
        job.caret = el.querySelector<HTMLElement>('[data-caret]') ?? undefined;
        this.setTyped(job, 0);
      }
      this.jobs.push(job);
    });
    this.state = 'building';
    this.raf = requestAnimationFrame(this.tick);
  }

  /** Finish instantly (skip button / reduced motion). */
  skip() {
    for (const j of this.jobs) this.finishJob(j);
    this.jobs = [];
    this.complete();
  }

  destroy() {
    cancelAnimationFrame(this.raf);
    window.removeEventListener('resize', this.resize);
    this.state = 'stopped';
    this.ctx.clearRect(0, 0, this.W, this.H);
  }

  /** Reveal every buildable element under root without animation. */
  static revealAll(root: HTMLElement) {
    root.querySelectorAll<HTMLElement>('[data-build]').forEach((el) => {
      el.classList.add('built');
      const typed = el.querySelector<HTMLElement>('[data-typed]');
      const ghost = el.querySelector<HTMLElement>('[data-ghost]');
      if (typed && ghost) { typed.textContent = el.dataset.text ?? ''; ghost.textContent = ''; }
    });
  }

  /* ---------- internals ---------- */

  private resize() {
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    this.W = window.innerWidth;
    this.H = window.innerHeight;
    this.canvas.width = this.W * dpr;
    this.canvas.height = this.H * dpr;
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }

  private spawnOffscreen(c: Cursor) {
    const side = Math.floor(rand(0, 4));
    c.x = side === 0 ? -40 : side === 1 ? this.W + 40 : rand(0, this.W);
    c.y = side === 2 ? -40 : side === 3 ? this.H + 40 : rand(0, this.H);
    c.tx = rand(0, this.W); c.ty = rand(0, this.H);
  }

  private setTyped(j: Job, n: number) {
    if (!j.typed || !j.ghost) return;
    j.typed.textContent = j.text.slice(0, n);
    j.ghost.textContent = j.text.slice(n);
  }

  private finishJob(j: Job) {
    j.el.classList.add('built');
    if (j.kind === 'type') { this.setTyped(j, j.text.length); j.caret?.classList.remove('on'); }
    if (j.c) { j.c.job = null; j.c.shape = j.c.base; }
  }

  private complete() {
    if (this.completed) return;
    this.completed = true;
    this.state = 'exiting';
    this.exitAt = performance.now() + 1300;
    // fly every cursor out through the nearest edge
    for (const c of this.cursors) {
      c.job = null; c.shape = c.base;
      const dl = c.x, dr = this.W - c.x, dt = c.y, db = this.H - c.y;
      const m = Math.min(dl, dr, dt, db);
      if (m === dl) { c.tx = -80; c.ty = c.y; } else if (m === dr) { c.tx = this.W + 80; c.ty = c.y; }
      else if (m === dt) { c.tx = c.x; c.ty = -80; } else { c.tx = c.x; c.ty = this.H + 80; }
    }
    this.opts.onComplete();
  }

  private freeCursorNear(x: number, y: number) {
    let best: Cursor | null = null, bd = Infinity;
    for (const c of this.cursors) {
      if (c.job) continue;
      const d = (c.x - x) ** 2 + (c.y - y) ** 2;
      if (d < bd) { bd = d; best = c; }
    }
    return best;
  }

  private tick(now: number) {
    if (this.state === 'stopped') return;
    const { ctx, W, H } = this;
    ctx.clearRect(0, 0, W, H);

    this.drawGuides(now);
    if (this.state === 'building') this.runJobs(now);
    this.drawSparks(now);
    this.moveCursors(now);

    if (this.state === 'exiting' && now > this.exitAt) {
      this.destroy();
      return;
    }
    this.raf = requestAnimationFrame(this.tick);
  }

  private runJobs(now: number) {
    for (const j of this.jobs) {
      const r = j.el.getBoundingClientRect();
      if (j.phase === 'wait') {
        if (now < j.startAt) continue;
        const c = this.freeCursorNear(r.left, r.top);
        if (!c) continue;
        j.c = c; c.job = j; j.phase = 'approach';
      }
      const c = j.c!;
      if (j.phase === 'approach') {
        c.tx = r.left; c.ty = r.top;
        if (Math.hypot(c.x - r.left, c.y - r.top) < 10) { j.phase = 'drag'; j.t0 = now; }
      }
      if (j.phase === 'drag') {
        const dur = 260 + Math.min(520, (r.width + r.height) * 0.45);
        const p = ease(Math.min(1, (now - j.t0) / dur));
        c.tx = r.left + r.width * p; c.ty = r.top + r.height * p;
        this.drawBox(r.left, r.top, r.width * p, r.height * p, 1, j.kind === 'block' ? 1 : 0);
        if (p >= 1) {
          j.phase = 'hold'; j.t0 = now;
          this.addGuides(j.el, now);
          this.addSparks(r.right, r.bottom, c.color, now);
          if (j.kind === 'block') j.el.classList.add('built');
          else {
            c.shape = 'ibeam';
            j.caret?.classList.add('on');
            j.dur = Math.min(1300, 120 + j.text.length * 26);
          }
        }
      } else if (j.phase === 'hold') {
        let done: boolean;
        if (j.kind === 'type') {
          const len = j.text.length;
          const n = Math.min(len, Math.ceil((len * (now - j.t0)) / j.dur));
          if (n !== j.n) { j.n = n; this.setTyped(j, n); }
          if (j.caret) { const cr = j.caret.getBoundingClientRect(); c.tx = cr.left + 2; c.ty = cr.top + cr.height / 2; }
          done = n >= len && now - j.t0 > j.dur + 300;
          this.drawBox(r.left, r.top, r.width, r.height, 1, 0);
        } else {
          this.drawBox(r.left, r.top, r.width, r.height, 1, Math.max(0, 1 - (now - j.t0) / 450));
          done = now - j.t0 > 600;
        }
        if (done) {
          j.phase = 'fade'; j.t0 = now;
          j.el.classList.add('built');
          j.caret?.classList.remove('on');
          c.shape = c.base; c.job = null; c.wanderAt = 0;
        }
      } else if (j.phase === 'fade') {
        const a = 1 - (now - j.t0) / 350;
        if (a <= 0) j.phase = 'done';
        else this.drawBox(r.left, r.top, r.width, r.height, a, 0);
      }
    }
    this.jobs = this.jobs.filter((j) => j.phase !== 'done');
    if (this.jobs.length === 0) this.complete();
  }

  private moveCursors(now: number) {
    for (const c of this.cursors) {
      if (this.state === 'building' && !c.job && now > c.wanderAt) {
        c.tx = rand(this.W * 0.05, this.W * 0.95);
        c.ty = rand(this.H * 0.08, this.H * 0.95);
        c.wanderAt = now + rand(1200, 4000);
      }
      const busy = !!c.job || this.state === 'exiting';
      const k = busy ? 0.09 : 0.012, d = busy ? 0.7 : 0.9;
      c.vx = (c.vx + (c.tx - c.x) * k) * d;
      c.vy = (c.vy + (c.ty - c.y) * k) * d;
      c.x += c.vx; c.y += c.vy;
      c.trail.push([c.x, c.y]);
      if (c.trail.length > 9) c.trail.shift();
      this.drawTrail(c);
      this.drawCursor(c);
    }
  }

  private addGuides(el: HTMLElement, now: number) {
    this.guides.push({ kind: 'h', el, color: Math.random() < 0.55 ? '#ff3b3b' : BLUE, born: now, life: 900 });
    if (Math.random() < 0.6) this.guides.push({ kind: 'v', el, color: '#ff3b3b', born: now, life: 1100 });
  }

  private addSparks(x: number, y: number, color: string, now: number) {
    for (let i = 0; i < 5; i++) {
      this.sparks.push({ x, y, vx: rand(-1.6, 1.6), vy: rand(-1.6, 1.6), born: now, life: rand(500, 900), color });
    }
  }

  /* ---------- drawing ---------- */

  private drawGuides(now: number) {
    const { ctx, W, H } = this;
    this.guides = this.guides.filter((g) => {
      const k = (now - g.born) / g.life;
      if (k >= 1) return false;
      const r = g.el.getBoundingClientRect();
      const v = g.kind === 'h' ? r.top : r.left;
      ctx.globalAlpha = (1 - k) * 0.9;
      ctx.strokeStyle = g.color; ctx.lineWidth = 1;
      ctx.beginPath();
      if (g.kind === 'h') { ctx.moveTo(0, v + 0.5); ctx.lineTo(W, v + 0.5); }
      else { ctx.moveTo(v + 0.5, 0); ctx.lineTo(v + 0.5, H); }
      ctx.stroke();
      ctx.globalAlpha = 1;
      return true;
    });
  }

  private drawSparks(now: number) {
    const { ctx } = this;
    this.sparks = this.sparks.filter((s) => {
      const k = (now - s.born) / s.life;
      if (k >= 1) return false;
      s.x += s.vx; s.y += s.vy;
      ctx.globalAlpha = 1 - k; ctx.strokeStyle = s.color; ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(s.x - 4, s.y); ctx.lineTo(s.x + 4, s.y);
      ctx.moveTo(s.x, s.y - 4); ctx.lineTo(s.x, s.y + 4);
      ctx.stroke();
      ctx.globalAlpha = 1;
      return true;
    });
  }

  private drawTrail(c: Cursor) {
    const { ctx } = this;
    const t = c.trail;
    for (let i = 1; i < t.length; i++) {
      const a = i / t.length;
      ctx.strokeStyle = c.color; ctx.globalAlpha = a * 0.55; ctx.lineWidth = 2.2 * a;
      ctx.beginPath(); ctx.moveTo(t[i - 1][0], t[i - 1][1]); ctx.lineTo(t[i][0], t[i][1]); ctx.stroke();
    }
    ctx.globalAlpha = 1;
  }

  private drawCursor(c: Cursor) {
    const { ctx } = this;
    ctx.save();
    ctx.translate(c.x, c.y);
    ctx.lineWidth = 1; ctx.strokeStyle = 'rgba(0,0,0,.85)'; ctx.fillStyle = c.color;
    if (c.shape === 'arrow') {
      ctx.beginPath();
      ctx.moveTo(0, 0); ctx.lineTo(0, 16); ctx.lineTo(4, 12.5); ctx.lineTo(7, 18.5);
      ctx.lineTo(9.5, 17.4); ctx.lineTo(6.6, 11.4); ctx.lineTo(11.5, 11.4); ctx.closePath();
      ctx.fill(); ctx.stroke();
    } else if (c.shape === 'plus') {
      ctx.strokeStyle = c.color; ctx.lineWidth = 2;
      ctx.beginPath(); ctx.moveTo(-7, 0); ctx.lineTo(7, 0); ctx.moveTo(0, -7); ctx.lineTo(0, 7); ctx.stroke();
    } else {
      ctx.strokeStyle = c.color; ctx.lineWidth = 1.6;
      ctx.beginPath();
      ctx.moveTo(-3, -9); ctx.lineTo(3, -9); ctx.moveTo(0, -9); ctx.lineTo(0, 9); ctx.moveTo(-3, 9); ctx.lineTo(3, 9);
      ctx.stroke();
    }
    ctx.restore();
  }

  private drawBox(x: number, y: number, w: number, h: number, alpha: number, fill: number) {
    if (w < 1 || h < 1) return;
    const { ctx } = this;
    ctx.globalAlpha = alpha;
    if (fill) { ctx.fillStyle = `rgba(120,128,145,${0.55 * fill})`; ctx.fillRect(x, y, w, h); }
    ctx.strokeStyle = BLUE; ctx.lineWidth = 1.5; ctx.strokeRect(x + 0.5, y + 0.5, w, h);
    const hs = 6;
    for (const [px, py] of [[x, y], [x + w, y], [x, y + h], [x + w, y + h]]) {
      ctx.fillStyle = '#fff'; ctx.fillRect(px - hs / 2, py - hs / 2, hs, hs);
      ctx.strokeStyle = BLUE; ctx.lineWidth = 1; ctx.strokeRect(px - hs / 2 + 0.5, py - hs / 2 + 0.5, hs - 1, hs - 1);
    }
    const label = `${Math.round(w)} × ${Math.round(h)}`;
    ctx.font = '600 10px ui-monospace, Menlo, monospace';
    const tw = ctx.measureText(label).width + 10;
    const lx = x + w / 2 - tw / 2, ly = y + h + 8;
    ctx.fillStyle = BLUE;
    ctx.beginPath(); ctx.roundRect(lx, ly, tw, 16, 3); ctx.fill();
    ctx.fillStyle = '#fff'; ctx.textBaseline = 'middle'; ctx.fillText(label, lx + 5, ly + 8.5);
    ctx.globalAlpha = 1;
  }
}
