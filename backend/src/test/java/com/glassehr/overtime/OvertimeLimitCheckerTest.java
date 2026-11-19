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
import com.glassehr.legalrate.LegalRateException;
import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 한도 검사 동작(합산 방식·되돌림·기준값 누락) 검사. 한도 값은 법령 값이 아닌 시험용 값(1시간)을 mock 으로 넣는다.
 * 법정 한도로 판정하는 정답 사례는 tests/legal-fixtures 를 받아 추가한다(docs/overtime-request-design.md §4).
 */
class OvertimeLimitCheckerTest {

    static final long EMP = 7L;
    static final LocalDate MON = LocalDate.of(2099, 3, 9);

    LegalRateService legalRates;
    DailyWorkSummaryMapper summaries;
    OvertimeRequestRepository requests;
    OvertimeLimitChecker checker;

    @BeforeEach
    void setUp() {
        legalRates = mock(LegalRateService.class);
        summaries = mock(DailyWorkSummaryMapper.class);
        requests = mock(OvertimeRequestRepository.class);
        checker = new OvertimeLimitChecker(legalRates, summaries, requests);
        LegalRateRow row = mock(LegalRateRow.class);
        when(row.rateValue()).thenReturn(BigDecimal.ONE);
        when(legalRates.getEffective(eq(LegalRateItem.WEEKLY_OVERTIME_LIMIT_HOURS), any(LocalDate.class)))
                .thenReturn(row);
        when(summaries.findRange(anyLong(), any(), any())).thenReturn(List.of());
        when(requests.findByEmpIdAndWorkDateBetweenAndStatusIn(anyLong(), any(), any(), any())).thenReturn(List.of());
    }

    static OvertimeRequest req(long id, OvertimeRequest.Kind kind, LocalDate d, int minutes) {
        LocalDateTime start = d.atTime(19, 0);
        OvertimeRequest r = new OvertimeRequest(EMP, kind, start, start.plusMinutes(minutes), 0, "사유",
                start.minusDays(1));
        ReflectionTestUtils.setField(r, "requestId", id);
        return r;
    }

    static DailyWorkSummaryRow actual(LocalDate d, int overtime) {
        return new DailyWorkSummaryRow(EMP, d, "WORKDAY", 1L, 480, null, 0, null, null, 60, 480 + overtime, 480, 0,
                overtime, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, "t",
                LocalDateTime.now());
    }

    @Test
    void 이미_승인된_신청과_이번_신청을_더해_주_한도를_넘으면_승인하지_않는다() {
        OvertimeRequest approved = req(1, OvertimeRequest.Kind.OVERTIME, MON, 40);
        when(requests.findByEmpIdAndWorkDateBetweenAndStatusIn(EMP, MON, MON.plusDays(6),
                List.of(OvertimeRequest.Status.APPROVED))).thenReturn(List.of(approved));

        assertThat(checker.check(req(2, OvertimeRequest.Kind.OVERTIME, MON.plusDays(1), 20)).totalMinutes())
                .isEqualTo(60);
        assertThatThrownBy(() -> checker.check(req(3, OvertimeRequest.Kind.OVERTIME, MON.plusDays(6), 21)))
                .isInstanceOfSatisfying(ApprovalException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY))
                .hasMessageContaining("제53조 제1항");
    }

    @Test
    void 실적과_승인_예정은_날마다_큰_값만_세어_두_번_세지_않는다() {
        OvertimeRequest approved = req(1, OvertimeRequest.Kind.OVERTIME, MON, 30);
        when(requests.findByEmpIdAndWorkDateBetweenAndStatusIn(EMP, MON, MON.plusDays(6),
                List.of(OvertimeRequest.Status.APPROVED))).thenReturn(List.of(approved));
        when(summaries.findRange(EMP, MON, MON.plusDays(6))).thenReturn(List.of(actual(MON, 35)));

        assertThat(checker.check(req(2, OvertimeRequest.Kind.OVERTIME, MON.plusDays(2), 25)).totalMinutes())
                .isEqualTo(60);
        assertThat(OvertimeLimitChecker.weekTotal(MON, Map.of(MON, 10), Map.of(MON, 30, MON.plusDays(7), 99)))
                .isEqualTo(30);
    }

    @Test
    void 다른_주의_승인분은_세지_않는다() {
        // 일요일 신청은 그 주 월요일부터, 다음 월요일 신청은 새 주로 본다.
        assertThat(checker.check(req(2, OvertimeRequest.Kind.OVERTIME, MON.plusDays(6), 60)).weekStart())
                .isEqualTo(MON);
        assertThat(checker.check(req(3, OvertimeRequest.Kind.OVERTIME, MON.plusDays(7), 60)).weekStart())
                .isEqualTo(MON.plusDays(7));
    }

    @Test
    void 한도_기준값이_없으면_409로_승인하지_않는다() {
        when(legalRates.getEffective(eq(LegalRateItem.WEEKLY_OVERTIME_LIMIT_HOURS), any(LocalDate.class)))
                .thenThrow(mock(LegalRateException.class));
        assertThatThrownBy(() -> checker.check(req(2, OvertimeRequest.Kind.OVERTIME, MON, 10)))
                .isInstanceOfSatisfying(ApprovalException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("WEEKLY_OVERTIME_LIMIT_HOURS");
    }
}
