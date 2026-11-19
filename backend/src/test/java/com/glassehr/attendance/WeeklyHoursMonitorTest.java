package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.attendance.WeeklyHoursMonitor.Limits;
import com.glassehr.attendance.WeeklyHoursMonitor.Status;
import com.glassehr.attendance.WeeklyHoursMonitor.Week;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 주간 한도 판정(합산·비교) 규칙 테스트. 한도 값은 법령 기준값이 아닌 임의의 입력값이다
 * (실제 값은 legal_rate 에서 읽으며, 여기서는 합산·경계 비교 동작만 본다). 날짜는 가짜 값(2099년).
 */
class WeeklyHoursMonitorTest {

    /** 기준 600분 + 연장 120분 = 한도 720분, 남은 60분 이하이면 근접. */
    private static final Limits LIMITS = new Limits(600, 120, 60);
    private static final LocalDate MON = LocalDate.of(2099, 3, 2);

    private static DailyWorkSummaryRow day(int offset, int worked, int overtime, int holiday, int holidayExcess, String issues) {
        return new DailyWorkSummaryRow(1L, MON.plusDays(offset), "WORKDAY", 1L, 480, null, 0, null, null, 0,
                worked, worked - overtime - holiday - holidayExcess, 0, overtime, 0, holiday, holidayExcess,
                null, null, null, null, issues, "PREVIEW", null);
    }

    @Test
    void 근로시간이_없으면_정상() {
        Week w = WeeklyHoursMonitor.judge(List.of(), LIMITS);
        assertThat(w.workedMinutes()).isZero();
        assertThat(w.remainingMinutes()).isEqualTo(720);
        assertThat(w.status()).isEqualTo(Status.NORMAL);
    }

    @Test
    void 휴일근로도_누적에_넣는다() {
        Week w = WeeklyHoursMonitor.judge(List.of(day(0, 300, 0, 0, 0, null), day(6, 200, 0, 150, 50, null)), LIMITS);
        assertThat(w.workedMinutes()).isEqualTo(500);
        assertThat(w.holidayMinutes()).isEqualTo(200);
        assertThat(w.limitOvertimeMinutes()).isZero();
        assertThat(w.status()).isEqualTo(Status.NORMAL);
    }

    @Test
    void 남은_시간이_경고_기준과_같으면_근접이고_한도와_같아도_초과가_아니다() {
        assertThat(WeeklyHoursMonitor.judge(List.of(day(0, 659, 0, 0, 0, null)), LIMITS).status()).isEqualTo(Status.NORMAL);
        assertThat(WeeklyHoursMonitor.judge(List.of(day(0, 660, 0, 0, 0, null)), LIMITS).status()).isEqualTo(Status.NEAR);
        Week atLimit = WeeklyHoursMonitor.judge(List.of(day(0, 720, 0, 0, 0, null)), LIMITS);
        assertThat(atLimit.status()).isEqualTo(Status.NEAR);
        assertThat(atLimit.remainingMinutes()).isZero();
        Week over = WeeklyHoursMonitor.judge(List.of(day(0, 721, 0, 0, 0, null)), LIMITS);
        assertThat(over.status()).isEqualTo(Status.OVER);
        assertThat(over.remainingMinutes()).isEqualTo(-1);
    }

    @Test
    void 제53조_한도는_일별_연장합이_아니라_주간_기준_초과분으로_판단한다() {
        // 일별 연장 합은 121분이지만 주간 총 실근로 500분이 주간 기준 600분 이내이므로 한도 대상 연장은 0분이다.
        Week w = WeeklyHoursMonitor.judge(List.of(day(0, 250, 61, 0, 0, null), day(1, 250, 60, 0, 0, null)), LIMITS);
        assertThat(w.overtimeMinutes()).isEqualTo(121);
        assertThat(w.limitOvertimeMinutes()).isZero();
        assertThat(w.overtimeRemainingMinutes()).isEqualTo(120);
        assertThat(w.status()).isEqualTo(Status.NORMAL);
    }

    @Test
    void 연장_남은_시간이_경고_기준_이하면_근접() {
        Week w = WeeklyHoursMonitor.judge(List.of(day(0, 660, 60, 0, 0, null)), LIMITS);
        assertThat(w.overtimeRemainingMinutes()).isEqualTo(60);
        assertThat(w.status()).isEqualTo(Status.NEAR);
    }

    @Test
    void 퇴근_누락이_있으면_표시한다() {
        Week w = WeeklyHoursMonitor.judge(List.of(day(0, 0, 0, 0, 0, "X," + WorkHoursService.ISSUE_MISSING_OUT)), LIMITS);
        assertThat(w.missingOut()).isTrue();
    }

    @Test
    void 한도_값이_잘못되면_거부() {
        assertThatThrownBy(() -> new Limits(0, 120, 60)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Limits(600, -1, 60)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Limits(600, 120, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
