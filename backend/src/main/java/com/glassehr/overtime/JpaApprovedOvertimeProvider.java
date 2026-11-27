package com.glassehr.overtime;

import com.glassehr.attendance.ApprovedOvertimeProvider;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 집계 엔진에 승인된 연장·휴일근로 신청 날짜를 준다. 집계 법인의 신청만 읽는다(#127). */
@Component
public class JpaApprovedOvertimeProvider implements ApprovedOvertimeProvider {

    private final OvertimeRequestRepository requests;

    public JpaApprovedOvertimeProvider(OvertimeRequestRepository requests) {
        this.requests = requests;
    }

    @Override
    public Set<LocalDate> approvedDates(String enterCd, long empId, LocalDate from, LocalDate to) {
        return requests.findByEnterCdAndEmpIdAndWorkDateBetweenAndStatusIn(enterCd, empId, from, to,
                        List.of(OvertimeRequest.Status.APPROVED)).stream()
                .map(OvertimeRequest::getWorkDate)
                .collect(Collectors.toSet());
    }
}
