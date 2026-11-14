package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassehr.attendance.AttendancePunch.Check;
import com.glassehr.attendance.AttendancePunch.Type;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 중복 출근·출근 없는 퇴근 판정과 하루 요약. 시각은 가짜 값(2099년). */
class PunchRulesTest {

    private static final LocalDateTime D = LocalDateTime.of(2099, 3, 2, 0, 0);

    private static AttendancePunch p(Type t, int h, int m) {
        return new AttendancePunch(5L, t, D.withHour(h).withMinute(m), null, null, AttendancePunch.Source.WEB, "emp1");
    }

    @Test
    void 첫_출근은_정상이고_다시_출근하면_중복_출근() {
        assertThat(PunchRules.check(List.of(), Type.IN)).isNull();
        assertThat(PunchRules.check(List.of(p(Type.IN, 9, 0)), Type.IN)).isEqualTo(Check.DUPLICATE_IN);
        // 퇴근 뒤 다시 출근해도 같은 근무일 두 번째 출근이다(출근 시각은 첫 출근).
        assertThat(PunchRules.check(List.of(p(Type.IN, 9, 0), p(Type.OUT, 12, 0)), Type.IN))
                .isEqualTo(Check.DUPLICATE_IN);
    }

    @Test
    void 출근_없이_퇴근하면_표시하고_퇴근을_여러_번_누르는_것은_정상() {
        assertThat(PunchRules.check(List.of(), Type.OUT)).isEqualTo(Check.OUT_WITHOUT_IN);
        assertThat(PunchRules.check(List.of(p(Type.OUT, 18, 0)), Type.OUT)).isEqualTo(Check.OUT_WITHOUT_IN);
        assertThat(PunchRules.check(List.of(p(Type.IN, 9, 0)), Type.OUT)).isNull();
        assertThat(PunchRules.check(List.of(p(Type.IN, 9, 0), p(Type.OUT, 18, 0)), Type.OUT)).isNull();
    }

    @Test
    void 기록이_없으면_출근_시각과_경과_시간이_없다() {
        PunchRules.Summary s = PunchRules.summarize(List.of(), D.withHour(10));
        assertThat(s.firstInAt()).isNull();
        assertThat(s.working()).isFalse();
        assertThat(s.elapsedMinutes()).isNull();
    }

    @Test
    void 근무_중이면_첫_출근부터_지금까지_분을_버림으로() {
        PunchRules.Summary s = PunchRules.summarize(List.of(p(Type.IN, 9, 0), p(Type.IN, 9, 5)),
                D.withHour(10).withMinute(30).withSecond(59));
        assertThat(s.firstInAt()).isEqualTo(D.withHour(9));
        assertThat(s.working()).isTrue();
        assertThat(s.lastOutAt()).isNull();
        assertThat(s.elapsedMinutes()).isEqualTo(90L);
    }

    @Test
    void 퇴근했으면_마지막_퇴근까지이고_시간이_지나도_늘지_않는다() {
        List<AttendancePunch> l = List.of(p(Type.IN, 9, 0), p(Type.OUT, 17, 50), p(Type.OUT, 18, 0));
        PunchRules.Summary s = PunchRules.summarize(l, D.withHour(23));
        assertThat(s.working()).isFalse();
        assertThat(s.lastOutAt()).isEqualTo(D.withHour(18));
        assertThat(s.elapsedMinutes()).isEqualTo(540L);
    }

    @Test
    void 퇴근_뒤_다시_출근하면_근무_중이고_첫_출근부터_센다() {
        List<AttendancePunch> l = List.of(p(Type.IN, 9, 0), p(Type.OUT, 12, 0), p(Type.IN, 13, 0));
        PunchRules.Summary s = PunchRules.summarize(l, D.withHour(14));
        assertThat(s.working()).isTrue();
        assertThat(s.lastOutAt()).isNull();
        assertThat(s.elapsedMinutes()).isEqualTo(300L);
    }

    @Test
    void 출근_없는_퇴근만_있으면_출근_시각이_없다() {
        PunchRules.Summary s = PunchRules.summarize(List.of(p(Type.OUT, 18, 0)), D.withHour(19));
        assertThat(s.firstInAt()).isNull();
        assertThat(s.lastOutAt()).isNull();
        assertThat(s.working()).isFalse();
        assertThat(s.elapsedMinutes()).isNull();
    }

    @Test
    void 판정마다_안내_문구가_있다() {
        for (Check c : Check.values()) {
            assertThat(PunchRules.message(c)).isNotBlank();
        }
        assertThat(PunchRules.message(null)).isNull();
    }
}
