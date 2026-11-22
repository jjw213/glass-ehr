import { describe, expect, it } from 'vitest';
import { formatMessageTime, MAX_MESSAGE_LENGTH, mergeMessages, messageError } from './messages';
import { mockApi } from './mock';
import { ApiError } from './types';

describe('messageError (#80)', () => {
  it('빈 메시지·공백만 있는 메시지는 거부한다', () => {
    expect(messageError('')).not.toBeNull();
    expect(messageError('  \n ')).not.toBeNull();
  });
  it('앞뒤 공백을 빼고 최대 길이까지 허용한다', () => {
    expect(messageError(` ${'가'.repeat(MAX_MESSAGE_LENGTH)} `)).toBeNull();
    expect(messageError('가'.repeat(MAX_MESSAGE_LENGTH + 1))).not.toBeNull();
  });
});

describe('formatMessageTime', () => {
  it('오늘은 시:분, 다른 날은 월/일', () => {
    expect(formatMessageTime('2099-03-02T09:05:00', '2099-03-02')).toBe('09:05');
    expect(formatMessageTime('2099-02-28T23:59:00', '2099-03-02')).toBe('02/28');
  });
});

describe('mergeMessages', () => {
  it('id 로 중복을 없애고 오래된 것부터 정렬한다', () => {
    const m = (id: number) => ({ id, senderEmpId: 1, mine: true, text: String(id), sentAt: '2099-03-02T09:00:00' });
    expect(mergeMessages([m(2), m(3)], [m(1), m(3), m(4)]).map((x) => x.id)).toEqual([1, 2, 3, 4]);
  });
});

describe('mockApi 메시지 (데모)', () => {
  it('보내기·읽음 처리 후 안 읽은 수가 0이 되고, 빈 메시지는 400', async () => {
    const [first] = await mockApi.threads();
    expect(first.unread).toBeGreaterThan(0);
    await mockApi.markRead(first.id);
    expect((await mockApi.threads()).find((t) => t.id === first.id)?.unread).toBe(0);
    const sent = await mockApi.send(first.id, '  데모 메시지  ');
    expect(sent).toMatchObject({ mine: true, text: '데모 메시지' });
    const page = await mockApi.messages(first.id);
    expect(page.messages.at(-1)?.id).toBe(sent.id);
    await expect(mockApi.send(first.id, '   ')).rejects.toBeInstanceOf(ApiError);
  });
  it('없는 대화방은 404', async () => {
    await expect(mockApi.messages(999)).rejects.toMatchObject({ status: 404 });
  });
});
