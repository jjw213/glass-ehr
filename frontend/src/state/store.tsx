import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { api, ApiError, type AppData as Bootstrap, type ChatMessage, type Dept, type MessagePage, type NewLeave } from '../api';
import { setUnauthorizedHandler } from '../api/http';

export type PageId = 'home' | 'attendance' | 'flexWork' | 'payroll' | 'approvals' | 'messages' | 'people' | 'org' | 'settings' | 'codes' | 'workPatterns' | 'holidays' | 'agreements' | 'familyLeaves' | 'retirements' | 'appointments' | 'privacyLogs' | 'overtime' | 'approvalInbox';
export const PAGE_IDS: PageId[] = ['home', 'attendance', 'flexWork', 'payroll', 'approvals', 'messages', 'people', 'org', 'settings', 'codes', 'workPatterns', 'holidays', 'agreements', 'familyLeaves', 'retirements', 'appointments', 'privacyLogs', 'overtime', 'approvalInbox'];
export type SceneId = 'sunset' | 'dawn' | 'night';

interface Toast { id: number; text: string }

interface Store {
  /* session & data (null until signed in) */
  data: Bootstrap | null;
  login: (enterCd: string, loginId: string, password: string) => Promise<void>;
  /** 그룹 관리자 법인 전환(#120). 바꾸면 화면 데이터를 모두 다시 받는다. 실패하면 false(오류는 토스트). */
  switchEnter: (enterCd: string) => Promise<boolean>;
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
  /** 메시지(#80). 보내면 대화방 목록도 다시 받는다. 실패하면 undefined(오류는 토스트). */
  send: (threadId: number, text: string) => Promise<ChatMessage | undefined>;
  loadMessages: (threadId: number, beforeId?: number) => Promise<MessagePage | undefined>;
  markRead: (threadId: number) => void;
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

  /** 세션이 끝났을 때(어느 API 든 401) 한 번만 로그인 화면으로 돌아간다(#76). */
  const signedInRef = useRef(false);
  const sessionLost = useCallback(() => {
    if (!signedInRef.current) return;
    signedInRef.current = false;
    setData(null); lostRef.current(); toast('세션이 만료됐어요. 다시 로그인해 주세요');
  }, [toast]);
  useEffect(() => {
    setUnauthorizedHandler(sessionLost);
    return () => setUnauthorizedHandler(null);
  }, [sessionLost]);

  /** run a server call; on 401 drop back to login, otherwise show the error */
  const guard = useCallback(async <T,>(fn: () => Promise<T>): Promise<T | undefined> => {
    try { return await fn(); }
    catch (e) {
      if (e instanceof ApiError && e.status === 401) sessionLost();
      else toast(e instanceof Error ? e.message : '알 수 없는 오류가 발생했어요');
      return undefined;
    }
  }, [toast, sessionLost]);

  const patch = (p: Partial<Bootstrap>) => setData((d) => (d ? { ...d, ...p } : d));

  const login = useCallback(async (enterCd: string, loginId: string, password: string) => {
    await api.login(enterCd, loginId, password);   // throws ApiError on bad credentials
    const boot = await api.bootstrap();
    // 사용자는 서버가 세션 쿠키로 식별한다(#76). 역할·권한은 서버가 DB에서 읽으므로 화면 값은 메뉴 표시에만 쓴다.
    signedInRef.current = true;
    setData(boot);
  }, []);

  const switchEnter = useCallback(async (enterCd: string) => {
    // 서버가 세션의 현재 법인을 바꾸면 모든 조회가 그 법인 조건으로 간다. 이전 법인 데이터가 남지 않게 전부 다시 받는다.
    const boot = await guard(async () => { await api.switchEnter(enterCd); return api.bootstrap(); });
    if (!boot) return false;
    setData(boot);
    toast(`${boot.me.enterName}(으)로 바꿨어요`);
    return true;
  }, [guard, toast]);

  const signOut = useCallback(async () => {
    signedInRef.current = false;
    try { await api.logout(); } catch { /* already gone */ }
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

  const refreshThreads = useCallback(async () => {
    const r = await guard(() => api.threads());
    if (r) patch({ threads: r });
  }, [guard]);

  const send = useCallback(async (threadId: number, text: string) => {
    const r = await guard(() => api.send(threadId, text));
    if (r) await refreshThreads();
    return r;
  }, [guard, refreshThreads]);

  const loadMessages = useCallback((threadId: number, beforeId?: number) => guard(() => api.messages(threadId, beforeId)), [guard]);

  const markRead = useCallback((threadId: number) => {
    setData((d) => (d ? { ...d, threads: d.threads.map((t) => (t.id === threadId ? { ...t, unread: 0 } : t)) } : d));
    void guard(() => api.markRead(threadId));
  }, [guard]);

  const deptMap = useMemo(() => new Map([
    ...(data?.legacyDepts ?? []).map((d) => [d.id, d] as const),
    ...(data?.depts ?? []).map((d) => [d.id, d] as const),
  ]), [data?.depts, data?.legacyDepts]);
  const dept = useCallback((id: string) => deptMap.get(id), [deptMap]);

  const value = useMemo<Store>(() => ({
    data, login, switchEnter, signOut, onSessionLost: (fn) => { lostRef.current = fn; }, dept,
    page, go, night, setNight, scene, setScene, orgDept, setOrgDept, toasts, toast,
    decide, addLeave, send, loadMessages, markRead, refreshThreads,
    pendingCount: data?.approvals.filter((a) => a.canDecide).length ?? 0,
    unreadCount: data?.threads.reduce((s, t) => s + t.unread, 0) ?? 0,
  }), [data, login, switchEnter, signOut, dept, page, go, night, scene, orgDept, toasts, toast, decide, addLeave, send, loadMessages, markRead, refreshThreads]);

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
