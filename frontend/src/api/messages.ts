/**
 * 사내 메시지(#80). Spring /api/threads 응답 모양. 시각은 Asia/Seoul 지역 시각(ISO, 시간대 없음).
 * 참여자는 사원 기준이라 deptId 는 Spring 부서 ID다.
 */

/** 대화 상대(나를 뺀 참여자). */
export interface ThreadMember { empId: number; name: string; deptId: number | null }

/** GET /api/threads 한 줄. 최근 메시지가 있는 대화방부터. */
export interface MessageThread {
  id: number;
  members: ThreadMember[];
  /** 마지막 메시지 본문(없으면 '') */
  preview: string;
  lastSentAt: string | null;
  /** 남이 보낸 메시지 중 내가 읽지 않은 수 */
  unread: number;
}

export interface ChatMessage { id: number; senderEmpId: number; mine: boolean; text: string; sentAt: string }

/** GET /api/threads/{id}/messages 한 페이지(오래된 것부터). hasMore 면 첫 메시지 id 를 beforeId 로 이전 페이지를 받는다. */
export interface MessagePage { messages: ChatMessage[]; hasMore: boolean }

/** 서버 MessageService.MAX_TEXT_LENGTH 와 같다(문자 수, 앞뒤 공백 제외). */
export const MAX_MESSAGE_LENGTH = 1000;

/** 대화방 이름: 상대 이름(여럿이면 쉼표로). */
export const threadTitle = (t: MessageThread) => t.members.map((m) => m.name).join(', ') || '(상대 없음)';

/** 아바타·부서 표시에 쓸 첫 상대의 부서 ID(문자열, 없으면 ''). */
export const threadDept = (t: MessageThread) => (t.members[0]?.deptId != null ? String(t.members[0].deptId) : '');

/** 메시지 시각 표시: 오늘(today='YYYY-MM-DD')이면 'HH:mm', 아니면 'MM/DD'. */
export function formatMessageTime(sentAt: string, today: string): string {
  return sentAt.slice(0, 10) === today ? sentAt.slice(11, 16) : `${sentAt.slice(5, 7)}/${sentAt.slice(8, 10)}`;
}

/** 이미 받은 메시지와 새로 받은 메시지를 id 로 합친다(오래된 것부터). */
export function mergeMessages(a: ChatMessage[], b: ChatMessage[]): ChatMessage[] {
  const byId = new Map<number, ChatMessage>();
  for (const m of [...a, ...b]) byId.set(m.id, m);
  return [...byId.values()].sort((x, y) => x.id - y.id);
}

/** 보내기 전 검사(서버도 같은 규칙으로 거부한다). 문제가 없으면 null. */
export function messageError(text: string): string | null {
  const body = text.trim();
  if (!body) return '메시지가 비어 있어요';
  if ([...body].length > MAX_MESSAGE_LENGTH) return `메시지는 ${MAX_MESSAGE_LENGTH}자까지 보낼 수 있어요`;
  return null;
}
