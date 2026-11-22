package com.glassehr.overtime;

import com.glassehr.attendance.ApprovedOvertimeProvider;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 집계 엔진에 승인된 연장·휴일근로 신청 날짜를 준다. */
@Component
public class JpaApprovedOvertimeProvider implements ApprovedOvertimeProvider {

    private final OvertimeRequestRepository requests;

    public JpaApprovedOvertimeProvider(OvertimeRequestRepository requests) {
        this.requests = requests;
    }

    @Override
    public Set<LocalDate> approvedDates(long empId, LocalDate from, LocalDate to) {
        return requests.findByEmpIdAndWorkDateBetweenAndStatusIn(empId, from, to,
                        List.of(OvertimeRequest.Status.APPROVED)).stream()
                .map(OvertimeRequest::getWorkDate)
                .collect(Collectors.toSet());
    }
}
