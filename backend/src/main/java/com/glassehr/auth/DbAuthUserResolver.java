package com.glassehr.auth;

import com.glassehr.org.OrgDeptService;
import com.glassehr.tenant.EnterCompanyGate;
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
    private final EnterCompanyGate companies;

    public DbAuthUserResolver(AppUserRepository users, AppRolePermissionRepository rolePerms, OrgDeptService org,
            EnterCompanyGate companies) {
        this.users = users;
        this.rolePerms = rolePerms;
        this.org = org;
        this.companies = companies;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthUser> resolve(String enterCd, String loginId, String currentEnterCd, String clientIp) {
        if (enterCd == null || currentEnterCd == null || loginId == null || loginId.isBlank()) {
            return Optional.empty();
        }
        boolean switched = !enterCd.equals(currentEnterCd);
        // 법인이 사용 중지되거나 다법인 배포 잠금에 걸리면 남은 세션도 쓰지 못하게 한다(로그인과 같은 규칙).
        if (!companies.allows(enterCd)) {
            return Optional.empty();
        }
        // 고객사는 주체 법인 → 고객사로만 정한다(#151, docs/saas-design.md 2.3). 고객사가 정지·해지·체험 종료면
        // 남은 세션도 다음 요청에서 끊는다(2.2). 안내 문구를 보여 주도록 따로 알린다.
        String tenantId = companies.tenantOf(enterCd);
        if (!companies.tenantUsable(tenantId)) {
            throw new TenantUnavailableException();
        }
        Optional<AppUser> found = users.find(enterCd, loginId.strip());
        if (found.isEmpty() || !found.get().isActive()) {
            return Optional.empty();
        }
        AppUser u = found.get();
        Set<String> roles = new HashSet<>(u.getRoles());
        // 자동 역할은 DB 에 넣을 수 없게 막았지만(ck_app_user_role_assignable) 한 번 더 걸러낸다.
        roles.remove(AuthUser.ROLE_DEPT_HEAD);
        roles.add(AuthUser.ROLE_EMPLOYEE);
        Set<Long> managed = Set.of();
        // 다른 법인으로 전환했으면 부서장 범위는 비운다. 부서장·본인 판단은 주체 법인의 사원 기준이고
        // 그 사원은 현재 법인에 없다(docs/tenant-design.md 4.3).
        if (u.getEmpId() != null && !switched) {
            // 부서장 범위는 계정(주체) 법인의 오늘 조직도에서만 계산한다(#122, docs/tenant-design.md 4장).
            managed = DeptScope.managedBy(org.tree(u.getEnterCd(), org.today()), u.getEmpId());
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
        // 현재 법인이 주체 법인과 다르면 전환 권한(GROUP_ADMIN)이 있고 그 법인이 지금도 사용 가능해야 한다.
        // 전환 뒤 법인이 사용 중지되거나 역할을 회수하면 남은 세션도 쓰지 못하게 한다(401, 다시 로그인).
        // 전환한 법인은 같은 고객사여야 한다(saas-design.md 2.3). 법인이 다른 고객사로 옮겨지면 남은 세션도 끊는다.
        if (switched && (!perms.contains(Permission.ENTER_SWITCH) || !companies.allows(currentEnterCd)
                || !tenantId.equals(companies.tenantOf(currentEnterCd)))) {
            return Optional.empty();
        }
        return Optional.of(new AuthUser(tenantId, currentEnterCd, u.getEnterCd(), u.getLoginId(), u.getEmpId(), roles, perms,
                managed, clientIp));
    }
}
