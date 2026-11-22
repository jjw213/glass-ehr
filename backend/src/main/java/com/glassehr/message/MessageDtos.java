package com.glassehr.message;

import java.time.LocalDateTime;
import java.util.List;

/** 메시지 API 요청·응답. 시각은 Asia/Seoul 지역 시각(ISO, 시간대 없음). */
public final class MessageDtos {

    private MessageDtos() {
    }

    /** 대화 상대(나를 뺀 참여자). deptId 는 현재 소속(없으면 null). */
    public record Member(long empId, String name, Long deptId) {
    }

    /**
     * 대화방 목록 한 줄.
     *
     * @param members    나를 뺀 참여자(이름순)
     * @param preview    마지막 메시지 본문(없으면 빈 문자열)
     * @param lastSentAt 마지막 메시지 시각(없으면 null)
     * @param unread     남이 보낸 메시지 중 내가 읽지 않은 수
     */
    public record ThreadSummary(long id, List<Member> members, String preview, LocalDateTime lastSentAt, int unread) {
    }

    /** 메시지 한 건. mine 은 내가 보낸 메시지인가. */
    public record MessageView(long id, long senderEmpId, boolean mine, String text, LocalDateTime sentAt) {
    }

    /**
     * 메시지 한 페이지(오래된 것부터). hasMore 면 첫 메시지 id 를 beforeId 로 주어 이전 페이지를 받는다.
     */
    public record MessagePage(List<MessageView> messages, boolean hasMore) {
    }

    public record SendRequest(String text) {
    }
}
