package com.glassehr.contract;

import org.springframework.http.HttpStatus;

/** 근로계약 API 오류. 상태 코드와 화면에 보일 메시지. */
public class ContractException extends RuntimeException {

    private final HttpStatus status;

    public ContractException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    static ContractException notFound(String message) {
        return new ContractException(HttpStatus.NOT_FOUND, message);
    }

    static ContractException badRequest(String message) {
        return new ContractException(HttpStatus.BAD_REQUEST, message);
    }

    static ContractException conflict(String message) {
        return new ContractException(HttpStatus.CONFLICT, message);
    }
}
