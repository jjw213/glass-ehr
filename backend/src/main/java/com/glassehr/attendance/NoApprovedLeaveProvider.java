package com.glassehr.attendance;

import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 휴가 모듈이 생기기 전의 기본 구현: 승인된 휴가가 없다.
 * 휴가 신청·결재 모듈을 만들 때 이 클래스를 지우고 실제 구현으로 바꾼다.
 */
@Component
public class NoApprovedLeaveProvider implements ApprovedLeaveProvider {

    @Override
    public Map<LocalDate, WorkHoursCalculator.Leave> approvedLeaves(long empId, LocalDate from, LocalDate to) {
        return Map.of();
    }
}
