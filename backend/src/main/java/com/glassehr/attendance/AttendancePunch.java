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
 * 출퇴근 원시 기록(#28, docs/attendance-punch-design.md). 테이블: V019__attendance_punch.sql.
 * 근로기준법 제48조(임금대장)·시행령 제27조의 근로시간 기재의 원자료가 되므로 추가만 한다.
 * 모든 칼럼이 {@code updatable = false} 이고 setter 가 없다. 정정은 별도 정정 행으로 한다(별도 이슈).
 * 시각은 서버 시각(Asia/Seoul, 시간대 없는 지역 시각)이다.
 */
@Entity
@Table(name = "attendance_punch")
public class AttendancePunch {

    /** 출근·퇴근. V019 ck_punch_type 과 같다. */
    public enum Type { IN, OUT }

    /** 기록 당시 판정(막지 않고 저장, 관리자 확인 대상). V019 ck_punch_check 와 같다. null 은 정상. */
    public enum Check { DUPLICATE_IN, OUT_WITHOUT_IN }

    /** 기록 경로. V019 ck_punch_source 와 같다. 관리자 대리 입력은 정정 이슈에서 추가한다. */
    public enum Source { WEB }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "punch_id", nullable = false, updatable = false)
    private Long punchId;

    @Column(name = "emp_id", nullable = false, updatable = false)
    private Long empId;

    @Enumerated(EnumType.STRING)
    @Column(name = "punch_type", nullable = false, updatable = false)
    private Type type;

    @Column(name = "punched_at", nullable = false, updatable = false)
    private LocalDateTime punchedAt;

    @Column(name = "work_date", nullable = false, updatable = false)
    private LocalDate workDate;

    @Column(name = "client_time", updatable = false)
    private LocalDateTime clientTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "punch_check", updatable = false)
    private Check check;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false)
    private Source source;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AttendancePunch() {
    }

    public AttendancePunch(long empId, Type type, LocalDateTime serverNow, LocalDateTime clientTime, Check check,
            Source source, String createdBy) {
        this.empId = empId;
        this.type = type;
        this.punchedAt = serverNow;
        this.workDate = serverNow.toLocalDate();
        this.clientTime = clientTime;
        this.check = check;
        this.source = source;
        this.createdBy = createdBy;
        this.createdAt = serverNow;
    }

    public Long getPunchId() { return punchId; }
    public Long getEmpId() { return empId; }
    public Type getType() { return type; }
    public LocalDateTime getPunchedAt() { return punchedAt; }
    public LocalDate getWorkDate() { return workDate; }
    public LocalDateTime getClientTime() { return clientTime; }
    public Check getCheck() { return check; }
    public Source getSource() { return source; }
    public String getCreatedBy() { return createdBy; }
}
