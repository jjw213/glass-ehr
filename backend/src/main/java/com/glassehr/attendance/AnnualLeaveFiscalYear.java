package com.glassehr.attendance;

import com.glassehr.attendance.AnnualLeaveRules.Attendance;
import com.glassehr.attendance.AnnualLeaveRules.DateRange;
import com.glassehr.attendance.AnnualLeaveRules.Rates;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 연차 유급휴가 발생 계산 — 회계연도 기준(docs/annual-leave-basis-design.md §1 {@code FISCAL_YEAR})과 퇴직 정산. 순수 함수.
 *
 * <p>회계연도 기준은 조문에 직접 규정된 방식이 아니라 고용노동부 행정해석에 따른 운영이다.
 * <ul>
 *   <li>최초 1년의 월 단위분은 입사일 기준과 같다(근로기준법 제60조 제2항·제7항 단서, {@link AnnualLeaveRules#firstYearMonthly}).</li>
 *   <li>입사한 회계연도의 남은 기간(입사일~회계연도 말일)분은 다음 회계연도 시작일에
 *       {@code 기본 일수 × 출근한 소정근로일수 ÷ 그 회계연도의 소정근로일수}를 정확한 분수로 준다
 *       (고용노동부 근기68207-620(2003.5.23.), 1350 상담 1000325013). 소수 처리를 정한 원문이 없어 반올림하지 않는다.</li>
 *   <li>온전한 회계연도마다 출근율이 기준 이상이면 제60조 제1항·제4항 일수, 미달이면 그 회계연도 중 개근한 달마다
 *       제2항 일수를 다음 회계연도 시작일에 한 건으로 준다(입사일 기준 fixture 규칙과 같은 방식). 발생일부터 1년 사용(제7항).</li>
 *   <li>제4항 가산의 계속근로연수: 입사일부터의 계속근로연수(1350 상담 1000135863, 임금근로시간과-2861)로 센다. 입사한 회계연도
 *       일부를 1년으로 세는지 확인한 원문이 없어, 두 해석의 일수가 달라지는 발생분은 {@link UnresolvedIncrementException}을 던진다.</li>
 * </ul>
 *
 * <p>퇴직 정산({@link #settlement}): 차이 = 입사일 기준 총일수 − 회계연도 기준 총일수. 양수면 부족분을 추가 정산하고, 음수는
 * 차감·환수하지 않는다(근로기준과-5802(2009.12.31.), 1350 상담 1000306110·1000325013 — 별도 재산정 규정이 없으면 회계연도 기준 유지).
 */
public final class AnnualLeaveFiscalYear {

    private static final int MONTHS_PER_YEAR = 12;
    private static final String PRORATED_BASIS = "근로기준법 제60조 제1항·제2항, 고용노동부 근기68207-620(2003.5.23.)";

    private AnnualLeaveFiscalYear() {
    }

    /** 정확한 분수 일수(기약분수, 분모 양수). 비례분의 소수를 임의로 반올림하지 않기 위해 쓴다. */
    public record LeaveDays(long numerator, long denominator) {
        public static final LeaveDays ZERO = new LeaveDays(0, 1);

        public LeaveDays {
            if (denominator == 0) {
                throw new IllegalArgumentException("분모가 0이에요");
            }
            long g = gcd(Math.abs(numerator), Math.abs(denominator));
            long sign = denominator < 0 ? -1 : 1;
            numerator = sign * numerator / g;
            denominator = sign * denominator / g;
        }

        public static LeaveDays of(long whole) {
            return new LeaveDays(whole, 1);
        }

        public LeaveDays plus(LeaveDays o) {
            return new LeaveDays(Math.addExact(Math.multiplyExact(numerator, o.denominator),
                    Math.multiplyExact(o.numerator, denominator)), Math.multiplyExact(denominator, o.denominator));
        }

        public LeaveDays minus(LeaveDays o) {
            return plus(new LeaveDays(-o.numerator, o.denominator));
        }

        public int signum() {
            return Long.signum(numerator);
        }

        @Override
        public String toString() {
            return denominator == 1 ? Long.toString(numerator) : numerator + "/" + denominator;
        }

        private static long gcd(long a, long b) {
            return b == 0 ? Math.max(a, 1) : gcd(b, a % b);
        }
    }

    public enum Kind {
        /** 제60조 제2항: 계속근로 1년 미만, 1개월 개근(입사일 기준과 같음). */
        MONTHLY_FIRST_YEAR("근로기준법 제60조 제2항"),
        /** 입사한 회계연도의 남은 기간분 비례 부여. */
        PRORATED(PRORATED_BASIS),
        /** 제60조 제1항·제4항: 온전한 회계연도 출근율 기준 이상. */
        ANNUAL("근로기준법 제60조 제1항·제4항"),
        /** 제60조 제2항: 온전한 회계연도 출근율 기준 미달, 그 회계연도 중 개근한 달마다. */
        MONTHLY_LOW_ATTENDANCE("근로기준법 제60조 제2항");

        private final String legalBasis;

        Kind(String legalBasis) {
            this.legalBasis = legalBasis;
        }

        public String legalBasis() {
            return legalBasis;
        }
    }

    /** 발생 한 건. {@code period}는 판단 기간(개근 1개월·입사 회계연도 남은 기간·온전한 회계연도). */
    public record Accrual(LocalDate grantDate, Kind kind, LeaveDays days, DateRange period, LocalDate lastUsableDate) {
        public String legalBasis() {
            return kind.legalBasis();
        }
    }

    /** 제4항 가산의 계속근로연수 해석이 정해지지 않아 일수를 확정할 수 없는 발생분. */
    public static final class UnresolvedIncrementException extends IllegalStateException {
        UnresolvedIncrementException(LocalDate grant, int fullYears, int withPartialYear) {
            super("회계연도 기준 가산 연수 산정 기준이 정해지지 않았어요: 발생일 " + grant + ", 온전한 회계연도 "
                    + fullYears + "년과 입사 회계연도 포함 " + withPartialYear + "년의 일수가 달라요(근로기준법 제60조 제4항)");
        }
    }

    /**
     * 입사일부터 기준일까지(기준일 포함) 회계연도 기준으로 발생한 연차 내역.
     *
     * @param fiscalYearStart 회계연도 시작 월일(2월 29일은 받지 않는다)
     * @param attendance      소정근로일은 입사한 회계연도의 시작일부터 있어야 한다(비례분 분모)
     */
    public static List<Accrual> accruals(LocalDate hireDate, MonthDay fiscalYearStart, LocalDate asOfDate,
            LocalDate lastWorkDay, Attendance attendance, Function<LocalDate, Rates> ratesAt) {
        Objects.requireNonNull(fiscalYearStart, "fiscalYearStart");
        if (fiscalYearStart.equals(MonthDay.of(2, 29))) {
            throw new IllegalArgumentException("회계연도 시작일로 2월 29일은 쓸 수 없어요");
        }
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
        List<Accrual> out = new ArrayList<>();
        for (AnnualLeaveRules.Accrual m : AnnualLeaveRules.firstYearMonthly(hireDate, asOfDate, lastWorkDay, attendance, ratesAt)) {
            out.add(new Accrual(m.grantDate(), Kind.MONTHLY_FIRST_YEAR, LeaveDays.of(m.days()), m.period(), m.lastUsableDate()));
        }

        LocalDate hireFyStart = fiscalYearStart.atYear(hireDate.getYear());
        if (hireFyStart.isAfter(hireDate)) {
            hireFyStart = fiscalYearStart.atYear(hireDate.getYear() - 1);
        }
        boolean partial = !hireFyStart.equals(hireDate);
        LocalDate periodStart = hireDate;
        for (int fy = 1; ; fy++) {
            LocalDate grant = fiscalYearStart.atYear(hireFyStart.getYear() + fy);
            if (!AnnualLeaveRules.accrues(grant, asOfDate, lastWorkDay)) {
                break;
            }
            DateRange period = new DateRange(periodStart, grant.minusDays(1));
            periodStart = grant;
            Rates rates = ratesAt.apply(grant);
            // 제7항: 발생일부터 1년간 사용.
            LocalDate lastUsable = AnnualLeaveRules.periodEnd(grant, MONTHS_PER_YEAR);
            if (fy == 1 && partial) {
                LeaveDays days = prorated(attendance, period, new DateRange(hireFyStart, grant.minusDays(1)), rates);
                if (days.signum() > 0) {
                    out.add(new Accrual(grant, Kind.PRORATED, days, period, lastUsable));
                }
                continue;
            }
            if (AnnualLeaveRules.meetsAttendanceRate(attendance, period, rates)) {
                int fullYears = partial ? fy - 1 : fy;
                int days = AnnualLeaveRules.annualDays(fullYears, rates);
                if (partial && AnnualLeaveRules.annualDays(fy, rates) != days) {
                    throw new UnresolvedIncrementException(grant, fullYears, fy);
                }
                out.add(new Accrual(grant, Kind.ANNUAL, LeaveDays.of(days), period, lastUsable));
            } else {
                int fullMonths = 0;
                for (int m = 1; m <= MONTHS_PER_YEAR; m++) {
                    if (attendance.absentIn(AnnualLeaveRules.monthPeriod(period.from(), m)) == 0) {
                        fullMonths++;
                    }
                }
                if (fullMonths > 0) {
                    out.add(new Accrual(grant, Kind.MONTHLY_LOW_ATTENDANCE,
                            LeaveDays.of((long) fullMonths * rates.monthlyDays()), period, lastUsable));
                }
            }
        }
        return out;
    }

    /** 근기68207-620: 기본 일수 × 출근한 소정근로일수(입사일~회계연도 말일) ÷ 그 회계연도의 소정근로일수. */
    private static LeaveDays prorated(Attendance a, DateRange worked, DateRange fiscalYear, Rates r) {
        long yearDays = a.scheduledIn(fiscalYear);
        if (yearDays == 0) {
            throw new IllegalArgumentException("입사한 회계연도의 소정근로일 자료가 없어요: " + fiscalYear);
        }
        long attended = a.scheduledIn(worked) - a.absentIn(worked);
        return new LeaveDays(Math.multiplyExact((long) r.baseDays(), attended), yearDays);
    }

    /**
     * 퇴직 정산(또는 기준일 비교).
     *
     * @param difference     입사일 기준 − 회계연도 기준
     * @param additionalDays 추가 정산할 부족분(차이가 양수일 때만, 음수는 차감하지 않으므로 0)
     */
    public record Settlement(LeaveDays hireDateTotal, LeaveDays fiscalYearTotal, LeaveDays difference, LeaveDays additionalDays) {
    }

    /** 입사일 기준과 회계연도 기준의 기준일(퇴직자는 마지막 근무일)까지 총 발생 일수를 비교한다. */
    public static Settlement settlement(LocalDate hireDate, MonthDay fiscalYearStart, LocalDate asOfDate,
            LocalDate lastWorkDay, Attendance attendance, Function<LocalDate, Rates> ratesAt) {
        LeaveDays hire = LeaveDays.ZERO;
        for (AnnualLeaveRules.Accrual a : AnnualLeaveRules.accruals(hireDate, asOfDate, lastWorkDay, attendance, ratesAt)) {
            hire = hire.plus(LeaveDays.of(a.days()));
        }
        LeaveDays fiscal = LeaveDays.ZERO;
        for (Accrual a : accruals(hireDate, fiscalYearStart, asOfDate, lastWorkDay, attendance, ratesAt)) {
            fiscal = fiscal.plus(a.days());
        }
        LeaveDays diff = hire.minus(fiscal);
        return new Settlement(hire, fiscal, diff, diff.signum() > 0 ? diff : LeaveDays.ZERO);
    }
}
