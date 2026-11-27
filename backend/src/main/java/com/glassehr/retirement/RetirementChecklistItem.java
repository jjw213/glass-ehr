package com.glassehr.retirement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/** 퇴직 처리 체크리스트 항목. 테이블: V022 retirement_checklist_item. 이름·근거는 생성 당시 템플릿 값을 남긴다. */
@Entity
@Table(name = "retirement_checklist_item")
@IdClass(RetirementChecklistItem.Key.class)
public class RetirementChecklistItem {

    /** 복합 키(enter_cd, case_id, item_code). DB PK 와 같다(#129, tenant-design.md 5.2). */
    public static class Key implements Serializable {
        private String enterCd;
        private Long caseId;
        private String itemCode;

        public Key() {
        }

        public Key(String enterCd, Long caseId, String itemCode) {
            this.enterCd = enterCd;
            this.caseId = caseId;
            this.itemCode = itemCode;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(enterCd, k.enterCd) && Objects.equals(caseId, k.caseId)
                    && Objects.equals(itemCode, k.itemCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(enterCd, caseId, itemCode);
        }
    }

    /**
     * 사원 법인(#124). PK 는 (enter_cd, case_id, item_code), FK 는 (enter_cd, case_id) → retirement_case.
     * 복합 ID 엔티티라 enter_cd 도 JPA 키에 넣는다(#129).
     */
    @Id
    @Column(name = "enter_cd", nullable = false, updatable = false)
    private String enterCd;

    @Id
    @Column(name = "case_id", nullable = false, updatable = false)
    private Long caseId;

    @Id
    @Column(name = "item_code", nullable = false, updatable = false)
    private String itemCode;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    @Column(name = "label", nullable = false, updatable = false)
    private String label;

    @Column(name = "legal_basis", updatable = false)
    private String legalBasis;

    @Column(name = "done_at")
    private LocalDateTime doneAt;

    @Column(name = "done_by")
    private String doneBy;

    @Column(name = "note")
    private String note;

    protected RetirementChecklistItem() {
    }

    RetirementChecklistItem(String enterCd, long caseId, RetirementChecklist.Item item) {
        this.enterCd = enterCd;
        this.caseId = caseId;
        this.itemCode = item.code();
        this.sortOrder = item.sortOrder();
        this.label = item.label();
        this.legalBasis = item.legalBasis();
    }

    void mark(boolean done, String note, String actor, LocalDateTime now) {
        this.doneAt = done ? now : null;
        this.doneBy = done ? actor : null;
        this.note = note;
    }

    public Long getCaseId() {
        return caseId;
    }

    public String getItemCode() {
        return itemCode;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getLabel() {
        return label;
    }

    public String getLegalBasis() {
        return legalBasis;
    }

    public LocalDateTime getDoneAt() {
        return doneAt;
    }

    public String getDoneBy() {
        return doneBy;
    }

    public String getNote() {
        return note;
    }
}
