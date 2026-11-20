package com.glassehr.attendance;

import com.glassehr.retirement.DismissalNoticeChecker;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * 연차 유급휴가 발생 계산 — 입사일 기준(docs/annual-leave-basis-design.md §1 {@code HIRE_DATE}). 순수 함수.
 *
 * <p>근거(근로기준법 제60조). 조문 원문은 law.go.kr 자동 조회가 되지 않아 아직 대조하지 못했다(#33).
 * 일수·출근율·가산 주기·상한은 모두 {@link Rates}(legal_rate, 발생일 기준 값)에서 받는다.
 * <ul>
 *   <li>제1항: 1년간 출근율이 기준({@code ANNUAL_LEAVE_ATTENDANCE_RATE}) 이상이면 기본 일수({@code ANNUAL_LEAVE_BASE_DAYS}).</li>
 *   <li>제2항: 계속근로 1년 미만이거나 1년간 출근율 기준 미달이면 1개월 개근마다 {@code ANNUAL_LEAVE_MONTHLY_DAYS}.</li>
 *   <li>제4항: 최초 {@code ANNUAL_LEAVE_INCREMENT_BASE_YEARS}년을 넘는 계속근로 연수
 *       {@code ANNUAL_LEAVE_INCREMENT_CYCLE_YEARS}년마다 {@code ANNUAL_LEAVE_INCREMENT_DAYS} 가산, 합계 {@code ANNUAL_LEAVE_MAX_DAYS} 한도.</li>
 *   <li>제6항: 출근한 것으로 보는 기간은 호출하는 쪽이 {@link Attendance#deemedAttendance()}로 넘긴다
 *       (어떤 휴직·휴가가 해당하는지는 이 함수가 정하지 않는다).</li>
 *   <li>제7항: 1년간 사용하지 않으면 소멸. 제2항(최초 1년)의 휴가는 최초 1년의 근로가 끝날 때까지.</li>
 * </ul>
 *
 * <p>기간 계산: 입사일 오전 0시 시작이라 초일 산입(민법 제157조 단서), 만료일은 민법 제160조 제2항·제3항
 * ({@link DismissalNoticeChecker#lastDayOfMonths}). 기간이 끝난 다음 날 근로관계가 있을 때 발생한다(마지막 근무일 ≥ 발생일).
 */
public final class AnnualLeaveRules {

    /** 1년 = 12개월(달력 단위, 법정 수치 아님). */
    private static final int MONTHS_PER_YEAR = 12;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /**
     * 법률 제17185호 시행일(2020-03-31, 부칙 제1조 공포한 날부터 시행). 부칙 제2조: 제60조 제7항 단서(최초 1년 월 단위분은
     * 최초 1년의 근로가 끝날 때까지 사용)는 시행 후 발생하는 연차부터 적용하고, 그 전 발생분은 종전대로 발생일부터 1년 사용한다.
     * 요율·기준값이 아니라 법률 시행일이라 legal_rate가 아닌 상수로 둔다.
     */
    static final LocalDate LAW_17185_EFFECTIVE = LocalDate.of(2020, 3, 31);

    private AnnualLeaveRules() {
    }

    /** 발생일 기준 legal_rate 값 묶음. */
    public record Rates(
            int baseDays,
            BigDecimal attendanceRatePercent,
            int monthlyDays,
            int incrementBaseYears,
            int incrementCycleYears,
            int incrementDays,
            int maxDays) {
        public Rates {
            Objects.requireNonNull(attendanceRatePercent, "attendanceRatePercent");
            if (baseDays <= 0 || monthlyDays <= 0 || incrementBaseYears <= 0 || incrementCycleYears <= 0
                    || incrementDays <= 0 || maxDays < baseDays || attendanceRatePercent.signum() <= 0) {
                throw new IllegalArgumentException("연차 기준값이 올바르지 않아요: " + this);
            }
        }
    }

    /** 양 끝을 포함하는 날짜 구간. */
    public record DateRange(LocalDate from, LocalDate to) {
        public DateRange {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            if (to.isBefore(from)) {
                throw new IllegalArgumentException("구간 끝이 시작보다 앞이에요: " + from + "~" + to);
            }
        }

        boolean contains(LocalDate d) {
            return !d.isBefore(from) && !d.isAfter(to);
        }
    }

    /**
     * 출근율 판단 자료.
     *
     * @param scheduledWorkDays 소정근로일
     * @param absentDays        결근일(소정근로일 중 출근하지 않은 날)
     * @param deemedAttendance  출근한 것으로 보는 기간(제60조 제6항). 이 기간의 결근일은 출근으로 센다.
     */
    public record Attendance(Set<LocalDate> scheduledWorkDays, Set<LocalDate> absentDays, List<DateRange> deemedAttendance) {
        public Attendance {
            scheduledWorkDays = Set.copyOf(scheduledWorkDays);
            absentDays = Set.copyOf(absentDays);
            deemedAttendance = List.copyOf(deemedAttendance);
            for (LocalDate d : absentDays) {
                if (!scheduledWorkDays.contains(d)) {
                    throw new IllegalArgumentException("결근일이 소정근로일이 아니에요: " + d);
                }
            }
        }

        private boolean countsAsAbsent(LocalDate d) {
            return absentDays.contains(d) && deemedAttendance.stream().noneMatch(r -> r.contains(d));
        }

        long scheduledIn(DateRange p) {
            return scheduledWorkDays.stream().filter(p::contains).count();
        }

        long absentIn(DateRange p) {
            return absentDays.stream().filter(p::contains).filter(this::countsAsAbsent).count();
        }
    }

    public enum Kind {
        /** 제60조 제2항: 계속근로 1년 미만, 1개월 개근. */
        MONTHLY_FIRST_YEAR("근로기준법 제60조 제2항"),
        /** 제60조 제1항·제4항: 1년 출근율 기준 이상. */
        ANNUAL("근로기준법 제60조 제1항·제4항"),
        /** 제60조 제2항: 1년 출근율 기준 미달, 그 1년 중 개근한 달마다. */
        MONTHLY_LOW_ATTENDANCE("근로기준법 제60조 제2항");

        private final String legalBasis;

        Kind(String legalBasis) {
            this.legalBasis = legalBasis;
        }

        public String legalBasis() {
            return legalBasis;
        }
    }

    /**
     * 발생 한 건.
     *
     * @param grantDate      발생일
     * @param days           발생 일수
     * @param period         판단 기간(개근 1개월 또는 출근율 1년)
     * @param lastUsableDate 사용할 수 있는 마지막 날. 다음 날 소멸(제60조 제7항)
     */
    public record Accrual(LocalDate grantDate, Kind kind, int days, DateRange period, LocalDate lastUsableDate) {
        public String legalBasis() {
            return kind.legalBasis();
        }
    }

    /**
     * 입사일부터 기준일까지(기준일 포함) 발생한 연차 내역.
     *
     * @param hireDate    입사일(계속근로 시작일)
     * @param asOfDate    기준일. 이 날까지의 발생만 돌려준다
     * @param lastWorkDay 마지막 근무일(퇴직일). 재직 중이면 null
     * @param attendance  출근율 판단 자료(입사일~기준일을 덮어야 한다)
     * @param ratesAt     발생일 기준 legal_rate 값
     */
    public static List<Accrual> accruals(LocalDate hireDate, LocalDate asOfDate, LocalDate lastWorkDay,
            Attendance attendance, Function<LocalDate, Rates> ratesAt) {
        Objects.requireNonNull(hireDate, "hireDate");
        Objects.requireNonNull(asOfDate, "asOfDate");
        Objects.requireNonNull(attendance, "attendance");
        Objects.requireNonNull(ratesAt, "ratesAt");
        if (asOfDate.isBefore(hireDate)) {
            throw new IllegalArgumentException("기준일이 입사일보다 앞이에요: " + asOfDate);
        }
        if (lastWorkDay != null && lastWorkDay.isBefore(hireDate)) {
            throw new IllegalArgumentException("마지막 근무일이 입사일보다 앞이에요: " + lastWorkDay);
        }
        // 제60조 제2항: 최초 1년 동안 1개월 개근마다. 제7항 단서: 최초 1년의 근로가 끝날 때까지 사용
        // (법률 제17185호 부칙 제2조: 시행일 전 발생분은 발생일부터 1년).
        List<Accrual> out = new ArrayList<>(firstYearMonthly(hireDate, asOfDate, lastWorkDay, attendance, ratesAt));

        for (int year = 1; ; year++) {
            DateRange period = new DateRange(periodStart(hireDate, MONTHS_PER_YEAR * (year - 1)),
                    periodEnd(hireDate, MONTHS_PER_YEAR * year));
            LocalDate grant = period.to().plusDays(1);
            if (!accrues(grant, asOfDate, lastWorkDay)) {
                break;
            }
            Rates rates = ratesAt.apply(grant);
            // 제7항: 발생일부터 1년간 사용.
            LocalDate lastUsable = periodEnd(grant, MONTHS_PER_YEAR);
            if (meetsAttendanceRate(attendance, period, rates)) {
                out.add(new Accrual(grant, Kind.ANNUAL, annualDays(year, rates), period, lastUsable));
            } else {
                int fullMonths = 0;
                for (int m = MONTHS_PER_YEAR * (year - 1) + 1; m <= MONTHS_PER_YEAR * year; m++) {
                    if (attendance.absentIn(monthPeriod(hireDate, m)) == 0) {
                        fullMonths++;
                    }
                }
                if (fullMonths > 0) {
                    out.add(new Accrual(grant, Kind.MONTHLY_LOW_ATTENDANCE, fullMonths * rates.monthlyDays(), period, lastUsable));
                }
            }
        }
        return out;
    }

    /** 제60조 제2항: 최초 1년 동안 1개월 개근마다 발생하는 월 단위분(회계연도 기준도 같은 규칙을 쓴다). */
    static List<Accrual> firstYearMonthly(LocalDate hireDate, LocalDate asOfDate, LocalDate lastWorkDay,
            Attendance attendance, Function<LocalDate, Rates> ratesAt) {
        List<Accrual> out = new ArrayList<>();
        LocalDate firstYearEnd = periodEnd(hireDate, MONTHS_PER_YEAR);
        for (int m = 1; m < MONTHS_PER_YEAR; m++) {
            DateRange month = monthPeriod(hireDate, m);
            LocalDate grant = month.to().plusDays(1);
            if (!accrues(grant, asOfDate, lastWorkDay)) {
                break;
            }
            if (attendance.absentIn(month) == 0) {
                LocalDate lastUsable = grant.isBefore(LAW_17185_EFFECTIVE) ? periodEnd(grant, MONTHS_PER_YEAR) : firstYearEnd;
                out.add(new Accrual(grant, Kind.MONTHLY_FIRST_YEAR, ratesAt.apply(grant).monthlyDays(), month, lastUsable));
            }
        }
        return out;
    }

    /** 제60조 제1항·제4항: 계속근로 {@code year}년을 채웠을 때의 일수. */
    static int annualDays(int year, Rates r) {
        int extraYears = Math.max(0, year - r.incrementBaseYears());
        int days = r.baseDays() + (extraYears / r.incrementCycleYears()) * r.incrementDays();
        return Math.min(days, r.maxDays());
    }

    /** 제60조 제1항: 출근일 / 소정근로일 ≥ 기준 %. 소정근로일이 없으면 판단할 수 없어 예외. */
    static boolean meetsAttendanceRate(Attendance a, DateRange period, Rates r) {
        long scheduled = a.scheduledIn(period);
        if (scheduled == 0) {
            throw new IllegalArgumentException("출근율 판단 기간에 소정근로일 자료가 없어요: " + period);
        }
        long attended = scheduled - a.absentIn(period);
        return BigDecimal.valueOf(attended).multiply(HUNDRED)
                .compareTo(r.attendanceRatePercent().multiply(BigDecimal.valueOf(scheduled))) >= 0;
    }

    static boolean accrues(LocalDate grant, LocalDate asOf, LocalDate lastWorkDay) {
        return !grant.isAfter(asOf) && (lastWorkDay == null || !lastWorkDay.isBefore(grant));
    }

    /** 시작일부터 m번째 달(1부터)의 판단 구간. */
    static DateRange monthPeriod(LocalDate hire, int m) {
        return new DateRange(periodStart(hire, m - 1), periodEnd(hire, m));
    }

    static LocalDate periodStart(LocalDate hire, int monthsBefore) {
        return monthsBefore == 0 ? hire : periodEnd(hire, monthsBefore).plusDays(1);
    }

    static LocalDate periodEnd(LocalDate start, int months) {
        return DismissalNoticeChecker.lastDayOfMonths(start, months);
    }
}
