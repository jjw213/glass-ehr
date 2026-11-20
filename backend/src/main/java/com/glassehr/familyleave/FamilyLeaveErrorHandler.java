package com.glassehr.familyleave;

import com.glassehr.appointment.AppointmentException;
import com.glassehr.attendance.AttendanceException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 육아휴직·단축·가족돌봄휴직 API 오류를 {"error": "..."} 형식으로 돌려준다.
 * 승인 중 만드는 인사발령(AppointmentException)·근무유형 배정(AttendanceException) 오류도 같은 형식이다.
 */
@RestControllerAdvice(assignableTypes = FamilyLeaveController.class)
public class FamilyLeaveErrorHandler {

    @ExceptionHandler(FamilyLeaveException.class)
    ResponseEntity<Map<String, String>> familyLeave(FamilyLeaveException e) {
        return body(e.status(), e.getMessage());
    }

    @ExceptionHandler(AppointmentException.class)
    ResponseEntity<Map<String, String>> appointment(AppointmentException e) {
        return body(e.status(), "인사발령을 만들지 못했어요: " + e.getMessage());
    }

    @ExceptionHandler(AttendanceException.class)
    ResponseEntity<Map<String, String>> attendance(AttendanceException e) {
        return body(e.status(), "근무유형을 배정하지 못했어요: " + e.getMessage());
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
        return body(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않아요(날짜는 YYYY-MM-DD, 종류는 정해진 코드)");
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
