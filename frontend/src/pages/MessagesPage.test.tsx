import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AppData, ChatMessage, MessagePage, MessageThread } from '../api';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

/** #80 메시지 화면: Spring 대화방 목록·메시지 페이지·보내기·읽음. 모두 가짜 값. */
let data: AppData;
const store = {
  send: vi.fn<(id: number, text: string) => Promise<ChatMessage | undefined>>(),
  loadMessages: vi.fn<(id: number, beforeId?: number) => Promise<MessagePage | undefined>>(),
  markRead: vi.fn<(id: number) => void>(),
  refreshThreads: vi.fn(async () => {}),
  toast: vi.fn<(t: string) => void>(),
  dept: (id: string) => (id === '3' ? { id: '3', name: '가짜팀', color: '#000', desc: '', lead: '' } : undefined),
};
vi.mock('../state/store', () => ({ useStore: () => store, useData: () => data }));
vi.mock('../hooks/useMediaQuery', () => ({ useIsMobile: () => false }));

const { MessagesPage } = await import('./MessagesPage');

const THREADS: MessageThread[] = [
  { id: 5, members: [{ empId: 8, name: '가짜상대', deptId: 3 }], preview: '두 번째', lastSentAt: '2099-03-02T09:01:00', unread: 1 },
  { id: 6, members: [{ empId: 9, name: '다른상대', deptId: null }], preview: '', lastSentAt: null, unread: 0 },
];
const msg = (id: number, mine: boolean, text: string): ChatMessage => ({ id, senderEmpId: mine ? 1 : 8, mine, text, sentAt: '2099-03-02T09:00:00' });

const flush = () => act(async () => { await Promise.resolve(); await Promise.resolve(); });

describe('MessagesPage', () => {
  let host: HTMLDivElement;
  let root: Root;
  beforeEach(() => {
    vi.clearAllMocks();
    data = {
      me: { loginId: 'emp1', empId: 1, name: '가짜사원', dept: '', role: 'EMPLOYEE', roles: [], permissions: [], annualLeave: 15 },
      depts: [], legacyDepts: [], dashboard: null, approvals: [], leaves: [], payslips: [], threads: THREADS,
    };
    store.loadMessages.mockResolvedValue({ messages: [msg(10, false, '첫 번째'), msg(11, false, '두 번째')], hasMore: true });
    host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host);
  });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  it('대화방 목록(안 읽은 수)과 첫 대화방의 메시지 페이지를 보여 주고 읽음 처리한다', async () => {
    act(() => root.render(<MessagesPage />));
    await flush();
    const text = host.textContent ?? '';
    expect(text).toContain('가짜상대');
    expect(text).toContain('가짜팀');
    expect(host.querySelector('.unread')?.textContent).toBe('1');
    expect(store.loadMessages).toHaveBeenCalledWith(5);
    expect(text).toContain('첫 번째');
    expect(text).toContain('이전 메시지 보기');
    expect(store.markRead).toHaveBeenCalledWith(5);
  });

  it('이전 메시지는 첫 메시지 id 를 beforeId 로 받는다', async () => {
    act(() => root.render(<MessagesPage />));
    await flush();
    store.loadMessages.mockResolvedValueOnce({ messages: [msg(3, true, '옛날 메시지')], hasMore: false });
    const older = [...host.querySelectorAll('button')].find((b) => b.textContent === '이전 메시지 보기')!;
    act(() => older.click());
    await flush();
    expect(store.loadMessages).toHaveBeenLastCalledWith(5, 10);
    const bubbles = [...host.querySelectorAll('.bubble p')].map((p) => p.textContent);
    expect(bubbles).toEqual(['옛날 메시지', '첫 번째', '두 번째']);
    expect(host.textContent).not.toContain('이전 메시지 보기');
  });

  it('공백만 있는 메시지는 보내지 않고, 보낸 메시지는 대화에 붙는다', async () => {
    act(() => root.render(<MessagesPage />));
    await flush();
    const input = host.querySelector<HTMLInputElement>('.chat__input input')!;
    const form = host.querySelector<HTMLFormElement>('.chat__input')!;
    const type = (v: string) => act(() => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, v);
      input.dispatchEvent(new Event('input', { bubbles: true }));
    });

    type('   ');
    act(() => form.requestSubmit());
    expect(store.send).not.toHaveBeenCalled();

    store.send.mockResolvedValueOnce(msg(12, true, '안녕하세요'));
    type('안녕하세요');
    act(() => form.requestSubmit());
    await flush();
    expect(store.send).toHaveBeenCalledWith(5, '안녕하세요');
    expect([...host.querySelectorAll('.bubble.me p')].map((p) => p.textContent)).toEqual(['안녕하세요']);
    expect(input.value).toBe('');
  });
});
