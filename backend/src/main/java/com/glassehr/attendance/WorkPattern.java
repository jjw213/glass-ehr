package com.glassehr.attendance;

import com.glassehr.code.YnConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 근무유형(시업·종업, 휴게, 요일 구분, 1일·1주 소정근로시간). 테이블: V012__work_pattern.sql.
 * 시각은 자정부터의 분. 소정근로시간은 {@link WorkPatternRules}가 계산한 값을 저장한다.
 */
@Entity
@Table(name = "work_pattern")
public class WorkPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pattern_id", nullable = false, updatable = false)
    private Long patternId;

    @Column(name = "pattern_code", nullable = false, updatable = false)
    private String patternCode;

    @Column(name = "pattern_name", nullable = false)
    private String patternName;

    @Column(name = "work_start_min", nullable = false)
    private Integer workStartMin;

    @Column(name = "work_end_min", nullable = false)
    private Integer workEndMin;

    @Column(name = "break_start_min")
    private Integer breakStartMin;

    @Column(name = "break_minutes", nullable = false)
    private Integer breakMinutes;

    @Column(name = "day_types", nullable = false)
    private String dayTypes;

    @Column(name = "daily_contract_minutes", nullable = false)
    private Integer dailyContractMinutes;

    @Column(name = "weekly_contract_minutes", nullable = false)
    private Integer weeklyContractMinutes;

    @Convert(converter = YnConverter.class)
    @Column(name = "use_yn", nullable = false)
    private Boolean active;

    @Column(name = "note")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected WorkPattern() {
    }

    public WorkPattern(String patternCode, String patternName, WorkPatternRules.Spec spec,
            WorkPatternRules.Result computed, boolean active, String note, LocalDateTime now) {
        this.patternCode = patternCode;
        this.patternName = patternName;
        applySpec(spec, computed);
        this.active = active;
        this.note = note;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void applySpec(WorkPatternRules.Spec spec, WorkPatternRules.Result computed) {
        this.workStartMin = spec.workStartMin();
        this.workEndMin = spec.workEndMin();
        this.breakStartMin = spec.breakStartMin();
        this.breakMinutes = spec.breakMinutes();
        this.dayTypes = spec.dayTypes();
        this.dailyContractMinutes = computed.dailyContractMinutes();
        this.weeklyContractMinutes = computed.weeklyContractMinutes();
    }

    void rename(String name, boolean active, String note, LocalDateTime now) {
        this.patternName = name;
        this.active = active;
        this.note = note;
        this.updatedAt = now;
    }

    public WorkPatternRules.Spec spec() {
        return new WorkPatternRules.Spec(workStartMin, workEndMin, breakStartMin, breakMinutes, dayTypes);
    }

    public Long getPatternId() {
        return patternId;
    }

    public String getPatternCode() {
        return patternCode;
    }

    public String getPatternName() {
        return patternName;
    }

    public int getDailyContractMinutes() {
        return dailyContractMinutes;
    }

    public int getWeeklyContractMinutes() {
        return weeklyContractMinutes;
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }

    public String getNote() {
        return note;
    }
}
