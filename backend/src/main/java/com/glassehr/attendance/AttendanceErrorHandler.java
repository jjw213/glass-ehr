package com.glassehr.attendance;

import com.glassehr.legalrate.LegalRateException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 근태 API 오류를 {"error": "...", "violations": [...]} 형식으로 돌려준다. */
@RestControllerAdvice(assignableTypes = WorkPatternController.class)
public class AttendanceErrorHandler {

    @ExceptionHandler(AttendanceException.class)
    ResponseEntity<Map<String, Object>> attendance(AttendanceException e) {
        return body(e.status(), e.getMessage(), WorkPatternDtos.ViolationView.of(e.violations()));
    }

    /** 법령 기준값이 아직 입력되지 않았거나 겹치면 저장할 수 없다(기본값으로 검사하지 않음). */
    @ExceptionHandler(LegalRateException.class)
    ResponseEntity<Map<String, Object>> legalRate(LegalRateException e) {
        return body(HttpStatus.CONFLICT, "법령 기준값(" + e.itemCode() + ", " + e.asOfDate()
                + " 적용)을 찾지 못해 검사할 수 없어요. 관리자에게 기준값 입력을 요청해 주세요", List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage())
                .filter(m -> m != null && !m.isBlank())
                .sorted()
                .findFirst()
                .orElse("입력값을 확인해 주세요");
        return body(HttpStatus.BAD_REQUEST, msg, List.of());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, Object>> unreadable(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않아요(날짜는 YYYY-MM-DD)", List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, Object>> integrity(DataIntegrityViolationException e) {
        return body(HttpStatus.CONFLICT, "다른 변경과 충돌했어요. 목록을 새로 불러온 뒤 다시 시도해 주세요", List.of());
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String msg,
            List<WorkPatternDtos.ViolationView> violations) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", msg);
        if (!violations.isEmpty()) {
            m.put("violations", violations);
        }
        return ResponseEntity.status(status).body(m);
    }
}
