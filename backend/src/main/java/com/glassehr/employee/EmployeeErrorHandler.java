package com.glassehr.employee;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 사원 API 오류를 {"error": "..."} 형식으로 돌려준다. 메시지에 요청 값(주민등록번호 등)을 되돌려 넣지 않는다.
 */
@RestControllerAdvice(assignableTypes = {EmployeeController.class, EmployeeImportController.class})
public class EmployeeErrorHandler {

    /** 일괄 등록 확정 거부: 422 + 행별 오류(미리보기와 같은 형식). */
    @ExceptionHandler(EmployeeImportService.NotConfirmableException.class)
    ResponseEntity<Map<String, Object>> notConfirmable(EmployeeImportService.NotConfirmableException e) {
        return ResponseEntity.status(EmployeeImportService.NOT_CONFIRMABLE)
                .body(Map.of("error", e.getMessage(), "preview", e.preview()));
    }

    /** 일괄 등록에서 파일(file) 칸을 빠뜨린 요청. */
    @ExceptionHandler({org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.MultipartException.class})
    ResponseEntity<Map<String, String>> missingFile(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "올릴 파일(file)을 선택해 주세요");
    }

    @ExceptionHandler(EmployeeException.class)
    ResponseEntity<Map<String, String>> employee(EmployeeException e) {
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

    /** JSON 형식 오류, 모르는 필드(수정 API의 소속·직급·직책 등), 주민등록번호 형식 오류. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> unreadable(Exception e) {
        return body(HttpStatus.BAD_REQUEST,
                "요청 형식이 올바르지 않아요(날짜는 YYYY-MM-DD, 주민등록번호는 숫자 6자리-7자리, 바꿀 수 없는 항목은 보내지 마세요)");
    }

    @ExceptionHandler(RrnCrypto.KeyNotConfiguredException.class)
    ResponseEntity<Map<String, String>> keyMissing(RrnCrypto.KeyNotConfiguredException e) {
        return body(HttpStatus.SERVICE_UNAVAILABLE, "주민등록번호 암호화 설정이 되어 있지 않아요. 관리자에게 문의해 주세요");
    }

    /** 동시 등록으로 같은 사번·같은 주민등록번호가 생기려 한 경우 등. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> integrity(DataIntegrityViolationException e) {
        return body(HttpStatus.CONFLICT, "다른 변경과 충돌했어요. 새로 불러온 뒤 다시 시도해 주세요");
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String msg) {
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }
}
