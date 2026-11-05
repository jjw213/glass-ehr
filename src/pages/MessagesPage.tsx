import { useEffect, useRef, useState, type FormEvent } from 'react';
import { LuArrowLeft, LuSend, LuPlus } from 'react-icons/lu';
import { Card, PageHead, Avatar, cx } from '../components/ui';
import { useData, useStore } from '../state/store';
import { useIsMobile } from '../hooks/useMediaQuery';

export function MessagesPage() {
  const { send, markRead, refreshThreads, toast, dept } = useStore();
  const { threads } = useData();
  const mobile = useIsMobile();
  const [selId, setSelId] = useState<string | null>(mobile ? null : threads[0]?.id ?? null);
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const sel = threads.find((t) => t.id === selId) ?? null;
  const endRef = useRef<HTMLDivElement>(null);

  // poll for new messages while this page is open
  useEffect(() => {
    const id = setInterval(() => { if (document.visibilityState === 'visible') void refreshThreads(); }, 4000);
    return () => clearInterval(id);
  }, [refreshThreads]);
  // opening a thread (or a new message arriving in it) marks it read
  useEffect(() => { if (selId && sel?.unread) markRead(selId); }, [selId, sel?.unread, markRead]);
  useEffect(() => { endRef.current?.scrollIntoView({ block: 'end', behavior: 'smooth' }); }, [sel?.msgs.length]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!sel || !text.trim() || sending) return;
    setSending(true);
    await send(sel.id, text.trim());
    setSending(false);
    setText('');
  };

  return (
    <>
      <PageHead title="메시지" sub="동료와 대화하고 업무를 빠르게 처리하세요" right={<button className="glass pill" onClick={() => toast('새 대화는 준비 중이에요')}><LuPlus /> 새 대화</button>} />
      <div className="split-view split-view--chat">
        {(!mobile || !sel) && (
          <Card i={1} className="split-view__list">
            <ul className="list">
              {threads.map((t) => (
                <li key={t.id} className={cx('list__row', t.id === selId && 'active')} onClick={() => setSelId(t.id)}>
                  <Avatar name={t.name} dept={t.dept} size={40} />
                  <div className="grow">
                    <strong>{t.name} <span className="muted sm">· {dept(t.dept)?.name}</span></strong>
                    <span className="muted sm ellipsis">{t.preview}</span>
                  </div>
                  {t.unread > 0 && <span className="unread">{t.unread}</span>}
                </li>
              ))}
            </ul>
          </Card>
        )}
        {(!mobile || sel) && sel && (
          <Card i={2} className="split-view__detail chat">
            <div className="chat__head">
              {mobile && <button className="iconbtn glass" onClick={() => setSelId(null)} aria-label="목록"><LuArrowLeft /></button>}
              <Avatar name={sel.name} dept={sel.dept} size={36} />
              <div><strong>{sel.name}</strong><span className="muted sm">{dept(sel.dept)?.name}</span></div>
            </div>
            <div className="chat__body">
              {sel.msgs.map((m, i) => (
                <div key={i} className={cx('bubble', m.from === 'me' ? 'me' : 'them')}>
                  <p>{m.text}</p>
                  <span>{m.time}</span>
                </div>
              ))}
              <div ref={endRef} />
            </div>
            <form className="chat__input" onSubmit={submit}>
              <input className="input" value={text} onChange={(e) => setText(e.target.value)} placeholder={`${sel.name}님에게 메시지 보내기`} />
              <button className="btn btn--primary icon-only" aria-label="보내기" disabled={!text.trim() || sending}><LuSend /></button>
            </form>
          </Card>
        )}
      </div>
    </>
  );
}
