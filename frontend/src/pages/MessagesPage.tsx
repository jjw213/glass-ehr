import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { LuArrowLeft, LuSend, LuPlus } from 'react-icons/lu';
import { Card, PageHead, Avatar, cx } from '../components/ui';
import { useData, useStore } from '../state/store';
import { useIsMobile } from '../hooks/useMediaQuery';
import { seoulToday } from '../api/employees';
import {
  formatMessageTime, MAX_MESSAGE_LENGTH, mergeMessages, messageError, threadDept, threadTitle, type ChatMessage,
} from '../api/messages';

/** 사내 메시지(#80). 대화방 목록은 Spring /api/threads, 본문은 대화방을 열 때 페이지로 받는다. */
export function MessagesPage() {
  const { send, loadMessages, markRead, refreshThreads, toast, dept } = useStore();
  const { threads } = useData();
  const mobile = useIsMobile();
  const [selId, setSelId] = useState<number | null>(mobile ? null : threads[0]?.id ?? null);
  const [msgs, setMsgs] = useState<ChatMessage[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const sel = threads.find((t) => t.id === selId) ?? null;
  const endRef = useRef<HTMLDivElement>(null);
  const selectedThreadRef = useRef(selId);
  const requestVersionRef = useRef(0);
  const msgsRef = useRef<ChatMessage[]>([]);
  selectedThreadRef.current = selId;
  const today = seoulToday();

  const replaceMessages = useCallback((next: ChatMessage[]) => {
    msgsRef.current = next;
    setMsgs(next);
  }, []);

  /** 최신 페이지부터 기존 마지막 메시지와 만날 때까지 받아 폴링 중 생긴 누락을 메운다. */
  const loadLatest = useCallback(async (threadId: number, reset: boolean, version: number) => {
    let page = await loadMessages(threadId);
    if (!page || selectedThreadRef.current !== threadId || requestVersionRef.current !== version) return;
    if (reset) {
      replaceMessages(page.messages);
      setHasMore(page.hasMore);
      return;
    }

    const previous = msgsRef.current;
    const lastReceivedId = previous.at(-1)?.id;
    let received = page.messages;
    while (lastReceivedId != null && page.hasMore && !received.some((message) => message.id === lastReceivedId)) {
      const beforeId = page.messages[0]?.id;
      if (beforeId == null) break;
      page = await loadMessages(threadId, beforeId);
      if (!page || selectedThreadRef.current !== threadId || requestVersionRef.current !== version) return;
      received = mergeMessages(page.messages, received);
    }
    replaceMessages(mergeMessages(previous, received));
  }, [loadMessages, replaceMessages]);

  // 대화방을 바꾸면 그 방의 최신 페이지를 새로 받는다.
  useEffect(() => {
    const version = ++requestVersionRef.current;
    replaceMessages([]); setHasMore(false); setSending(false); setText('');
    if (selId != null) void loadLatest(selId, true, version);
  }, [selId, loadLatest, replaceMessages]);

  // 이 화면이 열려 있는 동안 새 메시지를 확인한다.
  useEffect(() => {
    const id = setInterval(() => {
      if (document.visibilityState !== 'visible') return;
      void refreshThreads();
      if (selId != null) void loadLatest(selId, false, requestVersionRef.current);
    }, 4000);
    return () => clearInterval(id);
  }, [refreshThreads, loadLatest, selId]);

  // 대화방을 열거나(안 읽은 메시지가 새로 오면) 읽음 처리한다.
  useEffect(() => { if (selId != null && sel?.unread) markRead(selId); }, [selId, sel?.unread, markRead]);
  useEffect(() => { endRef.current?.scrollIntoView?.({ block: 'end', behavior: 'smooth' }); }, [msgs.length]);

  const loadOlder = async () => {
    if (selId == null || msgsRef.current.length === 0) return;
    const threadId = selId;
    const version = requestVersionRef.current;
    const page = await loadMessages(threadId, msgsRef.current[0].id);
    if (!page || selectedThreadRef.current !== threadId || requestVersionRef.current !== version) return;
    replaceMessages(mergeMessages(page.messages, msgsRef.current));
    setHasMore(page.hasMore);
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!sel || sending) return;
    const err = messageError(text);
    if (err) { toast(err); return; }
    const threadId = sel.id;
    const version = requestVersionRef.current;
    setSending(true);
    const sent = await send(threadId, text);
    if (sent && selectedThreadRef.current === threadId && requestVersionRef.current === version) {
      setSending(false); replaceMessages(mergeMessages(msgsRef.current, [sent])); setText('');
    } else if (selectedThreadRef.current === threadId && requestVersionRef.current === version) {
      setSending(false);
    }
  };

  return (
    <>
      <PageHead title="메시지" sub="동료와 대화하고 업무를 빠르게 처리하세요" right={<button className="glass pill" onClick={() => toast('새 대화는 준비 중이에요')}><LuPlus /> 새 대화</button>} />
      <div className="split-view split-view--chat">
        {(!mobile || !sel) && (
          <Card i={1} className="split-view__list">
            <ul className="list">
              {threads.length === 0 && <li className="list__row muted">대화가 없어요</li>}
              {threads.map((t) => (
                <li key={t.id} className={cx('list__row', t.id === selId && 'active')} onClick={() => setSelId(t.id)}>
                  <Avatar name={threadTitle(t)} dept={threadDept(t)} size={40} />
                  <div className="grow">
                    <strong>{threadTitle(t)} <span className="muted sm">· {dept(threadDept(t))?.name}</span></strong>
                    <span className="muted sm ellipsis">{t.preview}</span>
                  </div>
                  {t.lastSentAt && <span className="muted sm">{formatMessageTime(t.lastSentAt, today)}</span>}
                  {t.unread > 0 && <span className="unread" aria-label={`안 읽은 메시지 ${t.unread}개`}>{t.unread}</span>}
                </li>
              ))}
            </ul>
          </Card>
        )}
        {(!mobile || sel) && sel && (
          <Card i={2} className="split-view__detail chat">
            <div className="chat__head">
              {mobile && <button className="iconbtn glass" onClick={() => setSelId(null)} aria-label="목록"><LuArrowLeft /></button>}
              <Avatar name={threadTitle(sel)} dept={threadDept(sel)} size={36} />
              <div><strong>{threadTitle(sel)}</strong><span className="muted sm">{dept(threadDept(sel))?.name}</span></div>
            </div>
            <div className="chat__body">
              {hasMore && <button type="button" className="glass pill" onClick={() => void loadOlder()}>이전 메시지 보기</button>}
              {msgs.map((m) => (
                <div key={m.id} className={cx('bubble', m.mine ? 'me' : 'them')}>
                  {!m.mine && sel.members.length > 1 && <strong className="sm">{sel.members.find((x) => x.empId === m.senderEmpId)?.name}</strong>}
                  <p>{m.text}</p>
                  <span>{formatMessageTime(m.sentAt, today)}</span>
                </div>
              ))}
              <div ref={endRef} />
            </div>
            <form className="chat__input" onSubmit={submit}>
              <input className="input" value={text} aria-label={`메시지(최대 ${MAX_MESSAGE_LENGTH}자)`} onChange={(e) => setText(e.target.value)} placeholder={`${threadTitle(sel)}님에게 메시지 보내기`} />
              <button className="btn btn--primary icon-only" aria-label="보내기" disabled={!text.trim() || sending}><LuSend /></button>
            </form>
          </Card>
        )}
      </div>
    </>
  );
}
