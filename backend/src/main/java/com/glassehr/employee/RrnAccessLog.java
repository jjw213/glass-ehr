package com.glassehr.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/**
 * 주민등록번호 원문 조회 로그(추가만). 테이블: V009__employee.sql(rrn_access_log). docs/rrn-design.md 4장.
 * 원문·마스킹 값은 남기지 않는다(emp_id 만). 권한 없는 시도도 기록한다.
 */
@Entity
@Immutable
@Table(name = "rrn_access_log")
public class RrnAccessLog {

    /** 조회 목적(V009 ck_rrn_access_log_purpose 와 같아야 함). */
    public enum Purpose {
        /** 화면 원문 보기 */
        VIEW,
        /** 4대보험 신고 */
        SOCIAL_INSURANCE,
        /** 원천징수 */
        WITHHOLDING
    }

    /** 결과(V009 ck_rrn_access_log_result 와 같아야 함). */
    public enum Result {
        SUCCESS,
        DENIED,
        NOT_FOUND
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id", nullable = false, updatable = false)
    private Long logId;

    @Column(name = "accessor_id", nullable = false, updatable = false)
    private String accessorId;

    @Column(name = "accessor_role", updatable = false)
    private String accessorRole;

    @Column(name = "emp_id", nullable = false, updatable = false)
    private Long empId;

    @Column(name = "accessed_at", nullable = false, updatable = false)
    private LocalDateTime accessedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose_code", nullable = false, updatable = false)
    private Purpose purpose;

    @Column(name = "client_ip", updatable = false)
    private String clientIp;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, updatable = false)
    private Result result;

    protected RrnAccessLog() {
    }

    RrnAccessLog(RequestActor actor, long empId, Purpose purpose, Result result, LocalDateTime at) {
        this.accessorId = actor.actorId();
        this.accessorRole = actor.role() == null ? null : actor.role().name();
        this.empId = empId;
        this.purpose = purpose;
        this.clientIp = actor.clientIp();
        this.result = result;
        this.accessedAt = at;
    }

    public String getAccessorId() {
        return accessorId;
    }

    public String getAccessorRole() {
        return accessorRole;
    }

    public Long getEmpId() {
        return empId;
    }

    public LocalDateTime getAccessedAt() {
        return accessedAt;
    }

    public Purpose getPurpose() {
        return purpose;
    }

    public String getClientIp() {
        return clientIp;
    }

    public Result getResult() {
        return result;
    }
}
