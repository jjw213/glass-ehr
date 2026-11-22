package com.glassehr.message;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 메시지 API 오류를 {"error": "..."} 형식으로 돌려준다. */
@RestControllerAdvice(assignableTypes = MessageController.class)
public class MessageErrorHandler {

    @ExceptionHandler(MessageException.class)
    ResponseEntity<Map<String, String>> message(MessageException e) {
        return body(e.status(), e.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> unreadable(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않아요(대화·메시지 ID는 정수)");
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
