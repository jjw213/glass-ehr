package com.glassehr.code;

import org.springframework.http.HttpStatus;

/** 공통코드 API 오류. 상태 코드와 화면에 보일 메시지를 함께 갖는다. */
public class CodeException extends RuntimeException {

    private final HttpStatus status;

    public CodeException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    static CodeException groupNotFound(String groupCode) {
        return new CodeException(HttpStatus.NOT_FOUND, "코드그룹이 없어요: " + groupCode);
    }

    static CodeException codeNotFound(String groupCode, String code) {
        return new CodeException(HttpStatus.NOT_FOUND, "코드가 없어요: " + groupCode + "/" + code);
    }

    static CodeException duplicate(String groupCode, String code) {
        return new CodeException(HttpStatus.CONFLICT, "이미 있는 코드값이에요: " + groupCode + "/" + code);
    }

    static CodeException badRequest(String message) {
        return new CodeException(HttpStatus.BAD_REQUEST, message);
    }
}
