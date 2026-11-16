package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.attendance.WorkHoursCalculator.DayInput;
import com.glassehr.attendance.WorkHoursCalculator.DayKind;
import com.glassehr.attendance.WorkHoursCalculator.DayResult;
import com.glassehr.attendance.WorkHoursCalculator.Interval;
import com.glassehr.attendance.WorkHoursCalculator.Leave;
import com.glassehr.attendance.WorkHoursCalculator.LeaveKind;
import com.glassehr.attendance.WorkHoursCalculator.Rules;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 집계 엔진의 동작(구간 계산·분류 순서) 테스트. 기준값은 이 테스트가 넣는 입력이며 법령 정답이 아니다.
 * 법령 정답 대조는 tests/legal-fixtures/ 의 근로시간 사례로 따로 한다(정답 파일 준비 후 WorkHoursFixtureTest).
 * 날짜는 가짜 값(2099년). 2099-03-02 는 월요일.
 */
class WorkHoursCalculatorTest {

    /** 테스트 입력용 기준값: 1일 480분, 1주 2400분, 휴일 구분 480분, 야간 22:00~06:00. */
    private static final Rules RULES = new Rules(480, 2400, 480, 22 * 60, 6 * 60);
    private static final LocalDate MON = LocalDate.of(2099, 3, 2);

    private static LocalDateTime at(LocalDate d, int h, int m) {
        return d.atTime(h, m);
    }

    private static Interval iv(LocalDate d, int sh, int sm, int dayOffset, int eh, int em) {
        return new Interval(at(d, sh, sm), at(d.plusDays(dayOffset), eh, em));
    }

    private static Interval lunch(LocalDate d) {
        return iv(d, 12, 0, 0, 13, 0);
    }

    private static DayInput day(LocalDate d, DayKind kind, DayKind next, List<Interval> work, List<Interval> breaks,
            Leave leave) {
        return new DayInput(d, kind, next, kind == DayKind.WORKDAY ? 480 : 0, work, breaks, leave, RULES);
    }

    private static DayResult calc(DayKind kind, DayKind next, List<Interval> work, List<Interval> breaks) {
        return WorkHoursCalculator.calculateDay(day(MON, kind, next, work, breaks, null));
    }

    @Test
    void 평일_9시_18시_점심_1시간은_소정_8시간() {
        DayResult r = calc(DayKind.WORKDAY, DayKind.WORKDAY, List.of(iv(MON, 9, 0, 0, 18, 0)), List.of(lunch(MON)));
        assertThat(r.workedMinutes()).isEqualTo(480);
        assertThat(r.breakMinutes()).isEqualTo(60);
        assertThat(r.regularMinutes()).isEqualTo(480);
        assertThat(r.overtimeMinutes()).isZero();
        assertThat(r.nightMinutes()).isZero();
        assertThat(r.holidayMinutes()).isZero();
    }

    @Test
    void 하루_기준시간을_넘은_분은_연장() {
        DayResult r = calc(DayKind.WORKDAY, DayKind.WORKDAY, List.of(iv(MON, 9, 0, 0, 21, 0)), List.of(lunch(MON)));
        assertThat(r.workedMinutes()).isEqualTo(660);
        assertThat(r.regularMinutes()).isEqualTo(480);
        assertThat(r.overtimeMinutes()).isEqualTo(180);
    }

    @Test
    void 야간은_연장과_겹쳐서_따로_센다() {
        DayResult r = calc(DayKind.WORKDAY, DayKind.WORKDAY, List.of(iv(MON, 9, 0, 0, 23, 30)), List.of(lunch(MON)));
        assertThat(r.overtimeMinutes()).isEqualTo(330);
        assertThat(r.nightMinutes()).isEqualTo(90);
    }

    @Test
    void 기준시간_정각에_끝나면_연장_0분_1분_넘으면_1분() {
        assertThat(calc(DayKind.WORKDAY, DayKind.WORKDAY, List.of(iv(MON, 9, 0, 0, 17, 0)), List.of())
                .overtimeMinutes()).isZero();
        assertThat(calc(DayKind.WORKDAY, DayKind.WORKDAY, List.of(iv(MON, 9, 0, 0, 17, 1)), List.of())
                .overtimeMinutes()).isEqualTo(1);
    }

    @Test
    void 야간_경계_22시_정각과_6시_정각() {
        assertThat(calc(DayKind.WORKDAY, DayKind.WORKDAY, List.of(iv(MON, 14, 0, 0, 22, 0)), List.of())
                .nightMinutes()).isZero();
        assertThat(calc(DayKind.WORKDAY, DayKind.WORKDAY, List.of(iv(MON, 6, 0, 0, 14, 0)), List.of())
                .nightMinutes()).isZero();
        assertThat(calc(DayKind.WORKDAY, DayKind.WORKDAY, List.of(iv(MON, 5, 59, 0, 14, 0)), List.of())
                .nightMinutes()).isEqualTo(1);
    }

    @Test
    void 자정을_넘는_근무는_시작한_날의_하루로_본다() {
        // 22:00 ~ 다음 날 07:00, 휴게 02:00~03:00
        DayResult r = calc(DayKind.WORKDAY, DayKind.WORKDAY, List.of(iv(MON, 22, 0, 1, 7, 0)),
                List.of(new Interval(at(MON.plusDays(1), 2, 0), at(MON.plusDays(1), 3, 0))));
        assertThat(r.workDate()).isEqualTo(MON);
        assertThat(r.workedMinutes()).isEqualTo(480);
        assertThat(r.regularMinutes()).isEqualTo(480);
        assertThat(r.overtimeMinutes()).isZero();
        assertThat(r.nightMinutes()).isEqualTo(420);
    }

    @Test
    void 이른_새벽_출근은_전날_밤부터_이어진_야간_창으로_센다() {
        DayResult r = calc(DayKind.WORKDAY, DayKind.WORKDAY, List.of(iv(MON, 4, 0, 0, 12, 0)), List.of());
        assertThat(r.nightMinutes()).isEqualTo(120);
    }

    @Test
    void 휴게가_겹치면_한_번만_빼고_근로_밖_휴게는_빼지_않는다() {
        DayResult r = calc(DayKind.WORKDAY, DayKind.WORKDAY, List.of(iv(MON, 9, 0, 0, 18, 0)),
                List.of(lunch(MON), iv(MON, 12, 30, 0, 13, 30), iv(MON, 19, 0, 0, 20, 0)));
        assertThat(r.breakMinutes()).isEqualTo(90);
        assertThat(r.workedMinutes()).isEqualTo(450);
    }

    @Test
    void 근로_구간이_겹치면_합친다() {
        DayResult r = calc(DayKind.WORKDAY, DayKind.WORKDAY,
                List.of(iv(MON, 9, 0, 0, 12, 0), iv(MON, 11, 0, 0, 14, 0)), List.of());
        assertThat(r.workedMinutes()).isEqualTo(300);
    }

    @Test
    void 휴일근로는_기준시간_이내와_초과로_나누고_연장에_넣지_않는다() {
        DayResult r = calc(DayKind.HOLIDAY, DayKind.WORKDAY, List.of(iv(MON, 9, 0, 0, 20, 0)), List.of(lunch(MON)));
        assertThat(r.workedMinutes()).isEqualTo(600);
        assertThat(r.holidayMinutes()).isEqualTo(480);
        assertThat(r.holidayExcessMinutes()).isEqualTo(120);
        assertThat(r.overtimeMinutes()).isZero();
        assertThat(r.regularMinutes()).isZero();
    }

    @Test
    void 평일_근무가_자정을_넘어_휴일로_이어지면_0시_이후는_휴일근로() {
        DayResult r = calc(DayKind.WORKDAY, DayKind.HOLIDAY, List.of(iv(MON, 20, 0, 1, 2, 0)), List.of());
        assertThat(r.regularMinutes()).isEqualTo(240);
        assertThat(r.holidayMinutes()).isEqualTo(120);
        assertThat(r.nightMinutes()).isEqualTo(240);
    }

    @Test
    void 휴일_근무가_자정을_넘어_평일로_이어지면_0시_이후는_평일근로() {
        DayResult r = calc(DayKind.HOLIDAY, DayKind.WORKDAY, List.of(iv(MON, 18, 0, 1, 3, 0)), List.of());
        assertThat(r.holidayMinutes()).isEqualTo(360);
        assertThat(r.regularMinutes()).isEqualTo(180);
        assertThat(r.overtimeMinutes()).isZero();
    }

    @Test
    void 휴무일_근로는_휴일근로가_아니다() {
        DayResult r = calc(DayKind.OFFDAY, DayKind.HOLIDAY, List.of(iv(MON, 9, 0, 0, 13, 0)), List.of());
        assertThat(r.regularMinutes()).isEqualTo(240);
        assertThat(r.holidayMinutes()).isZero();
        assertThat(r.contractMinutes()).isZero();
    }

    @Test
    void 오전_반차일은_실제로_일한_구간만_세고_휴가는_연장_판단에_넣지_않는다() {
        // 오전 반차(240분) 후 14:00~23:00 근무, 점심 휴게는 근로 밖
        DayResult r = WorkHoursCalculator.calculateDay(day(MON, DayKind.WORKDAY, DayKind.WORKDAY,
                List.of(iv(MON, 14, 0, 0, 23, 0)), List.of(lunch(MON)), new Leave(LeaveKind.HALF_DAY_AM, 240)));
        assertThat(r.leaveKind()).isEqualTo(LeaveKind.HALF_DAY_AM);
        assertThat(r.leaveMinutes()).isEqualTo(240);
        assertThat(r.breakMinutes()).isZero();
        assertThat(r.workedMinutes()).isEqualTo(540);
        assertThat(r.overtimeMinutes()).isEqualTo(60);
        assertThat(r.nightMinutes()).isEqualTo(60);
    }

    @Test
    void 오후_반차일은_오전_근로만_센다() {
        DayResult r = WorkHoursCalculator.calculateDay(day(MON, DayKind.WORKDAY, DayKind.WORKDAY,
                List.of(iv(MON, 9, 0, 0, 13, 0)), List.of(lunch(MON)), new Leave(LeaveKind.HALF_DAY_PM, 240)));
        assertThat(r.workedMinutes()).isEqualTo(180);
        assertThat(r.breakMinutes()).isEqualTo(60);
        assertThat(r.leaveMinutes()).isEqualTo(240);
    }

    @Test
    void 기록이_없는_날은_모두_0분() {
        DayResult r = WorkHoursCalculator.calculateDay(day(MON, DayKind.WORKDAY, DayKind.WORKDAY, List.of(),
                List.of(lunch(MON)), new Leave(LeaveKind.FULL_DAY, 480)));
        assertThat(r.workedMinutes()).isZero();
        assertThat(r.breakMinutes()).isZero();
        assertThat(r.leaveMinutes()).isEqualTo(480);
    }

    private static List<DayInput> week(int[] workMinutesFrom9, DayKind[] kinds, Leave[] leaves) {
        List<DayInput> out = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = MON.plusDays(i);
            List<Interval> work = workMinutesFrom9[i] == 0 ? List.of()
                    : List.of(new Interval(at(d, 9, 0), at(d, 9, 0).plusMinutes(workMinutesFrom9[i])));
            DayKind next = i < 6 ? kinds[i + 1] : DayKind.WORKDAY;
            out.add(day(d, kinds[i], next, work, List.of(), leaves[i]));
        }
        return out;
    }

    private static final DayKind[] FIVE_DAY = {DayKind.WORKDAY, DayKind.WORKDAY, DayKind.WORKDAY, DayKind.WORKDAY,
        DayKind.WORKDAY, DayKind.OFFDAY, DayKind.HOLIDAY};

    @Test
    void 주_기준시간을_넘은_휴무일_근로는_연장으로_옮긴다() {
        List<DayResult> r = WorkHoursCalculator.calculateWeek(
                week(new int[] {480, 480, 480, 480, 480, 240, 0}, FIVE_DAY, new Leave[7]));
        assertThat(r.get(5).regularMinutes()).isZero();
        assertThat(r.get(5).overtimeMinutes()).isEqualTo(240);
        assertThat(r.subList(0, 5)).allMatch(d -> d.overtimeMinutes() == 0 && d.regularMinutes() == 480);
    }

    @Test
    void 일_연장은_주_합계에서_빼고_센다() {
        // 월 600분(연장 120) + 화~목 480분 + 금 360분 + 토 120분 → 연장 제외 합계 2400분(입력 기준 이하), 토요일은 연장 아님
        List<DayResult> r = WorkHoursCalculator.calculateWeek(
                week(new int[] {600, 480, 480, 480, 360, 120, 0}, FIVE_DAY, new Leave[7]));
        assertThat(r.get(0).overtimeMinutes()).isEqualTo(120);
        assertThat(r.get(5).regularMinutes()).isEqualTo(120);
        assertThat(r.get(5).overtimeMinutes()).isZero();
    }

    @Test
    void 휴가일은_실근로가_아니라_주_합계에_넣지_않는다() {
        Leave[] leaves = new Leave[7];
        leaves[0] = new Leave(LeaveKind.FULL_DAY, 480);
        List<DayResult> r = WorkHoursCalculator.calculateWeek(
                week(new int[] {0, 480, 480, 480, 480, 480, 0}, FIVE_DAY, leaves));
        assertThat(r.get(5).regularMinutes()).isEqualTo(480);
        assertThat(r.get(5).overtimeMinutes()).isZero();
    }

    @Test
    void 휴일근로는_주_합계에_넣지_않는다() {
        List<DayResult> r = WorkHoursCalculator.calculateWeek(
                week(new int[] {480, 480, 480, 480, 480, 0, 480}, FIVE_DAY, new Leave[7]));
        assertThat(r.get(6).holidayMinutes()).isEqualTo(480);
        assertThat(r).allMatch(d -> d.overtimeMinutes() == 0);
    }

    @Test
    void 주_기준시간을_걸치는_날은_넘는_분만_옮긴다() {
        List<DayResult> r = WorkHoursCalculator.calculateWeek(
                week(new int[] {480, 480, 480, 480, 420, 120, 0}, FIVE_DAY, new Leave[7]));
        assertThat(r.get(5).regularMinutes()).isEqualTo(60);
        assertThat(r.get(5).overtimeMinutes()).isEqualTo(60);
    }

    @Test
    void 결과는_항상_실근로_합이_맞다() {
        List<DayResult> r = WorkHoursCalculator.calculateWeek(
                week(new int[] {700, 600, 480, 480, 480, 300, 600}, FIVE_DAY, new Leave[7]));
        assertThat(r).allMatch(d -> d.workedMinutes()
                == d.regularMinutes() + d.overtimeMinutes() + d.holidayMinutes() + d.holidayExcessMinutes());
    }

    @Test
    void 한_주가_아닌_날이나_같은_날이_두_번이면_거부() {
        DayInput mon = day(MON, DayKind.WORKDAY, DayKind.WORKDAY, List.of(), List.of(), null);
        DayInput nextMon = day(MON.plusDays(7), DayKind.WORKDAY, DayKind.WORKDAY, List.of(), List.of(), null);
        assertThatThrownBy(() -> WorkHoursCalculator.calculateWeek(List.of(mon, nextMon)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkHoursCalculator.calculateWeek(List.of(mon, mon)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 근무일_다음_날_24시를_넘는_구간은_거부() {
        assertThatThrownBy(() -> day(MON, DayKind.WORKDAY, DayKind.WORKDAY,
                List.of(iv(MON, 9, 0, 2, 0, 1)), List.of(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Interval(at(MON, 9, 0), at(MON, 9, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 기준값이_잘못되면_거부() {
        assertThatThrownBy(() -> new Rules(0, 2400, 480, 1320, 360)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Rules(480, 2400, 480, 1320, 1320)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Rules(480, 2400, 480, 1440, 360)).isInstanceOf(IllegalArgumentException.class);
    }
}
