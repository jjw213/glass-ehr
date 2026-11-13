package com.glassehr.attendance;

import java.util.List;
import org.springframework.http.HttpStatus;

/** 근태 API 오류. 상태 코드, 화면 메시지, 근무유형 검사 위반 목록(있으면)을 갖는다. */
public class AttendanceException extends RuntimeException {

    private final HttpStatus status;
    private final List<WorkPatternRules.Violation> violations;

    public AttendanceException(HttpStatus status, String message, List<WorkPatternRules.Violation> violations) {
        super(message);
        this.status = status;
        this.violations = List.copyOf(violations);
    }

    public HttpStatus status() {
        return status;
    }

    public List<WorkPatternRules.Violation> violations() {
        return violations;
    }

    static AttendanceException notFound(String message) {
        return new AttendanceException(HttpStatus.NOT_FOUND, message, List.of());
    }

    static AttendanceException badRequest(String message) {
        return new AttendanceException(HttpStatus.BAD_REQUEST, message, List.of());
    }

    static AttendanceException conflict(String message) {
        return new AttendanceException(HttpStatus.CONFLICT, message, List.of());
    }

    /** 법정 기준 위반 등으로 저장 거부. 메시지는 첫 위반, 전체는 violations. */
    static AttendanceException rejected(List<WorkPatternRules.Violation> violations) {
        return new AttendanceException(HttpStatus.UNPROCESSABLE_ENTITY, violations.get(0).message(), violations);
    }
}
