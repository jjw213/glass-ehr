package com.glassehr.attendance;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 유연근무제 정의 검사와 단위기간·정산기간 집계(#38, docs/flexible-work-design.md). 상태 없는 순수 계산.
 * 한도 값은 {@link FlexibleWorkLegalRules}가 legal_rate 에서 적용일 기준으로 읽어 {@link Limits}로 넘긴다.
 *
 * <ul>
 *   <li>탄력적 근로시간제: 근로기준법 제51조 제1항(취업규칙, 2주 이내·특정 주 48시간),
 *       제51조 제2항(서면합의, 3개월 이내·특정 주 52시간·특정 날 12시간),
 *       제51조의2 제1항·제2항 제1호(3개월 초과 6개월 이내, 근로일 간 연속 11시간 휴식),
 *       제51조의3(단위기간 도중 입사·퇴직자의 실근로기간 평균 주 40시간 초과분 전부 가산)</li>
 *   <li>선택적 근로시간제: 제52조 제1항(정산기간 1개월, 신상품·신기술 연구개발 3개월 이내, 평균 주 40시간 총량),
 *       제52조 제2항 제1호(1개월 초과 정산기간의 근로일 간 연속 11시간 휴식),
 *       제52조 제2항 제2호(1개월 초과 정산기간은 매 1개월 평균 주 40시간 초과분 가산)</li>
 *   <li>재량근로: 제58조 제3항(서면합의로 정한 시간을 근로한 것으로 봄), 시행령 제31조(대상 업무).
 *       제58조는 제56조 적용을 제외하지 않으므로 간주시간의 1일 기준 초과분과 실제 야간·휴일근로는 가산 대상이다.</li>
 * </ul>
 * 총량 한도 = 1주 기준시간 × 역일 수 ÷ 7, 분 미만 버림(근로자에게 불리하지 않은 쪽 — fixture 정답과 같다).
 */
public final class FlexibleWorkCalculator {

    private static final int DAYS_PER_WEEK = 7;

    private FlexibleWorkCalculator() {
    }

    /** 근무제 종류. 값은 V029 ck_flex_work_assign_system 과 같다. */
    public enum WorkSystem {
        /** 근로기준법 제51조 제1항: 취업규칙으로 정하는 2주 이내 탄력적 근로시간제(서면합의 불필요). */
        FLEXIBLE_SHORT(null),
        /** 근로기준법 제51조 제2항: 서면합의 3개월 이내 탄력적 근로시간제. */
        FLEXIBLE_WRITTEN(AgreementType.FLEXIBLE_WRITTEN),
        /** 근로기준법 제51조의2: 서면합의 3개월 초과 6개월 이내 탄력적 근로시간제. */
        FLEXIBLE_LONG(AgreementType.FLEXIBLE_LONG),
        /** 근로기준법 제52조: 선택적 근로시간제. */
        SELECTIVE(AgreementType.SELECTIVE),
        /** 근로기준법 제58조 제3항: 재량근로. */
        DISCRETIONARY(AgreementType.DISCRETIONARY);

        private final AgreementType agreement;

        WorkSystem(AgreementType agreement) {
            this.agreement = agreement;
        }

        /** 근로자대표 서면합의가 필요한 제도면 그 합의 종류, 아니면 null. */
        public AgreementType requiredAgreement() {
            return agreement;
        }

        public boolean isFlexible() {
            return this == FLEXIBLE_SHORT || this == FLEXIBLE_WRITTEN || this == FLEXIBLE_LONG;
        }
    }

    /** 선택적 근로시간제 업무 구분(근로기준법 제52조 제1항 괄호). */
    public enum SelectiveJob {
        GENERAL,
        /** 신상품 또는 신기술의 연구개발 업무. */
        RND
    }

    /** 근로기준법 시행령 제31조 재량근로 대상 업무. */
    public enum DiscretionaryJob {
        /** 제1호: 신상품·신기술의 연구개발, 인문사회과학·자연과학분야의 연구 업무. */
        RND,
        /** 제2호: 정보처리시스템의 설계 또는 분석 업무. */
        INFO_SYSTEM,
        /** 제3호: 신문·방송·출판 사업의 기사 취재·편성·편집 업무. */
        NEWS,
        /** 제4호: 의복·실내장식·공업제품·광고 등의 디자인 또는 고안 업무. */
        DESIGN,
        /** 제5호: 방송 프로그램·영화 등 제작 사업의 프로듀서나 감독 업무. */
        BROADCAST_PRODUCER,
        /** 제6호: 그 밖에 고용노동부장관이 정하는 업무. */
        MINISTER_DESIGNATED;

        public static boolean isEligible(String code) {
            if (code == null) {
                return false;
            }
            for (DiscretionaryJob j : values()) {
                if (j.name().equals(code)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** 정의 검사 결과(fixture 기대_정의_검사와 같은 이름). */
    public enum Check {
        OK, UNIT_PERIOD_EXCEEDED, WEEK_CAP_EXCEEDED, DAY_CAP_EXCEEDED, REST_SHORT, AVG_EXCEEDED,
        SETTLEMENT_EXCEEDED, NOT_ELIGIBLE_JOB
    }

    /**
     * 적용일 기준 법정 한도(분·개월·주). 해당 제도에 없는 상한은 0(검사하지 않음).
     *
     * @param unitWeeks   단위기간 한도(주), 0이면 개월 한도를 쓴다
     * @param unitMonths  단위기간·정산기간 한도(개월)
     * @param weekCap     특정 주 상한(분)
     * @param dayCap      특정 날 상한(분)
     * @param restMinutes 근로일 간 연속 휴식 하한(분), 0이면 검사 안 함
     */
    public record Limits(int unitWeeks, int unitMonths, int weekCap, int dayCap, int restMinutes,
            int dailyStandard, int weeklyStandard) {
        public Limits {
            if (dailyStandard <= 0 || weeklyStandard <= 0) {
                throw new IllegalArgumentException("기준시간은 0보다 커야 해요");
            }
            if ((unitWeeks <= 0) == (unitMonths <= 0)) {
                throw new IllegalArgumentException("단위기간 한도는 주 또는 개월 중 하나만 있어야 해요");
            }
        }
    }

    /** 하루 계획(근로 없는 날은 시각 null, 분 0). */
    public record PlanDay(LocalDate date, LocalDateTime start, LocalDateTime end, int minutes) {
        public PlanDay {
            Objects.requireNonNull(date, "date");
            if (minutes < 0) {
                throw new IllegalArgumentException("계획 분은 0 이상이어야 해요");
            }
            if ((start == null) != (end == null) || (start != null && !end.isAfter(start))) {
                throw new IllegalArgumentException("계획 시각이 올바르지 않아요: " + date);
            }
        }
    }

    /** 하루 계획과 실근로(분). */
    public record ActualDay(LocalDate date, int planMinutes, int actualMinutes) {
        public ActualDay {
            Objects.requireNonNull(date, "date");
            if (planMinutes < 0 || actualMinutes < 0) {
                throw new IllegalArgumentException("분은 0 이상이어야 해요");
            }
        }
    }

    public record DayOvertime(LocalDate date, int overtimeMinutes) {
    }

    public record WeekOvertime(LocalDate weekStart, LocalDate weekEnd, int overtimeMinutes) {
    }

    /**
     * 탄력적 단위기간 정산 결과.
     *
     * @param periodOvertime 단위기간 전체 연장(일·주 연장 + 단위기간 평균 초과 또는 제51조의3 정산분)
     */
    public record FlexSettlement(List<DayOvertime> days, List<WeekOvertime> weeks, int periodOvertime) {
    }

    /** 선택적 근로시간제 하루 실근로(시업·종업은 코어타임·휴식 검사용, 없으면 null). */
    public record SelectiveDay(LocalDate date, LocalDateTime start, LocalDateTime end, int minutes) {
        public SelectiveDay {
            Objects.requireNonNull(date, "date");
            if (minutes < 0) {
                throw new IllegalArgumentException("분은 0 이상이어야 해요");
            }
            if ((start == null) != (end == null) || (start != null && !end.isAfter(start))) {
                throw new IllegalArgumentException("시각이 올바르지 않아요: " + date);
            }
        }
    }

    public record SelectiveResult(Check check, int totalLimitMinutes, int overtimeMinutes, int coreTimeMissDays) {
    }

    public record DiscretionaryResult(Check check, int recognizedMinutes, int overtimeMinutes, int nightMinutes,
            int holidayMinutes) {
    }

    // ---------------------------------------------------------------- 공통

    /** 근로기준법 제51조·제51조의2·제52조: 단위기간(정산기간)이 한도 이내인지. */
    static boolean withinUnitPeriod(LocalDate start, LocalDate end, Limits l) {
        LocalDate last = l.unitWeeks() > 0
                ? start.plusWeeks(l.unitWeeks()).minusDays(1)
                : lastDayOfMonths(start, l.unitMonths());
        return !end.isAfter(last);
    }

    /**
     * 시작일부터 n개월 기간의 마지막 날. 민법 제157조(초일 산입은 근로기간이 0시 시작이라 산입)·제160조 제2항(해당일의 전일),
     * 제160조 제3항(최종 월에 해당일이 없으면 그 월의 말일). 예: 1월 31일부터 3개월 → 4월 30일.
     */
    static LocalDate lastDayOfMonths(LocalDate start, int months) {
        LocalDate same = start.plusMonths(months);
        return same.getDayOfMonth() == start.getDayOfMonth() ? same.minusDays(1) : same;
    }

    /** 1주 기준시간 × 역일 ÷ 7, 분 미만 버림(근로기준법 제51조 제1항·제52조 제1항 "평균하여 1주 40시간"). */
    public static int totalLimit(LocalDate start, LocalDate endInclusive, int weeklyStandard) {
        long days = ChronoUnit.DAYS.between(start, endInclusive) + 1;
        return (int) Math.floorDiv(weeklyStandard * days, DAYS_PER_WEEK);
    }

    /** 근로일 종료 후 다음 근로일 시작까지 연속 휴식이 하한 미만인 곳이 있는지. */
    private static boolean restShort(List<LocalDateTime[]> spans, int restMinutes) {
        if (restMinutes <= 0) {
            return false;
        }
        List<LocalDateTime[]> sorted = new ArrayList<>(spans);
        sorted.sort(Comparator.comparing(s -> s[0]));
        for (int i = 1; i < sorted.size(); i++) {
            long rest = Duration.between(sorted.get(i - 1)[1], sorted.get(i)[0]).toMinutes();
            if (rest < restMinutes) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 탄력적

    /**
     * 탄력적 근로시간제 계획 검사. 순서: 단위기간 → 특정 날 상한 → 특정 주 상한 → 평균 주 기준시간 → 휴식.
     * 주는 단위기간 시작일부터 7일씩 끊는다.
     */
    public static Check checkFlexiblePlan(LocalDate periodStart, LocalDate periodEnd, List<PlanDay> plan, Limits l) {
        if (periodEnd.isBefore(periodStart) || !withinUnitPeriod(periodStart, periodEnd, l)) {
            return Check.UNIT_PERIOD_EXCEEDED;
        }
        // 근로기준법 제51조 제2항 단서·제51조의2 제1항 단서: 특정한 날 12시간
        if (l.dayCap() > 0 && plan.stream().anyMatch(d -> d.minutes() > l.dayCap())) {
            return Check.DAY_CAP_EXCEEDED;
        }
        // 근로기준법 제51조 제1항 단서(48시간), 제2항 단서·제51조의2 제1항 단서(52시간): 특정한 주
        if (l.weekCap() > 0) {
            for (int[] w : weekSums(periodStart, periodEnd, plan)) {
                if (w[0] > l.weekCap()) {
                    return Check.WEEK_CAP_EXCEEDED;
                }
            }
        }
        int total = plan.stream().mapToInt(PlanDay::minutes).sum();
        if (total > totalLimit(periodStart, periodEnd, l.weeklyStandard())) {
            return Check.AVG_EXCEEDED;
        }
        // 근로기준법 제51조의2 제2항 제1호: 근로일 종료 후 다음 근로일 개시 전까지 연속 11시간 이상 휴식
        List<LocalDateTime[]> spans = plan.stream().filter(d -> d.start() != null)
                .map(d -> new LocalDateTime[] {d.start(), d.end()}).toList();
        if (restShort(spans, l.restMinutes())) {
            return Check.REST_SHORT;
        }
        return Check.OK;
    }

    private static List<int[]> weekSums(LocalDate periodStart, LocalDate periodEnd, List<PlanDay> plan) {
        List<int[]> out = new ArrayList<>();
        for (LocalDate ws = periodStart; !ws.isAfter(periodEnd); ws = ws.plusDays(DAYS_PER_WEEK)) {
            LocalDate from = ws;
            LocalDate to = ws.plusDays(DAYS_PER_WEEK - 1L);
            out.add(new int[] {plan.stream()
                    .filter(d -> !d.date().isBefore(from) && !d.date().isAfter(to))
                    .mapToInt(PlanDay::minutes).sum()});
        }
        return out;
    }

    /**
     * 탄력적 근로시간제 단위기간 정산.
     *
     * <ul>
     *   <li>일: 계획이 1일 기준시간을 넘는 날은 계획 초과분, 아닌 날은 1일 기준시간 초과분이 연장
     *       (근로기준법 제51조 제1항·제2항 "특정한 날에 제50조 제2항의 근로시간을 초과하여 근로하게 할 수 있다").</li>
     *   <li>주: 일 연장을 뺀 주 실근로가 max(주 계획, 1주 기준시간)을 넘는 분.</li>
     *   <li>단위기간: 근로기간이 단위기간 전체면 일·주 연장을 뺀 실근로가 총량 한도를 넘는 분을 더한다.
     *       도중 입사·퇴직이면 근로기준법 제51조의3에 따라 실근로기간 실근로 합계 − 1주 기준시간 × 역일 ÷ 7
     *       (분 미만 버림)과 일·주 연장 합 중 큰 값.</li>
     * </ul>
     * 주는 단위기간 시작일부터 7일씩 끊고, 근로기간 밖의 날은 집계하지 않는다.
     */
    public static FlexSettlement settleFlexible(LocalDate periodStart, LocalDate periodEnd, LocalDate workFrom,
            LocalDate workTo, List<ActualDay> days, Limits l) {
        LocalDate from = workFrom.isAfter(periodStart) ? workFrom : periodStart;
        LocalDate to = workTo.isBefore(periodEnd) ? workTo : periodEnd;
        List<ActualDay> in = days.stream()
                .filter(d -> !d.date().isBefore(from) && !d.date().isAfter(to))
                .sorted(Comparator.comparing(ActualDay::date)).toList();

        List<DayOvertime> dayOt = new ArrayList<>();
        for (ActualDay d : in) {
            int base = Math.max(d.planMinutes(), l.dailyStandard());
            dayOt.add(new DayOvertime(d.date(), Math.max(0, d.actualMinutes() - base)));
        }
        List<WeekOvertime> weekOt = new ArrayList<>();
        for (LocalDate ws = periodStart; !ws.isAfter(periodEnd); ws = ws.plusDays(DAYS_PER_WEEK)) {
            LocalDate a = ws;
            LocalDate b = ws.plusDays(DAYS_PER_WEEK - 1L).isAfter(periodEnd) ? periodEnd : ws.plusDays(6);
            int plan = 0;
            int actualRest = 0;
            for (int i = 0; i < in.size(); i++) {
                ActualDay d = in.get(i);
                if (!d.date().isBefore(a) && !d.date().isAfter(b)) {
                    plan += d.planMinutes();
                    actualRest += d.actualMinutes() - dayOt.get(i).overtimeMinutes();
                }
            }
            weekOt.add(new WeekOvertime(a, b, Math.max(0, actualRest - Math.max(plan, l.weeklyStandard()))));
        }
        int dailyAndWeekly = dayOt.stream().mapToInt(DayOvertime::overtimeMinutes).sum()
                + weekOt.stream().mapToInt(WeekOvertime::overtimeMinutes).sum();
        int actualTotal = in.stream().mapToInt(ActualDay::actualMinutes).sum();
        int period;
        if (from.equals(periodStart) && to.equals(periodEnd)) {
            int rest = actualTotal - dailyAndWeekly;
            period = dailyAndWeekly + Math.max(0, rest - totalLimit(periodStart, periodEnd, l.weeklyStandard()));
        } else {
            // 근로기준법 제51조의3: 실제 근로한 기간을 평균하여 1주 40시간을 초과한 시간 전부 가산
            int over = Math.max(0, actualTotal - totalLimit(from, to, l.weeklyStandard()));
            period = Math.max(over, dailyAndWeekly);
        }
        return new FlexSettlement(dayOt, weekOt, period);
    }

    // ---------------------------------------------------------------- 선택적

    /**
     * 선택적 근로시간제 정산기간 검사와 정산.
     *
     * <ul>
     *   <li>정산기간 한도(근로기준법 제52조 제1항): 일반 1개월, 신상품·신기술 연구개발 3개월. 넘으면 SETTLEMENT_EXCEEDED.</li>
     *   <li>1개월 초과 정산기간의 근로일 간 휴식(제52조 제2항 제1호) 미달이면 REST_SHORT.</li>
     *   <li>1일 8시간·1주 40시간을 넘어도 총량 이내면 연장이 아니다(제52조 제1항).</li>
     *   <li>연장: 1개월 이하는 총량 초과분. 1개월 초과는 시작일부터 1개월씩 끊은 구간마다 평균 주 40시간 초과분을 더하고
     *       (제52조 제2항 제2호), 그 분을 뺀 나머지가 정산기간 총량을 넘으면 그 분도 더한다.</li>
     *   <li>코어타임 미준수일: 근로한 날 중 시업~종업이 코어타임 전체를 덮지 않는 날 수.</li>
     * </ul>
     */
    public static SelectiveResult settleSelective(LocalDate start, LocalDate end, List<SelectiveDay> days,
            LocalTime coreStart, LocalTime coreEnd, Limits l, int monthlyRestMinutes) {
        int limit = totalLimit(start, end, l.weeklyStandard());
        if (end.isBefore(start) || !withinUnitPeriod(start, end, l)) {
            return new SelectiveResult(Check.SETTLEMENT_EXCEEDED, limit, 0, 0);
        }
        boolean overOneMonth = end.isAfter(lastDayOfMonths(start, 1));
        List<LocalDateTime[]> spans = days.stream().filter(d -> d.start() != null)
                .map(d -> new LocalDateTime[] {d.start(), d.end()}).toList();
        Check check = overOneMonth && restShort(spans, monthlyRestMinutes) ? Check.REST_SHORT : Check.OK;

        int total = days.stream().mapToInt(SelectiveDay::minutes).sum();
        int overtime;
        if (!overOneMonth) {
            overtime = Math.max(0, total - limit);
        } else {
            int monthly = 0;
            for (LocalDate a = start; !a.isAfter(end); ) {
                LocalDate next = lastDayOfMonths(a, 1);
                LocalDate b = next.isAfter(end) ? end : next;
                LocalDate from = a;
                int sum = days.stream().filter(d -> !d.date().isBefore(from) && !d.date().isAfter(b))
                        .mapToInt(SelectiveDay::minutes).sum();
                monthly += Math.max(0, sum - totalLimit(a, b, l.weeklyStandard()));
                a = b.plusDays(1);
            }
            overtime = monthly + Math.max(0, total - monthly - limit);
        }
        int miss = 0;
        if (coreStart != null && coreEnd != null) {
            for (SelectiveDay d : days) {
                if (d.start() == null) {
                    continue;
                }
                LocalDateTime cs = d.date().atTime(coreStart);
                LocalDateTime ce = d.date().atTime(coreEnd);
                if (d.start().isAfter(cs) || d.end().isBefore(ce)) {
                    miss++;
                }
            }
        }
        return new SelectiveResult(check, limit, overtime, miss);
    }

    // ---------------------------------------------------------------- 재량

    /**
     * 재량근로 간주시간 집계(근로기준법 제58조 제3항). 실근로와 관계없이 서면합의 간주시간을 인정한다.
     * 간주시간이 1일 기준시간을 넘으면 그 분이 연장(제56조 제1항), 실제 야간근로(제56조 제3항)는 그대로 야간,
     * 휴일이면 간주시간이 휴일근로(제56조 제2항). 대상 업무(시행령 제31조)가 아니면 NOT_ELIGIBLE_JOB 과 0.
     */
    public static DiscretionaryResult settleDiscretionary(String jobCode, int deemedMinutes, int nightMinutes,
            boolean holiday, int dailyStandard) {
        if (!DiscretionaryJob.isEligible(jobCode)) {
            return new DiscretionaryResult(Check.NOT_ELIGIBLE_JOB, 0, 0, 0, 0);
        }
        if (deemedMinutes <= 0 || nightMinutes < 0) {
            throw new IllegalArgumentException("간주시간은 0보다, 야간분은 0 이상이어야 해요");
        }
        if (holiday) {
            return new DiscretionaryResult(Check.OK, deemedMinutes, 0, nightMinutes, deemedMinutes);
        }
        return new DiscretionaryResult(Check.OK, deemedMinutes, Math.max(0, deemedMinutes - dailyStandard),
                nightMinutes, 0);
    }
}
