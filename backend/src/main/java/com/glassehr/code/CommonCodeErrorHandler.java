package com.glassehr.code;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 공통코드 API 오류를 {"error": "..."} 형식으로 돌려준다(프론트 http.ts가 error 필드를 읽는다). */
@RestControllerAdvice(assignableTypes = CommonCodeController.class)
public class CommonCodeErrorHandler {

    @ExceptionHandler(CodeException.class)
    ResponseEntity<Map<String, String>> code(CodeException e) {
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

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    ResponseEntity<Map<String, String>> unreadable(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않아요");
    }

    /** 동시에 같은 코드값을 추가한 경우 등 DB 제약 위반. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> integrity(DataIntegrityViolationException e) {
        return body(HttpStatus.CONFLICT, "다른 변경과 충돌했어요. 목록을 새로 불러온 뒤 다시 시도해 주세요");
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String msg) {
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }
}
