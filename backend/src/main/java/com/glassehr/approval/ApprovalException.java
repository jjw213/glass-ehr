package com.glassehr.approval;

import org.springframework.http.HttpStatus;

/** 결재 API 오류. 상태 코드와 화면 메시지를 갖는다. */
public class ApprovalException extends RuntimeException {

    private final HttpStatus status;

    public ApprovalException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    static ApprovalException notFound(String message) {
        return new ApprovalException(HttpStatus.NOT_FOUND, message);
    }

    static ApprovalException badRequest(String message) {
        return new ApprovalException(HttpStatus.BAD_REQUEST, message);
    }

    static ApprovalException forbidden(String message) {
        return new ApprovalException(HttpStatus.FORBIDDEN, message);
    }

    static ApprovalException conflict(String message) {
        return new ApprovalException(HttpStatus.CONFLICT, message);
    }

    /** 결재선 설정 누락·결재자 없음 등으로 신청을 받을 수 없음. */
    public static ApprovalException rejected(String message) {
        return new ApprovalException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
