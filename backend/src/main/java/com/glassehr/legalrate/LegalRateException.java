package com.glassehr.legalrate;

import java.time.LocalDate;

/**
 * 기준값 조회 실패. 호출자가 조용히 기본값을 쓰지 않도록 예외로 알린다.
 * 메시지에는 항목코드·조회일·건수만 담고 기준값 자체는 담지 않는다.
 */
public class LegalRateException extends RuntimeException {

    public enum Reason {
        /** 조회일에 유효한 행이 없음 (미등록 항목 또는 기간 공백) */
        NOT_FOUND,
        /** 조회일에 유효한 행이 2건 이상 (기간 겹침 데이터 오류) */
        OVERLAPPING,
        /** 조회된 행의 단위가 항목 정의 단위와 다름 (데이터 오류) */
        UNIT_MISMATCH
    }

    private final Reason reason;
    private final String itemCode;
    private final LocalDate asOfDate;

    LegalRateException(Reason reason, String itemCode, LocalDate asOfDate, int count) {
        super("legal_rate " + reason + ": itemCode=" + itemCode + ", asOfDate=" + asOfDate + ", rows=" + count);
        this.reason = reason;
        this.itemCode = itemCode;
        this.asOfDate = asOfDate;
    }

    public Reason reason() {
        return reason;
    }

    public String itemCode() {
        return itemCode;
    }

    public LocalDate asOfDate() {
        return asOfDate;
    }
}
