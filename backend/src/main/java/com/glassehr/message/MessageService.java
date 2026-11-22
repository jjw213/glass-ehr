package com.glassehr.message;

import com.glassehr.auth.AuthUser;
import com.glassehr.message.MessageDtos.Member;
import com.glassehr.message.MessageDtos.MessagePage;
import com.glassehr.message.MessageDtos.MessageView;
import com.glassehr.message.MessageDtos.ThreadSummary;
import com.glassehr.message.MessageRepository.MessageRow;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사내 메시지(#80). Node {@code /api/threads} 를 대신한다.
 * 참여자는 사원 기준이라 사원과 연결되지 않은 계정은 대화방이 없고, 대화방 API 는 403 이다.
 * 참여자가 아닌 사원이 남의 대화방을 읽거나 쓰거나 읽음 처리하면 403, 없는 대화방은 404.
 */
@Service
public class MessageService {

    /** 본문 최대 길이(문자 수, 앞뒤 공백 제외). DB 열 VARCHAR2(1000 CHAR)와 같다. 법정 수치가 아니다. */
    static final int MAX_TEXT_LENGTH = 1000;
    /** 메시지 한 페이지 기본·최대 건수. */
    static final int DEFAULT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 200;

    private final MessageRepository repo;
    private final Clock clock;

    public MessageService(MessageRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ThreadSummary> threads(AuthUser user) {
        if (user.empId() == null) {
            return List.of();
        }
        long me = user.empId();
        Map<Long, List<Member>> others = repo.othersInThreadsOf(me);
        return repo.threadsOf(me).stream()
                .map(t -> new ThreadSummary(t.threadId(), others.getOrDefault(t.threadId(), List.of()),
                        t.preview() == null ? "" : t.preview(), t.lastSentAt(), t.unread()))
                .toList();
    }

    @Transactional(readOnly = true)
    public MessagePage messages(AuthUser user, long threadId, Long beforeId, Integer size) {
        long me = memberOrThrow(user, threadId);
        int n = size == null ? DEFAULT_PAGE_SIZE : size;
        if (n < 1 || n > MAX_PAGE_SIZE) {
            throw MessageException.badRequest("한 번에 1~" + MAX_PAGE_SIZE + "건까지 볼 수 있어요");
        }
        List<MessageRow> rows = repo.messagesBefore(threadId, beforeId, n + 1);
        boolean hasMore = rows.size() > n;
        List<MessageView> page = new ArrayList<>();
        for (MessageRow r : rows.subList(0, Math.min(n, rows.size()))) {
            page.add(view(r, me));
        }
        Collections.reverse(page); // 오래된 것부터
        return new MessagePage(page, hasMore);
    }

    @Transactional
    public MessageView send(AuthUser user, long threadId, String text) {
        long me = memberOrThrow(user, threadId);
        String body = text == null ? "" : text.strip();
        if (body.isEmpty()) {
            throw MessageException.badRequest("메시지가 비어 있어요");
        }
        if (body.codePointCount(0, body.length()) > MAX_TEXT_LENGTH) {
            throw MessageException.badRequest("메시지는 " + MAX_TEXT_LENGTH + "자까지 보낼 수 있어요");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        long id = repo.insertMessage(threadId, me, body, now);
        return new MessageView(id, me, true, body, now);
    }

    @Transactional
    public void markRead(AuthUser user, long threadId) {
        long me = memberOrThrow(user, threadId);
        repo.markRead(threadId, me, LocalDateTime.now(clock));
    }

    private long memberOrThrow(AuthUser user, long threadId) {
        if (!repo.threadExists(threadId)) {
            throw MessageException.notFound();
        }
        if (user.empId() == null || !repo.isMember(threadId, user.empId())) {
            throw MessageException.forbidden();
        }
        return user.empId();
    }

    private static MessageView view(MessageRow r, long me) {
        return new MessageView(r.messageId(), r.senderEmpId(), r.senderEmpId() == me, r.body(), r.sentAt());
    }
}
