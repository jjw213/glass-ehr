package com.glassehr.code;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 공통코드 그룹. 테이블: V006__common_code.sql.
 * V034(#121): 키는 (enter_cd, group_code). 법인마다 따로 둔다(docs/tenant-design.md 3.2).
 * V037(#140): 시스템 그룹(system_yn='Y')은 로직이 그룹코드를 참조하므로 삭제·사용 여부 변경을 막는다.
 * 사용자 그룹은 화면에서 추가·수정·삭제한다. 그룹코드(PK)는 바꾸지 않는다.
 */
@Entity
@Table(name = "common_code_group")
@IdClass(CodeGroup.Key.class)
public class CodeGroup {

    /** 복합키 (enter_cd, group_code). */
    public record Key(String enterCd, String groupCode) implements Serializable {
    }

    @Id
    @Column(name = "enter_cd", nullable = false, updatable = false)
    private String enterCd;

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

    @Convert(converter = YnConverter.class)
    @Column(name = "system_yn", nullable = false, updatable = false)
    private Boolean system;

    /** 다른 모듈 화면에서 편집하는 그룹의 화면 키. null 이면 공통코드 화면에서 편집. 마이그레이션으로만 정한다. */
    @Column(name = "edit_page", updatable = false)
    private String editPage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CodeGroup() {
    }

    CodeGroup(String enterCd, String groupCode, String groupName, String description, int sortOrder, boolean active,
            boolean system, LocalDateTime now) {
        this.enterCd = enterCd;
        this.groupCode = groupCode;
        this.groupName = groupName;
        this.description = description;
        this.sortOrder = sortOrder;
        this.active = active;
        this.system = system;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void change(String groupName, String description, int sortOrder, boolean active, LocalDateTime now) {
        this.groupName = groupName;
        this.description = description;
        this.sortOrder = sortOrder;
        this.active = active;
        this.updatedAt = now;
    }

    /** 테스트용. 다른 화면 편집 그룹은 마이그레이션으로만 정한다. */
    CodeGroup withEditPage(String editPage) {
        this.editPage = editPage;
        return this;
    }

    public String getEnterCd() {
        return enterCd;
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

    public boolean isSystem() {
        return system;
    }

    public String getEditPage() {
        return editPage;
    }
}
