package com.glassehr.overtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.glassehr.approval.ApprovalException;
import com.glassehr.attendance.DailyWorkSummaryMapper;
import com.glassehr.attendance.DailyWorkSummaryRow;
import com.glassehr.attendance.WorkPattern;
import com.glassehr.attendance.WorkPatternRepository;
import com.glassehr.attendance.WorkPatternRules;
import com.glassehr.legalrate.LegalRateException;
import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 한도 검사 동작(합산 방식·되돌림·기준값 누락) 검사. 기준값은 법령 값이 아닌 시험용 값
 * (한도 1시간, 1주 기준 2시간)을 mock 으로 넣는다. 법정 값 정답은 {@link WeeklyOvertimeLimitFixtureTest}.
 */
class OvertimeLimitCheckerTest {

    static final long EMP = 7L;
    static final LocalDate MON = LocalDate.of(2099, 3, 9);

    LegalRateService legalRates;
    DailyWorkSummaryMapper summaries;
    OvertimeRequestRepository requests;
    WorkPatternRepository patterns;
    OvertimeLimitChecker checker;

    static LegalRateRow rate(String code, int hours) {
        return new LegalRateRow(code, LocalDate.of(2000, 1, 1), null, BigDecimal.valueOf(hours), "HOURS", "시험", null);
    }

    @BeforeEach
    void setUp() {
        legalRates = mock(LegalRateService.class);
        summaries = mock(DailyWorkSummaryMapper.class);
        requests = mock(OvertimeRequestRepository.class);
        patterns = mock(WorkPatternRepository.class);
        checker = new OvertimeLimitChecker(legalRates, summaries, requests, patterns);
        when(legalRates.getEffective(eq(LegalRateItem.WEEKLY_OVERTIME_LIMIT_HOURS), any(LocalDate.class)))
                .thenReturn(rate("WEEKLY_OVERTIME_LIMIT_HOURS", 1));
        when(legalRates.getEffective(eq(LegalRateItem.WEEKLY_STANDARD_WORK_HOURS), any(LocalDate.class)))
                .thenReturn(rate("WEEKLY_STANDARD_WORK_HOURS", 2));
        when(summaries.findRange(anyLong(), any(), any())).thenReturn(List.of());
        when(requests.findByEmpIdAndWorkDateBetweenAndStatusIn(anyLong(), any(), any(), any())).thenReturn(List.of());
    }

    static OvertimeRequest req(long id, OvertimeRequest.Kind kind, LocalDate d, int minutes) {
        LocalDateTime start = d.atStartOfDay();
        OvertimeRequest r = new OvertimeRequest(EMP, kind, start, start.plusMinutes(minutes), 0, "사유",
                start.minusDays(1));
        ReflectionTestUtils.setField(r, "requestId", id);
        return r;
    }

    static DailyWorkSummaryRow actual(LocalDate d, String dayKind, int worked) {
        return actual(d, dayKind, worked, null, null);
    }

    static DailyWorkSummaryRow actual(LocalDate d, String dayKind, int worked, LocalDateTime start,
            LocalDateTime end) {
        return new DailyWorkSummaryRow(EMP, d, dayKind, 1L, 480, null, 0, start, end, 0, worked, 0, 0,
                0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, "t",
                LocalDateTime.now());
    }

    private void approved(OvertimeRequest... rs) {
        when(requests.findByEmpIdAndWorkDateBetweenAndStatusIn(EMP, MON, MON.plusDays(6),
                List.of(OvertimeRequest.Status.APPROVED))).thenReturn(List.of(rs));
    }

    @Test
    void 이미_승인된_신청과_이번_신청을_더해_1주_기준을_넘은_시간이_한도를_넘으면_승인하지_않는다() {
        approved(req(1, OvertimeRequest.Kind.OVERTIME, MON, 100));

        OvertimeLimitChecker.Result ok = checker.check(req(2, OvertimeRequest.Kind.OVERTIME, MON.plusDays(1), 80));
        assertThat(ok.weeklyWorkedMinutes()).isEqualTo(180);
        assertThat(ok.overtimeMinutes()).isEqualTo(60);
        assertThatThrownBy(() -> checker.check(req(3, OvertimeRequest.Kind.OVERTIME, MON.plusDays(6), 81)))
                .isInstanceOfSatisfying(ApprovalException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY))
                .hasMessageContaining("제53조 제1항");
    }

    @Test
    void 휴일근로_신청도_한도_합계에_넣어_검사한다() {
        approved(req(1, OvertimeRequest.Kind.OVERTIME, MON, 100));
        assertThatThrownBy(() -> checker.check(req(2, OvertimeRequest.Kind.HOLIDAY, MON.plusDays(6), 81)))
                .isInstanceOf(ApprovalException.class);
    }

    @Test
    void 실적에_포함된_신청은_중복하지_않고_아직_근무하지_않은_신청은_더한다() {
        LocalDateTime start = MON.atTime(9, 0);
        OvertimeRequest reflected = new OvertimeRequest(EMP, OvertimeRequest.Kind.OVERTIME, start, start.plusMinutes(30),
                0, "사유", start.minusDays(1));
        ReflectionTestUtils.setField(reflected, "requestId", 1L);
        OvertimeRequest future = new OvertimeRequest(EMP, OvertimeRequest.Kind.OVERTIME, start.plusHours(3),
                start.plusHours(3).plusMinutes(30), 0, "사유", start.minusDays(1));
        ReflectionTestUtils.setField(future, "requestId", 2L);
        approved(reflected, future);
        when(summaries.findRange(EMP, MON.minusDays(1), MON.plusDays(6)))
                .thenReturn(List.of(actual(MON, "WORKDAY", 120, start, start.plusHours(2))));

        assertThat(checker.check(reflected).weeklyWorkedMinutes()).isEqualTo(150);
        assertThat(checker.check(req(3, OvertimeRequest.Kind.OVERTIME, MON.plusDays(2), 30)).weeklyWorkedMinutes())
                .isEqualTo(180);
        assertThat(OvertimeLimitChecker.weekWorked(MON, Map.of(MON, 10), Map.of(MON, 30, MON.plusDays(7), 99)))
                .isEqualTo(40);
    }

    @Test
    void 실적과_겹친_신청분에서_근무유형의_휴게시간은_반영분으로_차감하지_않는다() {
        when(legalRates.getEffective(LegalRateItem.WEEKLY_STANDARD_WORK_HOURS, MON))
                .thenReturn(rate("WEEKLY_STANDARD_WORK_HOURS", 10));
        LocalDateTime requestStart = MON.atTime(12, 0);
        OvertimeRequest request = new OvertimeRequest(EMP, OvertimeRequest.Kind.OVERTIME, requestStart,
                requestStart.plusHours(3), 60, "사유", requestStart.minusDays(1));
        ReflectionTestUtils.setField(request, "requestId", 1L);
        WorkPattern pattern = new WorkPattern("DAY", "주간", new WorkPatternRules.Spec(540, 1080, 720, 60, "WWWWWOO"),
                new WorkPatternRules.Result(480, 2400, List.of()), true, null, requestStart.minusDays(10));
        ReflectionTestUtils.setField(pattern, "patternId", 1L);
        when(patterns.findById(1L)).thenReturn(Optional.of(pattern));
        when(summaries.findRange(EMP, MON.minusDays(1), MON.plusDays(6)))
                .thenReturn(List.of(actual(MON, "WORKDAY", 180, MON.atTime(9, 0), MON.atTime(13, 0))));

        assertThat(checker.check(request).weeklyWorkedMinutes()).isEqualTo(300);
    }

    @Test
    void 전날_시작한_야간조_실적과_다음날_신청의_겹침도_반영분으로_차감한다() {
        LocalDateTime start = MON.atTime(2, 0);
        OvertimeRequest request = new OvertimeRequest(EMP, OvertimeRequest.Kind.OVERTIME, start, start.plusHours(2),
                0, "사유", start.minusDays(1));
        ReflectionTestUtils.setField(request, "requestId", 1L);
        when(summaries.findRange(EMP, MON.minusDays(1), MON.plusDays(6))).thenReturn(List.of(
                actual(MON.minusDays(1), "WORKDAY", 420, MON.minusDays(1).atTime(22, 0), MON.atTime(6, 0))));

        assertThat(checker.check(request).weeklyWorkedMinutes()).isZero();
    }

    @Test
    void 다른_주의_승인분은_세지_않는다() {
        assertThat(checker.check(req(2, OvertimeRequest.Kind.OVERTIME, MON.plusDays(6), 60)).weekStart())
                .isEqualTo(MON);
        assertThat(checker.check(req(3, OvertimeRequest.Kind.OVERTIME, MON.plusDays(7), 60)).weekStart())
                .isEqualTo(MON.plusDays(7));
    }

    @Test
    void 한도_첫_적용시작일_뒤인데_기준값이_없으면_409로_승인하지_않는다() {
        when(legalRates.getEffective(eq(LegalRateItem.WEEKLY_OVERTIME_LIMIT_HOURS), any(LocalDate.class)))
                .thenThrow(mock(LegalRateException.class));
        when(legalRates.firstEffectiveFrom("WEEKLY_OVERTIME_LIMIT_HOURS")).thenReturn(Optional.of(MON));
        assertThatThrownBy(() -> checker.check(req(2, OvertimeRequest.Kind.OVERTIME, MON, 10)))
                .isInstanceOfSatisfying(ApprovalException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("WEEKLY_OVERTIME_LIMIT_HOURS");

        when(legalRates.firstEffectiveFrom("WEEKLY_OVERTIME_LIMIT_HOURS")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> checker.check(req(3, OvertimeRequest.Kind.OVERTIME, MON, 10)))
                .isInstanceOfSatisfying(ApprovalException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void 한도_첫_적용시작일_전의_근로는_한도를_적용하지_않는다() {
        when(legalRates.getEffective(eq(LegalRateItem.WEEKLY_OVERTIME_LIMIT_HOURS), any(LocalDate.class)))
                .thenThrow(mock(LegalRateException.class));
        when(legalRates.firstEffectiveFrom("WEEKLY_OVERTIME_LIMIT_HOURS")).thenReturn(Optional.of(MON.plusDays(1)));

        OvertimeLimitChecker.Result r = checker.check(req(2, OvertimeRequest.Kind.HOLIDAY, MON, 600));
        assertThat(r.limitMinutes()).isNull();
        assertThat(r.exceeded()).isFalse();
    }

    @Test
    void 주_기준근로시간_기준값이_없으면_409로_승인하지_않는다() {
        when(legalRates.getEffective(eq(LegalRateItem.WEEKLY_STANDARD_WORK_HOURS), any(LocalDate.class)))
                .thenThrow(mock(LegalRateException.class));
        assertThatThrownBy(() -> checker.check(req(2, OvertimeRequest.Kind.OVERTIME, MON, 10)))
                .isInstanceOfSatisfying(ApprovalException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("WEEKLY_STANDARD_WORK_HOURS");
    }
}
