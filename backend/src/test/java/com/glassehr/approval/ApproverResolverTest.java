package com.glassehr.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApproverResolverTest {

    static final LocalDate D = LocalDate.of(2026, 11, 19);

    /** 부서 10(부서장 7) ⊂ 상위 1(부서장 2). 사원 5는 부서 10. 인사 역할 100·101, 직책 T1 은 50. */
    static class FakeDirectory implements ApproverResolver.Directory {
        final Map<Long, Long> deptOf = new HashMap<>(Map.of(5L, 10L, 7L, 10L, 2L, 1L));
        final Map<Long, ApproverResolver.DeptInfo> depts = new HashMap<>(Map.of(
                10L, new ApproverResolver.DeptInfo(7L, 1L),
                1L, new ApproverResolver.DeptInfo(2L, null)));
        final Map<String, Set<Long>> roles = new HashMap<>(Map.of("HR", Set.of(100L, 101L)));
        final Map<String, Set<Long>> titles = new HashMap<>(Map.of("T1", Set.of(50L)));
        final Set<Long> inactive = new HashSet<>();
        LocalDate seenDate;

        @Override
        public Optional<Long> deptOf(long empId, LocalDate date) {
            seenDate = date;
            return Optional.ofNullable(deptOf.get(empId));
        }

        @Override
        public Optional<String> enterOf(long empId) {
            return Optional.of("C001");
        }

        @Override
        public Optional<ApproverResolver.DeptInfo> dept(String enterCd, long deptId, LocalDate date) {
            return Optional.ofNullable(depts.get(deptId));
        }

        @Override
        public Set<Long> activeWithTitle(String titleCode) {
            return titles.getOrDefault(titleCode, Set.of());
        }

        @Override
        public Set<Long> activeWithRole(String roleCode) {
            return roles.getOrDefault(roleCode, Set.of());
        }

        @Override
        public boolean isActive(long empId, LocalDate date) {
            return !inactive.contains(empId);
        }
    }

    final FakeDirectory dir = new FakeDirectory();
    final ApproverResolver resolver = new ApproverResolver(dir);

    static ApprovalTemplate.Step step(int no, ApproverKind kind, String ref, boolean skip) {
        return new ApprovalTemplate.Step(no, kind, ref, skip);
    }

    @Test
    void 지정_방식별로_신청일_기준_결재자를_찾는다() {
        List<ApproverResolver.ResolvedStep> r = resolver.resolve(List.of(
                step(1, ApproverKind.DEPT_HEAD, null, false),
                step(2, ApproverKind.UPPER_DEPT_HEAD, null, false),
                step(3, ApproverKind.POSITION, "T1", false),
                step(4, ApproverKind.ROLE, "HR", false),
                step(5, ApproverKind.EMPLOYEE, "42", false)), 5L, D);
        assertThat(r).extracting(ApproverResolver.ResolvedStep::approverEmpIds)
                .containsExactly(Set.of(7L), Set.of(2L), Set.of(50L), Set.of(100L, 101L), Set.of(42L));
        assertThat(r).noneMatch(ApproverResolver.ResolvedStep::skipped);
        assertThat(dir.seenDate).isEqualTo(D);
    }

    @Test
    void 신청자가_부서장이면_skip_if_self_단계는_건너뛴다() {
        List<ApproverResolver.ResolvedStep> r = resolver.resolve(List.of(
                step(1, ApproverKind.DEPT_HEAD, null, true),
                step(2, ApproverKind.UPPER_DEPT_HEAD, null, false)), 7L, D);
        assertThat(r.get(0).skipped()).isTrue();
        assertThat(r.get(0).approverEmpIds()).isEmpty();
        assertThat(r.get(1).approverEmpIds()).containsExactly(2L);
    }

    @Test
    void 신청자가_유일한_결재자이고_건너뛰기가_아니면_거부한다() {
        assertThatThrownBy(() -> resolver.resolve(List.of(step(1, ApproverKind.DEPT_HEAD, null, false)), 7L, D))
                .isInstanceOf(ApprovalException.class).hasMessageContaining("본인뿐");
    }

    @Test
    void 역할_후보에서_신청자_본인은_빠진다() {
        List<ApproverResolver.ResolvedStep> r = resolver.resolve(
                List.of(step(1, ApproverKind.ROLE, "HR", true)), 100L, D);
        assertThat(r.get(0).approverEmpIds()).containsExactly(101L);
        assertThat(r.get(0).skipped()).isFalse();
    }

    @Test
    void 부서장_공석이면_신청을_거부한다() {
        dir.depts.put(10L, new ApproverResolver.DeptInfo(null, 1L));
        assertThatThrownBy(() -> resolver.resolve(List.of(step(1, ApproverKind.DEPT_HEAD, null, false)), 5L, D))
                .isInstanceOf(ApprovalException.class).hasMessageContaining("1단계 결재자(부서장)를 찾지 못해");
    }

    @Test
    void 최상위_부서라_상위_부서장이_없으면_거부한다() {
        assertThatThrownBy(() -> resolver.resolve(
                List.of(step(1, ApproverKind.UPPER_DEPT_HEAD, null, false)), 2L, D))
                .isInstanceOf(ApprovalException.class).hasMessageContaining("상위 부서장");
    }

    @Test
    void 휴직_퇴직자는_결재자가_될_수_없다() {
        dir.inactive.add(7L);
        assertThatThrownBy(() -> resolver.resolve(List.of(step(1, ApproverKind.DEPT_HEAD, null, false)), 5L, D))
                .isInstanceOf(ApprovalException.class);
        dir.inactive.add(100L);
        assertThat(resolver.resolve(List.of(step(1, ApproverKind.ROLE, "HR", false)), 5L, D).get(0)
                .approverEmpIds()).containsExactly(101L);
    }

    @Test
    void 역할_보유자가_없으면_거부한다() {
        assertThatThrownBy(() -> resolver.resolve(List.of(step(1, ApproverKind.ROLE, "PAYROLL", false)), 5L, D))
                .isInstanceOf(ApprovalException.class).hasMessageContaining("역할 PAYROLL");
    }
}
