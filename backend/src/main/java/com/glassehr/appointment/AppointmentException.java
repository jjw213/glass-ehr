package com.glassehr.appointment;

import org.springframework.http.HttpStatus;

/** 인사발령 API 오류. 상태 코드와 화면에 보일 메시지. */
public class AppointmentException extends RuntimeException {

    private final HttpStatus status;

    public AppointmentException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    static AppointmentException notFound(String message) {
        return new AppointmentException(HttpStatus.NOT_FOUND, message);
    }

    static AppointmentException badRequest(String message) {
        return new AppointmentException(HttpStatus.BAD_REQUEST, message);
    }

    static AppointmentException conflict(String message) {
        return new AppointmentException(HttpStatus.CONFLICT, message);
    }
}
