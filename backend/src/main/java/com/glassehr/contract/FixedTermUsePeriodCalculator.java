package com.glassehr.contract;

import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 기간제근로자 사용기간 상한의 마지막 날 계산.
 *
 * <p>기간제법 제4조 제1항: 기간제근로자는 2년(계약 반복갱신 시 계속근로한 총기간)을 초과하지 않는 범위에서 사용할 수 있다.
 * 연 수는 코드에 쓰지 않고 legal_rate {@link LegalRateItem#FIXED_TERM_MAX_USE_YEARS}에서 <b>계속근로 시작일 기준 적용값</b>으로 읽는다.
 *
 * <p>기간 계산(민법 제157조·제160조, 2026-11-12 원문 미확인): 계속근로 시작일(오전 0시 시작이라 초일 산입)부터 N년.
 * 마지막 해에서 시작일에 해당하는 날의 전날에 만료하고, 해당하는 날이 없으면(2월 29일 시작 → 평년) 그 달 말일에 만료한다.
 * 이 계산의 정답값은 tests/legal-fixtures 에 사람이 검수한 파일이 생기면 그것으로 검증한다(현재 없음, #17 질문).
 */
@Component
public class FixedTermUsePeriodCalculator {

    private final LegalRateService legalRates;

    public FixedTermUsePeriodCalculator(LegalRateService legalRates) {
        this.legalRates = legalRates;
    }

    /** 사용기간 상한의 마지막 날. 기준값이 없으면 빈 값(조용히 2년을 가정하지 않음). */
    public Optional<LocalDate> lastDayWithinLimit(LocalDate continuousStart) {
        Objects.requireNonNull(continuousStart, "continuousStart");
        Optional<LegalRateRow> row = legalRates.findEffective(LegalRateItem.FIXED_TERM_MAX_USE_YEARS.code(),
                continuousStart);
        if (row.isEmpty()) {
            return Optional.empty();
        }
        LegalRateRow r = row.get();
        if (!LegalRateItem.FIXED_TERM_MAX_USE_YEARS.unit().equals(r.unit())) {
            throw new IllegalStateException("기간제 사용기간 기준값 단위가 YEARS 가 아니에요: " + r.effectiveFrom());
        }
        int years;
        try {
            years = r.rateValue().intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalStateException("기간제 사용기간 기준값은 정수 연 수여야 해요: " + r.effectiveFrom());
        }
        if (years <= 0) {
            throw new IllegalStateException("기간제 사용기간 기준값은 1 이상이어야 해요: " + r.effectiveFrom());
        }
        return Optional.of(lastDay(continuousStart, years));
    }

    /** 민법 제160조 제2항·제3항 방식의 만료일. */
    static LocalDate lastDay(LocalDate start, int years) {
        LocalDate corresponding = start.plusYears(years); // 해당일이 없으면 LocalDate 가 그 달 말일로 맞춘다
        if (corresponding.getDayOfMonth() != start.getDayOfMonth()) {
            return corresponding; // 제160조 제3항: 해당일이 없으면 그 월의 말일
        }
        return corresponding.minusDays(1); // 제160조 제2항: 해당일의 전일
    }
}
