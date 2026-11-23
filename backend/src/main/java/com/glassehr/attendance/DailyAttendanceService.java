package com.glassehr.attendance;

import com.glassehr.attendance.WorkHoursCalculator.DayKind;
import com.glassehr.attendance.WorkHoursCalculator.Leave;
import com.glassehr.attendance.WorkHoursCalculator.LeaveKind;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일별 출근·결근 판정(#114). 저장된 일별 근로시간 집계(#29 {@code attendance_daily_summary})와 승인된 휴가
 * ({@link ApprovedLeaveProvider})로 날마다 판정하고, 기간의 소정근로일 수·결근일 수를 돌려준다.
 * 연차 발생(#33)의 출근율 계산(근로기준법 제60조 제1항·제2항: 소정근로일 중 출근한 비율)이 이 결과를 쓴다.
 * 출근간주(제60조 제6항)·소정근로일 제외 사유 반영은 출근율을 계산하는 #33의 몫이다. 이 클래스에는 법정 수치가 없다.
 *
 * <p>판정 순서(설계 docs/daily-attendance-design.md)
 * <ol>
 *   <li>근무유형 미배정일 → 소정근로일 아님(소정근로일을 알 수 없음, NO_WORK_PATTERN 표시 그대로)</li>
 *   <li>휴일(주휴일·공휴일·회사휴일) → 휴일, 휴무일 → 휴무일. 그날 일했어도 소정근로일이 아니다(근로기준법 제55조).</li>
 *   <li>소정근로일에 승인된 종일 휴가 → 휴가(유급·무급)</li>
 *   <li>소정근로일에 출근 기록이 있음 → 출근. 지각·조퇴·퇴근 누락(MISSING_OUT)도 출근이다.</li>
 *   <li>소정근로일에 반차만 있고 출근 기록 없음 → 휴가(유급·무급)</li>
 *   <li>그 밖의 소정근로일(출근 기록도 승인된 휴가도 없음) → 결근</li>
 * </ol>
 */
@Service
public class DailyAttendanceService {

    /** 한 번에 볼 수 있는 최대 일수: 출근율 산정 1년(윤년 366일)을 한 번에 볼 수 있게(운영 보호용, 법정 수치 아님). */
    static final int MAX_RANGE_DAYS = 366;

    /** 날별 판정. */
    public enum DayStatus {
        /** 출근(지각·조퇴·퇴근 누락 포함) */
        PRESENT,
        /** 결근: 소정근로일에 출근 기록도 승인된 휴가도 없음 */
        ABSENT,
        /** 유급 휴가 */
        PAID_LEAVE,
        /** 무급 휴가 */
        UNPAID_LEAVE,
        /** 휴일(주휴일·공휴일·대체공휴일·회사휴일) */
        HOLIDAY,
        /** 휴무일 */
        OFF_DAY,
        /** 소정근로일 아님(근무유형 미배정일) */
        NOT_SCHEDULED;

        /** 소정근로일에 나오는 판정인지. */
        public boolean scheduled() {
            return this == PRESENT || this == ABSENT || this == PAID_LEAVE || this == UNPAID_LEAVE;
        }
    }

    /** 날짜별 판정. issues 는 집계 행의 표시(쉼표 구분, 없으면 null). */
    public record Day(LocalDate date, DayStatus status, String issues) {
    }

    /** (사원, 기간) 판정 결과. days 는 날짜 순, 기간의 모든 날. */
    public record Period(long empId, LocalDate from, LocalDate to, int scheduledDays, int absentDays,
            List<Day> days) {
    }

    private final DailyWorkSummaryMapper mapper;
    private final ApprovedLeaveProvider leaves;

    public DailyAttendanceService(DailyWorkSummaryMapper mapper, ApprovedLeaveProvider leaves) {
        this.mapper = mapper;
        this.leaves = leaves;
    }

    /**
     * [from, to](양 끝 포함)를 날마다 판정한다. 그 기간의 일별 집계가 모두 저장돼 있어야 한다 — 빠진 날이 있으면 409
     * (먼저 {@link WorkHoursService#recalculate}로 집계). 휴가는 지금 승인된 것을 읽는다.
     */
    @Transactional(readOnly = true)
    public Period judge(long empId, LocalDate from, LocalDate to) {
        checkRange(from, to);
        Map<LocalDate, DailyWorkSummaryRow> rows = mapper.findRange(empId, from, to).stream()
                .collect(Collectors.toMap(DailyWorkSummaryRow::workDate, Function.identity()));
        Map<LocalDate, Leave> leaveByDate = leaves.approvedLeaves(empId, from, to);
        List<Day> days = new ArrayList<>();
        int scheduled = 0;
        int absent = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            DailyWorkSummaryRow row = rows.get(d);
            if (row == null) {
                throw AttendanceException.conflict("일별 근로시간 집계가 없는 날이 있어요: " + d + " (먼저 재계산해 주세요)");
            }
            DayStatus status = classify(row, leaveByDate.get(d));
            if (status.scheduled()) {
                scheduled++;
            }
            if (status == DayStatus.ABSENT) {
                absent++;
            }
            days.add(new Day(d, status, row.issues()));
        }
        return new Period(empId, from, to, scheduled, absent, List.copyOf(days));
    }

    /** 한 날의 판정. 순서는 클래스 설명 참고. */
    static DayStatus classify(DailyWorkSummaryRow row, Leave leave) {
        if (row.patternId() == null) {
            return DayStatus.NOT_SCHEDULED;
        }
        DayKind kind = DayKind.valueOf(row.dayKind());
        if (kind == DayKind.HOLIDAY) {
            return DayStatus.HOLIDAY;
        }
        if (kind == DayKind.OFFDAY) {
            return DayStatus.OFF_DAY;
        }
        if (leave != null && leave.kind() == LeaveKind.FULL_DAY) {
            return leaveStatus(leave);
        }
        if (row.workStart() != null) {
            return DayStatus.PRESENT;
        }
        if (leave != null) {
            return leaveStatus(leave);
        }
        return DayStatus.ABSENT;
    }

    private static DayStatus leaveStatus(Leave leave) {
        return leave.paid() ? DayStatus.PAID_LEAVE : DayStatus.UNPAID_LEAVE;
    }

    private static void checkRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw AttendanceException.badRequest("기간(from, to)을 보내 주세요");
        }
        if (to.isBefore(from)) {
            throw AttendanceException.badRequest("끝 날짜가 시작 날짜보다 앞이에요");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw AttendanceException.badRequest("한 번에 " + MAX_RANGE_DAYS + "일까지 볼 수 있어요");
        }
    }
}
