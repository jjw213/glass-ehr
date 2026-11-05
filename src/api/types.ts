import type { Approval, Attendance, Bootstrap, LeaveRequest, NewLeave, Thread } from '../../shared/types';

export * from '../../shared/types';

/** Everything the UI can ask of the backend. Two implementations: http.ts (real server) and mock.ts (in-browser demo). */
export interface Api {
  readonly mode: 'http' | 'mock';
  login(loginId: string, password: string): Promise<void>;
  logout(): Promise<void>;
  bootstrap(): Promise<Bootstrap>;
  toggleClock(): Promise<Attendance>;
  addLeave(input: NewLeave): Promise<{ leaves: LeaveRequest[]; approvals: Approval[] }>;
  decide(id: string, action: 'approve' | 'reject'): Promise<{ approvals: Approval[]; leaves: LeaveRequest[] }>;
  threads(): Promise<Thread[]>;
  send(threadId: string, text: string): Promise<Thread[]>;
  markRead(threadId: string): Promise<void>;
}

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}
