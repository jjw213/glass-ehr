package com.glassehr.auth;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 역할별 권한 조회 전용. 쓰기는 마이그레이션으로만 한다. */
public interface AppRolePermissionRepository extends Repository<AppRolePermission, AppRolePermission.Id> {

    @Query("select p.id.permCode from AppRolePermission p where p.id.roleCode in :roles")
    List<String> findPermCodes(@Param("roles") Collection<String> roles);
}
