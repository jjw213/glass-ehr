package com.glassehr.attendance;

import com.glassehr.attendance.HolidayDtos.CompanyHolidayRequest;
import com.glassehr.attendance.HolidayDtos.HolidayRef;
import com.glassehr.attendance.HolidayDtos.HolidayView;
import com.glassehr.attendance.HolidayDtos.ImportResult;
import com.glassehr.attendance.HolidayDtos.SubstitutionRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 휴일 달력(#26, docs/holiday-calendar-design.md).
 *
 * <ul>
 *   <li>법정공휴일·대체공휴일(관공서의 공휴일에 관한 규정 제2조·제3조)은 유급휴일이다
 *       (근로기준법 제55조 제2항 본문, 시행령 제30조 제2항). 이 행은 사람이 넣은 CSV({@link PublicHolidayCsv})로만 만든다.</li>
 *   <li>휴일 대체(근로기준법 제55조 제2항 단서): 근로자대표와 서면으로 합의한 경우 공휴일을 특정한 근로일로 대체할 수 있다.
 *       대체 등록에는 휴일 대체용 서면합의 문서가 반드시 있어야 하고, 대체된 날이 유급휴일, 원래 공휴일은 근로일이 된다.</li>
 *   <li>회사휴일(창립기념일 등)은 취업규칙·단체협약으로 정하므로 유급 여부를 회사가 고른다.</li>
 * </ul>
 * 날짜는 Asia/Seoul 기준. 사원별 주휴일·휴무일(근무유형, #25)은 이 달력에 넣지 않는다.
 */
@Service
public class HolidayService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String REPLACEMENT_SUFFIX = " 대체";

    private final HolidayRepository holidays;
    private final LaborAgreementRepository agreements;
    private final PublicHolidayPaidRule paidRule;
    private final Clock clock;

    @Autowired
    public HolidayService(HolidayRepository holidays, LaborAgreementRepository agreements,
            PublicHolidayPaidRule paidRule) {
        this(holidays, agreements, paidRule, Clock.system(SEOUL));
    }

    HolidayService(HolidayRepository holidays, LaborAgreementRepository agreements, PublicHolidayPaidRule paidRule,
            Clock clock) {
        this.holidays = holidays;
        this.agreements = agreements;
        this.paidRule = paidRule;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(SEOUL));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(SEOUL));
    }

    // ---- 조회 ----

    /** 그 해(1월 1일~12월 31일) 휴일 달력. 대체 관계와 서면합의 제목을 붙인다. */
    @Transactional(readOnly = true)
    public List<HolidayView> year(int year) {
        if (year < 1900 || year > 9999) {
            throw AttendanceException.badRequest("연도를 확인해 주세요: " + year);
        }
        return views(holidays.findInRange(LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1)));
    }

    /**
     * 그 날이 실제로 쉬는 날(공휴일·대체공휴일·회사휴일·대체된 휴일)이면 true. 휴일 대체로 근로일이 된 공휴일은 false.
     * 근태 집계(휴일근로 판정)가 쓴다. 사원별 주휴일은 근무유형(#25)에서 따로 본다.
     */
    @Transactional(readOnly = true)
    public boolean isHoliday(LocalDate date) {
        List<Holiday> rows = holidays.findByHolidayDate(date);
        if (rows.isEmpty()) {
            return false;
        }
        Set<Long> replaced = replacedIds(rows);
        return rows.stream().anyMatch(h -> !replaced.contains(h.getHolidayId()));
    }

    private Set<Long> replacedIds(List<Holiday> rows) {
        List<Long> ids = rows.stream().filter(h -> h.getKind().isStatutory()).map(Holiday::getHolidayId).toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        return holidays.findByReplacesHolidayIdIn(ids).stream()
                .map(Holiday::getReplacesHolidayId).collect(Collectors.toSet());
    }

    private List<HolidayView> views(List<Holiday> rows) {
        List<Long> statutoryIds = rows.stream().filter(h -> h.getKind().isStatutory())
                .map(Holiday::getHolidayId).toList();
        Map<Long, Holiday> replacedBy = statutoryIds.isEmpty() ? Map.of()
                : holidays.findByReplacesHolidayIdIn(statutoryIds).stream()
                        .collect(Collectors.toMap(Holiday::getReplacesHolidayId, Function.identity(), (a, b) -> a));
        Set<Long> originalIds = rows.stream().map(Holiday::getReplacesHolidayId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Holiday> originals = originalIds.isEmpty() ? Map.of()
                : holidays.findAllById(originalIds).stream()
                        .collect(Collectors.toMap(Holiday::getHolidayId, Function.identity()));
        Set<Long> agreementIds = rows.stream().map(Holiday::getAgreementId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, LaborAgreement> agreementById = agreementIds.isEmpty() ? Map.of()
                : agreements.findAllById(agreementIds).stream()
                        .collect(Collectors.toMap(LaborAgreement::getAgreementId, Function.identity()));
        return rows.stream().map(h -> {
            Holiday by = replacedBy.get(h.getHolidayId());
            LaborAgreement a = h.getAgreementId() == null ? null : agreementById.get(h.getAgreementId());
            return new HolidayView(h.getHolidayId(), h.getHolidayDate(), h.getHolidayName(), h.getKind().name(),
                    h.getKind().label(), h.isPaid(), by == null, HolidayRef.of(by),
                    HolidayRef.of(h.getReplacesHolidayId() == null ? null : originals.get(h.getReplacesHolidayId())),
                    h.getAgreementId(), a == null ? null : a.getTitle(), h.getSourceRef(), h.getVerifiedDate(),
                    h.getNote());
        }).toList();
    }

    private HolidayView view(Holiday h) {
        return views(List.of(h)).get(0);
    }

    // ---- 공휴일 자료 불러오기 ----

    /**
     * 사람이 넣은 공휴일 CSV를 불러온다. 같은 날짜·이름·종류가 이미 있으면 건너뛰고, 같은 날짜·이름인데 종류가 다르면
     * 전체를 거부한다. 형식 오류가 하나라도 있으면 아무것도 저장하지 않는다.
     *
     * <p>근로기준법 제55조 제2항·부칙: 공휴일 행은 유급휴일로 저장하므로, 모든 행의 날짜에 대해 이 사업장 규모에
     * 제55조 제2항이 적용되는지 먼저 확인한다({@link PublicHolidayPaidRule}). 기준값이 없으면 409(LegalRateException),
     * 시행 전 날짜가 하나라도 있으면 422로 전체를 거부한다. 시행 전 날을 쉬게 했다면 회사휴일로 등록한다.
     */
    @Transactional
    public ImportResult importPublicHolidays(String csv, String actor) {
        List<PublicHolidayCsv.Row> rows;
        try {
            rows = PublicHolidayCsv.parse(csv);
        } catch (PublicHolidayCsv.FormatException e) {
            throw AttendanceException.badRequest(e.getMessage());
        }
        for (PublicHolidayCsv.Row r : rows) {
            if (!paidRule.appliesOn(r.date())) {
                throw rejected(r.line() + "번째 줄: " + r.date() + "은(는) 이 사업장 규모에 근로기준법 제55조 제2항이 "
                        + "시행되기 전이라 유급 공휴일로 넣을 수 없어요. 쉬게 했다면 회사휴일로 등록해 주세요");
            }
        }
        LocalDateTime now = now();
        Map<LocalDate, List<Holiday>> byDate = new HashMap<>();
        List<Holiday> created = new ArrayList<>();
        int skipped = 0;
        for (PublicHolidayCsv.Row r : rows) {
            List<Holiday> existing = byDate.computeIfAbsent(r.date(), holidays::findByHolidayDate);
            Holiday same = existing.stream().filter(h -> h.getHolidayName().equals(r.name())).findFirst().orElse(null);
            if (same != null) {
                if (same.getKind() != r.kind()) {
                    throw AttendanceException.conflict(r.line() + "번째 줄: " + r.date() + " " + r.name()
                            + "은(는) 이미 " + same.getKind().label() + "(으)로 등록돼 있어요");
                }
                skipped++;
                continue;
            }
            created.add(holidays.save(Holiday.statutory(r.date(), r.name(), r.kind(), r.source(), r.verified(),
                    actor, now)));
        }
        return new ImportResult(created.size(), skipped, created.isEmpty() ? List.of() : views(created));
    }

    // ---- 회사휴일 ----

    @Transactional
    public HolidayView createCompanyHoliday(CompanyHolidayRequest req, String actor) {
        if (!holidays.findByHolidayDate(req.date()).isEmpty()) {
            throw AttendanceException.conflict(req.date() + "은(는) 이미 휴일 달력에 있어요");
        }
        boolean paid = req.paid() == null || req.paid();
        return view(reserve(Holiday.company(req.date(), req.name().trim(), paid, clean(req.note()), actor, now())));
    }

    /**
     * 회사휴일·대체된 휴일을 그 날짜에 하나만 둔다. 앞의 "그 날에 휴일이 없다" 검사만으로는 동시에 들어온 두 요청이 모두
     * 통과할 수 있으므로, DB 유일 인덱스(V017 ux_holiday_reserved_date)로 직렬화하고 즉시 flush 해 충돌을 409로 돌려준다.
     */
    private Holiday reserve(Holiday h) {
        try {
            return holidays.saveAndFlush(h);
        } catch (DataIntegrityViolationException e) {
            throw AttendanceException.conflict(h.getHolidayDate() + "은(는) 방금 다른 휴일로 등록됐어요. 달력을 새로 보고 다시 시도해 주세요");
        }
    }

    /**
     * 회사휴일·휴일 대체를 지운다. 이미 지난(오늘 포함) 날이 걸려 있으면 지울 수 없다(과거 근태·급여가 달라지지 않게).
     * 공휴일 행은 공휴일 자료로만 관리하므로 여기서 지우지 않는다.
     */
    @Transactional
    public void delete(long holidayId) {
        Holiday h = holidays.findById(holidayId)
                .orElseThrow(() -> AttendanceException.notFound("휴일이 없어요: " + holidayId));
        LocalDate today = today();
        switch (h.getKind()) {
            case PUBLIC, SUBSTITUTE -> throw AttendanceException.badRequest(
                    "법정공휴일·대체공휴일은 공휴일 자료로만 관리해요. 근로일로 바꾸려면 휴일 대체를 등록해 주세요");
            case COMPANY -> {
                if (!h.getHolidayDate().isAfter(today)) {
                    throw AttendanceException.conflict("이미 지난 회사휴일은 지울 수 없어요");
                }
            }
            case REPLACEMENT -> {
                Holiday original = holidays.findById(h.getReplacesHolidayId()).orElseThrow();
                if (!h.getHolidayDate().isAfter(today) || !original.getHolidayDate().isAfter(today)) {
                    throw AttendanceException.conflict("원래 공휴일이나 대체된 날이 이미 지난 휴일 대체는 취소할 수 없어요");
                }
            }
            default -> throw new IllegalStateException(h.getKind().name());
        }
        holidays.delete(h);
    }

    // ---- 휴일 대체 ----

    /**
     * 근로기준법 제55조 제2항 단서: 근로자대표와 서면으로 합의한 경우 공휴일을 특정한 근로일로 대체할 수 있다.
     * <ul>
     *   <li>원래 휴일은 법정공휴일·대체공휴일만(제55조 제2항 본문의 휴일). 한 공휴일은 한 번만 대체한다.</li>
     *   <li>대신 쉴 날은 원래 날과 다르고, 휴일 달력에 휴일이 없는 날(근로일)이어야 한다.</li>
     *   <li>서면합의는 휴일 대체용이어야 하고, 원래 날과 대신 쉴 날 모두 합의 유효기간 안이며, 두 날보다 먼저 체결돼 있어야 한다.</li>
     * </ul>
     */
    @Transactional
    public HolidayView substitute(SubstitutionRequest req, String actor) {
        LaborAgreement agreement = agreements.findForUpdate(req.agreementId())
                .orElseThrow(() -> AttendanceException.badRequest(
                        "근로자대표 서면합의 문서가 없어요: " + req.agreementId() + " (근로기준법 제55조 제2항 단서)"));
        Holiday original = holidays.findForUpdate(req.originalHolidayId())
                .orElseThrow(() -> AttendanceException.notFound("공휴일이 없어요: " + req.originalHolidayId()));
        if (!original.getKind().isStatutory()) {
            throw AttendanceException.badRequest(
                    "법정공휴일·대체공휴일만 휴일 대체할 수 있어요(근로기준법 제55조 제2항): " + original.getKind().label());
        }
        if (holidays.findByReplacesHolidayId(original.getHolidayId()).isPresent()) {
            throw AttendanceException.conflict(original.getHolidayDate() + " " + original.getHolidayName()
                    + "은(는) 이미 다른 날로 대체됐어요");
        }
        LocalDate from = original.getHolidayDate();
        LocalDate to = req.substituteDate();
        if (to.equals(from)) {
            throw AttendanceException.badRequest("대신 쉴 날은 원래 공휴일과 달라야 해요");
        }
        if (!holidays.findByHolidayDate(to).isEmpty()) {
            throw AttendanceException.conflict(to + "은(는) 이미 휴일이에요. 근로일로만 대체할 수 있어요(근로기준법 제55조 제2항 단서)");
        }
        if (agreement.getAgreementType() != AgreementType.HOLIDAY_SUBSTITUTION) {
            throw rejected("휴일 대체에 관한 서면합의가 아니에요: " + agreement.getTitle());
        }
        if (!agreement.isEffectiveOn(from) || !agreement.isEffectiveOn(to)) {
            throw rejected("원래 공휴일(" + from + ")과 대신 쉴 날(" + to + ")이 모두 서면합의 유효기간 안이어야 해요");
        }
        // 휴일 달력은 회사 공통이라 사업장 한 곳 대상 합의로는 회사 전체 휴일을 대체할 수 없다(#27 대상 범위).
        if (agreement.getScopeType() != LaborAgreement.Scope.ALL) {
            throw rejected("휴일 달력은 회사 공통이라 회사 전체를 대상으로 한 서면합의가 있어야 해요: " + agreement.getTitle());
        }
        LocalDate earlier = from.isBefore(to) ? from : to;
        if (agreement.getSignedDate().isAfter(earlier)) {
            throw rejected("서면합의(" + agreement.getSignedDate() + " 체결)는 대체하는 날(" + earlier + ")보다 먼저 체결돼 있어야 해요");
        }
        String base = original.getHolidayName();
        int max = PublicHolidayCsv.NAME_MAX - REPLACEMENT_SUFFIX.length();
        String name = (base.length() > max ? base.substring(0, max) : base) + REPLACEMENT_SUFFIX;
        return view(reserve(Holiday.replacement(to, name, original.getHolidayId(), agreement.getAgreementId(),
                clean(req.note()), actor, now())));
    }

    private static AttendanceException rejected(String message) {
        return new AttendanceException(HttpStatus.UNPROCESSABLE_ENTITY, message, List.of());
    }

    // 서면합의 등록·조회는 #27 서면합의서 관리(LaborAgreementService, /api/attendance/agreements)로 옮겼다.

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
