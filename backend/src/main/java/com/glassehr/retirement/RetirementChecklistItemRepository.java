package com.glassehr.retirement;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 법인 조건(#124): 첫 인자 enterCd 로 그 법인 체크리스트만 본다. */
public interface RetirementChecklistItemRepository
        extends JpaRepository<RetirementChecklistItem, RetirementChecklistItem.Key> {

    List<RetirementChecklistItem> findByEnterCdAndCaseIdOrderBySortOrderAsc(String enterCd, long caseId);

    Optional<RetirementChecklistItem> findByEnterCdAndCaseIdAndItemCode(String enterCd, long caseId,
            String itemCode);
}
