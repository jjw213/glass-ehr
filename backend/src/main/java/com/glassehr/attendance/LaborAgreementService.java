package com.glassehr.attendance;

import com.glassehr.attendance.LaborAgreementDtos.AgreementDetail;
import com.glassehr.attendance.LaborAgreementDtos.AgreementRequest;
import com.glassehr.attendance.LaborAgreementDtos.AgreementView;
import com.glassehr.attendance.LaborAgreementDtos.ExpiringView;
import com.glassehr.attendance.LaborAgreementDtos.Fields;
import com.glassehr.employee.BizSiteRepository;
import com.glassehr.employee.Employee;
import com.glassehr.employee.EmployeeRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 근로자대표 서면합의서 관리(#27, docs/labor-agreement-design.md): 등록·수정·조회, 첨부파일, 만료 예정 알림.
 * 제도 설정 API가 쓰는 유효 합의서 확인은 {@link LaborAgreementGuard}.
 */
@Service
public class LaborAgreementService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** 받는 첨부 형식. 올린 쪽이 보낸 Content-Type 은 믿지 않고 파일 앞부분(매직 바이트)으로 판정한다. */
    enum FileKind {
        PDF("application/pdf", new byte[] {'%', 'P', 'D', 'F', '-'}),
        PNG("image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}),
        JPEG("image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        final String contentType;
        private final byte[] magic;

        FileKind(String contentType, byte[] magic) {
            this.contentType = contentType;
            this.magic = magic;
        }

        static Optional<FileKind> detect(byte[] b) {
            for (FileKind k : values()) {
                if (b.length >= k.magic.length && java.util.Arrays.equals(b, 0, k.magic.length, k.magic, 0, k.magic.length)) {
                    return Optional.of(k);
                }
            }
            return Optional.empty();
        }
    }

    private final LaborAgreementRepository agreements;
    private final LaborAgreementFileRepository files;
    private final HolidayRepository holidays;
    private final EmployeeRepository employees;
    private final BizSiteRepository sites;
    private final int expiryAlertDays;
    private final long maxFileBytes;
    private final Clock clock;

    @Autowired
    public LaborAgreementService(LaborAgreementRepository agreements, LaborAgreementFileRepository files,
            EmployeeRepository employees, BizSiteRepository sites, HolidayRepository holidays,
            @Value("${glassehr.agreement.expiry-alert-days}") int expiryAlertDays,
            @Value("${glassehr.agreement.max-file-bytes}") long maxFileBytes) {
        this(agreements, files, employees, sites, holidays, expiryAlertDays, maxFileBytes, Clock.system(SEOUL));
    }

    LaborAgreementService(LaborAgreementRepository agreements, LaborAgreementFileRepository files,
            EmployeeRepository employees, BizSiteRepository sites, HolidayRepository holidays, int expiryAlertDays, long maxFileBytes,
            Clock clock) {
        this.agreements = agreements;
        this.files = files;
        this.employees = employees;
        this.sites = sites;
        this.holidays = holidays;
        this.expiryAlertDays = expiryAlertDays;
        this.maxFileBytes = maxFileBytes;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(SEOUL));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(SEOUL));
    }

    public int expiryAlertDays() {
        return expiryAlertDays;
    }

    @Transactional(readOnly = true)
    public List<AgreementView> list() {
        LocalDate today = today();
        return agreements.findAllByOrderByEffectiveFromDescAgreementIdDesc().stream().map(a -> view(a, today)).toList();
    }

    @Transactional(readOnly = true)
    public AgreementDetail get(long agreementId) {
        LaborAgreement a = find(agreementId);
        return new AgreementDetail(view(a, today()), files.findViews(agreementId));
    }

    @Transactional
    public AgreementView create(AgreementRequest req, String actor) {
        Fields f = checked(req);
        LaborAgreement saved = agreements.save(new LaborAgreement(req.agreementType(), f, actor, now()));
        return view(saved, today());
    }

    @Transactional
    public AgreementView update(long agreementId, AgreementRequest req, String actor) {
        LaborAgreement a = agreements.findForUpdate(agreementId)
                .orElseThrow(() -> AttendanceException.notFound("합의서가 없어요: " + agreementId));
        if (a.getAgreementType() != req.agreementType()) {
            throw AttendanceException.badRequest("합의 유형은 바꿀 수 없어요. 새 합의서로 등록해 주세요");
        }
        Fields f = checked(req);
        preserveHolidaySubstitutions(agreementId, f);
        a.apply(f, actor, now());
        return view(a, today());
    }

    /** 근로기준법 제55조 제2항 단서: 이미 등록된 휴일 대체의 서면합의 조건을 유지한다. */
    private void preserveHolidaySubstitutions(long agreementId, Fields f) {
        for (Holiday replacement : holidays.findByAgreementId(agreementId)) {
            Holiday original = holidays.findById(replacement.getReplacesHolidayId())
                    .orElseThrow(() -> AttendanceException.conflict("휴일 대체의 원래 공휴일을 확인할 수 없어요"));
            for (LocalDate date : List.of(original.getHolidayDate(), replacement.getHolidayDate())) {
                if (f.scopeType() != LaborAgreement.Scope.ALL || date.isBefore(f.validFrom())
                        || (f.validUntil() != null && date.isAfter(f.validUntil()))
                        || f.signedDate().isAfter(date)) {
                    throw AttendanceException.conflict("이미 등록된 휴일 대체의 유효기간·체결일·회사 전체 대상 범위를 유지해 주세요");
                }
            }
        }
    }

    /** 만료 예정 알림: 오늘부터 days 일 안(오늘·마지막 날 포함)에 유효기간이 끝나는 합의서. days 미지정 = 설정값. */
    @Transactional(readOnly = true)
    public ExpiringView expiring(Integer days) {
        int d = days == null ? expiryAlertDays : days;
        if (d < 0 || d > 3650) {
            throw AttendanceException.badRequest("알림 기간은 0~3650일로 지정해 주세요");
        }
        LocalDate today = today();
        List<AgreementView> rows = agreements.findExpiring(today, today.plusDays(d)).stream()
                .map(a -> view(a, today)).toList();
        return new ExpiringView(d, rows);
    }

    @Transactional
    public AgreementFileView addFile(long agreementId, String originalName, byte[] content, String actor) {
        find(agreementId);
        if (content == null || content.length == 0) {
            throw AttendanceException.badRequest("빈 파일은 올릴 수 없어요");
        }
        if (content.length > maxFileBytes) {
            throw new AttendanceException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "첨부파일은 " + (maxFileBytes / (1024 * 1024)) + "MB 이하만 올릴 수 있어요", List.of());
        }
        FileKind kind = FileKind.detect(content).orElseThrow(() -> new AttendanceException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "합의서 첨부는 PDF·PNG·JPG 파일만 올릴 수 있어요", List.of()));
        LaborAgreementFile saved = files.save(new LaborAgreementFile(agreementId, cleanName(originalName, kind),
                kind.contentType, content, sha256(content), actor, now()));
        return new AgreementFileView(saved.getFileId(), agreementId, saved.getFileName(), saved.getContentType(),
                saved.getFileSize(), saved.getSha256(), saved.getUploadedBy(), saved.getUploadedAt());
    }

    @Transactional(readOnly = true)
    public LaborAgreementFile file(long agreementId, long fileId) {
        return files.findByFileIdAndAgreementId(fileId, agreementId)
                .orElseThrow(() -> AttendanceException.notFound("첨부파일이 없어요: " + fileId));
    }

    @Transactional
    public void deleteFile(long agreementId, long fileId) {
        files.delete(file(agreementId, fileId));
    }

    private Fields checked(AgreementRequest req) {
        Fields f = req.fields();
        List<String> errors = LaborAgreementRules.validate(f);
        if (!errors.isEmpty()) {
            throw AttendanceException.badRequest(errors.get(0));
        }
        if (f.scopeSiteCode() != null && !sites.existsById(f.scopeSiteCode())) {
            throw AttendanceException.badRequest("없는 사업장이에요: " + f.scopeSiteCode());
        }
        if (f.repEmpId() != null && !employees.existsById(f.repEmpId())) {
            throw AttendanceException.badRequest("없는 사원이에요: " + f.repEmpId());
        }
        return f;
    }

    private LaborAgreement find(long agreementId) {
        return agreements.findById(agreementId)
                .orElseThrow(() -> AttendanceException.notFound("합의서가 없어요: " + agreementId));
    }

    private AgreementView view(LaborAgreement a, LocalDate today) {
        Optional<Employee> rep = a.getRepEmpId() == null ? Optional.empty() : employees.findById(a.getRepEmpId());
        return AgreementView.of(a, rep.map(Employee::getName).orElse(null), rep.map(Employee::getEmpNo).orElse(null),
                a.getAgreementId() == null ? 0 : files.countByAgreementId(a.getAgreementId()), today);
    }

    /** 경로 구분자·제어문자를 지운 파일 이름(최대 255자). 비면 형식에 맞는 기본 이름. */
    static String cleanName(String name, FileKind kind) {
        String base = name == null ? "" : name.replaceAll(".*[/\\\\]", "").replaceAll("\\p{Cntrl}", "").trim();
        if (base.isEmpty()) {
            base = "agreement." + (kind == FileKind.JPEG ? "jpg" : kind.name().toLowerCase(java.util.Locale.ROOT));
        }
        return base.length() > 255 ? base.substring(base.length() - 255) : base;
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
