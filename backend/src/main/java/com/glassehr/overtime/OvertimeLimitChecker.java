package com.glassehr.overtime;

import com.glassehr.approval.ApprovalException;
import com.glassehr.attendance.DailyWorkSummaryMapper;
import com.glassehr.attendance.DailyWorkSummaryRow;
import com.glassehr.legalrate.LegalRateException;
import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 승인 시점 1주 연장근로 한도 검사(#30).
 * <p>근로기준법 제53조 제1항: 당사자 합의 시 1주 12시간을 한도로 제50조의 근로시간을 연장할 수 있다.
 * 한도는 legal_rate {@code WEEKLY_OVERTIME_LIMIT_HOURS}(근무일 기준 적용값)에서 읽는다. 값이 없으면 승인하지 않는다(409).
 * <p>1주는 근로기준법 제2조 제1항 제7호(휴일을 포함한 7일)에 따라 집계 엔진(#29)과 같은 월~일로 본다.
 * 합계 = 날마다 max(실적 연장분, 이미 승인된 연장 신청 + 이번 신청의 예정분)의 주 합.
 * 실적은 attendance_daily_summary.overtime_minutes(1일·1주 기준 초과, 휴일근로 제외)를 쓴다.
 * 휴일근로(HOLIDAY 신청·holiday_minutes)를 한도 합계에 넣을지는 법령 정답 fixture로 확정하기 전까지 넣지 않는다
 * (docs/overtime-request-design.md §4 미확정).
 */
@Component
public class OvertimeLimitChecker {

    static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

    private final LegalRateService legalRates;
    private final DailyWorkSummaryMapper summaries;
    private final OvertimeRequestRepository requests;

    public OvertimeLimitChecker(LegalRateService legalRates, DailyWorkSummaryMapper summaries,
            OvertimeRequestRepository requests) {
        this.legalRates = legalRates;
        this.summaries = summaries;
        this.requests = requests;
    }

    /** 검사 결과(분). */
    public record Result(LocalDate weekStart, int limitMinutes, int totalMinutes) {
        public boolean exceeded() {
            return totalMinutes > limitMinutes;
        }
    }

    /** 승인 직전에 부른다. 한도를 넘으면 422, 기준값이 없으면 409를 던져 승인 트랜잭션을 되돌린다. */
    public Result check(OvertimeRequest target) {
        if (target.getWorkKind() != OvertimeRequest.Kind.OVERTIME) {
            // 휴일근로의 한도 합산 여부는 fixture 확정 전(설계 §4). 한도 검사 없이 승인한다.
            return null;
        }
        LocalDate monday = target.getWorkDate().with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);
        int limit = limitMinutes(target.getWorkDate());
        Map<LocalDate, Integer> actual = new HashMap<>();
        for (DailyWorkSummaryRow r : summaries.findRange(target.getEmpId(), monday, sunday)) {
            actual.merge(r.workDate(), r.overtimeMinutes() == null ? 0 : r.overtimeMinutes(), Integer::sum);
        }
        Map<LocalDate, Integer> planned = new HashMap<>();
        List<OvertimeRequest> approved = requests.findByEmpIdAndWorkDateBetweenAndStatusIn(target.getEmpId(),
                monday, sunday, List.of(OvertimeRequest.Status.APPROVED));
        for (OvertimeRequest r : approved) {
            if (r.getWorkKind() == OvertimeRequest.Kind.OVERTIME && !r.getRequestId().equals(target.getRequestId())) {
                planned.merge(r.getWorkDate(), r.getPlannedMinutes(), Integer::sum);
            }
        }
        planned.merge(target.getWorkDate(), target.getPlannedMinutes(), Integer::sum);
        Result result = new Result(monday, limit, weekTotal(monday, actual, planned));
        if (result.exceeded()) {
            throw new ApprovalException(HttpStatus.UNPROCESSABLE_ENTITY, "승인하면 " + monday + " 주의 연장근로가 "
                    + hm(result.totalMinutes()) + "로 1주 한도 " + hm(limit)
                    + "를 넘어요(근로기준법 제53조 제1항). 신청을 반려하거나 시간을 줄여 다시 신청해 주세요");
        }
        return result;
    }

    /** 날마다 실적과 예정 중 큰 값을 더한다(같은 근로를 두 번 세지 않음). */
    static int weekTotal(LocalDate monday, Map<LocalDate, Integer> actual, Map<LocalDate, Integer> planned) {
        int total = 0;
        for (int i = 0; i < 7; i++) {
            LocalDate d = monday.plusDays(i);
            total += Math.max(actual.getOrDefault(d, 0), planned.getOrDefault(d, 0));
        }
        return total;
    }

    private int limitMinutes(LocalDate date) {
        LegalRateRow row;
        try {
            row = legalRates.getEffective(LegalRateItem.WEEKLY_OVERTIME_LIMIT_HOURS, date);
        } catch (LegalRateException e) {
            throw new ApprovalException(HttpStatus.CONFLICT, "1주 연장근로 한도 기준값(WEEKLY_OVERTIME_LIMIT_HOURS, "
                    + date + " 기준)을 찾을 수 없어 승인할 수 없어요. 관리자에게 법령 기준값 등록을 요청해 주세요");
        }
        try {
            int n = row.rateValue().multiply(MINUTES_PER_HOUR).intValueExact();
            if (n <= 0) {
                throw new ArithmeticException();
            }
            return n;
        } catch (ArithmeticException e) {
            throw new IllegalStateException("1주 연장근로 한도를 분 정수로 바꿀 수 없어요: " + row.effectiveFrom());
        }
    }

    static String hm(int minutes) {
        return minutes / 60 + "시간" + (minutes % 60 == 0 ? "" : " " + minutes % 60 + "분");
    }
}
