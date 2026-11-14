package com.glassehr.attendance;

import static com.glassehr.attendance.LaborAgreementRulesTest.agreement;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.attendance.LaborAgreementDtos.AgreementRequest;
import com.glassehr.attendance.LaborAgreementDtos.AgreementView;
import com.glassehr.attendance.LaborAgreementDtos.Status;
import com.glassehr.employee.BizSiteRepository;
import com.glassehr.employee.EmployeeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LaborAgreementServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);
    /** 테스트용 한도(운영 설정 값, 법령 수치 아님). */
    private static final long MAX = 16;
    private static final byte[] PDF = "%PDF-1.7 x".getBytes();

    private LaborAgreementRepository agreements;
    private LaborAgreementFileRepository files;
    private EmployeeRepository employees;
    private BizSiteRepository sites;
    private HolidayRepository holidays;
    private LaborAgreementService service;

    @BeforeEach
    void setUp() {
        agreements = mock(LaborAgreementRepository.class);
        files = mock(LaborAgreementFileRepository.class);
        employees = mock(EmployeeRepository.class);
        sites = mock(BizSiteRepository.class);
        holidays = mock(HolidayRepository.class);
        // 서울 2026-11-14 00:30 = UTC 2026-11-13 15:30 (서울 기준 날짜를 쓰는지 확인)
        Clock clock = Clock.fixed(Instant.parse("2026-11-13T15:30:00Z"), ZoneId.of("UTC"));
        service = new LaborAgreementService(agreements, files, employees, sites, holidays, 30, MAX, clock);
        when(agreements.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), "agreementId", 10L));
        when(files.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), "fileId", 20L));
    }

    /** DB IDENTITY 대신 저장 시 ID 를 채운다. */
    private static <T> T withId(T entity, String field, long id) {
        try {
            java.lang.reflect.Field f = entity.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static AgreementRequest req(AgreementType type, LaborAgreement.Scope scope, String site, Long repEmp) {
        return new AgreementRequest(type, "합의서", TODAY, TODAY, TODAY.plusYears(1), scope, site, "생산직 전원",
                repEmp == null ? LaborAgreement.RepType.MAJORITY_UNION : LaborAgreement.RepType.MAJORITY_REP,
                repEmp == null ? "가상노동조합" : null, repEmp, null, "문서함 A-1");
    }

    @Test
    void 오늘은_서울_기준() {
        assertThat(service.today()).isEqualTo(TODAY);
    }

    @Test
    void 등록하면_요청자와_상태를_남긴다() {
        AgreementView v = service.create(req(AgreementType.SELECTIVE, LaborAgreement.Scope.ALL, null, null), "hr1");
        assertThat(v.status()).isEqualTo(Status.ACTIVE);
        assertThat(v.updatedBy()).isEqualTo("hr1");
        assertThat(v.article()).isEqualTo("근로기준법 제52조");
        assertThat(v.daysUntilExpiry()).isEqualTo(365);
    }

    @Test
    void 없는_사업장이나_사원은_거부() {
        when(sites.existsById("S09")).thenReturn(false);
        assertThatThrownBy(() -> service.create(req(AgreementType.SELECTIVE, LaborAgreement.Scope.SITE, "S09", null), "hr1"))
                .hasMessageContaining("없는 사업장");
        when(employees.existsById(99L)).thenReturn(false);
        assertThatThrownBy(() -> service.create(req(AgreementType.SELECTIVE, LaborAgreement.Scope.ALL, null, 99L), "hr1"))
                .hasMessageContaining("없는 사원");
        verify(agreements, never()).save(any());
    }

    @Test
    void 유형은_수정할_수_없다() {
        LaborAgreement a = agreement(3, AgreementType.SELECTIVE, TODAY, null, LaborAgreement.Scope.ALL, null);
        when(agreements.findForUpdate(3L)).thenReturn(Optional.of(a));
        assertThatThrownBy(() -> service.update(3, req(AgreementType.DISCRETIONARY, LaborAgreement.Scope.ALL, null, null), "hr1"))
                .isInstanceOfSatisfying(AttendanceException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        AgreementView v = service.update(3, req(AgreementType.SELECTIVE, LaborAgreement.Scope.ALL, null, null), "hr2");
        assertThat(v.validUntil()).isEqualTo(TODAY.plusYears(1));
        assertThat(v.updatedBy()).isEqualTo("hr2");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"start", "end", "signed", "scope"})
    void 기존_휴일대체를_무효화하는_합의서_수정은_거부(String change) {
        LaborAgreement a = agreement(3, AgreementType.HOLIDAY_SUBSTITUTION,
                TODAY, TODAY.plusYears(1), LaborAgreement.Scope.ALL, null);
        when(agreements.findForUpdate(3L)).thenReturn(Optional.of(a));
        LocalDate originalDate = TODAY.plusDays(10);
        LocalDate replacementDate = TODAY.plusDays(20);
        Holiday original = Holiday.statutory(originalDate, "시험 공휴일", HolidayKind.PUBLIC,
                "시험 자료", TODAY, "hr1", TODAY.atStartOfDay());
        Holiday replacement = Holiday.replacement(replacementDate, "시험 대체", 100, 3,
                null, "hr1", TODAY.atStartOfDay());
        when(holidays.findByAgreementId(3)).thenReturn(List.of(replacement));
        when(holidays.findById(100L)).thenReturn(Optional.of(original));
        if (change.equals("scope")) when(sites.existsById("S01")).thenReturn(true);
        AgreementRequest r = new AgreementRequest(AgreementType.HOLIDAY_SUBSTITUTION, "변경 합의서",
                change.equals("signed") ? originalDate.plusDays(1) : TODAY,
                change.equals("start") ? originalDate.plusDays(1) : TODAY,
                change.equals("end") ? replacementDate.minusDays(1) : TODAY.plusYears(1),
                change.equals("scope") ? LaborAgreement.Scope.SITE : LaborAgreement.Scope.ALL,
                change.equals("scope") ? "S01" : null, "전체 근로자", LaborAgreement.RepType.MAJORITY_UNION,
                "가상노동조합", null, null, "문서함 A-1");
        assertThatThrownBy(() -> service.update(3, r, "hr2"))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(a.getTitle()).isNotEqualTo("변경 합의서");
        assertThat(a.getScopeType()).isEqualTo(LaborAgreement.Scope.ALL);
        assertThat(a.getValidFrom()).isEqualTo(TODAY);
        assertThat(a.getValidUntil()).isEqualTo(TODAY.plusYears(1));
    }

    @Test
    void 기존_휴일대체의_양끝날을_포함하는_수정은_허용() {
        LaborAgreement a = agreement(3, AgreementType.HOLIDAY_SUBSTITUTION,
                TODAY, TODAY.plusYears(1), LaborAgreement.Scope.ALL, null);
        when(agreements.findForUpdate(3L)).thenReturn(Optional.of(a));
        Holiday original = Holiday.statutory(TODAY, "시험 공휴일", HolidayKind.PUBLIC,
                "시험 자료", TODAY, "hr1", TODAY.atStartOfDay());
        Holiday replacement = Holiday.replacement(TODAY.plusYears(1), "시험 대체", 100, 3,
                null, "hr1", TODAY.atStartOfDay());
        when(holidays.findByAgreementId(3)).thenReturn(List.of(replacement));
        when(holidays.findById(100L)).thenReturn(Optional.of(original));
        AgreementView v = service.update(3,
                req(AgreementType.HOLIDAY_SUBSTITUTION, LaborAgreement.Scope.ALL, null, null), "hr2");
        assertThat(v.validFrom()).isEqualTo(TODAY);
        assertThat(v.validUntil()).isEqualTo(TODAY.plusYears(1));
        assertThat(v.updatedBy()).isEqualTo("hr2");
    }

    @Test
    void 상태는_기준일로_정한다() {
        LaborAgreement upcoming = agreement(1, AgreementType.SELECTIVE, TODAY.plusDays(1), null, LaborAgreement.Scope.ALL, null);
        LaborAgreement expired = agreement(2, AgreementType.SELECTIVE, TODAY.minusYears(1), TODAY.minusDays(1), LaborAgreement.Scope.ALL, null);
        LaborAgreement lastDay = agreement(3, AgreementType.SELECTIVE, TODAY.minusYears(1), TODAY, LaborAgreement.Scope.ALL, null);
        when(agreements.findAllByOrderByEffectiveFromDescAgreementIdDesc()).thenReturn(List.of(upcoming, expired, lastDay));
        assertThat(service.list()).extracting(AgreementView::status)
                .containsExactly(Status.UPCOMING, Status.EXPIRED, Status.ACTIVE);
    }

    @Test
    void 만료_예정은_오늘부터_설정_일수까지_조회한다() {
        when(agreements.findExpiring(TODAY, TODAY.plusDays(30))).thenReturn(List.of(
                agreement(1, AgreementType.SELECTIVE, TODAY.minusYears(1), TODAY.plusDays(30), LaborAgreement.Scope.ALL, null)));
        LaborAgreementDtos.ExpiringView v = service.expiring(null);
        assertThat(v.days()).isEqualTo(30);
        assertThat(v.agreements()).singleElement().satisfies(a -> assertThat(a.daysUntilExpiry()).isEqualTo(30));
        when(agreements.findExpiring(TODAY, TODAY)).thenReturn(List.of());
        assertThat(service.expiring(0).agreements()).isEmpty();
        assertThatThrownBy(() -> service.expiring(-1)).isInstanceOf(AttendanceException.class);
    }

    @Test
    void 만료_예정_조회는_배타_종료일로_하루_밀어_찾는다() {
        // DB 는 마지막 유효일 다음 날(effective_to)을 저장한다. 마지막 유효일 [오늘, 오늘+30] = effective_to [오늘+1, 오늘+31].
        LaborAgreementRepository repo = mock(LaborAgreementRepository.class,
                org.mockito.Mockito.withSettings().defaultAnswer(org.mockito.Answers.CALLS_REAL_METHODS));
        org.mockito.Mockito.doReturn(List.of()).when(repo).findExpiringRaw(any(), any());
        repo.findExpiring(TODAY, TODAY.plusDays(30));
        verify(repo).findExpiringRaw(TODAY.plusDays(1), TODAY.plusDays(31));
    }

    @Test
    void 저장은_배타_종료일이고_밖으로는_마지막_유효일이다() {
        LaborAgreement a = agreement(1, AgreementType.SELECTIVE, TODAY, TODAY.plusDays(9), LaborAgreement.Scope.ALL, null);
        assertThat(a.getValidUntil()).isEqualTo(TODAY.plusDays(9));
        assertThat(a.isEffectiveOn(TODAY.plusDays(9))).isTrue();
        assertThat(a.isEffectiveOn(TODAY.plusDays(10))).isFalse();
        assertThat(a.isEffectiveOn(TODAY.minusDays(1))).isFalse();
        try {
            java.lang.reflect.Field f = LaborAgreement.class.getDeclaredField("effectiveTo");
            f.setAccessible(true);
            assertThat(f.get(a)).isEqualTo(TODAY.plusDays(10));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void 문서_참조가_없으면_거부() {
        AgreementRequest r = req(AgreementType.SELECTIVE, LaborAgreement.Scope.ALL, null, null);
        AgreementRequest noDoc = new AgreementRequest(r.agreementType(), r.title(), r.signedDate(), r.validFrom(),
                r.validUntil(), r.scopeType(), r.scopeSiteCode(), r.targetDesc(), r.repType(), r.repUnionName(),
                r.repEmpId(), r.note(), "  ");
        assertThatThrownBy(() -> service.create(noDoc, "hr1")).hasMessageContaining("합의서 문서");
        verify(agreements, never()).save(any());
    }

    @Test
    void 첨부는_PDF_PNG_JPG만_내용으로_판정한다() {
        when(agreements.findById(anyLong())).thenReturn(Optional.of(
                agreement(1, AgreementType.SELECTIVE, TODAY, null, LaborAgreement.Scope.ALL, null)));
        AgreementFileView v = service.addFile(1, "C:\\docs\\합의서.pdf", PDF, "hr1");
        assertThat(v.fileName()).isEqualTo("합의서.pdf");
        assertThat(v.contentType()).isEqualTo("application/pdf");
        assertThat(v.sha256()).hasSize(64).matches("[0-9a-f]+");
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1};
        assertThat(service.addFile(1, null, png, "hr1").fileName()).isEqualTo("agreement.png");
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0};
        assertThat(service.addFile(1, "a.jpg", jpg, "hr1").contentType()).isEqualTo("image/jpeg");

        assertThatThrownBy(() -> service.addFile(1, "a.pdf", "<html>".getBytes(), "hr1"))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
        assertThatThrownBy(() -> service.addFile(1, "a.pdf", new byte[0], "hr1"))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void 첨부_크기_한도_경계() {
        when(agreements.findById(anyLong())).thenReturn(Optional.of(
                agreement(1, AgreementType.SELECTIVE, TODAY, null, LaborAgreement.Scope.ALL, null)));
        byte[] exact = new byte[(int) MAX];
        System.arraycopy(PDF, 0, exact, 0, 5);
        assertThat(service.addFile(1, "a.pdf", exact, "hr1").fileSize()).isEqualTo(MAX);
        byte[] over = new byte[(int) MAX + 1];
        System.arraycopy(PDF, 0, over, 0, 5);
        assertThatThrownBy(() -> service.addFile(1, "a.pdf", over, "hr1"))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @Test
    void 없는_합의서에_첨부하면_404() {
        when(agreements.findById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addFile(7, "a.pdf", PDF, "hr1"))
                .isInstanceOfSatisfying(AttendanceException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(files, never()).save(any());
    }

    @Test
    void 다른_합의서의_첨부는_찾지_않는다() {
        when(files.findByFileIdAndAgreementId(5L, 2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.file(2, 5))
                .isInstanceOfSatisfying(AttendanceException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
