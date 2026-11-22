package com.glassehr.message;

import org.springframework.http.HttpStatus;

/** 메시지 API 오류. 상태 코드와 화면에 보일 메시지. */
public class MessageException extends RuntimeException {

    private final HttpStatus status;

    public MessageException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    static MessageException notFound() {
        return new MessageException(HttpStatus.NOT_FOUND, "대화를 찾을 수 없어요");
    }

    static MessageException forbidden() {
        return new MessageException(HttpStatus.FORBIDDEN, "이 대화에 참여하고 있지 않아요");
    }

    static MessageException badRequest(String message) {
        return new MessageException(HttpStatus.BAD_REQUEST, message);
    }
}
