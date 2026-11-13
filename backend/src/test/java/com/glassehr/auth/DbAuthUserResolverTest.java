package com.glassehr.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.org.OrgDeptService;
import com.glassehr.org.OrgDtos.DeptNode;
import com.glassehr.org.OrgDtos.OrgTree;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DbAuthUserResolverTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AppRolePermissionRepository rolePerms = mock(AppRolePermissionRepository.class);
    private final OrgDeptService org = mock(OrgDeptService.class);
    private final DbAuthUserResolver resolver = new DbAuthUserResolver(users, rolePerms, org);
    private final Map<String, Set<Permission>> matrix = TestAuthUsers.v011Matrix();

    private static DeptNode node(long id, Long parent, Long head, DeptNode... children) {
        return new DeptNode(id, "D" + id, "부서" + id, parent, head, 0, TODAY, null, List.of(children));
    }

    /**
     * 1 (부서장 7)
     * ├─ 2 (부서장 8)
     * │   └─ 3
     * └─ 4
     * 5 (부서장 없음)
     */
    private static final OrgTree TREE = new OrgTree(TODAY, List.of(
            node(1, null, 7L, node(2, 1L, 8L, node(3, 2L, null)), node(4, 1L, null)),
            node(5, null, null)));

    @BeforeEach
    void setUp() {
        when(org.today()).thenReturn(TODAY);
        when(org.tree(TODAY)).thenReturn(TREE);
        when(rolePerms.findPermCodes(anyCollection())).thenAnswer(inv -> {
            Collection<String> roles = inv.getArgument(0);
            List<String> out = new ArrayList<>();
            for (String r : roles) {
                matrix.getOrDefault(r, Set.of()).forEach(p -> out.add(p.name()));
            }
            return out;
        });
    }

    @Test
    void 부서장_범위는_맡은_부서와_모든_하위부서() {
        assertThat(DeptScope.managedBy(TREE, 7)).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
        assertThat(DeptScope.managedBy(TREE, 8)).containsExactlyInAnyOrder(2L, 3L);
        assertThat(DeptScope.managedBy(TREE, 9)).isEmpty();
    }

    @Test
    void 조직도_부서장이면_DEPT_HEAD가_자동으로_붙는다() {
        when(users.findById("head")).thenReturn(Optional.of(new AppUser("head", 8L, true, Set.of())));
        AuthUser u = resolver.resolve("head", "10.0.0.1").orElseThrow();
        assertThat(u.roles()).containsExactlyInAnyOrder("DEPT_HEAD", "EMPLOYEE");
        assertThat(u.managedDeptIds()).containsExactlyInAnyOrder(2L, 3L);
        assertThat(u.permissions()).containsExactlyInAnyOrder(Permission.EMP_READ_DEPT, Permission.EMP_READ_SELF);
        assertThat(u.canSeeEmployee(99, 3L)).isTrue();
        assertThat(u.canSeeEmployee(99, 1L)).isFalse();
        assertThat(u.canSeeEmployee(99, null)).isFalse();
        assertThat(u.canSeePersonal(99)).isFalse();
        assertThat(u.clientIp()).isEqualTo("10.0.0.1");
    }

    @Test
    void DB에_DEPT_HEAD가_들어있어도_조직도_부서장이_아니면_무시한다() {
        when(users.findById("fake")).thenReturn(Optional.of(new AppUser("fake", 50L, true, Set.of("DEPT_HEAD"))));
        AuthUser u = resolver.resolve("fake", null).orElseThrow();
        assertThat(u.roles()).containsExactly("EMPLOYEE");
        assertThat(u.has(Permission.EMP_READ_DEPT)).isFalse();
        assertThat(u.managedDeptIds()).isEmpty();
    }

    @Test
    void 사원_연결이_없는_관리자_계정() {
        when(users.findById("admin")).thenReturn(Optional.of(new AppUser("admin", null, true, Set.of("ADMIN"))));
        AuthUser u = resolver.resolve(" admin ", null).orElseThrow();
        assertThat(u.roles()).containsExactlyInAnyOrder("ADMIN", "EMPLOYEE");
        assertThat(u.has(Permission.PRIVACY_LOG_READ)).isTrue();
        assertThat(u.has(Permission.RRN_REVEAL)).isFalse();
        assertThat(u.isSelf(1)).isFalse();
        verify(org, never()).tree(any());
    }

    @Test
    void 사용중지_없는_사용자_빈_ID는_빈값() {
        when(users.findById("off")).thenReturn(Optional.of(new AppUser("off", 3L, false, Set.of("HR"))));
        when(users.findById("none")).thenReturn(Optional.empty());
        assertThat(resolver.resolve("off", null)).isEmpty();
        assertThat(resolver.resolve("none", null)).isEmpty();
        assertThat(resolver.resolve(null, null)).isEmpty();
        assertThat(resolver.resolve(" ", null)).isEmpty();
    }

    @Test
    void 모르는_권한_코드는_주지_않는다() {
        when(users.findById("hr")).thenReturn(Optional.of(new AppUser("hr", null, true, Set.of("HR"))));
        when(rolePerms.findPermCodes(anyCollection())).thenReturn(List.of("EMP_READ_ALL", "SUPER_POWER"));
        AuthUser u = resolver.resolve("hr", null).orElseThrow();
        assertThat(u.permissions()).containsExactly(Permission.EMP_READ_ALL);
    }

    @Test
    void 여러_역할이면_권한은_합이고_요청자_역할은_원문권한_역할을_고른다() {
        when(users.findById("both")).thenReturn(Optional.of(new AppUser("both", null, true, Set.of("ADMIN", "HR"))));
        AuthUser u = resolver.resolve("both", "1.1.1.1").orElseThrow();
        assertThat(u.has(Permission.PRIVACY_LOG_READ)).isTrue();
        assertThat(u.has(Permission.RRN_REVEAL)).isTrue();
        assertThat(u.toActor().role()).isEqualTo(com.glassehr.employee.RequestActor.Role.HR);
        assertThat(u.toActor().actorId()).isEqualTo("both");
        assertThat(u.rolesText()).isEqualTo("ADMIN,EMPLOYEE,HR");
    }
}
