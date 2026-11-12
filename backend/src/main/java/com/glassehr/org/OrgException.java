package com.glassehr.org;

import org.springframework.http.HttpStatus;

/** 조직 API 오류. 상태 코드와 화면에 보일 메시지를 함께 갖는다. */
public class OrgException extends RuntimeException {

    private final HttpStatus status;

    public OrgException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    static OrgException deptNotFound(long deptId) {
        return new OrgException(HttpStatus.NOT_FOUND, "부서가 없어요: " + deptId);
    }

    static OrgException badRequest(String message) {
        return new OrgException(HttpStatus.BAD_REQUEST, message);
    }

    static OrgException conflict(String message) {
        return new OrgException(HttpStatus.CONFLICT, message);
    }
}
