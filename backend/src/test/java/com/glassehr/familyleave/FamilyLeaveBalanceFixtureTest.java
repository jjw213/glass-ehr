package com.glassehr.familyleave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import com.glassehr.familyleave.FamilyLeaveDtos.ChildBalance;
import com.glassehr.familyleave.FamilyLeaveDtos.CreateRequest;
import com.glassehr.familyleave.FamilyLeaveDtos.MonthUsage;
import com.glassehr.familyleave.FamilyLeaveRequest.Kind;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import com.glassehr.org.OrgWriteLock;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 육아휴직(남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조·제19조의4 제1항), 육아기 근로시간 단축(제19조의2 제1항·제3항·제4항,
 * 제19조의4 제2항), 가족돌봄휴직(제22조의2 제4항 제1호)의 허용 여부·남은 기간·남은 분할 횟수를
 * tests/legal-fixtures/family-leave-balance.csv 정답으로 검증한다. 기간 셈은 민법 제157조·제160조.
 *
 * <p>기준값은 legal_rate 시드(seed/legal_rate_hr_current.sql, seed/legal_rate_family_leave.sql)를 읽어
 * {@link LegalRateService} 조회 결과로 넣는다. fixture 해석은 docs/legal-rate-seed-sources.md 'fixture 해석과 범위'를 따른다.
 * <ul>
 *   <li>기대남은기간: 승인 후(반려면 신청 전) 남은 <b>온전한 개월 수</b>. 화면 값 "N개월에서 D일 뺀 기간"은 D가 있으면 N-1개월이다.</li>
 *   <li>기대남은분할횟수: 기준일의 법정 분할 횟수 - 이미 나눈 횟수(구간 수 - 1). 화면 값은 사용 구간 수 / 최대 구간 수(분할 횟수 + 1)다.</li>
 *   <li>주 15·35시간 경계 행은 시간 열이 없어 사례 이름의 "N시간"을 단축 근무유형의 주 소정근로시간으로 쓴다(같은 문서 355행).</li>
 * </ul>
 */
class FamilyLeaveBalanceFixtureTest {

    private static final String FIXTURE = "tests/legal-fixtures/family-leave-balance.csv";
    private static final List<String> SEEDS = List.of(
            "backend/src/main/resources/db/oracle/seed/legal_rate_hr_current.sql",
            "backend/src/main/resources/db/oracle/seed/legal_rate_family_leave.sql");
    private static final Pattern SEED_ROW = Pattern.compile(
            "insert_period\\(\\s*'([A-Z0-9_]+)',\\s*DATE '(\\d{4}-\\d{2}-\\d{2})',\\s*(NULL|DATE '\\d{4}-\\d{2}-\\d{2}'),"
                    + "\\s*([0-9.]+),\\s*'([A-Z]+)'");
    private static final Pattern HOURS = Pattern.compile("(\\d+)시간");
    private static final String CHILD = "첫째";
    private static final LocalDate CHILD_BIRTH = LocalDate.of(2019, 1, 1);
    private static final long CURRENT_PATTERN = 1L;
    private static final long REDUCED_PATTERN = 2L;
    /** 단축 전 근무유형의 주 소정근로시간(주 40시간). 시간 경계가 아닌 단축 행은 주 20시간 근무유형을 쓴다. */
    private static final int CURRENT_WEEKLY_MINUTES = 40 * 60;
    private static final int DEFAULT_REDUCED_HOURS = 20;

    record Case(String name, Kind kind, LocalDate asOf, List<LocalDate[]> used, List<LocalDate[]> usedParental,
            LocalDate start, LocalDate end, boolean extension, boolean allow, String remaining, Integer remainingSplits) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static Path repoFile(String relative) {
        for (Path base : List.of(Path.of(".."), Path.of("."))) {
            Path p = base.resolve(relative).normalize();
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        throw new IllegalStateException("파일을 찾을 수 없어요: " + relative);
    }

    private static List<LocalDate[]> periods(String cell) {
        List<LocalDate[]> out = new ArrayList<>();
        if (cell.isBlank()) {
            return out;
        }
        for (String p : cell.split(";")) {
            String[] se = p.trim().split("~");
            out.add(new LocalDate[] {LocalDate.parse(se[0]), LocalDate.parse(se[1])});
        }
        return out;
    }

    static Stream<Arguments> cases() throws IOException {
        List<String> lines = Files.readAllLines(repoFile(FIXTURE), StandardCharsets.UTF_8);
        List<Arguments> out = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            // 출처 열(12번째)에 쉼표가 있을 수 있어 앞 11열만 쓴다.
            String[] c = line.split(",", -1);
            out.add(Arguments.of(new Case(c[0], Kind.valueOf(c[1]), LocalDate.parse(c[2]), periods(c[3]), periods(c[4]),
                    LocalDate.parse(c[5]), LocalDate.parse(c[6]), "Y".equals(c[7]), "ALLOW".equals(c[8]), c[9],
                    c[10].isBlank() ? null : Integer.valueOf(c[10]))));
        }
        return out.stream();
    }

    private static List<LegalRateRow> seedRows() throws IOException {
        List<LegalRateRow> rows = new ArrayList<>();
        for (String seed : SEEDS) {
            Matcher m = SEED_ROW.matcher(Files.readString(repoFile(seed), StandardCharsets.UTF_8));
            while (m.find()) {
                LocalDate to = m.group(3).equals("NULL") ? null : LocalDate.parse(m.group(3).substring(6, 16));
                rows.add(new LegalRateRow(m.group(1), LocalDate.parse(m.group(2)), to, new BigDecimal(m.group(4)),
                        m.group(5), "seed", null));
            }
        }
        return rows;
    }

    // ---------------------------------------------------------------- 가짜 저장소·서비스

    private final List<FamilyLeaveRequest> store = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(1);
    private final AtomicLong refIds = new AtomicLong(100);

    private FamilyLeaveService service(int reducedWeeklyMinutes) throws IOException {
        List<LegalRateRow> rows = seedRows();
        LegalRateService rates = mock(LegalRateService.class);
        when(rates.findEffective(anyString(), any())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            LocalDate asOf = inv.getArgument(1);
            return rows.stream()
                    .filter(r -> r.itemCode().equals(code) && !r.effectiveFrom().isAfter(asOf)
                            && (r.effectiveTo() == null || asOf.isBefore(r.effectiveTo())))
                    .reduce((a, b) -> b.effectiveFrom().isAfter(a.effectiveFrom()) ? b : a);
        });

        FamilyLeaveRequestRepository requests = mock(FamilyLeaveRequestRepository.class);
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

        EmployeeRepository employees = mock(EmployeeRepository.class);
        Employee e = mock(Employee.class);
        when(e.getEmpId()).thenReturn(1L);
        when(e.getHireDate()).thenReturn(LocalDate.of(2015, 1, 1));
        when(employees.findForUpdate(1L)).thenReturn(Optional.of(e));
        when(employees.existsById(1L)).thenReturn(true);

        WorkPatternRepository patterns = mock(WorkPatternRepository.class);
        WorkPattern current = pattern("FULL", CURRENT_WEEKLY_MINUTES);
        WorkPattern reduced = pattern("REDUCED", reducedWeeklyMinutes);
        when(patterns.findById(CURRENT_PATTERN)).thenReturn(Optional.of(current));
        when(patterns.findById(REDUCED_PATTERN)).thenReturn(Optional.of(reduced));
        WorkPatternService workPatterns = mock(WorkPatternService.class);
        when(workPatterns.effective(eq(1L), any())).thenAnswer(i -> Optional.of(assignment(CURRENT_PATTERN, i.getArgument(1))));
        when(workPatterns.assign(eq(1L), any(), anyString())).thenAnswer(i -> {
            AssignRequest a = i.getArgument(1);
            return assignment(a.patternId(), a.validFrom());
        });
        when(workPatterns.replaceLatest(eq(1L), anyLong(), any())).thenAnswer(i -> {
            AssignRequest a = i.getArgument(2);
            return assignment(a.patternId(), a.validFrom());
        });

        AppointmentService appointments = mock(AppointmentService.class);
        when(appointments.create(eq("C001"), eq(1L), any())).thenAnswer(i -> appt(refIds.getAndIncrement(), i.getArgument(1)));
        // 휴직 발령 이력은 신청 기간 그대로 둔다(조기 복직 없음).
        when(appointments.leavePeriods(eq("C001"), eq(1L), any(), any())).thenAnswer(i -> store.stream()
                .filter(r -> r.getLeaveApptId() != null)
                .map(r -> new LeavePeriodView(1, r.getLeaveApptId(), r.getStartDate(), r.getEndDate(), r.getEndDate(),
                        null, "r"))
                .toList());

        Clock clock = Clock.fixed(Instant.parse("2026-11-19T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        return new FamilyLeaveService(requests, employees, patterns, workPatterns, appointments, rates,
                mock(OrgWriteLock.class), clock);
    }

    private static WorkPattern pattern(String code, int weeklyMinutes) {
        WorkPattern p = mock(WorkPattern.class);
        when(p.isActive()).thenReturn(true);
        when(p.getPatternCode()).thenReturn(code);
        when(p.getWeeklyContractMinutes()).thenReturn(weeklyMinutes);
        return p;
    }

    private static AssignmentView assignment(long patternId, LocalDate from) {
        return new AssignmentView(patternId * 1000, 1L, patternId, "P" + patternId, "근무유형" + patternId, from, null, "r");
    }

    private static AppointmentView appt(long id, AppointmentRequest r) {
        return new AppointmentView(id, "A" + id, r.apptTypeCode(), 1, r.effectiveDate(), null, null, r.reason(),
                r.leaveStartDate(), r.leaveEndDate(), false, null, LocalDateTime.now(), null, null, null);
    }

    private FamilyLeaveRequest entity(Kind kind, LocalDate start, LocalDate end, boolean extension) {
        boolean child = kind != Kind.FAMILY_CARE_LEAVE;
        FamilyLeaveRequest r = new FamilyLeaveRequest(1L, kind, child ? CHILD : null, child ? CHILD_BIRTH : null,
                child ? null : "부모", start, end, kind == Kind.CHILDCARE_REDUCED_HOURS ? REDUCED_PATTERN : null,
                extension, "fixture", null, "hr1", LocalDateTime.of(2015, 1, 1, 9, 0));
        r.withId(ids.getAndIncrement());
        store.add(r);
        return r;
    }

    /** 기존 사용기간을 승인된 신청으로 넣는다. 단축 행은 기존육아휴직사용기간에 있는 구간만 육아휴직이다. */
    private void history(Case c) {
        for (LocalDate[] p : c.used()) {
            Kind kind = c.kind();
            if (kind == Kind.CHILDCARE_REDUCED_HOURS) {
                kind = c.usedParental().stream().anyMatch(q -> q[0].equals(p[0]) && q[1].equals(p[1]))
                        ? Kind.PARENTAL_LEAVE : Kind.CHILDCARE_REDUCED_HOURS;
            }
            FamilyLeaveRequest r = entity(kind, p[0], p[1], false);
            if (kind.isLeaveOfAbsence()) {
                r.approveLeave(refIds.getAndIncrement(), refIds.getAndIncrement(), "hr1", LocalDateTime.of(2015, 1, 1, 9, 0));
            } else {
                r.approveReduced(refIds.getAndIncrement(), refIds.getAndIncrement(), "hr1", LocalDateTime.of(2015, 1, 1, 9, 0));
            }
        }
    }

    private static int reducedMinutes(Case c) {
        Matcher m = HOURS.matcher(c.name());
        int hours = DEFAULT_REDUCED_HOURS;
        while (m.find()) {
            hours = Integer.parseInt(m.group(1));
        }
        return hours * 60;
    }

    private static int wholeMonths(MonthUsage u) {
        assertThat(u.remainingMonths()).as("기준값이 있어 남은 기간이 계산돼야 한다").isNotNull();
        return Math.max(0, u.remainingMonths() - (u.remainingLessDays() > 0 ? 1 : 0));
    }

    // ---------------------------------------------------------------- 정답 대조

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void 정답_fixture와_같다(Case c) throws IOException {
        FamilyLeaveService service = service(reducedMinutes(c));
        history(c);
        CreateRequest req = new CreateRequest(c.kind(), c.kind() == Kind.FAMILY_CARE_LEAVE ? null : CHILD,
                c.kind() == Kind.FAMILY_CARE_LEAVE ? null : CHILD_BIRTH, c.kind() == Kind.FAMILY_CARE_LEAVE ? "부모" : null,
                c.start(), c.end(), c.kind() == Kind.CHILDCARE_REDUCED_HOURS ? REDUCED_PATTERN : null,
                c.kind() == Kind.PARENTAL_LEAVE && c.extension() ? Boolean.TRUE : null, "fixture");

        Throwable t = catchThrowable(() -> service.approve(service.create(1L, req, "hr1").reqId(), "hr1"));
        if (c.allow()) {
            assertThat(t).as("허용돼야 한다").isNull();
        } else {
            assertThat(t).as("거부돼야 한다").isInstanceOf(FamilyLeaveException.class);
            // 반려 시 값은 신청 전 값이다. 그 자녀의 남은 기간을 보이도록 신청 대기로만 남긴다(사용 기간에는 세지 않는다).
            entity(c.kind(), c.start(), c.end(), c.extension());
        }

        BalanceView b = service.balance(1L, c.asOf());
        if (c.kind() == Kind.FAMILY_CARE_LEAVE) {
            assertThat(b.familyCareLeave().year()).isEqualTo(c.asOf().getYear());
            assertThat(b.familyCareLeave().remainingDays() + "일").isEqualTo(c.remaining());
            return;
        }
        ChildBalance child = b.children().stream().filter(x -> x.childLabel().equals(CHILD)).findFirst().orElseThrow();
        MonthUsage u = c.kind() == Kind.PARENTAL_LEAVE ? child.parentalLeave() : child.reducedHours();
        assertThat(wholeMonths(u) + "개월").isEqualTo(c.remaining());
        if (c.remainingSplits() != null) {
            assertThat(u.usesMax()).isNotNull();
            int splitCount = u.usesMax() - 1;
            assertThat(splitCount - Math.max(0, u.uses() - 1)).isEqualTo(c.remainingSplits());
        }
    }

    @Test
    void fixture의_모든_필수_경계_사례가_있다() throws IOException {
        Set<String> names = Set.copyOf(cases().map(a -> a.get()[0].toString()).toList());
        assertThat(names).contains("추가6개월_시행전날_요건충족이어도불가", "추가6개월_시행일_요건충족",
                "분할1회한도도달_2020-12-07", "분할2회한도_2020-12-08_세번째구간", "분할2회한도초과_2025-02-22",
                "분할3회한도_2025-02-23_네번째구간", "단축_미사용육아휴직6개월_2배가산", "단축_시행전_미사용6개월_1배가산",
                "가족돌봄_연도넘김_연도별90일", "윤년2월29일시작_12개월", "월말1개월_1월31일시작");
    }
}
