package com.glassehr.retirement;

import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 해고 예고 기간 확인(경고만 하고 막지 않는다).
 *
 * <p>근로기준법 제26조(해고의 예고): 사용자는 근로자를 해고하려면 일정 기간 전에 예고해야 하고, 예고하지 않으면
 * 그 기간분 이상의 통상임금을 지급해야 한다. 단서 각 호(계속 근로 기간이 일정 개월 미만, 천재·사변 등 부득이한 사유,
 * 근로자의 고의로 사업에 막대한 지장 등)는 적용 제외. 조문 원문은 아직 law.go.kr에서 다시 확인하지 못했다
 * (docs/retirement-design.md 7장).
 *
 * <p>기간(일)과 적용 제외 개월 수는 코드에 쓰지 않고 legal_rate {@link LegalRateItem#DISMISSAL_NOTICE_DAYS}·
 * {@link LegalRateItem#DISMISSAL_NOTICE_EXEMPT_SERVICE_MONTHS}에서 <b>해고일(퇴직일) 기준 적용값</b>으로 읽는다.
 * 값이 없으면 조용히 기본값을 쓰지 않고 {@link NoticeCheck#UNKNOWN}을 돌려준다.
 *
 * <p>날짜 셈(tests/legal-fixtures/dismissal-notice.csv 로 검증):
 * <ul>
 *   <li>예고 일수 = 예고일 다음 날부터 해고일까지의 일수({@code DAYS.between(예고일, 해고일)}). 예고 일수 ≥ 기준 일수면 충족.
 *       민법 제157조 본문(초일 불산입).</li>
 *   <li>계속 근로 기간이 M개월 미만 = 해고일이 M개월 기간의 마지막 날({@link #lastDayOfMonths})보다 앞.
 *       민법 제157조 단서(입사일 오전 0시 시작이라 초일 산입)·제160조 제2항(최후 월의 해당일 전일 만료)·
 *       제160조 제3항(해당일이 없으면 그 월의 말일 만료).</li>
 *   <li>법률 제16270호 부칙 제2조: 개정 제26조 단서 제1호는 시행 후 근로계약을 체결한 근로자부터 적용한다.
 *       시행일은 코드에 쓰지 않고 legal_rate 적용 개월 수 행이 <b>입사일</b>(근로계약 체결일 대용)에도 적용되는지로 본다.
 *       적용되지 않으면 제1호 적용 제외로 보지 않고 예고 기간으로 다시 확인한다.</li>
 * </ul>
 */
@Component
public class DismissalNoticeChecker {

    /** 근로기준법 제26조 단서 각 호. V022 ck_retirement_exempt 와 같다. */
    public enum Exemption {
        /** 제1호: 계속 근로한 기간이 기준 개월 수 미만. */
        SHORT_SERVICE,
        /** 제2호: 천재·사변, 그 밖의 부득이한 사유로 사업을 계속하는 것이 불가능한 경우. */
        UNAVOIDABLE,
        /** 제3호: 근로자가 고의로 사업에 막대한 지장을 초래하거나 재산상 손해를 끼친 경우(시행령 별표 사유). */
        EMPLOYEE_FAULT
    }

    /** 확인 결과. V022 ck_retirement_notice_check 와 같다. */
    public enum NoticeCheck { NOT_APPLICABLE, OK, SHORT, EXEMPT, UNKNOWN }

    /**
     * @param requiredDays  기준 예고 일수(legal_rate). 값이 없으면 null.
     * @param noticeDays    실제 예고 일수
     * @param shortfallDays 미달 일수(SHORT 일 때만)
     */
    public record Result(NoticeCheck check, Integer requiredDays, Integer noticeDays, Integer shortfallDays,
            List<String> warnings) {
    }

    private final LegalRateService legalRates;

    public DismissalNoticeChecker(LegalRateService legalRates) {
        this.legalRates = legalRates;
    }

    public Result check(LocalDate hireDate, LocalDate noticeDate, LocalDate dismissalDate, Exemption exemption) {
        Objects.requireNonNull(hireDate, "hireDate");
        Objects.requireNonNull(noticeDate, "noticeDate");
        Objects.requireNonNull(dismissalDate, "dismissalDate");
        List<String> warnings = new ArrayList<>();
        int noticeDays = Math.toIntExact(ChronoUnit.DAYS.between(noticeDate, dismissalDate));

        if (exemption == Exemption.UNAVOIDABLE || exemption == Exemption.EMPLOYEE_FAULT) {
            warnings.add("근로기준법 제26조 단서 " + (exemption == Exemption.UNAVOIDABLE ? "제2호" : "제3호")
                    + " 적용 제외로 기록했어요. 사유를 뒷받침하는 자료를 보관해 주세요");
            return new Result(NoticeCheck.EXEMPT, null, noticeDays, null, warnings);
        }

        Optional<Integer> required = whole(LegalRateItem.DISMISSAL_NOTICE_DAYS, dismissalDate);

        if (exemption == Exemption.SHORT_SERVICE) {
            Optional<Integer> months = whole(LegalRateItem.DISMISSAL_NOTICE_EXEMPT_SERVICE_MONTHS, dismissalDate);
            if (months.isEmpty()) {
                warnings.add(missing(LegalRateItem.DISMISSAL_NOTICE_EXEMPT_SERVICE_MONTHS, dismissalDate,
                        "근속 기간 적용 제외(제26조 단서 제1호)"));
                return new Result(NoticeCheck.UNKNOWN, required.orElse(null), noticeDays, null, warnings);
            }
            // 법률 제16270호 부칙 제2조: 개정 제26조 단서 제1호는 시행 후 근로계약 체결자부터 적용.
            if (legalRates.findEffective(LegalRateItem.DISMISSAL_NOTICE_EXEMPT_SERVICE_MONTHS.code(), hireDate)
                    .isEmpty()) {
                warnings.add("입사일 " + hireDate + "이 근로기준법 제26조 단서 제1호(개정) 시행 전이라 법률 제16270호 부칙 제2조에 따라"
                        + " 근속 기간 적용 제외를 쓸 수 없어요. 예고 기간으로 다시 확인했어요(구법상 적용 제외 사유는 따로 확인해 주세요)");
            } else if (dismissalDate.isBefore(lastDayOfMonths(hireDate, months.get()))) {
                // 근로기준법 제26조 단서 제1호: 계속 근로한 기간이 M개월 미만인 경우
                return new Result(NoticeCheck.EXEMPT, required.orElse(null), noticeDays, null, warnings);
            } else {
                warnings.add("계속 근로 기간이 " + months.get() + "개월 이상이라 근로기준법 제26조 단서 제1호 적용 제외가 아니에요."
                        + " 예고 기간으로 다시 확인했어요");
            }
        }

        if (required.isEmpty()) {
            warnings.add(missing(LegalRateItem.DISMISSAL_NOTICE_DAYS, dismissalDate, "해고 예고 기간(제26조)"));
            return new Result(NoticeCheck.UNKNOWN, null, noticeDays, null, warnings);
        }
        int need = required.get();
        if (noticeDays >= need) {
            return new Result(NoticeCheck.OK, need, noticeDays, null, warnings);
        }
        int shortfall = need - noticeDays;
        // 근로기준법 제26조 본문: 예고하지 않았을 때 통상임금 지급 — 계산은 급여 모듈에서 한다.
        warnings.add("해고 예고 기간이 " + shortfall + "일 모자라요(예고 " + noticeDays + "일, 기준 " + need
                + "일). 근로기준법 제26조에 따른 해고예고수당 지급 대상인지 급여 담당과 확인해 주세요");
        return new Result(NoticeCheck.SHORT, need, noticeDays, shortfall, warnings);
    }

    /**
     * 입사일부터 M개월 기간의 마지막 날(만료일).
     * 민법 제157조 단서: 입사일은 오전 0시부터 시작하므로 초일 산입.
     * 민법 제160조 제2항: 월로 정한 기간은 최후의 월에서 기산일에 해당한 날의 전일로 만료.
     * 민법 제160조 제3항: 최종의 월에 해당일이 없으면 그 월의 말일로 만료(예: 1월 31일 입사 3개월 → 4월 30일).
     */
    public static LocalDate lastDayOfMonths(LocalDate start, int months) {
        LocalDate shifted = start.plusMonths(months);
        if (shifted.getDayOfMonth() != start.getDayOfMonth()) {
            // 해당일이 없어 plusMonths 가 그 달 말일로 맞춘 경우 — 그 말일이 만료일이다(제160조 제3항).
            return shifted;
        }
        return shifted.minusDays(1);
    }

    private Optional<Integer> whole(LegalRateItem item, LocalDate asOf) {
        Optional<LegalRateRow> row = legalRates.findEffective(item.code(), asOf);
        if (row.isEmpty()) {
            return Optional.empty();
        }
        LegalRateRow r = row.get();
        if (!item.unit().equals(r.unit())) {
            throw new IllegalStateException(item.code() + " 기준값 단위가 " + item.unit() + " 가 아니에요: " + r.effectiveFrom());
        }
        int v;
        try {
            v = r.rateValue().intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalStateException(item.code() + " 기준값은 정수여야 해요: " + r.effectiveFrom());
        }
        if (v <= 0) {
            throw new IllegalStateException(item.code() + " 기준값은 1 이상이어야 해요: " + r.effectiveFrom());
        }
        return Optional.of(v);
    }

    private static String missing(LegalRateItem item, LocalDate asOf, String what) {
        return what + " 기준값(legal_rate " + item.code() + ")이 " + asOf
                + " 기준으로 등록되어 있지 않아 확인하지 못했어요. 관리자에게 기준값 입력을 요청해 주세요";
    }
}
