package com.glassehr.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import org.hibernate.annotations.Immutable;

/** 역할별 권한(읽기 전용). 테이블: V011 app_role_permission. 바꾸려면 마이그레이션을 추가한다. */
@Entity
@Immutable
@Table(name = "app_role_permission")
public class AppRolePermission {

    @Embeddable
    public record Id(@Column(name = "role_code", nullable = false) String roleCode,
            @Column(name = "perm_code", nullable = false) String permCode) implements Serializable {
    }

    @EmbeddedId
    private Id id;

    protected AppRolePermission() {
    }

    AppRolePermission(String roleCode, String permCode) {
        this.id = new Id(roleCode, permCode);
    }

    public String getRoleCode() {
        return id.roleCode();
    }

    public String getPermCode() {
        return id.permCode();
    }
}
