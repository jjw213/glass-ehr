package com.glassehr.code;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 휴가 종류 상세 설정(V038 attendance_leave_type, #143). 휴가 종류 코드(common_code LEAVE_TYPE)와 1:1이며
 * 코드를 지울 때 함께 지운다. 법정휴가·연차(기본 코드)의 일수·유급 기간은 이 테이블이 아니라 legal_rate 에서 읽는다.
 */
@Entity
@Table(name = "attendance_leave_type")
@IdClass(AttendanceLeaveType.Key.class)
public class AttendanceLeaveType {

    /** 복합키 (enter_cd, code). */
    public record Key(String enterCd, String code) implements Serializable {
    }

    @Id
    @Column(name = "enter_cd", nullable = false, updatable = false)
    private String enterCd;

    @Id
    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "group_code", nullable = false, updatable = false)
    private String groupCode = CodeGroups.LEAVE_TYPE;

    @Convert(converter = YnConverter.class)
    @Column(name = "unit_day_yn", nullable = false)
    private Boolean unitDay;

    @Convert(converter = YnConverter.class)
    @Column(name = "unit_half_day_yn", nullable = false)
    private Boolean unitHalfDay;

    @Convert(converter = YnConverter.class)
    @Column(name = "unit_hour_yn", nullable = false)
    private Boolean unitHour;

    /** null = 법정휴가(유급 기간은 legal_rate). */
    @Convert(converter = YnConverter.class)
    @Column(name = "paid_yn")
    private Boolean paid;

    @Convert(converter = YnConverter.class)
    @Column(name = "proof_required_yn", nullable = false)
    private Boolean proofRequired;

    /** null = 한도 없음. */
    @Column(name = "annual_limit_days")
    private BigDecimal annualLimitDays;

    /** F/M, null = 제한 없음. 법정휴가 중 해당 휴가만(기본 코드). */
    @Column(name = "gender_limit", updatable = false)
    private String genderLimit;

    @Column(name = "legal_basis", updatable = false)
    private String legalBasis;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected AttendanceLeaveType() {
    }

    AttendanceLeaveType(String enterCd, String code, LocalDateTime now) {
        this.enterCd = enterCd;
        this.code = code;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void change(boolean unitDay, boolean unitHalfDay, boolean unitHour, Boolean paid, boolean proofRequired,
            BigDecimal annualLimitDays, LocalDateTime now) {
        this.unitDay = unitDay;
        this.unitHalfDay = unitHalfDay;
        this.unitHour = unitHour;
        this.paid = paid;
        this.proofRequired = proofRequired;
        this.annualLimitDays = annualLimitDays;
        this.updatedAt = now;
    }

    /** 테스트·시드 비교용. 성별 제한·법정 근거는 마이그레이션으로만 정한다. */
    AttendanceLeaveType withLegal(String genderLimit, String legalBasis) {
        this.genderLimit = genderLimit;
        this.legalBasis = legalBasis;
        return this;
    }

    public String getCode() {
        return code;
    }

    public boolean isUnitDay() {
        return unitDay;
    }

    public boolean isUnitHalfDay() {
        return unitHalfDay;
    }

    public boolean isUnitHour() {
        return unitHour;
    }

    public Boolean getPaid() {
        return paid;
    }

    public boolean isProofRequired() {
        return proofRequired;
    }

    public BigDecimal getAnnualLimitDays() {
        return annualLimitDays;
    }

    public String getGenderLimit() {
        return genderLimit;
    }

    public String getLegalBasis() {
        return legalBasis;
    }
}
