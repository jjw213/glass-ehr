package com.glassehr.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.code.AttendanceCodeDtos.AttendanceCodeCreateRequest;
import com.glassehr.code.AttendanceCodeDtos.AttendanceCodeUpdateRequest;
import com.glassehr.code.AttendanceCodeDtos.AttendanceCodeView;
import com.glassehr.code.AttendanceCodeDtos.LeaveDetailRequest;
import com.glassehr.code.AttendanceCodeDtos.LegalValue;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AttendanceCodeServiceTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 25, 9, 0);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private static final String E = "C001";
    private static final String LEAVE = CodeGroups.LEAVE_TYPE;

    private CodeGroupRepository groups;
    private CommonCodeRepository codes;
    private CodeUsageRepository usages;
    private AttendanceLeaveTypeRepository leaves;
    private LegalRateService legalRates;
    private AttendanceCodeService service;

    @BeforeEach
    void setUp() {
        groups = mock(CodeGroupRepository.class);
        codes = mock(CommonCodeRepository.class);
        usages = mock(CodeUsageRepository.class);
        leaves = mock(AttendanceLeaveTypeRepository.class);
        legalRates = mock(LegalRateService.class);
        // UTC 00:00 = 서울 09:00
        Clock clock = Clock.fixed(Instant.parse("2026-11-25T00:00:00Z"), ZoneId.of("UTC"));
        CommonCodeService common = new CommonCodeService(groups, codes, usages, clock);
        service = new AttendanceCodeService(common, codes, leaves, legalRates, clock);
        when(codes.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(leaves.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        for (String g : CodeGroups.ATTENDANCE) {
            CodeGroup group = new CodeGroup(E, g, g, null, 0, true, true, T0).withEditPage("attendanceCodes");
            when(groups.findByEnterCdAndGroupCode(E, g)).thenReturn(Optional.of(group));
            when(groups.existsByEnterCdAndGroupCode(E, g)).thenReturn(true);
        }
        when(legalRates.findEffective(anyString(), any())).thenReturn(Optional.empty());
    }

    private static LeaveDetailRequest dayOnly(boolean paid, String limit) {
        return new LeaveDetailRequest(true, false, false, paid, false, limit == null ? null : new BigDecimal(limit));
    }

    private static AttendanceCodeCreateRequest refresh() {
        return new AttendanceCodeCreateRequest("REFRESH", "리프레시 휴가", null, 140, true, "COMPANY",
                dayOnly(true, "5"));
    }

    private static CommonCode stored(String code, String processType, boolean system) {
        CommonCode c = new CommonCode(E, LEAVE, code, code + "명", null, 10, true, processType, T0);
        return system ? c.markSystem() : c;
    }

    private static AttendanceLeaveType detail(String code, Boolean paid, String limit) {
        AttendanceLeaveType d = new AttendanceLeaveType(E, code, T0);
        d.change(true, false, false, paid, false, limit == null ? null : new BigDecimal(limit), T0);
        return d;
    }

    @Test
    void 약정휴가_리프레시_휴가를_추가한다() {
        AttendanceCodeView v = service.create(E, LEAVE, refresh());

        assertThat(v.code()).isEqualTo("REFRESH");
        assertThat(v.processType()).isEqualTo("COMPANY");
        assertThat(v.system()).isFalse();
        assertThat(v.leave().paid()).isTrue();
        assertThat(v.leave().unitDay()).isTrue();
        assertThat(v.leave().unitHalfDay()).isFalse();
        assertThat(v.leave().annualLimitDays()).isEqualByComparingTo("5");
        assertThat(v.leave().legalValues()).isEmpty();
        verify(leaves).saveAndFlush(any());
    }

    @Test
    void 같은_코드값이면_409() {
        when(codes.existsByEnterCdAndGroupCodeAndCode(E, LEAVE, "REFRESH")).thenReturn(true);
        assertThatThrownBy(() -> service.create(E, LEAVE, refresh()))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void 법정휴가_처리방식으로는_추가할_수_없다() {
        var req = new AttendanceCodeCreateRequest("X", "x", null, 1, true, "STATUTORY", dayOnly(true, null));
        assertThatThrownBy(() -> service.create(E, LEAVE, req))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(codes, never()).saveAndFlush(any());
    }

    @Test
    void 무급휴가는_유급일_수_없고_사용_단위가_하나는_있어야_한다() {
        var unpaidPaid = new AttendanceCodeCreateRequest("X", "x", null, 1, true, "UNPAID", dayOnly(true, null));
        var noUnit = new AttendanceCodeCreateRequest("Y", "y", null, 1, true, "COMPANY",
                new LeaveDetailRequest(false, false, false, true, false, null));
        var noDetail = new AttendanceCodeCreateRequest("Z", "z", null, 1, true, "COMPANY", null);
        for (var req : List.of(unpaidPaid, noUnit, noDetail)) {
            assertThatThrownBy(() -> service.create(E, LEAVE, req)).isInstanceOf(CodeException.class);
        }
    }

    @Test
    void 근태_구분은_그룹_처리방식만_받고_휴가_설정이_없다() {
        var trip = new AttendanceCodeCreateRequest("SITE", "현장 출장", null, 50, true, "BUSINESS_TRIP", null);
        AttendanceCodeView v = service.create(E, CodeGroups.ATTENDANCE_TYPE, trip);
        assertThat(v.leave()).isNull();

        var wrong = new AttendanceCodeCreateRequest("W", "w", null, 1, true, "COMPANY", null);
        assertThatThrownBy(() -> service.create(E, CodeGroups.ATTENDANCE_TYPE, wrong)).isInstanceOf(CodeException.class);
    }

    @Test
    void 근태코드_그룹이_아니면_404() {
        assertThatThrownBy(() -> service.list(E, "JOB_GRADE"))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 다른_법인_그룹은_404() {
        assertThatThrownBy(() -> service.list("C002", LEAVE))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 약정휴가를_수정한다() {
        when(codes.findByEnterCdAndGroupCodeAndCode(E, LEAVE, "REFRESH"))
                .thenReturn(Optional.of(stored("REFRESH", "COMPANY", false)));
        when(leaves.findByEnterCdAndCode(E, "REFRESH")).thenReturn(Optional.of(detail("REFRESH", true, "5")));

        var req = new AttendanceCodeUpdateRequest("리프레시 휴가", "근속 3년마다", 140, true, "COMPANY",
                new LeaveDetailRequest(true, true, false, true, true, new BigDecimal("3")));
        AttendanceCodeView v = service.update(E, LEAVE, "REFRESH", req);

        assertThat(v.name()).isEqualTo("리프레시 휴가");
        assertThat(v.leave().unitHalfDay()).isTrue();
        assertThat(v.leave().proofRequired()).isTrue();
        assertThat(v.leave().annualLimitDays()).isEqualByComparingTo("3");
    }

    @Test
    void 약정휴가를_삭제하면_상세_설정도_지운다() {
        CommonCode c = stored("REFRESH", "COMPANY", false);
        AttendanceLeaveType d = detail("REFRESH", true, "5");
        when(codes.findByEnterCdAndGroupCodeAndCode(E, LEAVE, "REFRESH")).thenReturn(Optional.of(c));
        when(leaves.findByEnterCdAndCode(E, "REFRESH")).thenReturn(Optional.of(d));

        service.delete(E, LEAVE, "REFRESH");

        verify(leaves).delete(d);
        verify(codes).delete(c);
    }

    @Test
    void 같은_코드의_근태_구분을_삭제해도_휴가_상세_설정은_보존한다() {
        CommonCode c = new CommonCode(E, CodeGroups.ATTENDANCE_TYPE, "REFRESH", "리프레시 근무", null, 1, true,
                "WORK", T0);
        when(codes.findByEnterCdAndGroupCodeAndCode(E, CodeGroups.ATTENDANCE_TYPE, "REFRESH"))
                .thenReturn(Optional.of(c));

        service.delete(E, CodeGroups.ATTENDANCE_TYPE, "REFRESH");

        verify(leaves, never()).findByEnterCdAndCode(any(), any());
        verify(leaves, never()).delete(any());
        verify(codes).delete(c);
    }

    @Test
    void 법정휴가_기본_코드는_삭제할_수_없다() {
        when(codes.findByEnterCdAndGroupCodeAndCode(E, LEAVE, "MATERNITY"))
                .thenReturn(Optional.of(stored("MATERNITY", "STATUTORY", true)));

        assertThatThrownBy(() -> service.delete(E, LEAVE, "MATERNITY"))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
        verify(codes, never()).delete(any());
        verify(leaves, never()).delete(any());
    }

    @Test
    void 법정휴가는_일수_유급_처리방식을_바꿀_수_없고_사용_단위는_고칠_수_있다() {
        when(codes.findByEnterCdAndGroupCodeAndCode(E, LEAVE, "FAMILY_CARE"))
                .thenReturn(Optional.of(stored("FAMILY_CARE", "STATUTORY", true)));
        when(leaves.findByEnterCdAndCode(E, "FAMILY_CARE")).thenReturn(Optional.of(detail("FAMILY_CARE", null, null)));

        var limit = new AttendanceCodeUpdateRequest("가족돌봄휴가", null, 50, true, "STATUTORY",
                new LeaveDetailRequest(true, false, false, null, false, new BigDecimal("10")));
        var paid = new AttendanceCodeUpdateRequest("가족돌봄휴가", null, 50, true, "STATUTORY",
                new LeaveDetailRequest(true, false, false, true, false, null));
        var type = new AttendanceCodeUpdateRequest("가족돌봄휴가", null, 50, true, "COMPANY",
                new LeaveDetailRequest(true, false, false, null, false, null));
        for (var req : List.of(limit, paid, type)) {
            assertThatThrownBy(() -> service.update(E, LEAVE, "FAMILY_CARE", req))
                    .isInstanceOfSatisfying(CodeException.class,
                            e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
        }

        var units = new AttendanceCodeUpdateRequest("가족돌봄휴가", null, 50, true, "STATUTORY",
                new LeaveDetailRequest(true, false, true, null, true, null));
        AttendanceCodeView v = service.update(E, LEAVE, "FAMILY_CARE", units);
        assertThat(v.leave().unitHour()).isTrue();
        assertThat(v.leave().paid()).isNull();
        assertThat(v.processType()).isEqualTo("STATUTORY");
    }

    @Test
    void 법정휴가는_조회일_기준_법정_기준값을_보이고_없으면_값이_비어_있다() {
        when(codes.findByEnterCdAndGroupCodeOrderBySortOrderAscCodeAsc(E, LEAVE))
                .thenReturn(List.of(stored("FAMILY_CARE", "STATUTORY", true), stored("REFRESH", "COMPANY", false)));
        when(leaves.findByEnterCd(E)).thenReturn(List.of(detail("FAMILY_CARE", null, null), detail("REFRESH", true, "5")));
        LegalRateRow row = new LegalRateRow("FAMILY_CARE_LEAVE_DAYS_PER_YEAR", LocalDate.of(2020, 1, 1), null,
                new BigDecimal("10"), "DAYS", "남녀고용평등법 제22조의2", null);
        when(legalRates.findEffective("FAMILY_CARE_LEAVE_DAYS_PER_YEAR", TODAY)).thenReturn(Optional.of(row));

        List<AttendanceCodeView> list = service.list(E, LEAVE);

        List<LegalValue> legal = list.get(0).leave().legalValues();
        assertThat(legal).extracting(LegalValue::itemCode)
                .containsExactly("FAMILY_CARE_LEAVE_DAYS_PER_YEAR", "FAMILY_CARE_LEAVE_PAID_DAYS");
        assertThat(legal.get(0).value()).isEqualByComparingTo("10");
        assertThat(legal.get(1).value()).isNull();
        assertThat(list.get(1).leave().legalValues()).isEmpty();
    }

    @Test
    void 참조_건수를_공통코드_참조_목록에서_센다() {
        when(codes.findByEnterCdAndGroupCodeOrderBySortOrderAscCodeAsc(E, CodeGroups.ATTENDANCE_TYPE))
                .thenReturn(List.of(new CommonCode(E, CodeGroups.ATTENDANCE_TYPE, "REMOTE", "재택", null, 1, true,
                        "REMOTE", T0)));
        when(usages.countsByCode(any(), any())).thenReturn(Map.of());

        AttendanceCodeView v = service.list(E, CodeGroups.ATTENDANCE_TYPE).get(0);
        assertThat(v.inUse()).isFalse();
        assertThat(v.leave()).isNull();
    }
}
