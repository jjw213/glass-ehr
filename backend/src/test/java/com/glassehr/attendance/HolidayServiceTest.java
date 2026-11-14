package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.glassehr.attendance.HolidayDtos.CompanyHolidayRequest;
import com.glassehr.attendance.HolidayDtos.HolidayView;
import com.glassehr.attendance.HolidayDtos.ImportResult;
import com.glassehr.attendance.HolidayDtos.SubstitutionRequest;
import com.glassehr.legalrate.LegalRateException;
import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import com.glassehr.legalrate.LegalRateUnit;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

/**
 * 휴일 달력 서비스. 저장소는 메모리 목으로 흉내 낸다.
 * 날짜·이름은 <b>가짜 값(2099년, '시험 공휴일')이며 실제 공휴일이 아니다</b>. 실제 공휴일 자료는 사람이 넣는다.
 */
class HolidayServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2099, 1, 10);
    private static final LocalDateTime NOW = TODAY.atTime(9, 0);
    private static final String CSV = "날짜,이름,종류,출처,검증일\n"
            + "2099-03-01,시험 공휴일,법정공휴일,시험 규정 제1조,2099-01-01\n"
            + "2099-03-02,시험 공휴일 대체공휴일,대체공휴일,시험 규정 제2조,2099-01-01\n";

    private static final LocalDate PAID_FROM = LocalDate.of(2098, 1, 1);
    private static final LocalDate PAID_FROM_SMALL = LocalDate.of(2099, 1, 1);

    private final List<Holiday> rows = new ArrayList<>();
    private LocalDate reservedDateTaken;
    private final List<LaborAgreement> agreementRows = new ArrayList<>();
    private HolidayService service;

    @BeforeEach
    void setUp() {
        HolidayRepository holidays = mock(HolidayRepository.class);
        LaborAgreementRepository agreements = mock(LaborAgreementRepository.class);
        org.mockito.stubbing.Answer<Holiday> store = inv -> {
            Holiday h = inv.getArgument(0);
            if (reservedDateTaken != null && reservedDateTaken.equals(h.getHolidayDate())) {
                // DB 유일 인덱스 ux_holiday_reserved_date 위반(동시 요청이 먼저 커밋한 상황)을 흉내 낸다.
                throw new DataIntegrityViolationException("ux_holiday_reserved_date");
            }
            setField(h, "holidayId", (long) rows.size() + 1);
            rows.add(h);
            return h;
        };
        when(holidays.save(any())).thenAnswer(store);
        when(holidays.saveAndFlush(any())).thenAnswer(store);
        when(holidays.findById(anyLong())).thenAnswer(inv -> byId(inv.getArgument(0)));
        when(holidays.findForUpdate(anyLong())).thenAnswer(inv -> byId(inv.getArgument(0)));
        when(holidays.findAllById(any())).thenAnswer(inv -> {
            Iterable<Long> ids = inv.getArgument(0);
            List<Holiday> out = new ArrayList<>();
            ids.forEach(id -> byId(id).ifPresent(out::add));
            return out;
        });
        when(holidays.findByHolidayDate(any())).thenAnswer(inv ->
                rows.stream().filter(h -> h.getHolidayDate().equals(inv.getArgument(0))).toList());
        when(holidays.findByReplacesHolidayId(anyLong())).thenAnswer(inv ->
                rows.stream().filter(h -> Long.valueOf((long) inv.getArgument(0)).equals(h.getReplacesHolidayId()))
                        .findFirst());
        when(holidays.findByReplacesHolidayIdIn(any())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return rows.stream().filter(h -> h.getReplacesHolidayId() != null && ids.contains(h.getReplacesHolidayId()))
                    .toList();
        });
        when(holidays.findInRange(any(), any())).thenAnswer(inv -> {
            LocalDate from = inv.getArgument(0);
            LocalDate to = inv.getArgument(1);
            return rows.stream().filter(h -> !h.getHolidayDate().isBefore(from) && h.getHolidayDate().isBefore(to))
                    .sorted((a, b) -> a.getHolidayDate().compareTo(b.getHolidayDate())).toList();
        });
        org.mockito.Mockito.doAnswer(inv -> rows.remove((Holiday) inv.getArgument(0))).when(holidays).delete(any());
        when(agreements.findById(anyLong())).thenAnswer(inv ->
                agreementRows.stream().filter(a -> a.getAgreementId().equals(inv.getArgument(0))).findFirst());
        when(agreements.findAllById(any())).thenAnswer(inv -> {
            Iterable<Long> ids = inv.getArgument(0);
            List<LaborAgreement> out = new ArrayList<>();
            ids.forEach(id -> agreementRows.stream().filter(a -> a.getAgreementId().equals(id)).forEach(out::add));
            return out;
        });
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        // 가짜 기준값(실제 법정 값 아님): 2098년에는 적용 규모 하한 50명, 2099년부터 20명. 2098년 전은 기준값 없음.
        // 이 사업장 상시근로자 수는 30명으로 둔다.
        LegalRateService rates = mock(LegalRateService.class);
        when(rates.getEffective(org.mockito.ArgumentMatchers.eq(LegalRateItem.PUBLIC_HOLIDAY_PAID_MIN_HEADCOUNT), any()))
                .thenAnswer(inv -> {
                    LocalDate d = inv.getArgument(1);
                    if (d.isBefore(PAID_FROM)) {
                        throw mock(LegalRateException.class);
                    }
                    BigDecimal min = d.isBefore(PAID_FROM_SMALL) ? BigDecimal.valueOf(50) : BigDecimal.valueOf(20);
                    return new LegalRateRow(LegalRateItem.PUBLIC_HOLIDAY_PAID_MIN_HEADCOUNT.code(), PAID_FROM, null, min,
                            LegalRateUnit.COUNT, "시험 부칙", null);
                });
        service = new HolidayService(holidays, agreements, new PublicHolidayPaidRule(rates, 30), clock);
    }

    private Optional<Holiday> byId(Object id) {
        return rows.stream().filter(h -> h.getHolidayId().equals(id)).findFirst();
    }

    private static void setField(Object entity, String field, Object value) {
        try {
            Field f = entity.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(entity, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 휴일 대체 합의를 저장소에 넣는다. to 는 #26 때와 같이 배타 종료일(마지막 유효일 다음 날, NULL = 기한 없음).
     * 등록 API·검사는 #27 LaborAgreementService 로 옮겨 같은 형식 검사({@link LaborAgreementRules#validate})를 거친다.
     */
    private long agreement(LocalDate signed, LocalDate from, LocalDate to) {
        return agreement(AgreementType.HOLIDAY_SUBSTITUTION, LaborAgreement.Scope.ALL, signed, from, to);
    }

    private long agreement(AgreementType type, LaborAgreement.Scope scope, LocalDate signed, LocalDate from,
            LocalDate to) {
        LaborAgreementDtos.Fields f = new LaborAgreementDtos.Fields("시험 휴일대체 합의", signed, from,
                to == null ? null : to.minusDays(1), scope, scope == LaborAgreement.Scope.SITE ? "S01" : null,
                "전 직원", LaborAgreement.RepType.MAJORITY_UNION, "시험 노동조합", null, null, "문서함 TEST-001");
        List<String> errors = LaborAgreementRules.validate(f);
        if (!errors.isEmpty()) {
            throw AttendanceException.badRequest(errors.get(0));
        }
        LaborAgreement a = new LaborAgreement(type, f, "hr1", NOW);
        setField(a, "agreementId", (long) agreementRows.size() + 1);
        agreementRows.add(a);
        return a.getAgreementId();
    }

    private long publicHolidayId() {
        service.importPublicHolidays(CSV, "hr1");
        return rows.get(0).getHolidayId();
    }

    private static HttpStatus status(Throwable t) {
        return ((AttendanceException) t).status();
    }

    // ---- 공휴일 자료 ----

    @Test
    void 공휴일_CSV를_불러오면_유급_공휴일로_저장하고_다시_불러오면_건너뛴다() {
        ImportResult first = service.importPublicHolidays(CSV, "hr1");
        assertThat(first.inserted()).isEqualTo(2);
        assertThat(first.rows()).extracting(HolidayView::kind).containsExactly("PUBLIC", "SUBSTITUTE");
        assertThat(first.rows()).allMatch(HolidayView::paid).allMatch(HolidayView::effective);
        assertThat(rows.get(0).getSourceRef()).isEqualTo("시험 규정 제1조");
        assertThat(rows.get(0).getCreatedBy()).isEqualTo("hr1");

        ImportResult again = service.importPublicHolidays(CSV, "hr1");
        assertThat(again.inserted()).isZero();
        assertThat(again.skipped()).isEqualTo(2);
        assertThat(rows).hasSize(2);
    }

    @Test
    void 형식_오류는_400이고_아무것도_저장하지_않는다() {
        assertThatThrownBy(() -> service.importPublicHolidays(CSV + "2099-13-01,잘못,법정공휴일,출처,2099-01-01\n", "hr1"))
                .isInstanceOf(AttendanceException.class)
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("4번째 줄");
        assertThat(rows).isEmpty();
    }

    @Test
    void 같은_날짜_이름인데_종류가_다르면_409() {
        service.importPublicHolidays(CSV, "hr1");
        String other = "날짜,이름,종류,출처,검증일\n2099-03-01,시험 공휴일,대체공휴일,출처,2099-01-01\n";
        assertThatThrownBy(() -> service.importPublicHolidays(other, "hr1"))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void 사업장_규모에_제55조_제2항이_시행되기_전_날짜가_있으면_422이고_아무것도_저장하지_않는다() {
        // 2098년: 가짜 적용 규모 하한 50명 > 이 사업장 30명
        String csv = CSV + "2098-12-31,시험 시행 전 공휴일,법정공휴일,시험 규정 제1조,2099-01-01\n";
        assertThatThrownBy(() -> service.importPublicHolidays(csv, "hr1"))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY))
                .hasMessageContaining("4번째 줄").hasMessageContaining("제55조 제2항");
        assertThat(rows).isEmpty();
    }

    @Test
    void 적용_규모_기준값이_없는_날짜가_있으면_유급으로_저장하지_않는다() {
        String csv = CSV + "2097-12-31,시험 기준값 없는 공휴일,법정공휴일,시험 규정 제1조,2099-01-01\n";
        assertThatThrownBy(() -> service.importPublicHolidays(csv, "hr1")).isInstanceOf(LegalRateException.class);
        assertThat(rows).isEmpty();
    }

    @Test
    void 적용_규모_하한이_바뀌는_날부터_유급_공휴일로_넣을_수_있다() {
        String csv = "날짜,이름,종류,출처,검증일\n2099-01-01,시험 신정,법정공휴일,시험 규정 제1조,2099-01-01\n";
        assertThat(service.importPublicHolidays(csv, "hr1").inserted()).isEqualTo(1);
        assertThat(rows.get(0).isPaid()).isTrue();
    }

    @Test
    void 같은_날을_동시에_차지하면_나중_요청은_409() {
        LocalDate target = LocalDate.of(2099, 4, 7);
        reservedDateTaken = target;
        long agreementId = agreement(LocalDate.of(2099, 1, 1), LocalDate.of(2099, 1, 1), null);
        long id = publicHolidayId();
        assertThatThrownBy(() -> service.substitute(new SubstitutionRequest(id, target, agreementId, null), "hr1"))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.createCompanyHoliday(
                new CompanyHolidayRequest(target, "창립기념일", null, null), "hr1"))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void 공휴일_행은_지울_수_없다() {
        long id = publicHolidayId();
        assertThatThrownBy(() -> service.delete(id))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ---- 회사휴일 ----

    @Test
    void 회사휴일은_유급_여부를_고를_수_있고_기본은_유급() {
        HolidayView paid = service.createCompanyHoliday(
                new CompanyHolidayRequest(LocalDate.of(2099, 5, 10), " 창립기념일 ", null, null), "hr1");
        assertThat(paid.kind()).isEqualTo("COMPANY");
        assertThat(paid.kindLabel()).isEqualTo("회사휴일");
        assertThat(paid.name()).isEqualTo("창립기념일");
        assertThat(paid.paid()).isTrue();
        HolidayView unpaid = service.createCompanyHoliday(
                new CompanyHolidayRequest(LocalDate.of(2099, 5, 11), "무급 휴무", false, "노사 합의"), "hr1");
        assertThat(unpaid.paid()).isFalse();
    }

    @Test
    void 이미_휴일인_날에_회사휴일을_넣으면_409() {
        publicHolidayId();
        assertThatThrownBy(() -> service.createCompanyHoliday(
                new CompanyHolidayRequest(LocalDate.of(2099, 3, 1), "겹침", true, null), "hr1"))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void 회사휴일은_내일부터만_지울_수_있다() {
        long past = service.createCompanyHoliday(new CompanyHolidayRequest(TODAY, "오늘", true, null), "hr1").holidayId();
        long future = service.createCompanyHoliday(
                new CompanyHolidayRequest(TODAY.plusDays(1), "내일", true, null), "hr1").holidayId();
        assertThatThrownBy(() -> service.delete(past))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.CONFLICT));
        service.delete(future);
        assertThat(rows).extracting(Holiday::getHolidayName).containsExactly("오늘");
    }

    // ---- 휴일 대체 ----

    @Test
    void 서면합의로_공휴일을_근로일로_대체하면_원래_날은_근로일_대체된_날은_유급휴일() {
        long original = publicHolidayId();
        long agreementId = agreement(LocalDate.of(2099, 1, 5), LocalDate.of(2099, 1, 1), null);
        HolidayView r = service.substitute(
                new SubstitutionRequest(original, LocalDate.of(2099, 3, 5), agreementId, "생산 일정"), "hr1");
        assertThat(r.kind()).isEqualTo("REPLACEMENT");
        assertThat(r.kindLabel()).isEqualTo("대체된 휴일");
        assertThat(r.name()).isEqualTo("시험 공휴일 대체");
        assertThat(r.paid()).isTrue();
        assertThat(r.replaces().date()).isEqualTo(LocalDate.of(2099, 3, 1));
        assertThat(r.agreementId()).isEqualTo(agreementId);
        assertThat(r.agreementTitle()).isEqualTo("시험 휴일대체 합의");

        List<HolidayView> year = service.year(2099);
        HolidayView originalView = year.stream().filter(v -> v.holidayId() == original).findFirst().orElseThrow();
        assertThat(originalView.effective()).isFalse();
        assertThat(originalView.replacedBy().date()).isEqualTo(LocalDate.of(2099, 3, 5));
        assertThat(service.isHoliday(LocalDate.of(2099, 3, 1))).isFalse();
        assertThat(service.isHoliday(LocalDate.of(2099, 3, 5))).isTrue();
        assertThat(service.isHoliday(LocalDate.of(2099, 3, 2))).isTrue();
        assertThat(service.isHoliday(LocalDate.of(2099, 3, 6))).isFalse();
    }

    @Test
    void 서면합의가_없으면_대체할_수_없다() {
        long original = publicHolidayId();
        assertThatThrownBy(() -> service.substitute(
                new SubstitutionRequest(original, LocalDate.of(2099, 3, 5), 99L, null), "hr1"))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("서면합의");
        assertThat(rows).hasSize(2);
    }

    @Test
    void 합의_유효기간_밖이거나_대체일보다_늦게_체결한_합의는_422() {
        long original = publicHolidayId();
        // 유효기간 [2099-01-01, 2099-03-03): 원래 날(3/1)은 안, 대신 쉴 날(3/3)은 종료일이라 밖
        long shortTerm = agreement(LocalDate.of(2099, 1, 1), LocalDate.of(2099, 1, 1), LocalDate.of(2099, 3, 3));
        assertThatThrownBy(() -> service.substitute(
                new SubstitutionRequest(original, LocalDate.of(2099, 3, 3), shortTerm, null), "hr1"))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY))
                .hasMessageContaining("유효기간");
        // 대신 쉴 날이 원래 날보다 앞이면(2/27), 합의는 그보다 먼저 체결돼야 한다. 2/28 체결은 거부.
        long late = agreement(LocalDate.of(2099, 2, 28), LocalDate.of(2099, 1, 1), null);
        assertThatThrownBy(() -> service.substitute(
                new SubstitutionRequest(original, LocalDate.of(2099, 2, 27), late, null), "hr1"))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY))
                .hasMessageContaining("먼저 체결");
        // 같은 날 체결은 허용(경계)
        assertThat(service.substitute(
                new SubstitutionRequest(original, LocalDate.of(2099, 2, 28), late, null), "hr1").kind())
                .isEqualTo("REPLACEMENT");
    }

    @Test
    void 회사휴일은_휴일_대체_대상이_아니다() {
        long company = service.createCompanyHoliday(
                new CompanyHolidayRequest(LocalDate.of(2099, 5, 10), "창립기념일", true, null), "hr1").holidayId();
        long agreementId = agreement(LocalDate.of(2099, 1, 1), LocalDate.of(2099, 1, 1), null);
        assertThatThrownBy(() -> service.substitute(
                new SubstitutionRequest(company, LocalDate.of(2099, 5, 12), agreementId, null), "hr1"))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("제55조 제2항");
    }

    @Test
    void 이미_휴일인_날이나_같은_날로는_대체할_수_없고_한_공휴일은_한_번만_대체한다() {
        long original = publicHolidayId();
        long agreementId = agreement(LocalDate.of(2099, 1, 1), LocalDate.of(2099, 1, 1), null);
        assertThatThrownBy(() -> service.substitute(
                new SubstitutionRequest(original, LocalDate.of(2099, 3, 2), agreementId, null), "hr1"))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("근로일로만");
        assertThatThrownBy(() -> service.substitute(
                new SubstitutionRequest(original, LocalDate.of(2099, 3, 1), agreementId, null), "hr1"))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.BAD_REQUEST));
        service.substitute(new SubstitutionRequest(original, LocalDate.of(2099, 3, 5), agreementId, null), "hr1");
        assertThatThrownBy(() -> service.substitute(
                new SubstitutionRequest(original, LocalDate.of(2099, 3, 6), agreementId, null), "hr1"))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("이미 다른 날로");
    }

    @Test
    void 휴일_대체는_두_날이_모두_내일_이후일_때만_취소한다() {
        long original = publicHolidayId();
        long agreementId = agreement(LocalDate.of(2099, 1, 1), LocalDate.of(2099, 1, 1), null);
        long early = service.substitute(
                new SubstitutionRequest(original, TODAY, agreementId, null), "hr1").holidayId();
        assertThatThrownBy(() -> service.delete(early))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.CONFLICT));
        rows.removeIf(h -> h.getHolidayId() == early);
        long later = service.substitute(
                new SubstitutionRequest(original, LocalDate.of(2099, 3, 5), agreementId, null), "hr1").holidayId();
        service.delete(later);
        assertThat(service.isHoliday(LocalDate.of(2099, 3, 1))).isTrue();
    }

    // ---- 서면합의 ----

    @Test
    void 합의_종료일이_시작일_이전이면_400() {
        assertThatThrownBy(() -> agreement(LocalDate.of(2099, 1, 1), LocalDate.of(2099, 2, 1), LocalDate.of(2099, 2, 1)))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void 다른_제도의_합의나_사업장_한정_합의로는_회사_휴일을_대체할_수_없다() {
        long original = publicHolidayId();
        long selective = agreement(AgreementType.SELECTIVE, LaborAgreement.Scope.ALL,
                LocalDate.of(2099, 1, 1), LocalDate.of(2099, 1, 1), null);
        assertThatThrownBy(() -> service.substitute(
                new SubstitutionRequest(original, LocalDate.of(2099, 3, 5), selective, null), "hr1"))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY))
                .hasMessageContaining("휴일 대체에 관한 서면합의가 아니에요");
        long siteOnly = agreement(AgreementType.HOLIDAY_SUBSTITUTION, LaborAgreement.Scope.SITE,
                LocalDate.of(2099, 1, 1), LocalDate.of(2099, 1, 1), null);
        assertThatThrownBy(() -> service.substitute(
                new SubstitutionRequest(original, LocalDate.of(2099, 3, 5), siteOnly, null), "hr1"))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY))
                .hasMessageContaining("회사 전체");
    }

    @Test
    void 연도_범위를_벗어나면_400() {
        assertThatThrownBy(() -> service.year(10000))
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
