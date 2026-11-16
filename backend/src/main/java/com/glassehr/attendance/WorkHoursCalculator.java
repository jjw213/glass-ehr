package com.glassehr.attendance;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 근로시간 집계 엔진(#29). DB·시계·Spring 에 기대지 않는 순수 함수다. 급여 모듈은 이 결과(일별 집계)만 쓴다.
 * 설계: docs/work-hours-design.md
 *
 * <p>모든 기준값(1일·1주 기준시간, 야간 시각, 휴일근로 구분 기준시간)은 호출하는 쪽이 legal_rate 적용값으로
 * 채운 {@link Rules}로 받는다. 이 클래스에는 법정 수치가 없다. 시간은 분 단위 정수로만 계산한다(AGENTS.md).
 *
 * <p>계산 순서
 * <ol>
 *   <li>실근로 = 근로 구간의 합집합 − 휴게 구간의 합집합. 휴게가 겹치거나 근로 밖에 있어도 한 번만, 근로와 겹친 만큼만 뺀다
 *       (근로기준법 제54조 제1항: 휴게시간은 근로시간 도중에 주며 근로시간에 들어가지 않는다 — 제50조의 근로시간은 휴게 제외).</li>
 *   <li>휴일근로: 실근로 중 휴일인 역일(0시~24시)에 속한 분. 기준시간까지는 '휴일 기준시간 이내', 넘는 분은 '휴일 기준시간 초과'
 *       (근로기준법 제56조 제2항 제1호·제2호).</li>
 *   <li>1일 연장: 휴일이 아닌 역일의 실근로가 1일 기준시간을 넘는 분(근로기준법 제50조 제2항, 제53조 제1항, 제56조 제1항).
 *       자정을 넘는 근무는 시작한 날(근무일)의 하루 근로로 본다.</li>
 *   <li>1주 연장: 한 주(월~일) 동안 연장이 아닌 평일 실근로를 차례로 더해 1주 기준시간을 넘는 분을 연장으로 옮긴다
 *       (근로기준법 제50조 제1항, 제56조 제1항). 휴일근로는 이 합계에 넣지 않는다(제56조 제2항에서 따로 가산).</li>
 *   <li>야간: 실근로 중 야간 시각 구간(시작 시각~다음 날 종료 시각)에 든 분. 연장·휴일과 겹쳐서 따로 센다(근로기준법 제56조 제3항).</li>
 * </ol>
 * 휴가(연차·반차)는 실근로가 아니므로 연장 판단에 넣지 않고 {@code leaveMinutes}로만 남긴다. 반차일은 실제로 일한 구간만 센다.
 */
public final class WorkHoursCalculator {

    /** 하루 분 수(시계 단위, 법정 수치 아님). */
    static final int MINUTES_PER_DAY = 24 * 60;

    private WorkHoursCalculator() {
    }

    /** 그날의 성격. 근무유형 요일 구분(W·O·H)과 휴일 달력으로 정한다. */
    public enum DayKind {
        /** 소정근로일 */
        WORKDAY,
        /** 휴무일(무급, 휴일 아님) — 일하면 평일 근로로 센다 */
        OFFDAY,
        /** 휴일(주휴일·공휴일·대체공휴일·대체된 휴일·회사휴일) — 일하면 휴일근로 */
        HOLIDAY
    }

    /** 승인된 휴가의 종류. */
    public enum LeaveKind {
        FULL_DAY, HALF_DAY_AM, HALF_DAY_PM
    }

    /** 반개구간 [start, end). */
    public record Interval(LocalDateTime start, LocalDateTime end) {
        public Interval {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            if (!end.isAfter(start)) {
                throw new IllegalArgumentException("구간 끝이 시작보다 뒤여야 해요: " + start + " ~ " + end);
            }
        }
    }

    /** 승인된 휴가. minutes 는 휴가로 인정하는 소정근로 분(반차면 반차 분). */
    public record Leave(LeaveKind kind, int minutes) {
        public Leave {
            Objects.requireNonNull(kind, "kind");
            if (minutes < 0) {
                throw new IllegalArgumentException("휴가 분은 0 이상이어야 해요");
            }
        }
    }

    /**
     * 근무일 기준 법정 기준값(legal_rate 적용값을 분으로 바꾼 것).
     *
     * @param dailyStandardMinutes    근로기준법 제50조 제2항 1일 근로시간(DAILY_STANDARD_WORK_HOURS)
     * @param weeklyStandardMinutes   근로기준법 제50조 제1항 1주 근로시간(WEEKLY_STANDARD_WORK_HOURS)
     * @param holidayThresholdMinutes 근로기준법 제56조 제2항 휴일근로 구분 기준시간(HOLIDAY_PREMIUM_THRESHOLD_HOURS)
     * @param nightStartMinute        근로기준법 제56조 제3항 야간 시작(NIGHT_WORK_START_HOUR, 자정부터의 분)
     * @param nightEndMinute          근로기준법 제56조 제3항 야간 종료(NIGHT_WORK_END_HOUR, 자정부터의 분)
     */
    public record Rules(int dailyStandardMinutes, int weeklyStandardMinutes, int holidayThresholdMinutes,
            int nightStartMinute, int nightEndMinute) {
        public Rules {
            if (dailyStandardMinutes <= 0 || weeklyStandardMinutes <= 0 || holidayThresholdMinutes <= 0) {
                throw new IllegalArgumentException("기준시간은 1분 이상이어야 해요");
            }
            if (!inDay(nightStartMinute) || !inDay(nightEndMinute) || nightStartMinute == nightEndMinute) {
                throw new IllegalArgumentException("야간 시각이 잘못됐어요: " + nightStartMinute + "~" + nightEndMinute);
            }
        }
    }

    /**
     * 하루 입력.
     *
     * @param workDate        근무일(자정을 넘는 근무도 시작한 날)
     * @param dayKind         근무일의 성격
     * @param nextDayKind     다음 날의 성격(자정 넘어 일한 분의 휴일 여부 판단용)
     * @param contractMinutes 그날 소정근로시간(분). 소정근로일이 아니면 0
     * @param work            근로 구간(출근~퇴근). 근무일 0시부터 다음 날 24시 안이어야 한다
     * @param breaks          휴게 구간(근무유형 휴게 등). 겹쳐도 된다
     * @param leave           승인된 휴가(없으면 null)
     * @param rules           근무일 기준 법정 기준값
     */
    public record DayInput(LocalDate workDate, DayKind dayKind, DayKind nextDayKind, int contractMinutes,
            List<Interval> work, List<Interval> breaks, Leave leave, Rules rules) {
        public DayInput {
            Objects.requireNonNull(workDate, "workDate");
            Objects.requireNonNull(dayKind, "dayKind");
            Objects.requireNonNull(nextDayKind, "nextDayKind");
            Objects.requireNonNull(rules, "rules");
            if (contractMinutes < 0) {
                throw new IllegalArgumentException("소정근로시간은 0 이상이어야 해요");
            }
            work = List.copyOf(work);
            breaks = List.copyOf(breaks);
            LocalDateTime from = workDate.atStartOfDay();
            LocalDateTime to = workDate.plusDays(2).atStartOfDay();
            for (Interval i : work) {
                if (i.start().isBefore(from) || i.end().isAfter(to)) {
                    throw new IllegalArgumentException("근로 구간은 근무일 0시부터 다음 날 24시 안이어야 해요: " + i);
                }
            }
        }
    }

    /** 하루 결과(분). worked = regular + overtime + holiday + holidayExcess. night 는 겹쳐서 따로 센다. */
    public record DayResult(LocalDate workDate, DayKind dayKind, int contractMinutes, LeaveKind leaveKind,
            int leaveMinutes, int breakMinutes, int workedMinutes, int regularMinutes, int overtimeMinutes,
            int nightMinutes, int holidayMinutes, int holidayExcessMinutes) {

        DayResult withWeeklyOvertime(int moved) {
            return new DayResult(workDate, dayKind, contractMinutes, leaveKind, leaveMinutes, breakMinutes,
                    workedMinutes, regularMinutes - moved, overtimeMinutes + moved, nightMinutes, holidayMinutes,
                    holidayExcessMinutes);
        }
    }

    /** 하루만 계산(1주 연장 제외). */
    public static DayResult calculateDay(DayInput in) {
        Objects.requireNonNull(in, "in");
        // 분은 근무일 0시부터 센다.
        List<long[]> work = union(toMinutes(in.work(), in.workDate()));
        List<long[]> breaks = union(toMinutes(in.breaks(), in.workDate()));
        List<long[]> worked = subtract(work, breaks);

        int workedMinutes = total(worked);
        int breakMinutes = total(work) - workedMinutes;

        // 근로기준법 제56조 제2항: 휴일인 역일(근무일 [0,1440), 다음 날 [1440,2880))에 속한 실근로
        int holidayTotal = 0;
        if (in.dayKind() == DayKind.HOLIDAY) {
            holidayTotal += overlap(worked, 0, MINUTES_PER_DAY);
        }
        if (in.nextDayKind() == DayKind.HOLIDAY) {
            holidayTotal += overlap(worked, MINUTES_PER_DAY, 2L * MINUTES_PER_DAY);
        }
        Rules r = in.rules();
        // 제56조 제2항 제1호(기준시간 이내)·제2호(기준시간 초과)
        int holidayWithin = Math.min(holidayTotal, r.holidayThresholdMinutes());
        int holidayExcess = holidayTotal - holidayWithin;

        // 근로기준법 제50조 제2항·제56조 제1항: 휴일 아닌 실근로 중 1일 기준시간 초과분이 연장
        int weekday = workedMinutes - holidayTotal;
        int overtime = Math.max(0, weekday - r.dailyStandardMinutes());
        int regular = weekday - overtime;

        // 근로기준법 제56조 제3항: 야간 시각 구간과 겹친 실근로(전날 밤 ~ 다음 날 밤 창을 모두 본다)
        int night = 0;
        for (int k = -1; k <= 2; k++) {
            long start = (long) k * MINUTES_PER_DAY + r.nightStartMinute();
            long end = r.nightEndMinute() > r.nightStartMinute()
                    ? (long) k * MINUTES_PER_DAY + r.nightEndMinute()
                    : (long) (k + 1) * MINUTES_PER_DAY + r.nightEndMinute();
            night += overlap(worked, start, end);
        }

        Leave leave = in.leave();
        return new DayResult(in.workDate(), in.dayKind(), in.contractMinutes(),
                leave == null ? null : leave.kind(), leave == null ? 0 : leave.minutes(), breakMinutes,
                workedMinutes, regular, overtime, night, holidayWithin, holidayExcess);
    }

    /**
     * 한 주(월요일~일요일, 같은 주 안의 날만)를 계산한다. 1일 계산 뒤, 연장이 아닌 평일 실근로를 날짜순으로 더해
     * 1주 기준시간(근로기준법 제50조 제1항)을 넘는 분을 그날의 연장으로 옮긴다(제56조 제1항).
     * 1주 기준시간은 그 주 첫 입력일의 적용값을 쓴다.
     */
    public static List<DayResult> calculateWeek(List<DayInput> days) {
        Objects.requireNonNull(days, "days");
        if (days.isEmpty()) {
            return List.of();
        }
        List<DayInput> sorted = days.stream().sorted(Comparator.comparing(DayInput::workDate)).toList();
        LocalDate monday = sorted.get(0).workDate().with(DayOfWeek.MONDAY);
        for (int i = 0; i < sorted.size(); i++) {
            LocalDate d = sorted.get(i).workDate();
            if (!d.with(DayOfWeek.MONDAY).equals(monday)) {
                throw new IllegalArgumentException("한 주(월~일)의 날만 넣어 주세요: " + d);
            }
            if (i > 0 && d.equals(sorted.get(i - 1).workDate())) {
                throw new IllegalArgumentException("같은 날이 두 번 들어왔어요: " + d);
            }
        }
        int weeklyStandard = sorted.get(0).rules().weeklyStandardMinutes();
        List<DayResult> out = new ArrayList<>(sorted.size());
        int cumulative = 0;
        for (DayInput in : sorted) {
            DayResult r = calculateDay(in);
            int before = cumulative;
            cumulative += r.regularMinutes();
            int moved = Math.min(r.regularMinutes(), Math.max(0, cumulative - Math.max(before, weeklyStandard)));
            out.add(moved > 0 ? r.withWeeklyOvertime(moved) : r);
        }
        return out;
    }

    private static boolean inDay(int m) {
        return m >= 0 && m < MINUTES_PER_DAY;
    }

    private static long toMinute(LocalDate workDate, LocalDateTime t) {
        return Duration.between(workDate.atStartOfDay(), t).toMinutes();
    }

    private static List<long[]> toMinutes(List<Interval> intervals, LocalDate workDate) {
        List<long[]> out = new ArrayList<>(intervals.size());
        for (Interval i : intervals) {
            out.add(new long[] {toMinute(workDate, i.start()), toMinute(workDate, i.end())});
        }
        return out;
    }

    /** 겹치거나 맞닿은 구간을 합친다. */
    static List<long[]> union(List<long[]> in) {
        List<long[]> sorted = new ArrayList<>(in);
        sorted.sort(Comparator.comparingLong(a -> a[0]));
        List<long[]> out = new ArrayList<>();
        for (long[] i : sorted) {
            if (i[1] <= i[0]) {
                continue;
            }
            if (!out.isEmpty() && i[0] <= out.get(out.size() - 1)[1]) {
                long[] last = out.get(out.size() - 1);
                last[1] = Math.max(last[1], i[1]);
            } else {
                out.add(new long[] {i[0], i[1]});
            }
        }
        return out;
    }

    /** a − b (둘 다 합쳐진 구간). */
    static List<long[]> subtract(List<long[]> a, List<long[]> b) {
        List<long[]> out = new ArrayList<>();
        for (long[] i : a) {
            long cur = i[0];
            for (long[] c : b) {
                if (c[1] <= cur || c[0] >= i[1]) {
                    continue;
                }
                if (c[0] > cur) {
                    out.add(new long[] {cur, c[0]});
                }
                cur = Math.max(cur, c[1]);
            }
            if (cur < i[1]) {
                out.add(new long[] {cur, i[1]});
            }
        }
        return out;
    }

    private static int total(List<long[]> intervals) {
        long t = 0;
        for (long[] i : intervals) {
            t += i[1] - i[0];
        }
        return Math.toIntExact(t);
    }

    private static int overlap(List<long[]> intervals, long from, long to) {
        long t = 0;
        for (long[] i : intervals) {
            long s = Math.max(i[0], from);
            long e = Math.min(i[1], to);
            if (e > s) {
                t += e - s;
            }
        }
        return Math.toIntExact(t);
    }
}
