package com.glassehr.retirement;

import org.springframework.http.HttpStatus;

/** 퇴직 처리·사용증명서 API 오류. 상태 코드와 화면에 보일 메시지. */
public class RetirementException extends RuntimeException {

    private final HttpStatus status;

    public RetirementException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    static RetirementException notFound(String message) {
        return new RetirementException(HttpStatus.NOT_FOUND, message);
    }

    static RetirementException badRequest(String message) {
        return new RetirementException(HttpStatus.BAD_REQUEST, message);
    }

    static RetirementException conflict(String message) {
        return new RetirementException(HttpStatus.CONFLICT, message);
    }
}
