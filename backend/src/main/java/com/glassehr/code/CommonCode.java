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
 * 사원·발령 이력이 코드를 참조하므로 지우지 않고 사용 여부를 끈다. 코드값(PK)은 바꾸지 않는다.
 */
@Entity
@Table(name = "common_code")
@IdClass(CommonCodeId.class)
public class CommonCode {

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CommonCode() {
    }

    CommonCode(String groupCode, String code, String codeName, String description, int sortOrder,
            boolean active, LocalDateTime now) {
        this.groupCode = groupCode;
        this.code = code;
        this.codeName = codeName;
        this.description = description;
        this.sortOrder = sortOrder;
        this.active = active;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void change(String codeName, String description, int sortOrder, boolean active, LocalDateTime now) {
        this.codeName = codeName;
        this.description = description;
        this.sortOrder = sortOrder;
        this.active = active;
        this.updatedAt = now;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
