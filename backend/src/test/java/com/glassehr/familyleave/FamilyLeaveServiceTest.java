package com.glassehr.familyleave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.appointment.AppointmentDtos.AppointmentRequest;
import com.glassehr.appointment.AppointmentDtos.AppointmentView;
import com.glassehr.appointment.AppointmentDtos.LeavePeriodView;
import com.glassehr.appointment.AppointmentService;
import com.glassehr.attendance.WorkPattern;
import com.glassehr.attendance.WorkPatternDtos.AssignRequest;
import com.glassehr.attendance.WorkPatternDtos.AssignmentView;
import com.glassehr.attendance.WorkPatternRepository;
import com.glassehr.attendance.WorkPatternService;
import com.glassehr.employee.Employee;
import com.glassehr.employee.EmployeeRepository;
import com.glassehr.familyleave.FamilyLeaveDtos.BalanceView;
import com.glassehr.familyleave.FamilyLeaveDtos.CreateRequest;
import com.glassehr.familyleave.FamilyLeaveDtos.RequestView;
import com.glassehr.familyleave.FamilyLeaveRequest.Kind;
import com.glassehr.familyleave.FamilyLeaveRequest.Status;
import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import com.glassehr.org.OrgWriteLock;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

/**
 * 흐름(신청 → 승인 → 발령·근무유형)과 legal_rate 조회 연결을 검사한다.
 * 여기 기준값은 <b>테스트용 임의 값</b>이며 법령 정답이 아니다. 법령 정답 사례는 tests/legal-fixtures/ 로 받는다.
 */
class FamilyLeaveServiceTest {

    private static final String ENTER = "C001";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate HIRE = LocalDate.of(2020, 3, 2);
    private static final LocalDate START = LocalDate.of(2027, 3, 1);

    private FamilyLeaveRequestRepository requests;
    private EmployeeRepository employees;
    private WorkPatternRepository patterns;
    private WorkPatternService workPatterns;
    private AppointmentService appointments;
    private LegalRateService rates;
    private OrgWriteLock orgLock;
    private FamilyLeaveService service;
    private final List<FamilyLeaveRequest> store = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(1);
    private final AtomicLong apptIds = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        requests = mock(FamilyLeaveRequestRepository.class);
        employees = mock(EmployeeRepository.class);
        patterns = mock(WorkPatternRepository.class);
        workPatterns = mock(WorkPatternService.class);
        appointments = mock(AppointmentService.class);
        rates = mock(LegalRateService.class);
        orgLock = mock(OrgWriteLock.class);
        Clock clock = Clock.fixed(Instant.parse("2027-01-10T01:00:00Z"), SEOUL);
        service = new FamilyLeaveService(requests, employees, patterns, workPatterns, appointments, rates, orgLock, clock);

        Employee e = mock(Employee.class);
        when(e.getEmpId()).thenReturn(1L);
        when(e.getEnterCd()).thenReturn(ENTER);
        when(e.getHireDate()).thenReturn(HIRE);
        when(employees.findForUpdate(1L)).thenReturn(Optional.of(e));
        when(employees.existsById(1L)).thenReturn(true);

        when(requests.findByEmpIdOrderByStartDateAscReqIdAsc(1L)).thenAnswer(i -> List.copyOf(store));
        when(requests.saveAndFlush(any())).thenAnswer(i -> {
            FamilyLeaveRequest r = i.getArgument(0);
            if (r.getReqId() == null) {
                r.withId(ids.getAndIncrement());
                store.add(r);
            }
            return r;
        });
        when(requests.findEmpIdByReqId(anyLong())).thenReturn(Optional.of(1L));
        when(requests.findForUpdate(anyLong())).thenAnswer(i -> store.stream()
                .filter(r -> r.getReqId() == (long) i.getArgument(0)).findFirst());
        when(appointments.create(eq(1L), any())).thenAnswer(i -> appt(apptIds.getAndIncrement(), i.getArgument(1)));

        // 테스트용 임의 기준값(법령 정답 아님)
        rate(LegalRateItem.PARENTAL_LEAVE_MAX_MONTHS, 10);
        rate(LegalRateItem.PARENTAL_LEAVE_EXTENSION_MONTHS, 4);
        rate(LegalRateItem.PARENTAL_LEAVE_SPLIT_COUNT, 1);
        rate(LegalRateItem.PARENTAL_LEAVE_CHILD_MAX_AGE, 5);
        rate(LegalRateItem.PARENTAL_LEAVE_MIN_SERVICE_MONTHS, 3);
        rate(LegalRateItem.CHILDCARE_REDUCED_HOURS_MAX_MONTHS, 6);
        rate(LegalRateItem.CHILDCARE_REDUCED_UNUSED_LEAVE_MULTIPLIER, 3);
        rate(LegalRateItem.CHILDCARE_REDUCED_MIN_UNIT_MONTHS, 2);
        rate(LegalRateItem.CHILDCARE_REDUCED_WEEKLY_HOURS_MIN, 10);
        rate(LegalRateItem.CHILDCARE_REDUCED_WEEKLY_HOURS_MAX, 30);
        rate(LegalRateItem.CHILDCARE_REDUCED_CHILD_MAX_AGE, 7);
        rate(LegalRateItem.CHILDCARE_REDUCED_MIN_SERVICE_MONTHS, 3);
        rate(LegalRateItem.FAMILY_CARE_LEAVE_OF_ABSENCE_DAYS_PER_YEAR, 50);
        rate(LegalRateItem.FAMILY_CARE_LEAVE_MIN_UNIT_DAYS, 20);
        rate(LegalRateItem.FAMILY_CARE_LEAVE_MIN_SERVICE_MONTHS, 3);
    }

    private void rate(LegalRateItem item, int value) {
        when(rates.findEffective(eq(item.code()), any())).thenReturn(Optional.of(new LegalRateRow(item.code(),
                LocalDate.of(2000, 1, 1), null, BigDecimal.valueOf(value), item.unit(), "테스트", "https://example.test")));
    }

    private void noRate(LegalRateItem item) {
        when(rates.findEffective(eq(item.code()), any())).thenReturn(Optional.empty());
    }

    private static AppointmentView appt(long id, AppointmentRequest r) {
        return new AppointmentView(id, "A" + id, r.apptTypeCode(), 1, r.effectiveDate(), null, null, r.reason(),
                r.leaveStartDate(), r.leaveEndDate(), false, null, LocalDateTime.now(), null, null, null);
    }

    private static CreateRequest parental(String child, LocalDate birth, LocalDate start, LocalDate end) {
        return new CreateRequest(Kind.PARENTAL_LEAVE, child, birth, null, start, end, null, null, "양육");
    }

    private static CreateRequest reduced(LocalDate start, LocalDate end, long patternId) {
        return new CreateRequest(Kind.CHILDCARE_REDUCED_HOURS, "첫째", LocalDate.of(2025, 5, 1), null, start, end,
                patternId, null, "양육");
    }

    private static CreateRequest care(LocalDate start, LocalDate end) {
        return new CreateRequest(Kind.FAMILY_CARE_LEAVE, null, null, "부모", start, end, null, null, "간병");
    }

    /** 승인된 휴직을 만들고 휴직 발령 이력이 신청 기간 그대로라고 둔다. */
    private RequestView approvedLeave(CreateRequest req) {
        RequestView v = service.approve(service.create(1L, req, "hr1").reqId(), "hr1");
        List<LeavePeriodView> periods = store.stream().filter(r -> r.getLeaveApptId() != null)
                .map(r -> new LeavePeriodView(1, r.getLeaveApptId(), r.getStartDate(), r.getEndDate(), r.getEndDate(),
                        null, "r"))
                .toList();
        when(appointments.leavePeriods(eq(1L), any(), any())).thenReturn(periods);
        return v;
    }

    private static void assertStatus(Throwable t, HttpStatus status) {
        assertThat(t).isInstanceOf(FamilyLeaveException.class);
        assertThat(((FamilyLeaveException) t).status()).isEqualTo(status);
    }

    // ---------------------------------------------------------------- 신청

    @Test
    void 육아휴직_신청은_대기로_저장하고_자녀_나이를_넘으면_학년_확인_경고를_남긴다() {
        RequestView v = service.create(1L, parental("첫째", LocalDate.of(2020, 1, 1), START, START.plusMonths(2)), "hr1");
        assertThat(v.status()).isEqualTo(Status.REQUESTED);
        assertThat(v.requestedBy()).isEqualTo("hr1");
        assertThat(v.warnings()).singleElement().asString().contains("만 7세", "만 5세", "학년");
        verify(appointments, never()).create(anyLong(), any());
    }

    @Test
    void 계속근로가_기준보다_짧으면_거부하지_않고_경고만_남긴다() {
        Employee e = mock(Employee.class);
        when(e.getEmpId()).thenReturn(2L);
        when(e.getEnterCd()).thenReturn(ENTER);
        when(e.getHireDate()).thenReturn(START.minusMonths(1));
        when(employees.findForUpdate(2L)).thenReturn(Optional.of(e));
        RequestView v = service.create(2L, care(START, START.plusDays(29)), "hr1");
        assertThat(v.warnings()).singleElement().asString().contains("계속근로가 1개월", "3개월 미만");
    }

    @Test
    void 자격_기준값이_없으면_경고로_알린다() {
        noRate(LegalRateItem.PARENTAL_LEAVE_CHILD_MAX_AGE);
        RequestView v = service.create(1L, parental("첫째", LocalDate.of(2026, 1, 1), START, START.plusMonths(2)), "hr1");
        assertThat(v.warnings()).singleElement().asString().contains("PARENTAL_LEAVE_CHILD_MAX_AGE");
    }

    @Test
    void 출생_전_육아휴직은_임신_중_신청인지_확인하라고_알린다() {
        RequestView v = service.create(1L, parental("둘째", null, START, START.plusMonths(2)), "hr1");
        assertThat(v.warnings()).singleElement().asString().contains("임신 중");
    }

    @Test
    void 종류별_필수값이_없으면_400() {
        Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> service.create(1L,
                new CreateRequest(Kind.PARENTAL_LEAVE, " ", null, null, START, START.plusMonths(1), null, null, "r"), "hr1"));
        assertStatus(t, HttpStatus.BAD_REQUEST);
        t = org.assertj.core.api.Assertions.catchThrowable(() -> service.create(1L,
                new CreateRequest(Kind.FAMILY_CARE_LEAVE, null, null, null, START, START.plusMonths(1), null, null, "r"), "hr1"));
        assertStatus(t, HttpStatus.BAD_REQUEST);
        t = org.assertj.core.api.Assertions.catchThrowable(() -> service.create(1L,
                new CreateRequest(Kind.CHILDCARE_REDUCED_HOURS, "첫째", LocalDate.of(2025, 1, 1), null, START,
                        START.plusMonths(3), null, null, "r"), "hr1"));
        assertStatus(t, HttpStatus.BAD_REQUEST);
        t = org.assertj.core.api.Assertions.catchThrowable(() -> service.create(1L,
                new CreateRequest(Kind.FAMILY_CARE_LEAVE, null, null, "부모", START, START.plusMonths(1), null, true, "r"), "hr1"));
        assertStatus(t, HttpStatus.BAD_REQUEST);
        t = org.assertj.core.api.Assertions.catchThrowable(() -> service.create(1L,
                parental("첫째", null, START, START.minusDays(1)), "hr1"));
        assertStatus(t, HttpStatus.BAD_REQUEST);
        t = org.assertj.core.api.Assertions.catchThrowable(() -> service.create(1L,
                parental("첫째", null, HIRE.minusDays(1), START), "hr1"));
        assertStatus(t, HttpStatus.BAD_REQUEST);
    }

    @Test
    void 기간이_겹치는_신청은_409() {
        service.create(1L, parental("첫째", null, START, START.plusMonths(2)), "hr1");
        Throwable t = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.create(1L, care(START.plusMonths(1), START.plusMonths(3)), "hr1"));
        assertStatus(t, HttpStatus.CONFLICT);
    }

    // ---------------------------------------------------------------- 육아휴직 한도

    @Test
    void 육아휴직_한도_개월을_넘으면_409이고_추가기간_요건이_확인되면_늘어난다() {
        // 임의 한도 10개월: 10개월 1일은 거부
        Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> service.create(1L,
                parental("첫째", null, START, START.plusMonths(10)), "hr1"));
        assertStatus(t, HttpStatus.CONFLICT);
        assertThat(t).hasMessageContaining("10개월");
        // 추가기간(임의 4개월) 요건 확인 시 14개월까지
        RequestView v = service.create(1L, new CreateRequest(Kind.PARENTAL_LEAVE, "첫째", null, null, START,
                START.plusMonths(14).minusDays(1), null, true, "양육"), "hr1");
        assertThat(v.extension()).isTrue();
    }

    @Test
    void 분할_횟수를_넘으면_409() {
        // 임의 분할 1회 = 전체 2번 사용
        approvedLeave(parental("첫째", null, START, START.plusMonths(1).minusDays(1)));
        approvedLeave(parental("첫째", null, START.plusMonths(2), START.plusMonths(3).minusDays(1)));
        Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> service.create(1L,
                parental("첫째", null, START.plusMonths(4), START.plusMonths(5).minusDays(1)), "hr1"));
        assertStatus(t, HttpStatus.CONFLICT);
        assertThat(t).hasMessageContaining("1회까지");
        // 다른 자녀는 따로 센다
        service.create(1L, parental("둘째", null, START.plusMonths(4), START.plusMonths(5).minusDays(1)), "hr1");
    }

    @Test
    void 한도_기준값이_없으면_409() {
        noRate(LegalRateItem.PARENTAL_LEAVE_MAX_MONTHS);
        Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> service.create(1L,
                parental("첫째", null, START, START.plusMonths(1)), "hr1"));
        assertStatus(t, HttpStatus.CONFLICT);
    }

    @Test
    void 이미_가산_기간을_단축에_쓴_뒤에는_그_기간을_육아휴직으로_다시_쓸_수_없다() {
        patternsForReduced(20);
        LocalDate reducedEnd = START.plusMonths(12).minusDays(1);
        service.approve(service.create(1L, reduced(START, reducedEnd, 10L), "hr1").reqId(), "hr1");

        Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> service.create(1L,
                parental("첫째", null, reducedEnd.plusDays(1), reducedEnd.plusMonths(10)), "hr1"));

        assertStatus(t, HttpStatus.CONFLICT);
        assertThat(t).hasMessageContaining("이미 쓴 단축 기간");
    }

    // ---------------------------------------------------------------- 승인 → 발령

    @Test
    void 휴직_승인은_휴직_발령을_확정하고_종료일_다음날_복직_발령을_예정으로_만든다() {
        LocalDate end = START.plusMonths(3).minusDays(1);
        long id = service.create(1L, parental("첫째", null, START, end), "hr1").reqId();
        RequestView v = service.approve(id, "boss");

        ArgumentCaptor<AppointmentRequest> captor = ArgumentCaptor.forClass(AppointmentRequest.class);
        InOrder order = inOrder(orgLock, employees, appointments);
        order.verify(orgLock).acquire();
        order.verify(employees).findForUpdate(1L);
        order.verify(appointments).create(eq(1L), captor.capture());
        order.verify(appointments).confirm(100L);
        order.verify(appointments).create(eq(1L), captor.capture());
        verify(appointments, never()).confirm(101L);

        AppointmentRequest leave = captor.getAllValues().get(0);
        assertThat(leave.apptTypeCode()).isEqualTo("LEAVE");
        assertThat(leave.effectiveDate()).isEqualTo(START);
        assertThat(leave.leaveStartDate()).isEqualTo(START);
        assertThat(leave.leaveEndDate()).isEqualTo(end);
        assertThat(leave.reason()).contains("육아휴직");
        AppointmentRequest ret = captor.getAllValues().get(1);
        assertThat(ret.apptTypeCode()).isEqualTo("RETURN");
        assertThat(ret.effectiveDate()).isEqualTo(end.plusDays(1));

        assertThat(v.status()).isEqualTo(Status.APPROVED);
        assertThat(v.decidedBy()).isEqualTo("boss");
        assertThat(v.leaveApptId()).isEqualTo(100L);
        assertThat(v.returnApptId()).isEqualTo(101L);
    }

    @Test
    void 처리된_신청은_다시_승인하거나_반려할_수_없다() {
        long id = approvedLeave(care(START, START.plusDays(29))).reqId();
        assertStatus(org.assertj.core.api.Assertions.catchThrowable(() -> service.approve(id, "hr1")),
                HttpStatus.CONFLICT);
        assertStatus(org.assertj.core.api.Assertions.catchThrowable(() -> service.reject(id, "x", "hr1")),
                HttpStatus.CONFLICT);
    }

    @Test
    void 반려는_사유가_필요하고_취소는_대기_신청만_한다() {
        long id = service.create(1L, care(START, START.plusDays(29)), "hr1").reqId();
        assertStatus(org.assertj.core.api.Assertions.catchThrowable(() -> service.reject(id, " ", "hr1")),
                HttpStatus.BAD_REQUEST);
        RequestView v = service.reject(id, "서류 미비", "hr1");
        assertThat(v.status()).isEqualTo(Status.REJECTED);
        assertThat(v.decisionNote()).isEqualTo("서류 미비");
        // 반려된 신청은 겹침 검사에서 빠진다
        long id2 = service.create(1L, care(START, START.plusDays(29)), "hr1").reqId();
        assertThat(service.cancel(id2, null, "hr1").status()).isEqualTo(Status.CANCELLED);
        verify(appointments, never()).create(anyLong(), any());
    }

    // ---------------------------------------------------------------- 육아기 근로시간 단축

    private void patternsForReduced(int reducedWeeklyHours) {
        WorkPattern reducedPattern = mock(WorkPattern.class);
        when(reducedPattern.isActive()).thenReturn(true);
        when(reducedPattern.getWeeklyContractMinutes()).thenReturn(reducedWeeklyHours * 60);
        when(patterns.findByEnterCdAndPatternId(ENTER, 10L)).thenReturn(Optional.of(reducedPattern));
        WorkPattern standard = mock(WorkPattern.class);
        when(standard.getWeeklyContractMinutes()).thenReturn(40 * 60);
        when(patterns.findByEnterCdAndPatternId(ENTER, 20L)).thenReturn(Optional.of(standard));
        when(workPatterns.effective(eq(ENTER), eq(1L), any())).thenReturn(
                Optional.of(new AssignmentView(5, 1, 20, "STD", "표준", HIRE, null, null)));
        AtomicLong assignIds = new AtomicLong(50);
        when(workPatterns.assign(eq(ENTER), eq(1L), any(), anyString())).thenAnswer(i -> {
            AssignRequest r = i.getArgument(2);
            return new AssignmentView(assignIds.getAndIncrement(), 1, r.patternId(), "P", "P", r.validFrom(), null,
                    r.reason());
        });
        when(workPatterns.replaceLatest(eq(ENTER), eq(1L), anyLong(), any())).thenAnswer(i -> {
            AssignRequest r = i.getArgument(3);
            return new AssignmentView(51, 1, r.patternId(), "P", "P", r.validFrom(), null, r.reason());
        });
    }

    @Test
    void 단축_승인은_단축_근무유형을_배정하고_종료일_다음날_원래_근무유형으로_되돌린다() {
        patternsForReduced(20);
        LocalDate end = START.plusMonths(3).minusDays(1);
        long id = service.create(1L, reduced(START, end, 10L), "hr1").reqId();
        RequestView v = service.approve(id, "hr1");

        ArgumentCaptor<AssignRequest> captor = ArgumentCaptor.forClass(AssignRequest.class);
        verify(workPatterns, org.mockito.Mockito.times(2)).assign(eq(ENTER), eq(1L), captor.capture(), eq("hr1"));
        assertThat(captor.getAllValues().get(0).patternId()).isEqualTo(10L);
        assertThat(captor.getAllValues().get(0).validFrom()).isEqualTo(START);
        assertThat(captor.getAllValues().get(1).patternId()).isEqualTo(20L);
        assertThat(captor.getAllValues().get(1).validFrom()).isEqualTo(end.plusDays(1));
        assertThat(v.reducedAssignId()).isEqualTo(50L);
        assertThat(v.restoreAssignId()).isEqualTo(51L);
        verify(appointments, never()).create(anyLong(), any());
    }

    @Test
    void 직전_단축의_복원일에_이어지는_단축은_복원_배정을_바꾼다() {
        patternsForReduced(20);
        LocalDate firstEnd = START.plusMonths(2).minusDays(1);
        service.approve(service.create(1L, reduced(START, firstEnd, 10L), "hr1").reqId(), "hr1");
        LocalDate secondEnd = firstEnd.plusMonths(2);

        service.approve(service.create(1L, reduced(firstEnd.plusDays(1), secondEnd, 10L), "hr1").reqId(), "hr1");

        verify(workPatterns).replaceLatest(eq(ENTER), eq(1L), eq(51L),
                org.mockito.ArgumentMatchers.argThat(r -> r.validFrom().equals(firstEnd.plusDays(1))));
    }

    @Test
    void 단축_근무유형의_주_소정근로시간이_기준_밖이면_400() {
        patternsForReduced(31);
        Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> service.create(1L,
                reduced(START, START.plusMonths(3).minusDays(1), 10L), "hr1"));
        assertStatus(t, HttpStatus.BAD_REQUEST);
        assertThat(t).hasMessageContaining("10시간 이상 30시간 이하");
    }

    @Test
    void 단축_1회_최소_기간보다_짧으면_400() {
        patternsForReduced(20);
        Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> service.create(1L,
                reduced(START, START.plusMonths(2).minusDays(2), 10L), "hr1"));
        assertStatus(t, HttpStatus.BAD_REQUEST);
        assertThat(t).hasMessageContaining("2개월 이상");
    }

    @Test
    void 단축_한도는_기본_기간에_미사용_육아휴직_기간_배수를_더한다() {
        patternsForReduced(20);
        // 육아휴직 임의 한도 10개월 중 8개월 사용 → 미사용 2개월 × 임의 배수 3 + 임의 기본 6 = 12개월
        approvedLeave(parental("첫째", null, START, START.plusMonths(8).minusDays(1)));
        LocalDate s = START.plusMonths(8);
        Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> service.create(1L,
                reduced(s, s.plusMonths(12), 10L), "hr1"));
        assertStatus(t, HttpStatus.CONFLICT);
        assertThat(t).hasMessageContaining("12개월");
        service.create(1L, reduced(s, s.plusMonths(12).minusDays(1), 10L), "hr1");
    }

    // ---------------------------------------------------------------- 가족돌봄휴직

    @Test
    void 가족돌봄휴직은_1회_최소_일수와_연간_한도를_검사한다() {
        Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> service.create(1L,
                care(START, START.plusDays(18)), "hr1"));
        assertStatus(t, HttpStatus.BAD_REQUEST);
        approvedLeave(care(START, START.plusDays(29)));
        // 같은 해 30일 + 21일 = 51일 > 임의 한도 50일
        t = org.assertj.core.api.Assertions.catchThrowable(() -> service.create(1L,
                care(START.plusMonths(2), START.plusMonths(2).plusDays(20)), "hr1"));
        assertStatus(t, HttpStatus.CONFLICT);
        // 연도를 넘으면 해마다 나누어 센다: 2027년 12월 11일 + 2028년 10일
        service.create(1L, care(LocalDate.of(2027, 12, 21), LocalDate.of(2028, 1, 10)), "hr1");
    }

    // ---------------------------------------------------------------- 남은 기간

    @Test
    void 남은_기간은_자녀별_육아휴직_단축과_그해_가족돌봄휴직을_보여준다() {
        approvedLeave(parental("첫째", null, START, START.plusMonths(3).minusDays(1)));
        approvedLeave(care(START.plusMonths(4), START.plusMonths(4).plusDays(24)));
        BalanceView b = service.balance(1L, LocalDate.of(2027, 6, 1));

        assertThat(b.children()).singleElement().satisfies(c -> {
            assertThat(c.childLabel()).isEqualTo("첫째");
            assertThat(c.parentalLeave().limitMonths()).isEqualTo(10);
            assertThat(c.parentalLeave().usedMonths()).isEqualTo(3);
            assertThat(c.parentalLeave().usedDays()).isZero();
            assertThat(c.parentalLeave().remainingMonths()).isEqualTo(7);
            assertThat(c.parentalLeave().uses()).isEqualTo(1);
            assertThat(c.parentalLeave().usesMax()).isEqualTo(2);
            // 단축: 6 + (10 - 3) × 3 = 27
            assertThat(c.reducedHours().limitMonths()).isEqualTo(27);
            assertThat(c.reducedHours().remainingMonths()).isEqualTo(27);
        });
        assertThat(b.familyCareLeave().year()).isEqualTo(2027);
        assertThat(b.familyCareLeave().usedDays()).isEqualTo(25);
        assertThat(b.familyCareLeave().remainingDays()).isEqualTo(25);
    }

    @Test
    void 남은_기간은_기준값이_없으면_한도를_비우고_안내한다() {
        approvedLeave(parental("첫째", null, START, START.plusMonths(3).minusDays(1)));
        noRate(LegalRateItem.CHILDCARE_REDUCED_UNUSED_LEAVE_MULTIPLIER);
        noRate(LegalRateItem.FAMILY_CARE_LEAVE_OF_ABSENCE_DAYS_PER_YEAR);
        BalanceView b = service.balance(1L, LocalDate.of(2027, 6, 1));
        assertThat(b.children().get(0).reducedHours().limitMonths()).isNull();
        assertThat(b.children().get(0).reducedHours().remainingMonths()).isNull();
        assertThat(b.familyCareLeave().limitDays()).isNull();
        assertThat(b.notes()).anyMatch(n -> n.contains("CHILDCARE_REDUCED_UNUSED_LEAVE_MULTIPLIER"))
                .anyMatch(n -> n.contains("FAMILY_CARE_LEAVE_OF_ABSENCE_DAYS_PER_YEAR"));
    }

    @Test
    void 휴직_발령이_취소되거나_일찍_복직하면_실제_휴직_기간만_센다() {
        approvedLeave(parental("첫째", null, START, START.plusMonths(6).minusDays(1)));
        FamilyLeaveRequest r = store.get(0);
        // 3개월 만에 복직 발령으로 끝남
        when(appointments.leavePeriods(eq(1L), any(), any())).thenReturn(List.of(new LeavePeriodView(1,
                r.getLeaveApptId(), START, r.getEndDate(), START.plusMonths(3).minusDays(1), 999L, "r")));
        assertThat(service.balance(1L, START).children().get(0).parentalLeave().usedMonths()).isEqualTo(3);
        // 발령 취소로 이력에 없음
        when(appointments.leavePeriods(eq(1L), any(), any())).thenReturn(List.of());
        assertThat(service.balance(1L, START).children().get(0).parentalLeave().usedMonths()).isZero();
    }

    @Test
    void 조기_복직_뒤_기간과_취소된_휴직은_겹침에서_제외한다() {
        patternsForReduced(20);
        approvedLeave(parental("첫째", null, START, START.plusMonths(6).minusDays(1)));
        FamilyLeaveRequest r = store.get(0);
        when(appointments.leavePeriods(eq(1L), any(), any())).thenReturn(List.of(new LeavePeriodView(1,
                r.getLeaveApptId(), START, r.getEndDate(), START.plusMonths(3).minusDays(1), 999L, "조기 복직")));

        service.create(1L, reduced(START.plusMonths(4), START.plusMonths(6).minusDays(1), 10L), "hr1");

        store.remove(1);
        when(appointments.leavePeriods(eq(1L), any(), any())).thenReturn(List.of());
        service.create(1L, care(START.plusMonths(1), START.plusMonths(2).minusDays(1)), "hr1");
    }

    @Test
    void 기준값_단위가_다르면_409() {
        when(rates.findEffective(eq(LegalRateItem.PARENTAL_LEAVE_MAX_MONTHS.code()), any())).thenReturn(Optional.of(
                new LegalRateRow("PARENTAL_LEAVE_MAX_MONTHS", LocalDate.of(2000, 1, 1), null, BigDecimal.TEN, "DAYS",
                        "테스트", "https://example.test")));
        assertThatThrownBy(() -> service.create(1L, parental("첫째", null, START, START.plusMonths(1)), "hr1"))
                .isInstanceOf(FamilyLeaveException.class).hasMessageContaining("단위");
    }
}
