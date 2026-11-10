import { httpApi } from './http';
import { mockApi } from './mock';
import type { Api } from './types';

/**
 * VITE_API=mock  → in-browser sample data, no server needed (demo / design work)
 * otherwise      → real server under /api
 */
export const api: Api = import.meta.env.VITE_API === 'mock' ? mockApi : httpApi;

/** show the "데모 계정 hr / 1234" hint on the login card */
export const SHOW_DEMO_HINT = api.mode === 'mock' || import.meta.env.DEV;

export * from './types';
