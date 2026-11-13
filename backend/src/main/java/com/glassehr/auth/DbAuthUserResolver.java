package com.glassehr.auth;

import com.glassehr.org.OrgDeptService;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB 기반 사용자 해석(V013).
 * <ul>
 *   <li>부여 역할은 app_user_role 에서 읽는다. 요청 헤더의 역할(X-Actor-Role)은 쓰지 않는다.</li>
 *   <li>모든 사용자에게 EMPLOYEE(본인 정보)를 붙인다.</li>
 *   <li>연결된 사원이 오늘(Asia/Seoul) 조직도에서 부서장이면 DEPT_HEAD 를 붙이고, 맡은 부서와 모든 하위부서를 범위로 둔다.</li>
 *   <li>권한은 역할 권한의 합(app_role_permission). 코드에 없는 권한 코드는 무시한다(모르는 권한을 주지 않음).</li>
 * </ul>
 * 개인정보 보호법 제29조·개인정보의 안전성 확보조치 기준: 접근 권한을 업무에 필요한 최소 범위로 차등 부여한다.
 */
@Component
public class DbAuthUserResolver implements AuthUserResolver {

    private final AppUserRepository users;
    private final AppRolePermissionRepository rolePerms;
    private final OrgDeptService org;

    public DbAuthUserResolver(AppUserRepository users, AppRolePermissionRepository rolePerms, OrgDeptService org) {
        this.users = users;
        this.rolePerms = rolePerms;
        this.org = org;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthUser> resolve(String loginId, String clientIp) {
        if (loginId == null || loginId.isBlank()) {
            return Optional.empty();
        }
        Optional<AppUser> found = users.findById(loginId.strip());
        if (found.isEmpty() || !found.get().isActive()) {
            return Optional.empty();
        }
        AppUser u = found.get();
        Set<String> roles = new HashSet<>(u.getRoles());
        // 자동 역할은 DB 에 넣을 수 없게 막았지만(ck_app_user_role_assignable) 한 번 더 걸러낸다.
        roles.remove(AuthUser.ROLE_DEPT_HEAD);
        roles.add(AuthUser.ROLE_EMPLOYEE);
        Set<Long> managed = Set.of();
        if (u.getEmpId() != null) {
            managed = DeptScope.managedBy(org.tree(org.today()), u.getEmpId());
            if (!managed.isEmpty()) {
                roles.add(AuthUser.ROLE_DEPT_HEAD);
            }
        }
        Set<Permission> perms = EnumSet.noneOf(Permission.class);
        for (String code : rolePerms.findPermCodes(roles)) {
            try {
                perms.add(Permission.valueOf(code));
            } catch (IllegalArgumentException ignored) {
                // 코드에 없는 권한은 주지 않는다.
            }
        }
        return Optional.of(new AuthUser(u.getLoginId(), u.getEmpId(), roles, perms, managed, clientIp));
    }
}
