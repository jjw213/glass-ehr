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
 * 근로자대표 서면합의서(#27). 테이블: V019__labor_agreement.sql. 유효기간은 양 끝 포함 [validFrom, validUntil],
 * validUntil NULL 은 기간 정함 없음.
 */
@Entity
@Table(name = "labor_agreement")
public class LaborAgreement {

    /** 대상 범위. ALL = 회사 전체, SITE = 사업장 한 곳. */
    public enum Scope { ALL, SITE }

    /** 근로자대표 구분(근로기준법 제24조 제3항). */
    public enum RepType {
        /** 근로자의 과반수로 조직된 노동조합. */
        MAJORITY_UNION,
        /** 근로자의 과반수를 대표하는 자. */
        MAJORITY_PERSON
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "agreement_id", nullable = false, updatable = false)
    private Long agreementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "agreement_type", nullable = false, updatable = false)
    private AgreementType agreementType;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "signed_date", nullable = false)
    private LocalDate signedDate;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false)
    private Scope scopeType;

    @Column(name = "scope_site_code")
    private String scopeSiteCode;

    @Column(name = "target_desc", nullable = false)
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
        this.validFrom = f.validFrom();
        this.validUntil = f.validUntil();
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
        return validFrom;
    }

    public LocalDate getValidUntil() {
        return validUntil;
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
