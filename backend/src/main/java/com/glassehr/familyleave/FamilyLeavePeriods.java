package com.glassehr.familyleave;

import com.glassehr.retirement.DismissalNoticeChecker;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 사용 기간 길이 계산(#37). 법령 수치는 없고 기간 계산 방식만 있다.
 * <ul>
 *   <li>민법 제157조 단서: 휴직·단축은 시작일 오전 0시부터라 시작일을 넣어 센다.</li>
 *   <li>민법 제160조 제2항·제3항: 월로 정한 기간은 최후 월의 해당일 전일(해당일이 없으면 그 월 말일)에 끝난다
 *       ({@link DismissalNoticeChecker#lastDayOfMonths}).</li>
 * </ul>
 * 나누어 쓴 기간의 남는 일수를 합쳐 몇 개월로 볼지는 원문·정답 fixture 로 확인되지 않아 월과 일을 따로 돌려준다
 * (docs/family-leave-design.md 7장).
 */
final class FamilyLeavePeriods {

    private FamilyLeavePeriods() {
    }

    /** 개월과 남는 일수. */
    record MonthsDays(int months, int days) {
        static final MonthsDays ZERO = new MonthsDays(0, 0);

        MonthsDays plus(MonthsDays o) {
            return new MonthsDays(months + o.months, days + o.days);
        }

        /** 한도 개월 수를 넘는지. 남는 일수가 있으면 한도와 같은 개월 수도 넘는 것으로 본다. */
        boolean exceeds(int limitMonths) {
            return months > limitMonths || (months == limitMonths && days > 0);
        }
    }

    /** start~end(양끝 포함)를 민법 제160조로 센 개월 수와 남는 일수. */
    static MonthsDays length(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            return MonthsDays.ZERO;
        }
        int months = 0;
        while (!DismissalNoticeChecker.lastDayOfMonths(start, months + 1).isAfter(end)) {
            months++;
        }
        LocalDate rest = months == 0 ? start : DismissalNoticeChecker.lastDayOfMonths(start, months).plusDays(1);
        return new MonthsDays(months, (int) ChronoUnit.DAYS.between(rest, end) + 1);
    }

    /** start~end(양끝 포함) 중 해당 연도(1월 1일~12월 31일)에 든 일수. */
    static int daysInYear(LocalDate start, LocalDate end, int year) {
        LocalDate from = start.isAfter(LocalDate.of(year, 1, 1)) ? start : LocalDate.of(year, 1, 1);
        LocalDate to = end.isBefore(LocalDate.of(year, 12, 31)) ? end : LocalDate.of(year, 12, 31);
        return to.isBefore(from) ? 0 : (int) ChronoUnit.DAYS.between(from, to) + 1;
    }
}
