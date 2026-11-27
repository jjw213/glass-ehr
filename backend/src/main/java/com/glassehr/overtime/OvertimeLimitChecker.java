package com.glassehr.overtime;

import com.glassehr.approval.ApprovalException;
import com.glassehr.attendance.DailyWorkSummaryMapper;
import com.glassehr.attendance.DailyWorkSummaryRow;
import com.glassehr.attendance.WorkPattern;
import com.glassehr.attendance.WorkPatternRepository;
import com.glassehr.legalrate.LegalRateException;
import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 승인 시점 1주 연장근로 한도 검사(#30). 정답: tests/legal-fixtures/weekly-overtime-limit.csv(PR #97, 사람 승인).
 * <ul>
 *   <li>근로기준법 제53조 제1항: 당사자 합의 시 1주 12시간을 한도로 연장. 한도는 legal_rate
 *       {@code WEEKLY_OVERTIME_LIMIT_HOURS}(근로일 기준 적용값).</li>
 *   <li>근로기준법 제2조 제1항 제7호: 1주는 휴일을 포함한 7일. 집계 엔진(#29)과 같은 월~일.</li>
 *   <li>한도 판정용 연장근로 = 1주 실근로시간 중 제50조 제1항의 1주 기준(legal_rate
 *       {@code WEEKLY_STANDARD_WORK_HOURS})을 넘은 시간. 휴일근로도 소정근로일과 구분 없이 포함한다
 *       (대법원 2020도15393을 반영한 고용노동부 2024-01-22 변경 행정해석). 1일 8시간 초과분만으로는 늘지 않는다.</li>
 *   <li>1주 실근로 = 실적 실근로분 + 승인된 신청과 이번 신청 중 실적 근무 구간에 아직 반영되지 않은 예정분.
 *       같은 날이라도 실적 근무 구간 밖의 예정분은 더하고, 이미 실적에 포함된 신청은 두 번 세지 않는다.</li>
 *   <li>근로일이 {@code WEEKLY_OVERTIME_LIMIT_HOURS}의 첫 적용시작일보다 앞서면(30~49명 사업장은
 *       법률 제15513호 부칙 제1조 제2항 제3호에 따라 2021-07-01 전) 이 한도를 적용하지 않는다.
 *       그 뒤인데 기준값이 없으면 승인하지 않는다(409).</li>
 * </ul>
 */
@Component
public class OvertimeLimitChecker {

    static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

    private final LegalRateService legalRates;
    private final DailyWorkSummaryMapper summaries;
    private final OvertimeRequestRepository requests;
    private final WorkPatternRepository patterns;

    public OvertimeLimitChecker(LegalRateService legalRates, DailyWorkSummaryMapper summaries,
            OvertimeRequestRepository requests, WorkPatternRepository patterns) {
        this.legalRates = legalRates;
        this.summaries = summaries;
        this.requests = requests;
        this.patterns = patterns;
    }

    /**
     * 검사 결과(분). {@code limitMinutes}가 null이면 근로일에 한도가 아직 시행되지 않아 적용하지 않은 것이다.
     * {@code overtimeMinutes}는 1주 실근로 합계에서 1주 기준근로시간을 뺀 값(최솟값 0).
     */
    public record Result(LocalDate weekStart, Integer limitMinutes, int weeklyWorkedMinutes, int overtimeMinutes) {
        public boolean exceeded() {
            return limitMinutes != null && overtimeMinutes > limitMinutes;
        }
    }

    /** 승인 직전에 부른다. 한도를 넘으면 422, 기준값이 없으면 409를 던져 승인 트랜잭션을 되돌린다. */
    public Result check(String enterCd, OvertimeRequest target) {
        LocalDate workDate = target.getWorkDate();
        LocalDate monday = workDate.with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);
        Integer limit = limitMinutes(workDate);
        int standard = hoursAsMinutes(LegalRateItem.WEEKLY_STANDARD_WORK_HOURS, workDate);

        Map<LocalDate, Integer> actual = new HashMap<>();
        List<DailyWorkSummaryRow> actualRows = new ArrayList<>();
        for (DailyWorkSummaryRow r : summaries.findRange(enterCd, target.getEmpId(), monday.minusDays(1), sunday)) {
            if (r.workedMinutes() != null && r.workedMinutes() > 0) {
                actualRows.add(r);
                if (!r.workDate().isBefore(monday)) {
                    actual.merge(r.workDate(), r.workedMinutes(), Integer::sum);
                }
            }
        }
        Map<LocalDate, Integer> planned = new HashMap<>();
        List<OvertimeRequest> approved = requests.findByEnterCdAndEmpIdAndWorkDateBetweenAndStatusIn(enterCd, target.getEmpId(),
                monday, sunday, List.of(OvertimeRequest.Status.APPROVED));
        for (OvertimeRequest r : approved) {
            if (!r.getRequestId().equals(target.getRequestId())) {
                planned.merge(r.getWorkDate(), unreflectedMinutes(r, actualRows), Integer::sum);
            }
        }
        planned.merge(target.getWorkDate(),
                unreflectedMinutes(target, actualRows), Integer::sum);
        int worked = weekWorked(monday, actual, planned);
        Result result = new Result(monday, limit, worked, Math.max(0, worked - standard));
        if (result.exceeded()) {
            throw new ApprovalException(HttpStatus.UNPROCESSABLE_ENTITY, "승인하면 " + monday + " 주의 연장근로가 "
                    + hm(result.overtimeMinutes()) + "로 1주 한도 " + hm(limit)
                    + "를 넘어요(근로기준법 제53조 제1항, 휴일근로 포함). 신청을 반려하거나 시간을 줄여 다시 신청해 주세요");
        }
        return result;
    }

    /** 날마다 실적과 아직 실적 근무 구간에 반영되지 않은 승인 예정분을 더한다. 주 밖의 날은 무시. */
    static int weekWorked(LocalDate monday, Map<LocalDate, Integer> actual, Map<LocalDate, Integer> planned) {
        int total = 0;
        for (int i = 0; i < 7; i++) {
            LocalDate d = monday.plusDays(i);
            total += actual.getOrDefault(d, 0) + planned.getOrDefault(d, 0);
        }
        return total;
    }

    /** 신청 예정분 중 출퇴근 실적 구간과 겹치지 않아 아직 실적에 반영되지 않은 분. */
    int unreflectedMinutes(OvertimeRequest request, List<DailyWorkSummaryRow> rows) {
        int reflected = 0;
        boolean hasTimedActual = false;
        boolean hasUntimedActualOnRequestDate = false;
        for (DailyWorkSummaryRow row : rows) {
            LocalDateTime start = row.workStart();
            LocalDateTime end = row.workEnd();
            if (start == null || end == null) {
                if (row.workDate().equals(request.getWorkDate())) {
                    hasUntimedActualOnRequestDate = true;
                }
                continue;
            }
            hasTimedActual = true;
            LocalDateTime overlapStart = request.getStartAt().isAfter(start) ? request.getStartAt() : start;
            LocalDateTime overlapEnd = request.getEndAt().isBefore(end) ? request.getEndAt() : end;
            if (overlapEnd.isAfter(overlapStart)) {
                int overlap = Math.toIntExact(Duration.between(overlapStart, overlapEnd).toMinutes());
                if (row.patternId() != null) {
                    WorkPattern pattern = patterns.findByEnterCdAndPatternId(request.getEnterCd(), row.patternId())
                            .orElse(null);
                    if (pattern != null && pattern.spec().breakStartMin() != null) {
                        LocalDate breakDay = pattern.spec().breakStartMin() >= pattern.spec().workStartMin()
                                ? row.workDate() : row.workDate().plusDays(1);
                        LocalDateTime breakStart = breakDay.atStartOfDay()
                                .plusMinutes(pattern.spec().breakStartMin());
                        LocalDateTime breakEnd = breakStart.plusMinutes(pattern.spec().breakMinutes());
                        LocalDateTime breakOverlapStart = overlapStart.isAfter(breakStart) ? overlapStart : breakStart;
                        LocalDateTime breakOverlapEnd = overlapEnd.isBefore(breakEnd) ? overlapEnd : breakEnd;
                        if (breakOverlapEnd.isAfter(breakOverlapStart)) {
                            overlap -= Math.toIntExact(Duration.between(breakOverlapStart, breakOverlapEnd).toMinutes());
                        }
                    }
                }
                reflected += overlap;
            }
        }
        // 과거 집계나 법령 fixture처럼 구간 없이 일 합계만 있는 실적은 기존처럼 그날 예정분을 대체한다.
        if (hasUntimedActualOnRequestDate && !hasTimedActual) {
            return 0;
        }
        return Math.max(0, request.getPlannedMinutes() - Math.min(request.getPlannedMinutes(), reflected));
    }

    /** 한도(분). 첫 적용시작일 전이면 null(미시행), 그 뒤 누락이면 409. */
    private Integer limitMinutes(LocalDate date) {
        String code = LegalRateItem.WEEKLY_OVERTIME_LIMIT_HOURS.code();
        try {
            return toMinutes(legalRates.getEffective(LegalRateItem.WEEKLY_OVERTIME_LIMIT_HOURS, date));
        } catch (LegalRateException e) {
            Optional<LocalDate> first = legalRates.firstEffectiveFrom(code);
            if (first.isPresent() && date.isBefore(first.get())) {
                return null;
            }
            throw missing(code, date);
        }
    }

    private int hoursAsMinutes(LegalRateItem item, LocalDate date) {
        try {
            return toMinutes(legalRates.getEffective(item, date));
        } catch (LegalRateException e) {
            throw missing(item.code(), date);
        }
    }

    private static ApprovalException missing(String code, LocalDate date) {
        return new ApprovalException(HttpStatus.CONFLICT, "1주 연장근로 한도 검사에 필요한 기준값(" + code + ", "
                + date + " 기준)을 찾을 수 없어 승인할 수 없어요. 관리자에게 법령 기준값 등록을 요청해 주세요");
    }

    private static int toMinutes(LegalRateRow row) {
        try {
            int n = row.rateValue().multiply(MINUTES_PER_HOUR).intValueExact();
            if (n <= 0) {
                throw new ArithmeticException();
            }
            return n;
        } catch (ArithmeticException e) {
            throw new IllegalStateException("기준값을 분 정수로 바꿀 수 없어요: " + row.itemCode() + " "
                    + row.effectiveFrom());
        }
    }

    static String hm(int minutes) {
        return minutes / 60 + "시간" + (minutes % 60 == 0 ? "" : " " + minutes % 60 + "분");
    }
}
