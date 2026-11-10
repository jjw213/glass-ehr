import { useEffect, useRef, useState, type FormEvent } from 'react';
import { LuEye, LuEyeOff, LuLock, LuUser, LuArrowRight, LuLoaderCircle } from 'react-icons/lu';
import { CursorBuilder } from '../../lib/cursorBuilder';
import { BuildText } from '../intro/BuildText';
import { cx } from '../ui';
import { usePrefersReducedMotion } from '../../hooks/useMediaQuery';
import { useStore } from '../../state/store';
import { SHOW_DEMO_HINT } from '../../api';

interface Props {
  /** play the cursor-swarm build intro (first visit) */
  intro: boolean;
  /** called after the card has animated out */
  onLogin: () => void;
}

export function LoginScreen({ intro, onLogin }: Props) {
  const rootRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const builderRef = useRef<CursorBuilder | null>(null);
  const idRef = useRef<HTMLInputElement>(null);
  const reduced = usePrefersReducedMotion();
  const { login } = useStore();

  const [built, setBuilt] = useState(!intro);
  const [id, setId] = useState('');
  const [pw, setPw] = useState('');
  const [showPw, setShowPw] = useState(false);
  const [remember, setRemember] = useState(true);
  const [error, setError] = useState('');
  const formRef = useRef<HTMLFormElement>(null);
  const [loading, setLoading] = useState(false);
  const [leaving, setLeaving] = useState(false);

  // run the cursor build intro
  useEffect(() => {
    const root = rootRef.current, canvas = canvasRef.current;
    if (!root || !canvas) return;
    if (!intro || reduced) {
      CursorBuilder.revealAll(root);
      setBuilt(true);
      return;
    }
    const b = new CursorBuilder(canvas, root, { onComplete: () => setBuilt(true) });
    builderRef.current = b;
    b.start();
    return () => { b.destroy(); builderRef.current = null; };
  }, [intro, reduced]);

  useEffect(() => {
    if (built) idRef.current?.focus({ preventScroll: true });
  }, [built]);

  const skip = () => builderRef.current?.skip();

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!built) { skip(); return; }
    if (loading) return;
    if (!id.trim() || !pw) { fail('아이디와 비밀번호를 입력해 주세요.'); return; }
    setError('');
    setLoading(true);
    try {
      await login(id.trim(), pw);       // server checks the password and loads the data
    } catch (err) {
      setLoading(false);
      fail(err instanceof Error ? err.message : '로그인에 실패했어요');
      return;
    }
    setLeaving(true);
    setTimeout(onLogin, 620);
  };

  const fail = (msg: string) => {
    setError(msg);
    formRef.current?.animate(
      [{ transform: 'translateX(0)' }, { transform: 'translateX(-10px)' }, { transform: 'translateX(8px)' }, { transform: 'translateX(-5px)' }, { transform: 'translateX(0)' }],
      { duration: 380, easing: 'ease-out' },
    );
  };

  return (
    <div ref={rootRef} className={cx('login', built && 'intro-done', leaving && 'leaving')}>
      <canvas ref={canvasRef} className="intro-canvas" />

      <div className="login__brand">
        <span className="logo-mark" data-build="block">H</span>
        <BuildText text="Glass HR" className="login__brand-name" />
      </div>

      <form
        ref={formRef}
        className="glass login-card"
        data-build="block"
        onSubmit={submit}
        noValidate
      >
        <div className="login-card__head">
          <span className="login-card__mark" data-build="block"><LuLock /></span>
          <h1><BuildText text="다시 오신 걸 환영해요" /></h1>
          <p className="muted"><BuildText text="인사 시스템에 로그인하세요" /></p>
        </div>

        <label className="field">
          <span className="field__label"><BuildText text="아이디" /></span>
          <span className="input-wrap" data-build="block">
            <LuUser className="input-wrap__icon" />
            <input ref={idRef} value={id} onChange={(e) => setId(e.target.value)} autoComplete="username" placeholder="사번 또는 아이디" disabled={!built || loading} />
          </span>
        </label>

        <label className="field">
          <span className="field__label"><BuildText text="비밀번호" /></span>
          <span className="input-wrap" data-build="block">
            <LuLock className="input-wrap__icon" />
            <input type={showPw ? 'text' : 'password'} value={pw} onChange={(e) => setPw(e.target.value)} autoComplete="current-password" placeholder="••••••••" disabled={!built || loading} />
            <button type="button" className="input-wrap__eye" onClick={() => setShowPw((v) => !v)} aria-label={showPw ? '비밀번호 숨기기' : '비밀번호 보기'}>
              {showPw ? <LuEyeOff /> : <LuEye />}
            </button>
          </span>
        </label>

        <div className="login-card__row">
          <label className="check" data-build="block">
            <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />
            <span>로그인 상태 유지</span>
          </label>
          <a href="#" className="link" onClick={(e) => e.preventDefault()}><BuildText text="비밀번호 찾기" /></a>
        </div>

        <p className={cx('login-card__error', error && 'show')} role="alert">{error}</p>

        <button className="btn btn--primary btn--block" data-build="block" disabled={loading}>
          {loading ? <LuLoaderCircle className="spin" /> : <>로그인 <LuArrowRight /></>}
        </button>

        {SHOW_DEMO_HINT && <p className="login-card__hint muted sm"><BuildText text="데모 계정  ·  hr / 1234" /></p>}
      </form>

      {!built && (
        <button type="button" className="glass skip" onClick={skip}>건너뛰기</button>
      )}
    </div>
  );
}
