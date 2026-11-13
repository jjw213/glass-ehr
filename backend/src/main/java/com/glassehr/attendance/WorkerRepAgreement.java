package com.glassehr.attendance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 근로자대표 서면합의 문서(#26, 구조: docs/work-schedule-types-design.md §3). 테이블: V017__holiday_calendar.sql.
 * 지금은 휴일 대체(근로기준법 제55조 제2항 단서) 합의만 쓴다. 근로자대표 실명 등 개인정보는 저장하지 않고
 * 대표 구분(과반수 노동조합 / 근로자 과반수 대표자)과 합의서 문서 참조만 둔다.
 */
@Entity
@Table(name = "worker_rep_agreement")
public class WorkerRepAgreement {

    /** 합의 종류. V017 ck_wra_kind 와 같다. */
    public enum Kind { HOLIDAY_SUBSTITUTION }

    /** 근로자대표 구분. V017 ck_wra_rep_type 과 같다. */
    public enum RepType { MAJORITY_UNION, MAJORITY_REP }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "agreement_id", nullable = false, updatable = false)
    private Long agreementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "agreement_kind", nullable = false, updatable = false)
    private Kind kind;

    @Column(name = "title", nullable = false, updatable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "rep_type", nullable = false, updatable = false)
    private RepType repType;

    @Column(name = "signed_date", nullable = false, updatable = false)
    private LocalDate signedDate;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to", updatable = false)
    private LocalDate effectiveTo;

    @Column(name = "document_ref", nullable = false, updatable = false)
    private String documentRef;

    @Column(name = "note", updatable = false)
    private String note;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected WorkerRepAgreement() {
    }

    public WorkerRepAgreement(Kind kind, String title, RepType repType, LocalDate signedDate, LocalDate effectiveFrom,
            LocalDate effectiveTo, String documentRef, String note, String createdBy, LocalDateTime now) {
        this.kind = kind;
        this.title = title;
        this.repType = repType;
        this.signedDate = signedDate;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.documentRef = documentRef;
        this.note = note;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    /** 유효기간 [effectiveFrom, effectiveTo) 안이면 true. effectiveTo null = 기한 없음. */
    public boolean isEffectiveOn(LocalDate d) {
        return !effectiveFrom.isAfter(d) && (effectiveTo == null || d.isBefore(effectiveTo));
    }

    public Long getAgreementId() { return agreementId; }
    public Kind getKind() { return kind; }
    public String getTitle() { return title; }
    public RepType getRepType() { return repType; }
    public LocalDate getSignedDate() { return signedDate; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public String getDocumentRef() { return documentRef; }
    public String getNote() { return note; }
    public String getCreatedBy() { return createdBy; }
}
