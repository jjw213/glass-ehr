import { useCallback, useEffect, useState } from 'react';
import { Scene } from './components/Scene';
import { LoginScreen } from './components/auth/LoginScreen';
import { AppShell } from './components/layout/AppShell';
import { StoreProvider, useStore } from './state/store';
import { SessionCtx } from './state/session';

type Phase = 'login' | 'app';

function Root() {
  const { scene, night, data, signOut, onSessionLost } = useStore();
  const [phase, setPhase] = useState<Phase>('login');
  const [intro, setIntro] = useState(true);   // cursor intro only on first entry
  const [loginKey, setLoginKey] = useState(0);

  const toLogin = useCallback((replayIntro: boolean) => {
    setIntro(replayIntro);
    setLoginKey((k) => k + 1);
    setPhase('login');
  }, []);

  const logout = useCallback((replayIntro = false) => {
    void signOut();
    toLogin(replayIntro);
  }, [signOut, toLogin]);

  useEffect(() => onSessionLost(() => toLogin(false)), [onSessionLost, toLogin]);

  const inApp = phase === 'app' && !!data;

  return (
    <SessionCtx.Provider value={{ logout }}>
      <div className={night ? 'theme night' : 'theme'}>
        <Scene mode={inApp ? 'app' : 'login'} variant={night ? 'night' : scene} />
        {inApp ? (
          <>
            <div className="sweep" aria-hidden />
            <AppShell />
          </>
        ) : (
          <LoginScreen key={loginKey} intro={intro} onLogin={() => setPhase('app')} />
        )}
      </div>
    </SessionCtx.Provider>
  );
}

export default function App() {
  return (
    <StoreProvider>
      <Root />
    </StoreProvider>
  );
}
