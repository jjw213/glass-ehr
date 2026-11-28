package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.attendance.WorkPolicyDtos.AllowanceDto;
import com.glassehr.attendance.WorkPolicyDtos.WorkPolicyRequest;
import com.glassehr.attendance.WorkPolicyRules.AllowMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/** 근무 정책 저장·숨김·법인 격리(#159). 기준값은 테스트 입력. */
class WorkPolicyServiceTest {

    private static final String A = "C001";
    private static final String B = "C002";

    private WorkPolicyRepository policies;
    private WorkPatternRepository patterns;
    private WorkPatternLegalLimits limits;
    private WorkPolicyService service;

    @BeforeEach
    void setUp() {
        policies = mock(WorkPolicyRepository.class);
        patterns = mock(WorkPatternRepository.class);
        limits = mock(WorkPatternLegalLimits.class);
        when(limits.asOf(any())).thenReturn(new WorkPatternRules.Limits(480, 2400, 240, 30, 480, 60, 1));
        when(limits.weeklyOvertimeLimitMinutes(any())).thenReturn(720);
        when(policies.saveAndFlush(any())).thenAnswer(inv -> {
            WorkPolicy w = inv.getArgument(0);
            ReflectionTestUtils.setField(w, "policyId", 5L);
            return w;
        });
        service = new WorkPolicyService(policies, patterns, mock(EmpWorkPatternRepository.class), limits,
                Clock.fixed(Instant.parse("2099-03-10T01:00:00Z"), WorkHoursService.SEOUL));
    }

    private static WorkPolicyRequest request(Long patternId, AllowanceDto overtime) {
        AllowanceDto none = new AllowanceDto(AllowMode.UNLIMITED, null, null, null);
        return new WorkPolicyRequest(patternId, " 기본 ", overtime, none, none, true, 15, 0,
                WorkPolicyRules.Rounding.UP, 0, 0, true, WorkPolicyRules.MissingOut.SCHEDULED_END, true);
    }

    private static WorkPolicy stored(String enter) {
        return new WorkPolicy(enter, null, "기본", WorkPolicyRules.SYSTEM_DEFAULT, "hr", LocalDateTime.now());
    }

    @Test
    void 기본_정책을_저장하고_법정_한도를_넘으면_422() {
        var v = service.create(A, request(null, new AllowanceDto(AllowMode.LIMIT, null, 600, null)), "hr");
        assertThat(v.policyId()).isEqualTo(5L);
        assertThat(v.policyName()).isEqualTo("기본");
        assertThat(v.unitMinutes()).isEqualTo(15);

        assertThatThrownBy(() -> service.create(A, request(null, new AllowanceDto(AllowMode.LIMIT, null, 900, null)),
                "hr")).isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void 같은_대상에_사용_중_정책이_있으면_409() {
        when(policies.findByEnterCdAndActive(A, true)).thenReturn(List.of(stored(A)));
        assertThatThrownBy(() -> service.create(A, request(null, new AllowanceDto(AllowMode.DENY, null, null, null)),
                "hr")).isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void 다른_법인_정책과_근무유형은_404() {
        when(policies.findByEnterCdAndPolicyId(A, 9L)).thenReturn(Optional.empty());
        when(policies.findByEnterCdAndPolicyId(B, 9L)).thenReturn(Optional.of(stored(B)));
        assertThatThrownBy(() -> service.update(A, 9L, request(null, new AllowanceDto(AllowMode.DENY, null, null,
                null)), "hr")).isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.delete(A, 9L, "hr")).isInstanceOfSatisfying(AttendanceException.class,
                e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
        when(patterns.findByEnterCdAndPatternId(A, 3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(A, request(3L, new AllowanceDto(AllowMode.DENY, null, null, null)),
                "hr")).isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 쓰인_정책은_지우지_않고_숨긴다() {
        WorkPolicy used = stored(A);
        when(policies.findByEnterCdAndPolicyId(A, 1L)).thenReturn(Optional.of(used));
        when(policies.countSummaries(A, null)).thenReturn(3L);
        assertThat(service.delete(A, 1L, "hr").hidden()).isTrue();
        assertThat(used.isActive()).isFalse();
        verify(policies, never()).delete(any());

        WorkPolicy unused = stored(A);
        when(policies.findByEnterCdAndPolicyId(A, 2L)).thenReturn(Optional.of(unused));
        when(policies.countSummaries(A, null)).thenReturn(0L);
        assertThat(service.delete(A, 2L, "hr").hidden()).isFalse();
        verify(policies).delete(unused);
    }

    @Test
    void 정책_해석은_근무유형_재정의_법인_기본_시스템_기본_순() {
        assertThat(service.resolved(A).forPattern(3L)).isEqualTo(WorkPolicyRules.SYSTEM_DEFAULT);
        WorkPolicy def = new WorkPolicy(A, null, "기본", request(null, new AllowanceDto(AllowMode.DENY, null, null,
                null)).toPolicy(), "hr", LocalDateTime.now());
        when(policies.findByEnterCdAndActive(A, true)).thenReturn(List.of(def));
        assertThat(service.resolved(A).forPattern(3L).overtime().mode()).isEqualTo(AllowMode.DENY);
    }
}
