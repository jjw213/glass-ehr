package com.glassehr.attendance;

import com.glassehr.legalrate.LegalRateException;
import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소정근로시간으로 근로자 유형을 정하고 주휴·연차 적용 안내를 만든다(#158, docs/part-time-worker-design.md).
 *
 * <p>통상근로자 1주 소정근로시간은 법인의 사용 중인 "적용 근로자 유형 = 통상" 근무유형 중 가장 긴 것이다.
 * 그런 근무유형이 없으면 근로기준법 제50조 제1항·제2항 기준근로시간(legal_rate WEEKLY_STANDARD_WORK_HOURS,
 * DAILY_STANDARD_WORK_HOURS)을 통상근로자의 1주 소정근로시간·1일 소정근로시간으로 본다.
 */
@Service
public class WorkerTypeService {

    private final WorkPatternRepository patterns;
    private final WorkerTypeLegalRules rules;
    private final LegalRateService legalRates;

    public WorkerTypeService(WorkPatternRepository patterns, WorkerTypeLegalRules rules, LegalRateService legalRates) {
        this.patterns = patterns;
        this.rules = rules;
        this.legalRates = legalRates;
    }

    /**
     * 통상근로자 기준.
     *
     * @param weeklyMinutes 1주 소정근로시간(분)
     * @param workDaysPerWeek 1주 소정근로일수. 계산할 수 없으면 null
     * @param patternCode 기준으로 쓴 근무유형 코드. 법정 기준근로시간이면 null
     */
    public record Baseline(int weeklyMinutes, Integer workDaysPerWeek, String patternCode) {
    }

    /**
     * 화면 안내.
     *
     * @param paidHolidayMinutes 단시간근로자 주휴일 1일분 시간(분, 시행령 [별표 2]). 통상·계산 불가면 null, 초단시간이면 0
     * @param baseLeaveDays 통상근로자 기본 연차일수(legal_rate ANNUAL_LEAVE_BASE_DAYS, 제60조 제1항)
     * @param baseLeaveMinutes 그 연차일수에 대한 단시간근로자 연차 시간(분). 통상이면 null, 초단시간이면 0
     */
    public record WorkerTypeView(WorkerType workerType, int weeklyContractMinutes, int fullTimeWeeklyMinutes,
            String fullTimePatternCode, int ultraShortWeeklyMinutes, boolean weeklyHolidayApplies,
            boolean annualLeaveApplies, Integer paidHolidayMinutes, Integer baseLeaveDays, Integer baseLeaveMinutes,
            String notice) {
    }

    @Transactional(readOnly = true)
    public Baseline baseline(String enterCd, LocalDate date) {
        return patterns.findByEnterCdOrderByPatternCodeAsc(enterCd).stream()
                .filter(p -> p.isActive() && p.getWorkerType() == WorkerType.FULL_TIME)
                .max(Comparator.comparingInt(WorkPattern::getWeeklyContractMinutes))
                .map(p -> new Baseline(p.getWeeklyContractMinutes(),
                        WorkPatternRules.count(p.spec().dayTypes(), WorkPatternRules.WORK_DAY), p.getPatternCode()))
                .orElseGet(() -> legalBaseline(date));
    }

    /** 근로기준법 제50조 제1항·제2항 기준근로시간을 통상근로자 소정근로시간으로 본다. */
    private Baseline legalBaseline(LocalDate date) {
        int weekly = legalRates.getEffective(LegalRateItem.WEEKLY_STANDARD_WORK_HOURS, date).rateValue()
                .multiply(BigDecimal.valueOf(WorkerTypeRules.MINUTES_PER_HOUR)).intValueExact();
        int daily = rules.asOf(date).annualLeaveHourMinutes();
        Integer days = weekly % daily == 0 ? weekly / daily : null;
        return new Baseline(weekly, days, null);
    }

    /** 매주 같은 소정근로시간(분)인 사원·근무유형의 근로자 유형. */
    @Transactional(readOnly = true)
    public WorkerType classify(String enterCd, int weeklyMinutes, LocalDate date) {
        return WorkerTypeRules.classify(weeklyMinutes, baseline(enterCd, date).weeklyMinutes(), rules.asOf(date));
    }

    /**
     * 근로계약 화면 안내. 기준값(legal_rate)이 그 날짜에 없으면 판정하지 않고 null — 기본값으로 추측하지 않는다.
     */
    @Transactional(readOnly = true)
    public WorkerTypeView describe(String enterCd, Integer weeklyMinutes, LocalDate date) {
        Objects.requireNonNull(date, "date");
        if (weeklyMinutes == null || weeklyMinutes <= 0) {
            return null;
        }
        try {
            WorkerTypeRules.Thresholds t = rules.asOf(date);
            Baseline b = baseline(enterCd, date);
            WorkerType type = WorkerTypeRules.classify(weeklyMinutes, b.weeklyMinutes(), t);
            int baseDays = legalRates.getEffective(LegalRateItem.ANNUAL_LEAVE_BASE_DAYS, date).rateValue()
                    .intValueExact();
            Integer holiday = null;
            Integer leave = null;
            if (type != WorkerType.FULL_TIME) {
                // 시행령 [별표 2] 제4호나목: 4주 총 소정근로시간 ÷ 통상근로자 4주 총 소정근로일수 = 1주분 ÷ 1주 소정근로일수
                holiday = b.workDaysPerWeek() == null ? null
                        : WorkerTypeRules.paidHolidayMinutes(type, weeklyMinutes, b.workDaysPerWeek());
                leave = WorkerTypeRules.proportionalAnnualLeaveMinutes(type, baseDays, weeklyMinutes,
                        b.weeklyMinutes(), t);
            }
            boolean applies = type.weeklyHolidayAndAnnualLeaveApply();
            return new WorkerTypeView(type, weeklyMinutes, b.weeklyMinutes(), b.patternCode(),
                    t.ultraShortWeeklyMinutes(), applies, applies, holiday, baseDays, leave, notice(type, t));
        } catch (LegalRateException | ArithmeticException e) {
            return null;
        }
    }

    static String notice(WorkerType type, WorkerTypeRules.Thresholds t) {
        return switch (type) {
            case FULL_TIME -> "통상근로자예요.";
            case PART_TIME -> "단시간근로자예요. 주휴·연차는 통상근로자의 소정근로시간에 비례해 시간 단위로 줘요"
                    + "(근로기준법 제18조 제1항, 시행령 제9조 제1항 [별표 2]).";
            case ULTRA_SHORT -> t.averageWeeks() + "주 평균 1주 소정근로시간이 "
                    + WorkPatternRules.hm(t.ultraShortWeeklyMinutes()) + " 미만인 초단시간근로자예요. "
                    + "주휴일(제55조)과 연차 유급휴가(제60조)를 적용하지 않아요(근로기준법 제18조 제3항).";
        };
    }
}
