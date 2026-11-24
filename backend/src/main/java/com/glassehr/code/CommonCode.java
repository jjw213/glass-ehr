package com.glassehr.code;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 공통코드. 테이블: V006__common_code.sql.
 * 어떤 테이블도 참조하지 않는 코드만 지운다(참조 목록 {@link CodeReferences}). 참조 중이거나 기본 코드면 사용 여부를 끈다.
 * 코드값(PK)은 바꾸지 않는다. V037(#140): 기본 코드(system_yn='Y')는 로직이 코드값을 직접 참조하며 마이그레이션으로만 정한다.
 * V034(#121): 키는 (enter_cd, group_code, code). 법인마다 따로 둔다(docs/tenant-design.md 3.2).
 */
@Entity
@Table(name = "common_code")
@IdClass(CommonCodeId.class)
public class CommonCode {

    @Id
    @Column(name = "enter_cd", nullable = false, updatable = false)
    private String enterCd;

    @Id
    @Column(name = "group_code", nullable = false, updatable = false)
    private String groupCode;

    @Id
    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "code_name", nullable = false)
    private String codeName;

    @Column(name = "description")
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Convert(converter = YnConverter.class)
    @Column(name = "use_yn", nullable = false)
    private Boolean active;

    @Convert(converter = YnConverter.class)
    @Column(name = "system_yn", nullable = false, updatable = false)
    private Boolean system = Boolean.FALSE;

    /** 근태코드 그룹(LEAVE_TYPE·ATTENDANCE_TYPE)의 처리방식(V038, #143). 다른 그룹은 null. */
    @Column(name = "process_type")
    private String processType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CommonCode() {
    }

    CommonCode(String enterCd, String groupCode, String code, String codeName, String description, int sortOrder,
            boolean active, LocalDateTime now) {
        this.enterCd = enterCd;
        this.groupCode = groupCode;
        this.code = code;
        this.codeName = codeName;
        this.description = description;
        this.sortOrder = sortOrder;
        this.active = active;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 근태코드 추가(#143). 처리방식은 {@link AttendanceCodeService#PROCESS_TYPES} 안의 값이다. */
    CommonCode(String enterCd, String groupCode, String code, String codeName, String description, int sortOrder,
            boolean active, String processType, LocalDateTime now) {
        this(enterCd, groupCode, code, codeName, description, sortOrder, active, now);
        this.processType = processType;
    }

    void changeProcessType(String processType) {
        this.processType = processType;
    }

    void change(String codeName, String description, int sortOrder, boolean active, LocalDateTime now) {
        this.codeName = codeName;
        this.description = description;
        this.sortOrder = sortOrder;
        this.active = active;
        this.updatedAt = now;
    }

    public String getEnterCd() {
        return enterCd;
    }

    public String getGroupCode() {
        return groupCode;
    }

    public String getCode() {
        return code;
    }

    public String getCodeName() {
        return codeName;
    }

    public String getDescription() {
        return description;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public String getProcessType() {
        return processType;
    }

    public boolean isSystem() {
        return system;
    }

    /** 테스트용. 기본 코드는 마이그레이션으로만 정한다. */
    CommonCode markSystem() {
        this.system = Boolean.TRUE;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
