package com.glassehr.legalrate;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 특정 날짜 기준 법령 기준값 조회.
 *
 * <p>최저임금·4대보험 요율·비과세 한도 등은 코드에 쓰지 않고 이 서비스로 읽는다(AGENTS.md 법령 규칙).
 * 과거 날짜를 넘기면 그 날짜에 적용되던 값을 돌려주므로 재계산이 가능하다.
 */
@Service
public class LegalRateService {

    private final LegalRateMapper mapper;

    public LegalRateService(LegalRateMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 조회일에 유효한 기준값 1건. 0건이면 {@link LegalRateException.Reason#NOT_FOUND},
     * 2건 이상이면 {@link LegalRateException.Reason#OVERLAPPING}.
     */
    public LegalRateRow getEffective(String itemCode, LocalDate asOfDate) {
        List<LegalRateRow> rows = fetch(itemCode, asOfDate);
        if (rows.isEmpty()) {
            throw new LegalRateException(LegalRateException.Reason.NOT_FOUND, itemCode, asOfDate, 0);
        }
        return rows.get(0);
    }

    /** 미등록을 정상 흐름으로 다룰 때 쓴다. 겹침은 여전히 예외다. */
    public Optional<LegalRateRow> findEffective(String itemCode, LocalDate asOfDate) {
        List<LegalRateRow> rows = fetch(itemCode, asOfDate);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private List<LegalRateRow> fetch(String itemCode, LocalDate asOfDate) {
        Objects.requireNonNull(itemCode, "itemCode");
        Objects.requireNonNull(asOfDate, "asOfDate");
        // SQL이 이미 반개구간으로 거르지만, 매핑 오류로 기간 밖 행이 섞이지 않도록 한 번 더 확인한다.
        List<LegalRateRow> rows = mapper.findEffective(itemCode, asOfDate).stream()
                .filter(r -> itemCode.equals(r.itemCode()) && r.isEffectiveOn(asOfDate))
                .toList();
        if (rows.size() > 1) {
            throw new LegalRateException(
                    LegalRateException.Reason.OVERLAPPING, itemCode, asOfDate, rows.size());
        }
        return rows;
    }
}
