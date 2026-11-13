package com.glassehr.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.employee.RequestActor;
import com.glassehr.employee.RrnAccessLog;
import com.glassehr.employee.RrnAccessLogRepository;
import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PrivacyLogServiceTest {

    private final PrivacyAccessLogRepository privacy = mock(PrivacyAccessLogRepository.class);
    private final RrnAccessLogRepository rrn = mock(RrnAccessLogRepository.class);
    private final PrivacyLogService service = new PrivacyLogService(privacy, rrn);

    private static PrivacyAccessLog p(LocalDateTime at, long target) {
        return new PrivacyAccessLog("hr1", "EMPLOYEE,HR", target, PrivacyAccessLog.Item.EMP_PERSONAL,
                PrivacyAccessLog.Action.VIEW, PrivacyAccessLog.Result.SUCCESS, "127.0.0.1", "/api/employees/" + target, at);
    }

    /** RrnAccessLog 생성자는 employee 패키지 전용이라 리플렉션으로 만든다(테스트 데이터). */
    private static RrnAccessLog r(LocalDateTime at, long empId) throws Exception {
        Constructor<RrnAccessLog> c = RrnAccessLog.class.getDeclaredConstructor(RequestActor.class, long.class,
                RrnAccessLog.Purpose.class, RrnAccessLog.Result.class, LocalDateTime.class);
        c.setAccessible(true);
        return c.newInstance(new RequestActor("pay1", RequestActor.Role.PAYROLL, "10.0.0.2"), empId,
                RrnAccessLog.Purpose.WITHHOLDING, RrnAccessLog.Result.SUCCESS, at);
    }

    @Test
    void 두_로그를_합쳐_최근_순으로_보이고_기간은_끝날_포함() throws Exception {
        LocalDateTime t1 = LocalDateTime.of(2026, 11, 1, 9, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 11, 1, 10, 0);
        LocalDateTime t3 = LocalDateTime.of(2026, 11, 2, 23, 59, 59);
        when(privacy.search(any(), any(), any(), any(), any())).thenReturn(List.of(p(t3, 5), p(t1, 6)));
        when(rrn.search(any(), any(), any(), any(), any())).thenReturn(List.of(r(t2, 7)));

        PrivacyLogService.LogPage page = service.search(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2), " hr1 ", 5L);

        assertThat(page.rows()).extracting(PrivacyLogService.LogRow::accessedAt).containsExactly(t3, t2, t1);
        PrivacyLogService.LogRow rrnRow = page.rows().get(1);
        assertThat(rrnRow.source()).isEqualTo("RRN");
        assertThat(rrnRow.item()).isEqualTo("RRN");
        assertThat(rrnRow.purpose()).isEqualTo("WITHHOLDING");
        assertThat(rrnRow.targetEmpId()).isEqualTo(7L);
        assertThat(page.truncated()).isFalse();
        verify(privacy).search(eq(LocalDateTime.of(2026, 11, 1, 0, 0)), eq(LocalDateTime.of(2026, 11, 3, 0, 0)),
                eq("hr1"), eq(5L), any());
    }

    @Test
    void 빈_조회자는_조건없음() {
        when(privacy.search(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(rrn.search(any(), any(), any(), any(), any())).thenReturn(List.of());
        service.search(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1), "  ", null);
        verify(rrn).search(any(), any(), isNull(), isNull(), any());
    }

    @Test
    void 최대_행수를_넘으면_잘라서_알린다() {
        List<PrivacyAccessLog> many = new ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2026, 11, 1, 0, 0);
        for (int i = 0; i < PrivacyLogService.MAX_ROWS + 1; i++) {
            many.add(p(base.plusSeconds(i), i));
        }
        when(privacy.search(any(), any(), any(), any(), any())).thenReturn(many);
        when(rrn.search(any(), any(), any(), any(), any())).thenReturn(List.of());
        PrivacyLogService.LogPage page = service.search(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1), null, null);
        assertThat(page.rows()).hasSize(PrivacyLogService.MAX_ROWS);
        assertThat(page.truncated()).isTrue();
    }

    @Test
    void 기간_오류() {
        assertThatThrownBy(() -> service.search(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 11, 1), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
