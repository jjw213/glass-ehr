package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.glassehr.attendance.WeeklyHoursMonitor.Limits;
import com.glassehr.auth.AuthUser;
import com.glassehr.employee.EmployeeRepository;
import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import com.glassehr.org.OrgDeptService;
import com.glassehr.tenant.EnterSettingKey;
import com.glassehr.tenant.EnterSettingTestSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 한도를 legal_rate 에서 그 주 월요일 기준으로 읽어 분으로 바꾸는지 본다.
 * 기준값은 임의의 가짜 값(법령 수치가 아님)이고 날짜는 2099년.
 */
class WeeklyHoursServiceTest {

    private static final LocalDate MON = LocalDate.of(2099, 3, 2);

    private final LegalRateService rates = mock(LegalRateService.class);
    private final WeeklyHoursService service = new WeeklyHoursService(mock(WorkHoursService.class), rates,
            mock(EmployeeRepository.class), mock(OrgDeptService.class),
            EnterSettingTestSupport.defaults(Map.of(EnterSettingKey.WEEKLY_HOURS_WARN_REMAIN_MINUTES, 90)),
            Clock.fixed(Instant.parse("2099-03-04T01:00:00Z"), WorkHoursService.SEOUL));

    private static LegalRateRow row(LegalRateItem item, String value) {
        return new LegalRateRow(item.name(), LocalDate.of(2099, 1, 1), null, new BigDecimal(value), "HOURS", "테스트", null);
    }

    @Test
    void 한도는_legal_rate_시간값을_분으로_바꾸고_경고_기준은_회사_설정() {
        when(rates.getEffective(LegalRateItem.WEEKLY_STANDARD_WORK_HOURS, MON)).thenReturn(row(LegalRateItem.WEEKLY_STANDARD_WORK_HOURS, "10"));
        when(rates.getEffective(LegalRateItem.WEEKLY_OVERTIME_LIMIT_HOURS, MON)).thenReturn(row(LegalRateItem.WEEKLY_OVERTIME_LIMIT_HOURS, "2.5"));
        Limits limits = service.limits("C001", MON);
        assertThat(limits.standardMinutes()).isEqualTo(600);
        assertThat(limits.overtimeMinutes()).isEqualTo(150);
        assertThat(limits.warnRemainMinutes()).isEqualTo(90);
    }

    @Test
    void 분_정수가_아닌_기준값은_거부() {
        when(rates.getEffective(LegalRateItem.WEEKLY_STANDARD_WORK_HOURS, MON)).thenReturn(row(LegalRateItem.WEEKLY_STANDARD_WORK_HOURS, "10.001"));
        assertThatThrownBy(() -> service.limits("C001", MON)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 사원과_연결되지_않은_계정은_내_주간을_못_본다() {
        AuthUser user = mock(AuthUser.class);
        when(user.empId()).thenReturn(null);
        assertThatThrownBy(() -> service.mine(user)).isInstanceOf(AttendanceException.class);
    }

    @Test
    void 경고_기준이_음수면_시작하지_않는다() {
        // 범위 검사는 법인 설정 기본값을 읽을 때 한다(#131).
        assertThatThrownBy(() -> EnterSettingTestSupport.defaults(Map.of(EnterSettingKey.WEEKLY_HOURS_WARN_REMAIN_MINUTES, -1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
