package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassehr.attendance.WorkPatternRules.Code;
import com.glassehr.attendance.WorkPatternRules.Limits;
import com.glassehr.attendance.WorkPatternRules.Result;
import com.glassehr.attendance.WorkPatternRules.Spec;
import com.glassehr.attendance.WorkPatternRules.Violation;
import org.junit.jupiter.api.Test;

/**
 * 근무유형 검사 로직(비교·계산 방식) 테스트.
 *
 * <p>여기 쓰는 기준값(1일 7시간, 1주 30시간, 3시간→20분, 6시간→45분, 주휴일 2일)은 비교 로직 확인용 <b>임의 값이며 법령 수치가 아니다</b>.
 * 법정 기준값으로 판정한 정답은 tests/legal-fixtures 의 사람 검수 파일로만 검증한다(AGENTS.md).
 */
class WorkPatternRulesTest {

    /** 임의 기준값(법령 수치 아님). */
    private static final Limits FAKE = new Limits(7 * 60, 30 * 60, 3 * 60, 20, 6 * 60, 45, 2);

    private static final String FIVE_DAYS_TWO_HOLIDAYS = "WWWWWHH";

    private static Spec spec(int start, int end, Integer breakStart, int breakMin, String days) {
        return new Spec(start, end, breakStart, breakMin, days);
    }

    private static int hm(int h, int m) {
        return h * 60 + m;
    }

    private static boolean has(Result r, Code c) {
        return r.violations().stream().map(Violation::code).anyMatch(c::equals);
    }

    @Test
    void 소정근로시간은_시업부터_종업까지에서_휴게를_뺀다() {
        // 06:00~13:45, 휴게 09:00부터 45분, 근무일 4일 → 1일 7시간, 1주 28시간
        Result r = WorkPatternRules.check(spec(hm(6, 0), hm(13, 45), hm(9, 0), 45, "WWWWOHH"), FAKE);
        assertThat(r.dailyContractMinutes()).isEqualTo(hm(7, 0));
        assertThat(r.weeklyContractMinutes()).isEqualTo(hm(28, 0));
        assertThat(r.violations()).isEmpty();
    }

    @Test
    void 일_한도와_같으면_통과하고_1분이라도_넘으면_거부() {
        Result same = WorkPatternRules.check(spec(hm(9, 0), hm(16, 45), hm(12, 0), 45, "WOOOOHH"), FAKE);
        assertThat(same.dailyContractMinutes()).isEqualTo(FAKE.dailyMaxMinutes());
        assertThat(has(same, Code.DAILY_LIMIT_EXCEEDED)).isFalse();

        Result over = WorkPatternRules.check(spec(hm(9, 0), hm(16, 46), hm(12, 0), 45, "WOOOOHH"), FAKE);
        assertThat(has(over, Code.DAILY_LIMIT_EXCEEDED)).isTrue();
        assertThat(over.ok()).isFalse();
    }

    @Test
    void 주_한도는_1일_소정근로시간_곱하기_근무일수로_본다() {
        // 1일 6시간(휴게 45분) x 5일 = 30시간 → 통과, x 6일(휴일 1일이라 주휴 부족도 함께) = 36시간 → 초과
        Result five = WorkPatternRules.check(spec(hm(9, 0), hm(15, 45), hm(12, 0), 45, FIVE_DAYS_TWO_HOLIDAYS), FAKE);
        assertThat(five.weeklyContractMinutes()).isEqualTo(FAKE.weeklyMaxMinutes());
        assertThat(five.violations()).isEmpty();

        Result six = WorkPatternRules.check(spec(hm(9, 0), hm(15, 45), hm(12, 0), 45, "WWWWWWH"), FAKE);
        assertThat(has(six, Code.WEEKLY_LIMIT_EXCEEDED)).isTrue();
        assertThat(has(six, Code.DAILY_LIMIT_EXCEEDED)).isFalse();
    }

    @Test
    void 휴게는_근로시간_구간별_기준_이상이면_최소_시간이_필요하다() {
        // 기준 미만: 휴게 없어도 통과
        Result shortWork = WorkPatternRules.check(spec(hm(9, 0), hm(11, 59), null, 0, FIVE_DAYS_TWO_HOLIDAYS), FAKE);
        assertThat(has(shortWork, Code.BREAK_INSUFFICIENT)).isFalse();
        // 짧은 구간 기준과 같은 근로시간: 짧은 구간 휴게 필요
        Result atShort = WorkPatternRules.check(spec(hm(9, 0), hm(12, 0), null, 0, FIVE_DAYS_TWO_HOLIDAYS), FAKE);
        assertThat(has(atShort, Code.BREAK_INSUFFICIENT)).isTrue();
        Result atShortOk = WorkPatternRules.check(spec(hm(9, 0), hm(12, 20), hm(10, 0), 20, FIVE_DAYS_TWO_HOLIDAYS), FAKE);
        assertThat(atShortOk.dailyContractMinutes()).isEqualTo(hm(3, 0));
        assertThat(has(atShortOk, Code.BREAK_INSUFFICIENT)).isFalse();
        // 긴 구간 기준 이상: 짧은 구간 휴게로는 부족
        Result atLong = WorkPatternRules.check(spec(hm(9, 0), hm(15, 20), hm(12, 0), 20, "WOOOOHH"), FAKE);
        assertThat(atLong.dailyContractMinutes()).isEqualTo(hm(6, 0));
        assertThat(has(atLong, Code.BREAK_INSUFFICIENT)).isTrue();
        Result atLongOk = WorkPatternRules.check(spec(hm(9, 0), hm(15, 45), hm(12, 0), 45, "WOOOOHH"), FAKE);
        assertThat(has(atLongOk, Code.BREAK_INSUFFICIENT)).isFalse();
    }

    @Test
    void 필요_휴게_구간_선택() {
        assertThat(WorkPatternRules.requiredBreakMinutes(hm(2, 59), FAKE)).isZero();
        assertThat(WorkPatternRules.requiredBreakMinutes(hm(3, 0), FAKE)).isEqualTo(20);
        assertThat(WorkPatternRules.requiredBreakMinutes(hm(5, 59), FAKE)).isEqualTo(20);
        assertThat(WorkPatternRules.requiredBreakMinutes(hm(6, 0), FAKE)).isEqualTo(45);
    }

    @Test
    void 주휴일이_기준_횟수보다_적으면_거부() {
        Result one = WorkPatternRules.check(spec(hm(9, 0), hm(11, 0), null, 0, "WWWWWOH"), FAKE);
        assertThat(has(one, Code.WEEKLY_HOLIDAY_REQUIRED)).isTrue();
        Result none = WorkPatternRules.check(spec(hm(9, 0), hm(11, 0), null, 0, "WWWWWOO"), FAKE);
        assertThat(has(none, Code.WEEKLY_HOLIDAY_REQUIRED)).isTrue();
        Result two = WorkPatternRules.check(spec(hm(9, 0), hm(11, 0), null, 0, "WWWWWHH"), FAKE);
        assertThat(two.violations()).isEmpty();
    }

    @Test
    void 종업이_시업보다_이르면_다음_날_종업으로_본다() {
        // 22:00 ~ 다음 날 04:45, 휴게 01:00부터 45분 → 6시간
        Result r = WorkPatternRules.check(spec(hm(22, 0), hm(4, 45), hm(1, 0), 45, "WOOOOHH"), FAKE);
        assertThat(r.dailyContractMinutes()).isEqualTo(hm(6, 0));
        assertThat(r.violations()).isEmpty();
    }

    @Test
    void 휴게는_근로시간_도중에_있어야_한다() {
        Result atStart = WorkPatternRules.check(spec(hm(9, 0), hm(15, 45), hm(9, 0), 45, "WOOOOHH"), FAKE);
        assertThat(has(atStart, Code.BREAK_OUTSIDE_WORK)).isTrue();
        Result atEnd = WorkPatternRules.check(spec(hm(9, 0), hm(15, 45), hm(15, 0), 45, "WOOOOHH"), FAKE);
        assertThat(has(atEnd, Code.BREAK_OUTSIDE_WORK)).isTrue();
        Result before = WorkPatternRules.check(spec(hm(9, 0), hm(15, 45), hm(8, 0), 45, "WOOOOHH"), FAKE);
        assertThat(has(before, Code.BREAK_OUTSIDE_WORK)).isTrue();
    }

    @Test
    void 형식_오류는_법정_검사_없이_돌려준다() {
        assertThat(WorkPatternRules.checkStructure(spec(-1, hm(18, 0), null, 0, "WWWWWOH")))
                .extracting(Violation::code).contains(Code.INVALID_TIME);
        assertThat(WorkPatternRules.checkStructure(spec(hm(9, 0), hm(18, 0), null, 0, "WWWWWX")))
                .extracting(Violation::code).contains(Code.INVALID_DAY_TYPES);
        assertThat(WorkPatternRules.checkStructure(spec(hm(9, 0), hm(18, 0), null, 0, "OOOOOHH")))
                .extracting(Violation::code).contains(Code.NO_WORK_DAY);
        assertThat(WorkPatternRules.checkStructure(spec(hm(9, 0), hm(18, 0), null, 30, "WWWWWOH")))
                .extracting(Violation::code).contains(Code.INVALID_BREAK);
        assertThat(WorkPatternRules.checkStructure(spec(hm(9, 0), hm(18, 0), hm(12, 0), 0, "WWWWWOH")))
                .extracting(Violation::code).contains(Code.INVALID_BREAK);
        Result r = WorkPatternRules.check(spec(hm(9, 0), hm(18, 0), null, 0, null), FAKE);
        assertThat(r.violations()).extracting(Violation::code).containsExactly(Code.INVALID_DAY_TYPES);
    }

    @Test
    void 위반_메시지에_근거_조문이_있다() {
        Result r = WorkPatternRules.check(spec(hm(0, 0), hm(23, 0), null, 0, "WWWWWWW"), FAKE);
        assertThat(r.violations()).extracting(Violation::code).contains(
                Code.DAILY_LIMIT_EXCEEDED, Code.WEEKLY_LIMIT_EXCEEDED, Code.BREAK_INSUFFICIENT, Code.WEEKLY_HOLIDAY_REQUIRED);
        assertThat(r.violations()).allSatisfy(v -> assertThat(v.message()).containsPattern("근로기준법 제5[045]조"));
    }

    @Test
    void 시간_표시() {
        assertThat(WorkPatternRules.hm(30)).isEqualTo("30분");
        assertThat(WorkPatternRules.hm(480)).isEqualTo("8시간");
        assertThat(WorkPatternRules.hm(510)).isEqualTo("8시간 30분");
    }
}
