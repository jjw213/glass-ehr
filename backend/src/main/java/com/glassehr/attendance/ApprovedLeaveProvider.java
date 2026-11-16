package com.glassehr.attendance;

import java.time.LocalDate;
import java.util.Map;

/**
 * 근로시간 집계(#29)가 쓰는 승인된 휴가 입력. 휴가 신청·결재 모듈이 이 인터페이스를 구현한다.
 * 그 모듈이 생기기 전에는 {@link NoApprovedLeaveProvider}(휴가 없음)가 쓰인다.
 */
public interface ApprovedLeaveProvider {

    /** [from, to] 기간(양 끝 포함)에 승인된 휴가. 날짜마다 하나. */
    Map<LocalDate, WorkHoursCalculator.Leave> approvedLeaves(long empId, LocalDate from, LocalDate to);
}
