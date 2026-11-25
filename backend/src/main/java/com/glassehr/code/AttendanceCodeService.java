package com.glassehr.code;

import com.glassehr.code.AttendanceCodeDtos.AttendanceCodeCreateRequest;
import com.glassehr.code.AttendanceCodeDtos.AttendanceCodeUpdateRequest;
import com.glassehr.code.AttendanceCodeDtos.AttendanceCodeView;
import com.glassehr.code.AttendanceCodeDtos.LeaveDetail;
import com.glassehr.code.AttendanceCodeDtos.LeaveDetailRequest;
import com.glassehr.code.AttendanceCodeDtos.LegalValue;
import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 근태코드(휴가 종류 LEAVE_TYPE·근태 구분 ATTENDANCE_TYPE) 관리(#143). 설계: docs/attendance-code-design.md.
 * 저장은 공통코드에 하고 삭제 규칙(기본 코드·참조 중이면 409)은 {@link CommonCodeService#requireDeletable}를 그대로 쓴다.
 * 공통코드 API 는 이 두 그룹(edit_page='attendanceCodes')을 고치지 않는다. 처리방식·상세 설정을 함께 저장하기 때문이다.
 * 모든 메서드의 enterCd 는 세션의 현재 법인이다. 다른 법인의 그룹·코드는 404.
 */
@Service
@Transactional(readOnly = true)
public class AttendanceCodeService {

    /** 그룹별 처리방식. DB CHECK(ck_common_code_process, V041)와 같다. 로직은 코드값이 아니라 처리방식을 본다. */
    public static final Map<String, List<String>> PROCESS_TYPES = Map.of(
            CodeGroups.LEAVE_TYPE, List.of("ANNUAL", "STATUTORY", "COMPANY", "UNPAID"),
            CodeGroups.ATTENDANCE_TYPE, List.of("BUSINESS_TRIP", "OUTSIDE_WORK", "REMOTE", "EDUCATION"));

    /** 화면에서 추가할 수 있는 휴가 처리방식. 연차·법정휴가는 법이 정하므로 기본 코드(V041)만 둔다. */
    static final Set<String> USER_LEAVE_PROCESS_TYPES = Set.of("COMPANY", "UNPAID");

    static final String UNPAID = "UNPAID";

    private record LegalItem(LegalRateItem item, String label) {
    }

    /**
     * 기본 코드별로 화면에 보일 법정 기준값(legal_rate). 값은 코드에 두지 않고 조회일 기준으로 읽는다(AGENTS.md 법령 규칙).
     */
    private static final Map<String, List<LegalItem>> LEGAL_ITEMS = Map.of(
            // 근로기준법 제60조 제1항·제2항·제4항: 연차 기본 일수·1년 미만 월 1일·가산 한도
            "ANNUAL", List.of(new LegalItem(LegalRateItem.ANNUAL_LEAVE_BASE_DAYS, "기본 일수"),
                    new LegalItem(LegalRateItem.ANNUAL_LEAVE_MONTHLY_DAYS, "1년 미만 매월 발생"),
                    new LegalItem(LegalRateItem.ANNUAL_LEAVE_MAX_DAYS, "최대 일수")),
            // 근로기준법 제74조 제1항·제4항: 출산전후휴가 일수(다태아)와 그중 유급 기간
            "MATERNITY", List.of(new LegalItem(LegalRateItem.MATERNITY_LEAVE_DAYS, "휴가 일수"),
                    new LegalItem(LegalRateItem.MATERNITY_LEAVE_MULTIPLE_DAYS, "휴가 일수(다태아)"),
                    new LegalItem(LegalRateItem.MATERNITY_LEAVE_PAID_DAYS, "유급 일수"),
                    new LegalItem(LegalRateItem.MATERNITY_LEAVE_MULTIPLE_PAID_DAYS, "유급 일수(다태아)")),
            // 남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의2 제1항: 배우자 출산휴가 일수·유급 일수
            "SPOUSE_MATERNITY", List.of(new LegalItem(LegalRateItem.SPOUSE_MATERNITY_LEAVE_DAYS, "휴가 일수"),
                    new LegalItem(LegalRateItem.SPOUSE_MATERNITY_LEAVE_PAID_DAYS, "유급 일수")),
            // 근로기준법 제73조: 생리휴가 월 일수(무급)
            "MENSTRUAL", List.of(new LegalItem(LegalRateItem.MENSTRUAL_LEAVE_DAYS_PER_MONTH, "월 일수")),
            // 남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제22조의2 제2항·제4항: 가족돌봄휴가 연간 일수·유급 일수
            "FAMILY_CARE", List.of(new LegalItem(LegalRateItem.FAMILY_CARE_LEAVE_DAYS_PER_YEAR, "연간 일수"),
                    new LegalItem(LegalRateItem.FAMILY_CARE_LEAVE_PAID_DAYS, "유급 일수")),
            // 남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의3 제1항: 난임치료휴가 연간 일수·유급 일수
            "INFERTILITY_TREATMENT", List.of(
                    new LegalItem(LegalRateItem.INFERTILITY_TREATMENT_LEAVE_DAYS_PER_YEAR, "연간 일수"),
                    new LegalItem(LegalRateItem.INFERTILITY_TREATMENT_LEAVE_PAID_DAYS, "유급 일수")));

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final CommonCodeService common;
    private final CommonCodeRepository codes;
    private final AttendanceLeaveTypeRepository leaves;
    private final LegalRateService legalRates;
    private final Clock clock;

    @Autowired
    public AttendanceCodeService(CommonCodeService common, CommonCodeRepository codes,
            AttendanceLeaveTypeRepository leaves, LegalRateService legalRates) {
        this(common, codes, leaves, legalRates, Clock.system(SEOUL));
    }

    AttendanceCodeService(CommonCodeService common, CommonCodeRepository codes, AttendanceLeaveTypeRepository leaves,
            LegalRateService legalRates, Clock clock) {
        this.common = common;
        this.codes = codes;
        this.leaves = leaves;
        this.legalRates = legalRates;
        this.clock = clock.withZone(SEOUL);
    }

    /** 관리 화면 목록(사용 안 함 포함). 코드마다 참조 건수·휴가 상세 설정·법정 기준값을 붙인다. */
    public List<AttendanceCodeView> list(String enterCd, String groupCode) {
        requireAttendanceGroup(enterCd, groupCode);
        Map<String, Long> counts = common.usageCounts(enterCd, groupCode);
        Map<String, AttendanceLeaveType> details = isLeave(groupCode)
                ? leaves.findByEnterCd(enterCd).stream()
                        .collect(Collectors.toMap(AttendanceLeaveType::getCode, Function.identity()))
                : Map.of();
        LocalDate today = LocalDate.now(clock);
        return codes.findByEnterCdAndGroupCodeOrderBySortOrderAscCodeAsc(enterCd, groupCode).stream()
                .map(c -> view(c, counts.getOrDefault(c.getCode(), 0L), details.get(c.getCode()), today))
                .toList();
    }

    public AttendanceCodeView get(String enterCd, String groupCode, String code) {
        requireAttendanceGroup(enterCd, groupCode);
        CommonCode c = common.findCode(enterCd, groupCode, code);
        long count = common.usageCounts(enterCd, groupCode).getOrDefault(code, 0L);
        return view(c, count, leaves.findByEnterCdAndCode(enterCd, code).orElse(null), LocalDate.now(clock));
    }

    @Transactional
    public AttendanceCodeView create(String enterCd, String groupCode, AttendanceCodeCreateRequest req) {
        requireAttendanceGroup(enterCd, groupCode);
        requireUserProcessType(groupCode, req.processType());
        if (codes.existsByEnterCdAndGroupCodeAndCode(enterCd, groupCode, req.code())) {
            throw CodeException.duplicate(groupCode, req.code());
        }
        LocalDateTime now = now();
        CommonCode c = codes.saveAndFlush(new CommonCode(enterCd, groupCode, req.code(), req.name().strip(),
                blankToNull(req.description()), req.sortOrder(), req.active(), req.processType(), now));
        AttendanceLeaveType detail = null;
        if (isLeave(groupCode)) {
            LeaveDetailRequest d = requireLeaveDetail(req.processType(), req.leave());
            detail = new AttendanceLeaveType(enterCd, req.code(), now);
            detail.change(d.unitDay(), d.unitHalfDay(), d.unitHour(), d.paid(), d.proofRequired(),
                    d.annualLimitDays(), now);
            detail = leaves.saveAndFlush(detail);
        } else if (req.leave() != null) {
            throw CodeException.badRequest("근태 구분에는 휴가 상세 설정을 두지 않아요");
        }
        return view(c, 0L, detail, LocalDate.now(clock));
    }

    /**
     * 수정. 기본 코드(연차·법정휴가)는 처리방식·유급 여부·연간 한도를 바꿀 수 없다(일수·유급 기간은 legal_rate).
     * 사용 단위·증빙 여부·이름·설명·순서·사용 여부는 고칠 수 있다.
     */
    @Transactional
    public AttendanceCodeView update(String enterCd, String groupCode, String code, AttendanceCodeUpdateRequest req) {
        requireAttendanceGroup(enterCd, groupCode);
        CommonCode c = common.findCode(enterCd, groupCode, code);
        if (c.isSystem()) {
            if (!req.processType().equals(c.getProcessType())) {
                throw CodeException.conflict("기본 코드는 처리방식을 바꿀 수 없어요");
            }
        } else {
            requireUserProcessType(groupCode, req.processType());
        }
        LocalDateTime now = now();
        AttendanceLeaveType detail = null;
        if (isLeave(groupCode)) {
            detail = leaves.findByEnterCdAndCode(enterCd, code)
                    .orElseGet(() -> new AttendanceLeaveType(enterCd, code, now));
            if (c.isSystem()) {
                LeaveDetailRequest d = requireUnits(req.leave());
                if (!Objects.equals(d.paid(), detail.getPaid())
                        || !sameAmount(d.annualLimitDays(), detail.getAnnualLimitDays())) {
                    throw CodeException.conflict("법정휴가·연차의 유급 여부와 일수는 법령 기준값을 따라요. 바꿀 수 없어요");
                }
                detail.change(d.unitDay(), d.unitHalfDay(), d.unitHour(), detail.getPaid(), d.proofRequired(),
                        detail.getAnnualLimitDays(), now);
            } else {
                LeaveDetailRequest d = requireLeaveDetail(req.processType(), req.leave());
                detail.change(d.unitDay(), d.unitHalfDay(), d.unitHour(), d.paid(), d.proofRequired(),
                        d.annualLimitDays(), now);
            }
            detail = leaves.saveAndFlush(detail);
        } else if (req.leave() != null) {
            throw CodeException.badRequest("근태 구분에는 휴가 상세 설정을 두지 않아요");
        }
        c.change(req.name().strip(), blankToNull(req.description()), req.sortOrder(), req.active(), now);
        if (!c.isSystem()) {
            c.changeProcessType(req.processType());
        }
        c = codes.saveAndFlush(c);
        long count = common.usageCounts(enterCd, groupCode).getOrDefault(code, 0L);
        return view(c, count, detail, LocalDate.now(clock));
    }

    /** 삭제. 공통코드 삭제 규칙(기본 코드·참조 중이면 409)을 그대로 쓰고, 휴가 상세 설정을 함께 지운다. */
    @Transactional
    public void delete(String enterCd, String groupCode, String code) {
        requireAttendanceGroup(enterCd, groupCode);
        CommonCode c = common.findCode(enterCd, groupCode, code);
        common.requireDeletable(enterCd, c);
        if (isLeave(groupCode)) {
            leaves.findByEnterCdAndCode(enterCd, code).ifPresent(d -> {
                leaves.delete(d);
                leaves.flush();
            });
        }
        codes.delete(c);
        codes.flush();
    }

    private AttendanceCodeView view(CommonCode c, long usageCount, AttendanceLeaveType d, LocalDate today) {
        LeaveDetail leave = d == null ? null
                : new LeaveDetail(d.isUnitDay(), d.isUnitHalfDay(), d.isUnitHour(), d.getPaid(), d.isProofRequired(),
                        d.getAnnualLimitDays(), d.getGenderLimit(), d.getLegalBasis(),
                        c.isSystem() ? legalValues(c.getCode(), today) : List.of());
        return new AttendanceCodeView(c.getGroupCode(), c.getCode(), c.getCodeName(), c.getDescription(),
                c.getSortOrder(), c.isActive(), c.isSystem(), c.getProcessType(), usageCount > 0, usageCount, leave);
    }

    /** 조회일 기준 법정 기준값. 아직 등록되지 않은 값은 value=null 로 보인다(화면: '기준값 미등록'). */
    private List<LegalValue> legalValues(String code, LocalDate today) {
        return LEGAL_ITEMS.getOrDefault(code, List.of()).stream().map(li -> {
            Optional<LegalRateRow> row = legalRates.findEffective(li.item().code(), today);
            return new LegalValue(li.item().code(), li.label(), row.map(LegalRateRow::rateValue).orElse(null),
                    li.item().unit(), row.map(LegalRateRow::effectiveFrom).orElse(null));
        }).toList();
    }

    private void requireAttendanceGroup(String enterCd, String groupCode) {
        if (!CodeGroups.ATTENDANCE.contains(groupCode)) {
            throw CodeException.groupNotFound(groupCode);
        }
        common.findGroup(enterCd, groupCode);
    }

    private static void requireUserProcessType(String groupCode, String processType) {
        if (!PROCESS_TYPES.get(groupCode).contains(processType)) {
            throw CodeException.badRequest("이 그룹에 없는 처리방식이에요: " + processType);
        }
        if (isLeave(groupCode) && !USER_LEAVE_PROCESS_TYPES.contains(processType)) {
            throw CodeException.badRequest("연차·법정휴가는 기본 코드만 둘 수 있어요. 회사 약정휴가나 무급휴가로 추가해 주세요");
        }
    }

    private static LeaveDetailRequest requireUnits(LeaveDetailRequest d) {
        if (d == null) {
            throw CodeException.badRequest("휴가 상세 설정을 입력해 주세요");
        }
        if (!d.unitDay() && !d.unitHalfDay() && !d.unitHour()) {
            throw CodeException.badRequest("사용 단위를 하나 이상 골라 주세요");
        }
        return d;
    }

    /** 약정·무급휴가 상세 설정. 유급 여부는 꼭 정하고, 무급휴가는 유급일 수 없다. */
    private static LeaveDetailRequest requireLeaveDetail(String processType, LeaveDetailRequest req) {
        LeaveDetailRequest d = requireUnits(req);
        if (d.paid() == null) {
            throw CodeException.badRequest("유급 여부를 정해 주세요");
        }
        if (UNPAID.equals(processType) && d.paid()) {
            throw CodeException.badRequest("무급휴가는 유급으로 둘 수 없어요");
        }
        return d;
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private static boolean isLeave(String groupCode) {
        return CodeGroups.LEAVE_TYPE.equals(groupCode);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
