package com.glassehr.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 고객사 마스터(전체 공통). 테이블: V051 tenant. 설계: docs/saas-design.md 2장.
 * 고객사 → 법인(enter_company.tenant_id) → 사업장. 관리 화면·API는 후속 이슈라 지금은 읽기 전용으로 쓴다.
 */
@Entity
@Table(name = "tenant")
public class Tenant {

    /** 기존 운영 데이터의 고객사(V051, saas-design.md 2.5). */
    public static final String DEFAULT_TENANT_ID = "T001";

    /** 고객사 상태(saas-design.md 2.2). */
    public enum Status {
        TRIAL, ACTIVE, SUSPENDED, TERMINATED
    }

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "tenant_name", nullable = false)
    private String tenantName;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    /** 체험 종료 시각(Asia/Seoul). TRIAL 일 때만 의미가 있다. */
    @Column(name = "trial_ends_at")
    private LocalDateTime trialEndsAt;

    protected Tenant() {
    }

    Tenant(String tenantId, Status status, LocalDateTime trialEndsAt) {
        this.tenantId = tenantId;
        this.tenantName = tenantId;
        this.slug = tenantId.toLowerCase(java.util.Locale.ROOT);
        this.status = status;
        this.trialEndsAt = trialEndsAt;
    }

    /**
     * 이 시각에 로그인·요청을 받을 수 있는가(saas-design.md 2.2). ACTIVE 는 가능, SUSPENDED·TERMINATED 는 불가,
     * TRIAL 은 체험 종료 시각 전까지만 가능(종료 시각이 지나면 SUSPENDED 처럼 거부). 종료 시각이 없는 TRIAL 은 DB CHECK 가
     * 막지만 들어오면 거부한다.
     */
    public boolean usableAt(LocalDateTime now) {
        return switch (status) {
            case ACTIVE -> true;
            case TRIAL -> trialEndsAt != null && now.isBefore(trialEndsAt);
            case SUSPENDED, TERMINATED -> false;
        };
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getTenantName() {
        return tenantName;
    }

    public String getSlug() {
        return slug;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDateTime getTrialEndsAt() {
        return trialEndsAt;
    }
}
