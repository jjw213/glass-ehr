package com.glassehr.attendance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * attendance_daily_summary 한 행(V023). 시간은 분, 가산율은 legal_rate 퍼센트 숫자.
 * 열 순서는 DailyWorkSummaryMapper.xml 의 constructor 와 같아야 한다.
 */
public record DailyWorkSummaryRow(
        Long empId,
        LocalDate workDate,
        String dayKind,
        Long patternId,
        Integer contractMinutes,
        String leaveKind,
        Integer leaveMinutes,
        LocalDateTime workStart,
        LocalDateTime workEnd,
        Integer breakMinutes,
        Integer workedMinutes,
        Integer recognizedMinutes,
        Integer regularMinutes,
        Integer extraMinutes,
        Integer overtimeMinutes,
        Integer nightMinutes,
        Integer holidayMinutes,
        Integer holidayExcessMinutes,
        BigDecimal overtimeRate,
        BigDecimal nightRate,
        BigDecimal holidayRate,
        BigDecimal holidayExcessRate,
        String issues,
        String calculatedBy,
        LocalDateTime calculatedAt) {

    /** 정책 인정 단위 도입 전 호출부와 테스트 자료는 시간 분류 합계로 인정 시간을 채운다. */
    public DailyWorkSummaryRow(Long empId, LocalDate workDate, String dayKind, Long patternId,
            Integer contractMinutes, String leaveKind, Integer leaveMinutes, LocalDateTime workStart,
            LocalDateTime workEnd, Integer breakMinutes, Integer workedMinutes, Integer regularMinutes,
            Integer extraMinutes, Integer overtimeMinutes, Integer nightMinutes, Integer holidayMinutes,
            Integer holidayExcessMinutes, BigDecimal overtimeRate, BigDecimal nightRate, BigDecimal holidayRate,
            BigDecimal holidayExcessRate, String issues, String calculatedBy, LocalDateTime calculatedAt) {
        this(empId, workDate, dayKind, patternId, contractMinutes, leaveKind, leaveMinutes, workStart, workEnd,
                breakMinutes, workedMinutes,
                regularMinutes + extraMinutes + overtimeMinutes + holidayMinutes + holidayExcessMinutes,
                regularMinutes, extraMinutes, overtimeMinutes, nightMinutes, holidayMinutes, holidayExcessMinutes,
                overtimeRate, nightRate, holidayRate, holidayExcessRate, issues, calculatedBy, calculatedAt);
    }
}
