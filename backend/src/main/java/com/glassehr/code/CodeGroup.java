package com.glassehr.code;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 공통코드 그룹. 테이블: V006__common_code.sql. 그룹은 마이그레이션으로만 추가하므로 읽기 전용으로 쓴다. */
@Entity
@Table(name = "common_code_group")
public class CodeGroup {

    @Id
    @Column(name = "group_code", nullable = false, updatable = false)
    private String groupCode;

    @Column(name = "group_name", nullable = false)
    private String groupName;

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

    protected CodeGroup() {
    }

    /** 테스트용. 운영 데이터는 마이그레이션으로 넣는다. */
    CodeGroup(String groupCode, String groupName, String description, int sortOrder, boolean active) {
        this.groupCode = groupCode;
        this.groupName = groupName;
        this.description = description;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    public String getGroupCode() {
        return groupCode;
    }

    public String getGroupName() {
        return groupName;
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
}
