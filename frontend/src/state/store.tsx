import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { api, ApiError, type Bootstrap, type Dept, type NewLeave } from '../api';
import { setActorId } from '../api/http';

export type PageId = 'home' | 'attendance' | 'payroll' | 'approvals' | 'messages' | 'people' | 'org' | 'settings' | 'codes' | 'workPatterns' | 'agreements' | 'privacyLogs';
export const PAGE_IDS: PageId[] = ['home', 'attendance', 'payroll', 'approvals', 'messages', 'people', 'org', 'settings', 'codes', 'workPatterns', 'agreements', 'privacyLogs'];
export type SceneId = 'sunset' | 'dawn' | 'night';

interface Toast { id: number; text: string }

interface Store {
  /* session & data (null until signed in) */
  data: Bootstrap | null;
  login: (loginId: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
  /** called when the server says the session is gone */
  onSessionLost: (fn: () => void) => void;
  dept: (id: string) => Dept | undefined;

  /* ui */
  page: PageId; go: (p: PageId) => void;
  night: boolean; setNight: (v: boolean) => void;
  scene: SceneId; setScene: (s: SceneId) => void;
  orgDept: string | null; setOrgDept: (d: string | null) => void;
  toasts: Toast[]; toast: (text: string) => void;

  /* actions → server */
  decide: (id: string, action: 'approve' | 'reject') => Promise<void>;
  addLeave: (l: NewLeave) => Promise<boolean>;
  toggleClock: () => Promise<void>;
  send: (threadId: string, text: string) => Promise<void>;
  markRead: (threadId: string) => void;
  refreshThreads: () => Promise<void>;

  pendingCount: number; unreadCount: number;
}

const Ctx = createContext<Store | null>(null);

const readHash = (): PageId => {
  const h = window.location.hash.slice(1) as PageId;
  return PAGE_IDS.includes(h) ? h : 'home';
};

export function StoreProvider({ children }: { children: ReactNode }) {
  const [data, setData] = useState<Bootstrap | null>(null);
  const [page, setPage] = useState<PageId>(readHash);
  const [night, setNight] = useState(false);
  const [scene, setScene] = useState<SceneId>('sunset');
  const [orgDept, setOrgDept] = useState<string | null>(null);
  const [toasts, setToasts] = useState<Toast[]>([]);
  const lostRef = useRef<() => void>(() => {});

  const go = useCallback((p: PageId) => {
    setPage(p);
    try { if (window.location.hash !== '#' + p) window.location.hash = p; } catch { /* sandboxed frame */ }
    window.scrollTo({ top: 0 });
  }, []);

  useEffect(() => {
    const onHash = () => setPage(readHash());
    window.addEventListener('hashchange', onHash);
    return () => window.removeEventListener('hashchange', onHash);
  }, []);

  const toast = useCallback((text: string) => {
    const id = Date.now() + Math.random();
    setToasts((t) => [...t, { id, text }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 2800);
  }, []);

  /** run a server call; on 401 drop back to login, otherwise show the error */
  const guard = useCallback(async <T,>(fn: () => Promise<T>): Promise<T | undefined> => {
    try { return await fn(); }
    catch (e) {
      if (e instanceof ApiError && e.status === 401) { setActorId(null); setData(null); lostRef.current(); toast('세션이 만료됐어요. 다시 로그인해 주세요'); }
      else toast(e instanceof Error ? e.message : '알 수 없는 오류가 발생했어요');
      return undefined;
    }
  }, [toast]);

  const patch = (p: Partial<Bootstrap>) => setData((d) => (d ? { ...d, ...p } : d));

  const login = useCallback(async (loginId: string, password: string) => {
    await api.login(loginId, password);   // throws ApiError on bad credentials
    const boot = await api.bootstrap();
    // Spring 백엔드 요청에 로그인 아이디를 싣는다(#19). 화면이 그려지기 전에 정해 둬야 첫 요청부터 실린다.
    // 역할·권한은 서버가 DB에서 읽으므로 화면 값은 메뉴 표시에만 쓴다.
    setActorId(boot.me.loginId);
    setData(boot);
  }, []);

  const signOut = useCallback(async () => {
    try { await api.logout(); } catch { /* already gone */ }
    setActorId(null);
    setData(null);
  }, []);

  const decide = useCallback(async (id: string, action: 'approve' | 'reject') => {
    const r = await guard(() => api.decide(id, action));
    if (r) { patch(r); toast(action === 'approve' ? '결재를 승인했어요' : '결재를 반려했어요'); }
  }, [guard, toast]);

  const addLeave = useCallback(async (l: NewLeave) => {
    const r = await guard(() => api.addLeave(l));
    if (r) { patch(r); toast('휴가 신청이 결재로 올라갔어요'); }
    return !!r;
  }, [guard, toast]);

  const toggleClock = useCallback(async () => {
    const was = data?.attendance;
    const r = await guard(() => api.toggleClock());
    if (!r) return;
    patch({ attendance: r });
    toast(!was?.clockIn ? '출근 처리됐어요' : was.clockOut ? '퇴근을 취소했어요' : '퇴근 처리됐어요. 수고하셨어요!');
  }, [data?.attendance, guard, toast]);

  const send = useCallback(async (threadId: string, text: string) => {
    const r = await guard(() => api.send(threadId, text));
    if (r) patch({ threads: r });
  }, [guard]);

  const markRead = useCallback((threadId: string) => {
    setData((d) => (d ? { ...d, threads: d.threads.map((t) => (t.id === threadId ? { ...t, unread: 0 } : t)) } : d));
    void guard(() => api.markRead(threadId));
  }, [guard]);

  const refreshThreads = useCallback(async () => {
    const r = await guard(() => api.threads());
    if (r) patch({ threads: r });
  }, [guard]);

  const deptMap = useMemo(() => new Map((data?.depts ?? []).map((d) => [d.id, d])), [data?.depts]);
  const dept = useCallback((id: string) => deptMap.get(id), [deptMap]);

  const value = useMemo<Store>(() => ({
    data, login, signOut, onSessionLost: (fn) => { lostRef.current = fn; }, dept,
    page, go, night, setNight, scene, setScene, orgDept, setOrgDept, toasts, toast,
    decide, addLeave, toggleClock, send, markRead, refreshThreads,
    pendingCount: data?.approvals.filter((a) => a.canDecide).length ?? 0,
    unreadCount: data?.threads.reduce((s, t) => s + t.unread, 0) ?? 0,
  }), [data, login, signOut, dept, page, go, night, scene, orgDept, toasts, toast, decide, addLeave, toggleClock, send, markRead, refreshThreads]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useStore() {
  const s = useContext(Ctx);
  if (!s) throw new Error('useStore must be used inside <StoreProvider>');
  return s;
}

/** Data for screens shown after login (AppShell guarantees it is loaded). */
export function useData(): Bootstrap {
  const { data } = useStore();
  if (!data) throw new Error('useData() before login');
  return data;
}
