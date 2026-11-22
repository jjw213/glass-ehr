package com.glassehr.attendance;

import java.time.LocalDate;
import java.util.Set;

/**
 * 근로시간 집계(#29)가 참조하는 승인된 연장·휴일근로 신청(#30). 구현은 com.glassehr.overtime.
 * 집계 결과에 연장·휴일근로가 있는데 그날 승인된 신청이 없으면 {@code OVERTIME_NOT_APPROVED} 확인 표시를 남긴다.
 */
public interface ApprovedOvertimeProvider {

    /** [from, to](양 끝 포함) 중 승인된 신청이 있는 근로일. */
    Set<LocalDate> approvedDates(long empId, LocalDate from, LocalDate to);
}
