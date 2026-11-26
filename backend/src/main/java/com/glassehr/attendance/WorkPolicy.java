package com.glassehr.attendance;

import com.glassehr.attendance.WorkPolicyRules.AllowMode;
import com.glassehr.attendance.WorkPolicyRules.Allowance;
import com.glassehr.attendance.WorkPolicyRules.MissingOut;
import com.glassehr.attendance.WorkPolicyRules.Policy;
import com.glassehr.attendance.WorkPolicyRules.Rounding;
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
import java.time.LocalDateTime;

/**
 * 근무 정책(기록·인정 규칙, #159). 테이블: V045__work_policy.sql. patternId 가 null 이면 법인 기본 정책.
 * 값 검사는 {@link WorkPolicyRules}. 쓰인 정책은 지우지 않고 숨긴다(active=false).
 */
@Entity
@Table(name = "work_policy")
public class WorkPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "policy_id", nullable = false, updatable = false)
    private Long policyId;

    /** 법인 코드. PK 는 DB 에서 (enter_cd, policy_id). policy_id 는 전체에서 유일하므로 JPA 식별자는 policy_id. */
    @Column(name = "enter_cd", nullable = false, updatable = false)
    private String enterCd;

    @Column(name = "pattern_id", updatable = false)
    private Long patternId;

    @Column(name = "policy_name", nullable = false)
    private String policyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "ot_mode", nullable = false)
    private AllowMode otMode;
    @Column(name = "ot_day_limit_min")
    private Integer otDay;
    @Column(name = "ot_week_limit_min")
    private Integer otWeek;
    @Column(name = "ot_month_limit_min")
    private Integer otMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "night_mode", nullable = false)
    private AllowMode nightMode;
    @Column(name = "night_day_limit_min")
    private Integer nightDay;
    @Column(name = "night_week_limit_min")
    private Integer nightWeek;
    @Column(name = "night_month_limit_min")
    private Integer nightMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "hol_mode", nullable = false)
    private AllowMode holMode;
    @Column(name = "hol_day_limit_min")
    private Integer holDay;
    @Column(name = "hol_week_limit_min")
    private Integer holWeek;
    @Column(name = "hol_month_limit_min")
    private Integer holMonth;

    @Convert(converter = YnConverter.class)
    @Column(name = "extra_separate_yn", nullable = false)
    private Boolean separateStatutoryExtra;

    @Column(name = "unit_minutes", nullable = false)
    private Integer unitMinutes;

    @Column(name = "min_recognize_minutes", nullable = false)
    private Integer minRecognizeMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "rounding_mode", nullable = false)
    private Rounding rounding;

    @Column(name = "on_time_in_grace_min", nullable = false)
    private Integer onTimeInGrace;

    @Column(name = "on_time_out_grace_min", nullable = false)
    private Integer onTimeOutGrace;

    @Convert(converter = YnConverter.class)
    @Column(name = "early_in_yn", nullable = false)
    private Boolean earlyInAllowed;

    @Enumerated(EnumType.STRING)
    @Column(name = "missing_out_mode", nullable = false)
    private MissingOut missingOut;

    @Convert(converter = YnConverter.class)
    @Column(name = "realtime_yn", nullable = false)
    private Boolean realtimeReflect;

    @Convert(converter = YnConverter.class)
    @Column(name = "use_yn", nullable = false)
    private Boolean active;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected WorkPolicy() {
    }

    public WorkPolicy(String enterCd, Long patternId, String policyName, Policy policy, String actor,
            LocalDateTime now) {
        this.enterCd = enterCd;
        this.patternId = patternId;
        this.active = true;
        this.createdBy = actor;
        this.createdAt = now;
        apply(policyName, policy, actor, now);
    }

    void apply(String name, Policy p, String actor, LocalDateTime now) {
        this.policyName = name;
        this.otMode = p.overtime().mode();
        this.otDay = p.overtime().dayLimit();
        this.otWeek = p.overtime().weekLimit();
        this.otMonth = p.overtime().monthLimit();
        this.nightMode = p.night().mode();
        this.nightDay = p.night().dayLimit();
        this.nightWeek = p.night().weekLimit();
        this.nightMonth = p.night().monthLimit();
        this.holMode = p.holiday().mode();
        this.holDay = p.holiday().dayLimit();
        this.holWeek = p.holiday().weekLimit();
        this.holMonth = p.holiday().monthLimit();
        this.separateStatutoryExtra = p.separateStatutoryExtra();
        this.unitMinutes = p.unitMinutes();
        this.minRecognizeMinutes = p.minRecognizeMinutes();
        this.rounding = p.rounding();
        this.onTimeInGrace = p.onTimeInGraceMinutes();
        this.onTimeOutGrace = p.onTimeOutGraceMinutes();
        this.earlyInAllowed = p.earlyInAllowed();
        this.missingOut = p.missingOut();
        this.realtimeReflect = p.realtimeReflect();
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    void hide(String actor, LocalDateTime now) {
        this.active = false;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public Policy toPolicy() {
        return new Policy(new Allowance(otMode, otDay, otWeek, otMonth),
                new Allowance(nightMode, nightDay, nightWeek, nightMonth),
                new Allowance(holMode, holDay, holWeek, holMonth), separateStatutoryExtra, unitMinutes,
                minRecognizeMinutes, rounding, onTimeInGrace, onTimeOutGrace, earlyInAllowed, missingOut,
                realtimeReflect);
    }

    public Long getPolicyId() {
        return policyId;
    }

    public String getEnterCd() {
        return enterCd;
    }

    public Long getPatternId() {
        return patternId;
    }

    public String getPolicyName() {
        return policyName;
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
