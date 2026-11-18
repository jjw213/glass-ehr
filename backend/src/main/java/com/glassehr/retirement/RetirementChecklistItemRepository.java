package com.glassehr.retirement;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetirementChecklistItemRepository
        extends JpaRepository<RetirementChecklistItem, RetirementChecklistItem.Key> {

    List<RetirementChecklistItem> findByCaseIdOrderBySortOrderAsc(long caseId);

    Optional<RetirementChecklistItem> findByCaseIdAndItemCode(long caseId, String itemCode);
}
