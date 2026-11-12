package com.glassehr.employee;

import org.springframework.http.HttpStatus;

/** 사원 API 오류. 상태 코드와 화면에 보일 메시지. 메시지에 주민등록번호 등 개인정보를 넣지 않는다. */
public class EmployeeException extends RuntimeException {

    private final HttpStatus status;

    public EmployeeException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    static EmployeeException notFound(long empId) {
        return new EmployeeException(HttpStatus.NOT_FOUND, "사원이 없어요: " + empId);
    }

    static EmployeeException badRequest(String message) {
        return new EmployeeException(HttpStatus.BAD_REQUEST, message);
    }

    static EmployeeException conflict(String message) {
        return new EmployeeException(HttpStatus.CONFLICT, message);
    }

    static EmployeeException forbidden(String message) {
        return new EmployeeException(HttpStatus.FORBIDDEN, message);
    }
}
