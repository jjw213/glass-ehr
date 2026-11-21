package com.glassehr.attendance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 유연근무제 날별 근로계획. 테이블: V029__flex_work_assign.sql.
 * 탄력적은 사용자가 정한 근로일별 근로시간, 선택적은 사원이 입력한 시업·종업. 근로 없는 날은 시각 null·분 0.
 */
@Entity
@Table(name = "flex_work_plan")
@IdClass(FlexWorkPlan.Key.class)
public class FlexWorkPlan {

    @Id
    @Column(name = "flex_id", nullable = false, updatable = false)
    private Long flexId;

    @Id
    @Column(name = "work_date", nullable = false, updatable = false)
    private LocalDate workDate;

    @Column(name = "plan_start")
    private LocalDateTime planStart;

    @Column(name = "plan_end")
    private LocalDateTime planEnd;

    @Column(name = "plan_minutes", nullable = false)
    private Integer planMinutes;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected FlexWorkPlan() {
    }

    FlexWorkPlan(long flexId, LocalDate workDate, LocalDateTime planStart, LocalDateTime planEnd, int planMinutes,
            String updatedBy, LocalDateTime now) {
        this.flexId = flexId;
        this.workDate = workDate;
        this.planStart = planStart;
        this.planEnd = planEnd;
        this.planMinutes = planMinutes;
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

    public Long getFlexId() {
        return flexId;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public LocalDateTime getPlanStart() {
        return planStart;
    }

    public LocalDateTime getPlanEnd() {
        return planEnd;
    }

    public int getPlanMinutes() {
        return planMinutes;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    /** 복합키 (flex_id, work_date). */
    public static class Key implements Serializable {
        private Long flexId;
        private LocalDate workDate;

        protected Key() {
        }

        public Key(Long flexId, LocalDate workDate) {
            this.flexId = flexId;
            this.workDate = workDate;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(flexId, k.flexId) && Objects.equals(workDate, k.workDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(flexId, workDate);
        }
    }
}
