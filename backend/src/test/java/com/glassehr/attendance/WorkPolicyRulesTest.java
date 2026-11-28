package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassehr.attendance.WorkPolicyRules.AllowMode;
import com.glassehr.attendance.WorkPolicyRules.Allowance;
import com.glassehr.attendance.WorkPolicyRules.MissingOut;
import com.glassehr.attendance.WorkPolicyRules.Policy;
import com.glassehr.attendance.WorkPolicyRules.Rounding;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 근무 정책 검사·적용(#159). 기준값은 테스트 입력(법령 정답 아님). */
class WorkPolicyRulesTest {

    /** 테스트 입력: 1일 480·1주 2400, 휴게 240분→30분·480분→60분, 주휴 1회. */
    private static final WorkPatternRules.Limits LIMITS = new WorkPatternRules.Limits(480, 2400, 240, 30, 480, 60, 1);
    private static final int WEEKLY_OT = 720;
    private static final WorkPatternRules.Spec DAY = new WorkPatternRules.Spec(540, 1080, 720, 60, "WWWWWOH");

    private static Policy with(Allowance ot, Allowance hol, int unit, int min, Rounding r) {
        return new Policy(ot, Allowance.UNLIMITED, hol, true, unit, min, r, 0, 0, true, MissingOut.IN_ONLY, true);
    }

    private static Allowance limit(Integer day, Integer week, Integer month) {
        return new Allowance(AllowMode.LIMIT, day, week, month);
    }

    @Test
    void 인정_단위_올림_내림과_최소_인정() {
        Policy up = with(Allowance.UNLIMITED, Allowance.UNLIMITED, 15, 0, Rounding.UP);
        assertThat(WorkPolicyRules.recognize(7, up)).isEqualTo(15);
        assertThat(WorkPolicyRules.recognize(30, up)).isEqualTo(30);
        assertThat(WorkPolicyRules.recognize(0, up)).isZero();
        Policy down = with(Allowance.UNLIMITED, Allowance.UNLIMITED, 15, 0, Rounding.DOWN);
        assertThat(WorkPolicyRules.recognize(22, down)).isEqualTo(15);
        Policy min = with(Allowance.UNLIMITED, Allowance.UNLIMITED, 15, 10, Rounding.UP);
        assertThat(WorkPolicyRules.recognize(9, min)).isZero();
        assertThat(WorkPolicyRules.recognize(10, min)).isEqualTo(15);
        assertThat(WorkPolicyRules.recognize(7, WorkPolicyRules.SYSTEM_DEFAULT)).isEqualTo(7);
    }

    @Test
    void 야간_올림은_실근로시간을_넘지_않는다() {
        Policy up = with(Allowance.UNLIMITED, Allowance.UNLIMITED, 15, 0, Rounding.UP);
        WorkHoursCalculator.DayResult raw = new WorkHoursCalculator.DayResult(LocalDate.of(2099, 3, 4),
                WorkHoursCalculator.DayKind.WORKDAY, 480, null, 0, 0, 7, 7, 0, 0, 7, 0, 0);

        assertThat(WorkPolicyRules.recognizeDay(raw, up, 480).nightMinutes()).isEqualTo(7);
    }

    @Test
    void 인정_시간을_내려도_법정_한도용_실근로시간은_보존한다() {
        Policy down = with(Allowance.UNLIMITED, Allowance.UNLIMITED, 60, 0, Rounding.DOWN);
        WorkHoursCalculator.DayResult raw = new WorkHoursCalculator.DayResult(LocalDate.of(2099, 3, 5),
                WorkHoursCalculator.DayKind.WORKDAY, 480, null, 0, 0, 487, 480, 0, 7, 0, 0, 0);

        WorkHoursCalculator.DayResult recognized = WorkPolicyRules.recognizeDay(raw, down, 480);

        assertThat(recognized.workedMinutes()).isEqualTo(487);
        assertThat(recognized.overtimeMinutes()).isZero();
    }

    @Test
    void 월_누계를_지원하지_않는_야간_월_한도는_거부한다() {
        Policy d = WorkPolicyRules.SYSTEM_DEFAULT;
        Policy p = new Policy(d.overtime(), limit(null, null, 600), d.holiday(), true, 1, 0, Rounding.UP,
                0, 0, true, MissingOut.IN_ONLY, true);

        assertThat(WorkPolicyRules.check(p, null, LIMITS, WEEKLY_OT))
                .extracting(WorkPatternRules.Violation::message)
                .containsExactly("야간근로 월 한도는 월 누계 집계를 지원한 뒤 설정할 수 있어요");
    }

    @Test
    void 법정_1주_연장_한도를_넘는_정책은_거부한다() {
        assertThat(WorkPolicyRules.check(with(limit(null, 720, null), Allowance.UNLIMITED, 1, 0, Rounding.UP), null,
                LIMITS, WEEKLY_OT)).isEmpty();
        assertThat(WorkPolicyRules.check(with(limit(null, 721, null), Allowance.UNLIMITED, 1, 0, Rounding.UP), null,
                LIMITS, WEEKLY_OT)).extracting(WorkPatternRules.Violation::code)
                .containsExactly(WorkPatternRules.Code.POLICY_OVERTIME_LIMIT_EXCEEDED);
        // 휴일근로도 1주 실근로에 포함 — 연장·휴일 1주 한도의 합도 법정 한도 이내
        assertThat(WorkPolicyRules.check(with(limit(null, 600, null), limit(null, 240, null), 1, 0, Rounding.UP),
                null, LIMITS, WEEKLY_OT)).extracting(WorkPatternRules.Violation::code)
                .containsExactly(WorkPatternRules.Code.POLICY_OVERTIME_LIMIT_EXCEEDED);
    }

    @Test
    void 일_연장_한도까지_일할_때_휴게가_모자라면_거부한다() {
        // 소정 480 + 연장 120 = 600분 → 필요 휴게 60분. 근무유형 휴게 60분이면 통과.
        assertThat(WorkPolicyRules.check(with(limit(120, null, null), Allowance.UNLIMITED, 1, 0, Rounding.UP), DAY,
                LIMITS, WEEKLY_OT)).isEmpty();
        // 휴게 30분 근무유형(소정 210분 이하는 아님): 09:00~13:00, 휴게 30분 → 소정 210, +연장 300 = 510 → 60분 필요
        WorkPatternRules.Spec shortBreak = new WorkPatternRules.Spec(540, 780, 660, 30, "WWWWWOH");
        assertThat(WorkPolicyRules.check(with(limit(300, null, null), Allowance.UNLIMITED, 1, 0, Rounding.UP),
                shortBreak, LIMITS, WEEKLY_OT)).extracting(WorkPatternRules.Violation::code)
                .containsExactly(WorkPatternRules.Code.POLICY_BREAK_INSUFFICIENT);
    }

    @Test
    void 형식_오류() {
        assertThat(WorkPolicyRules.check(with(new Allowance(AllowMode.DENY, 60, null, null), Allowance.UNLIMITED, 1,
                0, Rounding.UP), null, LIMITS, WEEKLY_OT)).isNotEmpty();
        assertThat(WorkPolicyRules.check(with(limit(null, null, null), Allowance.UNLIMITED, 1, 0, Rounding.UP), null,
                LIMITS, WEEKLY_OT)).isNotEmpty();
        assertThat(WorkPolicyRules.check(with(limit(200, 100, null), Allowance.UNLIMITED, 1, 0, Rounding.UP), null,
                LIMITS, WEEKLY_OT)).isNotEmpty();
        assertThat(WorkPolicyRules.check(with(Allowance.UNLIMITED, Allowance.UNLIMITED, 0, 0, Rounding.UP), null,
                LIMITS, WEEKLY_OT)).isNotEmpty();
    }

    @Test
    void 정시_인정_범위와_이른_출근() {
        LocalDateTime s = LocalDateTime.of(2099, 3, 4, 9, 0);
        LocalDateTime e = LocalDateTime.of(2099, 3, 4, 18, 0);
        Policy grace = new Policy(Allowance.UNLIMITED, Allowance.UNLIMITED, Allowance.UNLIMITED, true, 1, 0,
                Rounding.UP, 5, 10, false, MissingOut.IN_ONLY, true);
        var r = WorkPolicyRules.record(grace, s.plusMinutes(4), e.plusMinutes(9), s, e);
        assertThat(r.start()).isEqualTo(s);
        assertThat(r.end()).isEqualTo(e);
        r = WorkPolicyRules.record(grace, s.minusMinutes(40), e.plusMinutes(30), s, e);
        assertThat(r.start()).isEqualTo(s);
        assertThat(r.end()).isEqualTo(e.plusMinutes(30));
    }
}
