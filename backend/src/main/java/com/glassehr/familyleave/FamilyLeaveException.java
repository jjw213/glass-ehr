package com.glassehr.familyleave;

import org.springframework.http.HttpStatus;

/** 육아휴직·단축·가족돌봄휴직 처리 오류. 상태 코드와 화면에 보일 메시지를 담는다. */
public class FamilyLeaveException extends RuntimeException {

    private final HttpStatus status;

    FamilyLeaveException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    static FamilyLeaveException notFound(String message) {
        return new FamilyLeaveException(HttpStatus.NOT_FOUND, message);
    }

    static FamilyLeaveException badRequest(String message) {
        return new FamilyLeaveException(HttpStatus.BAD_REQUEST, message);
    }

    static FamilyLeaveException conflict(String message) {
        return new FamilyLeaveException(HttpStatus.CONFLICT, message);
    }
}
