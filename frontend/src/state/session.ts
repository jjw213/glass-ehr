import { createContext, useContext } from 'react';

export interface Session { logout: (replayIntro?: boolean) => void }
export const SessionCtx = createContext<Session>({ logout: () => {} });
export const useSession = () => useContext(SessionCtx);
