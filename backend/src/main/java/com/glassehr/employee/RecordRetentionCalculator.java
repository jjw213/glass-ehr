package com.glassehr.employee;

import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 근로자 명부 보존기한 계산.
 *
 * <p>근로기준법 제42조(계약 서류의 보존): 사용자는 근로자 명부 등 근로계약에 관한 중요한 서류를 일정 기간 보존해야 한다.
 * 보존 기간(연 수)은 코드에 쓰지 않고 legal_rate 항목 {@link LegalRateItem#EMPLOYEE_RECORD_RETENTION_YEARS}에서
 * <b>퇴직일 기준 적용값</b>으로 읽는다. 기산일은 해고·퇴직·사망일(이슈 #14 "퇴직 후 보존기한", 같은 법 시행령 제22조 — 원문 재확인 대상).
 *
 * <p>보존기한 = 퇴직일 + N년(달력 기준. 2월 29일 퇴직이고 N년 뒤가 평년이면 2월 28일). 이 날까지 보존한다.
 * 값이 없으면 {@link com.glassehr.legalrate.LegalRateException}(조용히 기본값을 쓰지 않음).
 */
@Component
public class RecordRetentionCalculator {

    private final LegalRateService legalRates;

    public RecordRetentionCalculator(LegalRateService legalRates) {
        this.legalRates = legalRates;
    }

    public LocalDate retentionUntil(LocalDate retireDate) {
        Objects.requireNonNull(retireDate, "retireDate");
        LegalRateRow row = legalRates.getEffective(LegalRateItem.EMPLOYEE_RECORD_RETENTION_YEARS, retireDate);
        BigDecimal v = row.rateValue();
        int years;
        try {
            years = v.intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalStateException("보존 기간 기준값은 정수 연 수여야 해요: " + row.itemCode() + " " + row.effectiveFrom());
        }
        if (years <= 0) {
            throw new IllegalStateException("보존 기간 기준값은 1 이상이어야 해요: " + row.itemCode() + " " + row.effectiveFrom());
        }
        return retireDate.plusYears(years);
    }
}
