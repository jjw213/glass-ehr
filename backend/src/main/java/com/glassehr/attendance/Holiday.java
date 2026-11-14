package com.glassehr.attendance;

import com.glassehr.code.YnConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 휴일 달력의 한 날(#26). 테이블: V017__holiday_calendar.sql. 종류는 {@link HolidayKind}. 만든 뒤 바꾸지 않는다. */
@Entity
@Table(name = "holiday")
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "holiday_id", nullable = false, updatable = false)
    private Long holidayId;

    @Column(name = "holiday_date", nullable = false, updatable = false)
    private LocalDate holidayDate;

    @Column(name = "holiday_name", nullable = false, updatable = false)
    private String holidayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "holiday_kind", nullable = false, updatable = false)
    private HolidayKind kind;

    @Convert(converter = YnConverter.class)
    @Column(name = "paid_yn", nullable = false, updatable = false)
    private Boolean paid;

    @Column(name = "replaces_holiday_id", updatable = false)
    private Long replacesHolidayId;

    @Column(name = "agreement_id", updatable = false)
    private Long agreementId;

    @Column(name = "source_ref", updatable = false)
    private String sourceRef;

    @Column(name = "verified_date", updatable = false)
    private LocalDate verifiedDate;

    @Column(name = "note", updatable = false)
    private String note;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Holiday() {
    }

    private Holiday(LocalDate date, String name, HolidayKind kind, boolean paid, Long replacesHolidayId,
            Long agreementId, String sourceRef, LocalDate verifiedDate, String note, String createdBy,
            LocalDateTime now) {
        this.holidayDate = date;
        this.holidayName = name;
        this.kind = kind;
        this.paid = paid;
        this.replacesHolidayId = replacesHolidayId;
        this.agreementId = agreementId;
        this.sourceRef = sourceRef;
        this.verifiedDate = verifiedDate;
        this.note = note;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    /** 사람이 넣은 공휴일 자료의 한 행. 근로기준법 제55조 제2항 본문: 관공서 공휴일은 유급휴일. */
    public static Holiday statutory(LocalDate date, String name, HolidayKind kind, String sourceRef,
            LocalDate verifiedDate, String createdBy, LocalDateTime now) {
        if (!kind.isStatutory()) {
            throw new IllegalArgumentException("공휴일 자료에는 법정공휴일·대체공휴일만 넣을 수 있습니다: " + kind);
        }
        return new Holiday(date, name, kind, true, null, null, sourceRef, verifiedDate, null, createdBy, now);
    }

    /** 회사휴일(취업규칙·단체협약). 유급 여부는 회사가 정한다. */
    public static Holiday company(LocalDate date, String name, boolean paid, String note, String createdBy,
            LocalDateTime now) {
        return new Holiday(date, name, HolidayKind.COMPANY, paid, null, null, null, null, note, createdBy, now);
    }

    /** 근로기준법 제55조 제2항 단서: 서면합의로 공휴일을 특정 근로일로 대체. 대체된 날이 유급휴일이 된다. */
    public static Holiday replacement(LocalDate date, String name, long replacesHolidayId, long agreementId,
            String note, String createdBy, LocalDateTime now) {
        return new Holiday(date, name, HolidayKind.REPLACEMENT, true, replacesHolidayId, agreementId, null, null,
                note, createdBy, now);
    }

    public Long getHolidayId() { return holidayId; }
    public LocalDate getHolidayDate() { return holidayDate; }
    public String getHolidayName() { return holidayName; }
    public HolidayKind getKind() { return kind; }
    public boolean isPaid() { return Boolean.TRUE.equals(paid); }
    public Long getReplacesHolidayId() { return replacesHolidayId; }
    public Long getAgreementId() { return agreementId; }
    public String getSourceRef() { return sourceRef; }
    public LocalDate getVerifiedDate() { return verifiedDate; }
    public String getNote() { return note; }
    public String getCreatedBy() { return createdBy; }
}
