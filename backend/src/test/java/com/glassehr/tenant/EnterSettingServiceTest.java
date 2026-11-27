package com.glassehr.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.attendance.PublicHolidayPaidRule;
import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import com.glassehr.legalrate.LegalRateUnit;
import com.glassehr.tenant.EnterSettingService.SettingException;
import com.glassehr.tenant.EnterSettingService.SettingView;
import com.glassehr.tenant.EnterSettingService.UpdateRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 법인별 회사 설정(#131): 법인 값 우선·기본값 대체·적용시작일·입력 검증·변경 이력, 규모별 시행 판정 연결. */
class EnterSettingServiceTest {

    private static final String A = "C001";
    private static final String B = "ZZ02";
    private static final LocalDate D = LocalDate.of(2099, 1, 1);

    private final EnterSettingRepository repo = mock(EnterSettingRepository.class);
    // 서울 2099-06-01 09:00
    private final Clock clock = Clock.fixed(Instant.parse("2099-06-01T00:00:00Z"), ZoneId.of("UTC"));
    private final EnterSettingService service = new EnterSettingService(repo, defaults(), clock);

    private static Map<EnterSettingKey, Integer> defaults() {
        Map<EnterSettingKey, Integer> m = new EnumMap<>(EnterSettingKey.class);
        for (EnterSettingKey k : EnterSettingKey.values()) {
            m.put(k, 30);
        }
        return m;
    }

    @Test
    void 법인_값이_없으면_기본값_있으면_그_법인_값() {
        when(repo.findEffective(A, "WORKPLACE_REGULAR_HEADCOUNT", D)).thenReturn(Optional.of(12));
        when(repo.findEffective(B, "WORKPLACE_REGULAR_HEADCOUNT", D)).thenReturn(Optional.empty());
        assertThat(service.value(A, EnterSettingKey.WORKPLACE_REGULAR_HEADCOUNT, D)).isEqualTo(12);
        assertThat(service.value(B, EnterSettingKey.WORKPLACE_REGULAR_HEADCOUNT, D)).isEqualTo(30);
    }

    @Test
    void 기본값이_범위_밖이면_시작하지_않는다() {
        Map<EnterSettingKey, Integer> m = defaults();
        m.put(EnterSettingKey.WORKPLACE_REGULAR_HEADCOUNT, 0);
        assertThatThrownBy(() -> new EnterSettingService(repo, m, clock)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 목록은_오늘_적용값과_앞으로_적용될_값을_함께_보여준다() {
        LocalDateTime t = LocalDateTime.of(2099, 1, 1, 9, 0);
        when(repo.findAll(A)).thenReturn(List.of(
                new EnterSettingRepository.Entry("WORKPLACE_REGULAR_HEADCOUNT", LocalDate.of(2099, 1, 1), 20, "hr", t),
                new EnterSettingRepository.Entry("WORKPLACE_REGULAR_HEADCOUNT", LocalDate.of(2099, 6, 1), 40, "hr", t),
                new EnterSettingRepository.Entry("WORKPLACE_REGULAR_HEADCOUNT", LocalDate.of(2099, 7, 1), 50, "hr", t)));
        List<SettingView> views = service.list(A);
        assertThat(views).extracting(SettingView::key)
                .containsExactly(java.util.Arrays.stream(EnterSettingKey.values()).map(Enum::name).toArray(String[]::new));
        SettingView hc = views.get(0);
        // 오늘(서울 2099-06-01)이 적용시작일인 값이 오늘 값.
        assertThat(hc.value()).isEqualTo(40);
        assertThat(hc.effectiveFrom()).isEqualTo(LocalDate.of(2099, 6, 1));
        assertThat(hc.entries()).hasSize(3);
        SettingView other = views.get(1);
        assertThat(other.value()).isEqualTo(30);
        assertThat(other.effectiveFrom()).isNull();
    }

    @Test
    void 새로_등록하면_값과_이력을_남기고_바꾸면_옛_값을_이력에_남긴다() {
        when(repo.lockValue(A, "CONTRACT_EXPIRY_ALERT_DAYS", D)).thenReturn(Optional.empty());
        service.update(A, "CONTRACT_EXPIRY_ALERT_DAYS", new UpdateRequest(D, new BigDecimal("45")), "hr1");
        verify(repo).insert(eq(A), eq("CONTRACT_EXPIRY_ALERT_DAYS"), eq(D), eq(45), eq("hr1"), any());
        verify(repo).insertChange(eq(A), eq("CONTRACT_EXPIRY_ALERT_DAYS"), eq(D), eq(null), eq(45), eq("hr1"), any());

        when(repo.lockValue(A, "AGREEMENT_EXPIRY_ALERT_DAYS", D)).thenReturn(Optional.of(30));
        service.update(A, "AGREEMENT_EXPIRY_ALERT_DAYS", new UpdateRequest(D, new BigDecimal("14")), "hr1");
        verify(repo).update(eq(A), eq("AGREEMENT_EXPIRY_ALERT_DAYS"), eq(D), eq(14), eq("hr1"), any());
        verify(repo).insertChange(eq(A), eq("AGREEMENT_EXPIRY_ALERT_DAYS"), eq(D), eq(30), eq(14), eq("hr1"), any());
    }

    @Test
    void 같은_값이면_이력을_남기지_않는다() {
        when(repo.lockValue(A, "CONTRACT_EXPIRY_ALERT_DAYS", D)).thenReturn(Optional.of(30));
        service.update(A, "CONTRACT_EXPIRY_ALERT_DAYS", new UpdateRequest(D, new BigDecimal("30.0")), "hr1");
        verify(repo, never()).update(anyString(), anyString(), any(), anyInt(), anyString(), any());
        verify(repo, never()).insertChange(anyString(), anyString(), any(), any(), anyInt(), anyString(), any());
    }

    @Test
    void 잘못된_입력은_400_없는_키는_404() {
        for (UpdateRequest bad : List.of(new UpdateRequest(null, BigDecimal.TEN), new UpdateRequest(D, null),
                new UpdateRequest(D, new BigDecimal("1.5")), new UpdateRequest(D, BigDecimal.ZERO),
                new UpdateRequest(D, new BigDecimal("1000001")))) {
            assertThatThrownBy(() -> service.update(A, "WORKPLACE_REGULAR_HEADCOUNT", bad, "hr1"))
                    .isInstanceOfSatisfying(SettingException.class,
                            e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        assertThatThrownBy(() -> service.update(A, "NO_SUCH", new UpdateRequest(D, BigDecimal.TEN), "hr1"))
                .isInstanceOfSatisfying(SettingException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(repo, never()).insert(anyString(), anyString(), any(), anyInt(), anyString(), any());
    }

    /**
     * 근로기준법 제55조 제2항·부칙(규모별 시행일): legal_rate 적용 규모 하한과 그 법인의 그 날짜 상시근로자 수를 비교한다.
     * 하한 값은 임의의 가짜 값(법정 수치가 아님).
     */
    @Test
    void 공휴일_유급_규모별_시행_판정은_법인_설정의_상시근로자_수를_쓴다() {
        LegalRateService rates = mock(LegalRateService.class);
        when(rates.getEffective(eq(LegalRateItem.PUBLIC_HOLIDAY_PAID_MIN_HEADCOUNT), any())).thenReturn(
                new LegalRateRow(LegalRateItem.PUBLIC_HOLIDAY_PAID_MIN_HEADCOUNT.code(), D, null, BigDecimal.valueOf(25),
                        LegalRateUnit.COUNT, "시험 부칙", null));
        LocalDate before = LocalDate.of(2099, 2, 28);
        LocalDate after = LocalDate.of(2099, 3, 1);
        // 법인 A: 3월 1일부터 상시근로자 수 25명(그 전 10명). 법인 B: 값 없음 → 기본값 30명.
        when(repo.findEffective(A, "WORKPLACE_REGULAR_HEADCOUNT", before)).thenReturn(Optional.of(10));
        when(repo.findEffective(A, "WORKPLACE_REGULAR_HEADCOUNT", after)).thenReturn(Optional.of(25));
        when(repo.findEffective(eq(B), eq("WORKPLACE_REGULAR_HEADCOUNT"), any())).thenReturn(Optional.empty());
        PublicHolidayPaidRule rule = new PublicHolidayPaidRule(rates, service);

        assertThat(rule.appliesOn(A, before)).isFalse();
        assertThat(rule.appliesOn(A, after)).isTrue();
        assertThat(rule.appliesOn(B, before)).isTrue();

        // 같은 날짜라도 법인마다 판정이 다르다.
        when(repo.findEffective(B, "WORKPLACE_REGULAR_HEADCOUNT", before)).thenReturn(Optional.of(24));
        assertThat(rule.appliesOn(B, before)).isFalse();
        assertThat(rule.appliesOn(A, after)).isTrue();
    }
}
