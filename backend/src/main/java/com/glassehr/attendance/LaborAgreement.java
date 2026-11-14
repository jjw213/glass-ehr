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
 * 근로자대표 서면합의서(#27). 테이블: worker_rep_agreement(V017 #26 이 만들고 V020 이 넓힘).
 * <p>
 * 유효기간: DB 는 V017 그대로 [effective_from, effective_to)(effective_to = 마지막 유효일 다음 날)로 저장하고,
 * 이 클래스 밖으로는 합의서에 적힌 대로 양 끝 포함 [validFrom, validUntil] 로 내보낸다. validUntil NULL = 기한 없음.
 */
@Entity
@Table(name = "worker_rep_agreement")
public class LaborAgreement {

    /** 대상 범위. ALL = 회사 전체, SITE = 사업장 한 곳. */
    public enum Scope { ALL, SITE }

    /** 근로자대표 구분(근로기준법 제24조 제3항). 값은 V017 ck_wra_rep_type 과 같다. */
    public enum RepType {
        /** 근로자의 과반수로 조직된 노동조합. */
        MAJORITY_UNION,
        /** 근로자의 과반수를 대표하는 자. */
        MAJORITY_REP
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "agreement_id", nullable = false, updatable = false)
    private Long agreementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "agreement_kind", nullable = false, updatable = false)
    private AgreementType agreementType;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "signed_date", nullable = false)
    private LocalDate signedDate;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** 마지막 유효일의 다음 날(배타). NULL = 기한 없음. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "document_ref", nullable = false)
    private String documentRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false)
    private Scope scopeType;

    @Column(name = "scope_site_code")
    private String scopeSiteCode;

    /** #26 때 등록된 행은 비어 있을 수 있다. 새로 저장할 때는 필수(LaborAgreementRules). */
    @Column(name = "target_desc")
    private String targetDesc;

    @Enumerated(EnumType.STRING)
    @Column(name = "rep_type", nullable = false)
    private RepType repType;

    @Column(name = "rep_union_name")
    private String repUnionName;

    @Column(name = "rep_emp_id")
    private Long repEmpId;

    @Column(name = "note")
    private String note;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected LaborAgreement() {
    }

    public LaborAgreement(AgreementType agreementType, LaborAgreementDtos.Fields f, String actor, LocalDateTime now) {
        this.agreementType = agreementType;
        this.createdBy = actor;
        this.createdAt = now;
        apply(f, actor, now);
    }

    void apply(LaborAgreementDtos.Fields f, String actor, LocalDateTime now) {
        this.title = f.title();
        this.signedDate = f.signedDate();
        this.effectiveFrom = f.validFrom();
        this.effectiveTo = f.validUntil() == null ? null : f.validUntil().plusDays(1);
        this.documentRef = f.documentRef();
        this.scopeType = f.scopeType();
        this.scopeSiteCode = f.scopeSiteCode();
        this.targetDesc = f.targetDesc();
        this.repType = f.repType();
        this.repUnionName = f.repUnionName();
        this.repEmpId = f.repEmpId();
        this.note = f.note();
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    /** d 가 유효기간(양 끝 포함) 안이면 true. */
    public boolean isEffectiveOn(LocalDate d) {
        return !effectiveFrom.isAfter(d) && (effectiveTo == null || d.isBefore(effectiveTo));
    }

    public Long getAgreementId() {
        return agreementId;
    }

    public AgreementType getAgreementType() {
        return agreementType;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getSignedDate() {
        return signedDate;
    }

    public LocalDate getValidFrom() {
        return effectiveFrom;
    }

    /** 마지막 유효일(포함). NULL = 기한 없음. */
    public LocalDate getValidUntil() {
        return effectiveTo == null ? null : effectiveTo.minusDays(1);
    }

    public String getDocumentRef() {
        return documentRef;
    }

    public Scope getScopeType() {
        return scopeType;
    }

    public String getScopeSiteCode() {
        return scopeSiteCode;
    }

    public String getTargetDesc() {
        return targetDesc;
    }

    public RepType getRepType() {
        return repType;
    }

    public String getRepUnionName() {
        return repUnionName;
    }

    public Long getRepEmpId() {
        return repEmpId;
    }

    public String getNote() {
        return note;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
