package com.glassehr.attendance;

import static com.glassehr.attendance.LaborAgreementRulesTest.agreement;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 제도 설정 API 공통 함수: 유효한 서면합의서가 없으면 422. */
class LaborAgreementGuardTest {

    private static final LocalDate JAN1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate DEC31 = LocalDate.of(2026, 12, 31);

    private LaborAgreementRepository repo;
    private LaborAgreementGuard guard;

    @BeforeEach
    void setUp() {
        repo = mock(LaborAgreementRepository.class);
        guard = new LaborAgreementGuard(repo);
    }

    @Test
    void 덮는_합의서가_있으면_통과하고_근거_합의서를_준다() {
        LaborAgreement a = agreement(5, AgreementType.FLEXIBLE_WRITTEN, JAN1, DEC31, LaborAgreement.Scope.ALL, null);
        when(repo.findByAgreementType(AgreementType.FLEXIBLE_WRITTEN)).thenReturn(List.of(a));
        when(repo.findById(5L)).thenReturn(Optional.of(a));
        assertThat(guard.requireValid(AgreementType.FLEXIBLE_WRITTEN, JAN1, LocalDate.of(2026, 3, 31), "S01"))
                .isSameAs(a);
        assertThat(guard.check(AgreementType.FLEXIBLE_WRITTEN, JAN1, DEC31, null).agreementId()).isEqualTo(5L);
    }

    @Test
    void 합의서가_없으면_422와_근거_조문() {
        when(repo.findByAgreementType(AgreementType.COMPENSATORY_LEAVE)).thenReturn(List.of());
        assertThatThrownBy(() -> guard.requireValid(AgreementType.COMPENSATORY_LEAVE, JAN1, null, null))
                .isInstanceOfSatisfying(AttendanceException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.getMessage()).contains("근로기준법 제57조").contains("등록된 합의서가 없어요");
                });
    }

    @Test
    void 만료된_합의서만_있으면_422() {
        LaborAgreement expired = agreement(1, AgreementType.ANNUAL_LEAVE_SUBSTITUTE, JAN1.minusYears(1),
                JAN1.minusDays(1), LaborAgreement.Scope.ALL, null);
        when(repo.findByAgreementType(AgreementType.ANNUAL_LEAVE_SUBSTITUTE)).thenReturn(List.of(expired));
        LaborAgreementDtos.CheckResult r = guard.check(AgreementType.ANNUAL_LEAVE_SUBSTITUTE, JAN1, JAN1, null);
        assertThat(r.ok()).isFalse();
        assertThat(r.message()).contains("근로기준법 제62조").contains("2026-01-01 ~ 2026-01-01");
        assertThatThrownBy(() -> guard.requireValid(AgreementType.ANNUAL_LEAVE_SUBSTITUTE, JAN1, JAN1, null))
                .isInstanceOf(AttendanceException.class);
    }

    @Test
    void 적용_종료일이_시작일보다_빠르면_400() {
        assertThatThrownBy(() -> guard.check(AgreementType.SELECTIVE, DEC31, JAN1, null))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
