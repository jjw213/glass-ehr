package com.glassehr.org;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 조직 API 오류를 {"error": "..."} 형식으로 돌려준다(공통코드 API와 같은 형식). */
@RestControllerAdvice(assignableTypes = OrgController.class)
public class OrgErrorHandler {

    @ExceptionHandler(OrgException.class)
    ResponseEntity<Map<String, String>> org(OrgException e) {
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
        return body(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않아요(날짜는 YYYY-MM-DD)");
    }

    /** 동시에 같은 부서코드를 신설한 경우 등 DB 제약 위반. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> integrity(DataIntegrityViolationException e) {
        return body(HttpStatus.CONFLICT, "다른 변경과 충돌했어요. 조직도를 새로 불러온 뒤 다시 시도해 주세요");
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String msg) {
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }
}
