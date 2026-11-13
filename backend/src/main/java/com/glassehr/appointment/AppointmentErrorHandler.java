package com.glassehr.appointment;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 인사발령 API 오류를 {"error": "..."} 형식으로 돌려준다. */
@RestControllerAdvice(assignableTypes = AppointmentController.class)
public class AppointmentErrorHandler {

    @ExceptionHandler(AppointmentException.class)
    ResponseEntity<Map<String, String>> appointment(AppointmentException e) {
        return body(e.status(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage())
                .filter(m -> m != null && !m.isBlank())
                .sorted()
                .findFirst()
                .orElse("입력값을 확인해 주세요");
        return body(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> unreadable(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않아요(날짜는 YYYY-MM-DD, 부서 ID는 정수)");
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
