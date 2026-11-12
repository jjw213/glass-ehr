package com.glassehr.org;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.org.OrgDtos.DeptChangeRequest;
import com.glassehr.org.OrgDtos.DeptCloseRequest;
import com.glassehr.org.OrgDtos.DeptCreateRequest;
import com.glassehr.org.OrgDtos.DeptNode;
import com.glassehr.org.OrgDtos.DeptView;
import com.glassehr.org.OrgDtos.OrgTree;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/** 조직 서비스 규칙 검증. 저장소는 메모리에 두는 가짜(조회 조건은 V008·저장소 쿼리와 같은 반개구간). */
class OrgDeptServiceTest {

    private static final LocalDate D0 = LocalDate.of(2026, 1, 1);

    private final Map<Long, OrgDept> deptStore = new LinkedHashMap<>();
    private final List<OrgDeptHist> histStore = new ArrayList<>();
    private long nextId = 1;

    private OrgDeptRepository depts;
    private OrgDeptHistRepository hists;
    private OrgWriteLock lock;
    private DeptMemberChecker members;
    private OrgDeptService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        depts = mock(OrgDeptRepository.class);
        hists = mock(OrgDeptHistRepository.class);
        lock = mock(OrgWriteLock.class);
        members = mock(DeptMemberChecker.class);

        when(depts.saveAndFlush(any())).thenAnswer(inv -> {
            OrgDept d = inv.getArgument(0);
            if (d.getDeptId() == null) {
                ReflectionTestUtils.setField(d, "deptId", nextId++);
            }
            deptStore.put(d.getDeptId(), d);
            return d;
        });
        when(depts.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(deptStore.get((Long) inv.getArgument(0))));
        when(depts.findAllById(any())).thenAnswer(inv -> {
            List<OrgDept> out = new ArrayList<>();
            ((Iterable<Long>) inv.getArgument(0)).forEach(id -> {
                if (deptStore.containsKey(id)) {
                    out.add(deptStore.get(id));
                }
            });
            return out;
        });
        when(depts.existsByDeptCode(anyString()))
                .thenAnswer(inv -> deptStore.values().stream().anyMatch(d -> d.getDeptCode().equals(inv.getArgument(0))));

        when(hists.saveAndFlush(any())).thenAnswer(inv -> {
            OrgDeptHist h = inv.getArgument(0);
            if (!histStore.contains(h)) {
                histStore.add(h);
            }
            return h;
        });
        when(hists.findEffectiveOn(any())).thenAnswer(inv -> {
            LocalDate d = inv.getArgument(0);
            return histStore.stream().filter(h -> h.isEffectiveOn(d)).toList();
        });
        when(hists.findEffectiveOnOrAfter(any())).thenAnswer(inv -> {
            LocalDate d = inv.getArgument(0);
            return histStore.stream().filter(h -> h.getValidTo() == null || h.getValidTo().isAfter(d)).toList();
        });
        when(hists.findFirstByDeptIdOrderByValidFromDesc(anyLong())).thenAnswer(inv -> histStore.stream()
                .filter(h -> h.getDeptId().equals(inv.getArgument(0)))
                .max(Comparator.comparing(OrgDeptHist::getValidFrom)));

        Clock clock = Clock.fixed(Instant.parse("2026-11-11T15:30:00Z"), ZoneId.of("UTC")); // 서울 2026-11-12 00:30
        service = new OrgDeptService(depts, hists, lock, members, clock);
    }

    // ---------------------------------------------------------------- 도우미

    private long create(String code, String name, Long parent, int sort, LocalDate from) {
        return service.create(new DeptCreateRequest(code, name, parent, null, sort, from, null)).deptId();
    }

    private DeptView change(long id, String name, Long parent, Long head, int sort, LocalDate d) {
        return service.change(id, new DeptChangeRequest(name, parent, head, sort, d, "개편"));
    }

    private DeptView move(long id, Long parent, LocalDate d) {
        OrgDeptHist cur = latest(id);
        return change(id, cur.getDeptName(), parent, cur.getHeadEmpId(), cur.getSortOrder(), d);
    }

    private OrgDeptHist latest(long id) {
        return hists.findFirstByDeptIdOrderByValidFromDesc(id).orElseThrow();
    }

    private static void expectStatus(Runnable r, HttpStatus status, Consumer<String> msg) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(OrgException.class, e -> {
            assertThat(e.status()).isEqualTo(status);
            msg.accept(e.getMessage());
        });
    }

    private static List<String> codes(List<DeptNode> nodes) {
        return nodes.stream().map(DeptNode::deptCode).toList();
    }

    private static DeptNode find(OrgTree t, String code) {
        return findIn(t.roots(), code);
    }

    private static DeptNode findIn(List<DeptNode> nodes, String code) {
        for (DeptNode n : nodes) {
            if (n.deptCode().equals(code)) {
                return n;
            }
            DeptNode c = findIn(n.children(), code);
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 트리 조회

    @Test
    void 기준일_트리는_계층으로_묶이고_형제는_정렬순서_부서코드_순이다() {
        long hq = create("HQ", "본사", null, 0, D0);
        create("D200", "영업부", hq, 20, D0);
        create("D100", "인사부", hq, 10, D0);
        long d300 = create("D300", "총무부", hq, 10, D0);
        create("D310", "구매팀", d300, 0, D0);
        create("LAB", "연구소", null, 0, D0);

        OrgTree t = service.tree(D0);

        assertThat(t.baseDate()).isEqualTo(D0);
        assertThat(codes(t.roots())).containsExactly("HQ", "LAB");
        assertThat(codes(t.roots().get(0).children())).containsExactly("D100", "D300", "D200");
        assertThat(codes(find(t, "D300").children())).containsExactly("D310");
        assertThat(find(t, "D310").parentDeptId()).isEqualTo(d300);
    }

    @Test
    void 신설일_당일부터_보이고_전날에는_없다() {
        create("HQ", "본사", null, 0, D0);
        assertThat(service.tree(D0.minusDays(1)).roots()).isEmpty();
        assertThat(codes(service.tree(D0).roots())).containsExactly("HQ");
    }

    @Test
    void 이름_변경_전날은_옛_이름_당일은_새_이름() {
        long id = create("D100", "인사부", null, 0, D0);
        LocalDate d = LocalDate.of(2026, 7, 1);

        DeptView v = change(id, "인사팀", null, null, 0, d);

        assertThat(v.changeType()).isEqualTo(DeptChangeType.RENAME);
        assertThat(v.validFrom()).isEqualTo(d);
        assertThat(v.validTo()).isNull();
        assertThat(service.tree(d.minusDays(1)).roots().get(0).name()).isEqualTo("인사부");
        assertThat(service.tree(d.minusDays(1)).roots().get(0).validTo()).isEqualTo(d);
        assertThat(service.tree(d).roots().get(0).name()).isEqualTo("인사팀");
        // 이력은 추가만: 행 2개, 기간이 맞닿음
        assertThat(histStore).hasSize(2);
        assertThat(histStore.get(0).getValidTo()).isEqualTo(histStore.get(1).getValidFrom());
    }

    @Test
    void 상위부서_이동_전후로_트리_위치가_바뀐다() {
        long a = create("A", "가", null, 0, D0);
        long b = create("B", "나", null, 1, D0);
        long c = create("C", "다", a, 0, D0);
        LocalDate d = LocalDate.of(2026, 3, 1);

        DeptView v = move(c, b, d);

        assertThat(v.changeType()).isEqualTo(DeptChangeType.MOVE);
        assertThat(find(service.tree(d.minusDays(1)), "C").parentDeptId()).isEqualTo(a);
        assertThat(codes(find(service.tree(d), "B").children())).containsExactly("C");
        assertThat(find(service.tree(d), "A").children()).isEmpty();
    }

    @Test
    void 변경유형은_상위부서_이름_그밖_순으로_정한다() {
        long a = create("A", "가", null, 0, D0);
        long b = create("B", "나", null, 0, D0);
        assertThat(change(b, "나2", a, 9L, 5, LocalDate.of(2026, 2, 1)).changeType()).isEqualTo(DeptChangeType.MOVE);
        assertThat(change(b, "나3", a, 8L, 5, LocalDate.of(2026, 3, 1)).changeType()).isEqualTo(DeptChangeType.RENAME);
        assertThat(change(b, "나3", a, 7L, 6, LocalDate.of(2026, 4, 1)).changeType()).isEqualTo(DeptChangeType.UPDATE);
        assertThat(find(service.tree(LocalDate.of(2026, 4, 1)), "B").headEmpId()).isEqualTo(7L);
    }

    // ---------------------------------------------------------------- 신설 검사

    @Test
    void 쓰기는_모두_조직_잠금을_건다() {
        long id = create("A", "가", null, 0, D0);
        change(id, "가2", null, null, 0, D0.plusDays(1));
        service.close(id, new DeptCloseRequest(D0.plusDays(2), null));
        verify(lock, times(3)).acquire();
    }

    @Test
    void 중복_부서코드는_409() {
        create("A", "가", null, 0, D0);
        expectStatus(() -> create("A", "나", null, 0, D0), HttpStatus.CONFLICT, m -> assertThat(m).contains("A"));
    }

    @Test
    void 상위부서_신설일_전으로는_하위를_만들_수_없다() {
        long p = create("P", "상위", null, 0, LocalDate.of(2026, 5, 1));
        expectStatus(() -> create("C", "하위", p, 0, LocalDate.of(2026, 4, 30)), HttpStatus.BAD_REQUEST,
                m -> assertThat(m).contains("2026-05-01"));
        assertThat(create("C", "하위", p, 0, LocalDate.of(2026, 5, 1))).isPositive();
    }

    @Test
    void 없는_상위부서는_400() {
        expectStatus(() -> create("C", "하위", 999L, 0, D0), HttpStatus.BAD_REQUEST, m -> assertThat(m).contains("999"));
    }

    @Test
    void 폐지되었거나_폐지_예정인_부서는_상위로_지정할_수_없다() {
        long p = create("P", "상위", null, 0, D0);
        service.close(p, new DeptCloseRequest(LocalDate.of(2026, 12, 31), null));
        long x = create("X", "다른", null, 0, D0);

        expectStatus(() -> create("C", "하위", p, 0, LocalDate.of(2026, 6, 1)), HttpStatus.BAD_REQUEST,
                m -> assertThat(m).contains("폐지"));
        expectStatus(() -> move(x, p, LocalDate.of(2026, 6, 1)), HttpStatus.BAD_REQUEST, m -> assertThat(m).contains("폐지"));
    }

    // ---------------------------------------------------------------- 변경 검사

    @Test
    void 자기_자신을_상위로_둘_수_없다() {
        long a = create("A", "가", null, 0, D0);
        expectStatus(() -> move(a, a, D0.plusDays(1)), HttpStatus.BAD_REQUEST, m -> assertThat(m).contains("자기 자신"));
        assertThat(histStore).hasSize(1);
    }

    @Test
    void 자기_하위로_옮기면_순환_참조로_거부한다() {
        long a = create("A", "가", null, 0, D0);
        long b = create("B", "나", a, 0, D0);
        long c = create("C", "다", b, 0, D0);

        expectStatus(() -> move(a, c, D0.plusDays(10)), HttpStatus.BAD_REQUEST, m -> assertThat(m).contains("순환"));
        expectStatus(() -> move(a, b, D0.plusDays(10)), HttpStatus.BAD_REQUEST, m -> assertThat(m).contains("순환"));
        // 아무 행도 닫히지 않았다
        assertThat(histStore).hasSize(3).allMatch(h -> h.getValidTo() == null);
    }

    @Test
    void 미래로_예약된_다른_부서_이동_때문에_생길_순환도_거부한다() {
        long b = create("B", "나", null, 0, D0);
        long a = create("A", "가", b, 0, D0);
        long c = create("C", "다", null, 0, D0);
        move(b, c, LocalDate.of(2026, 12, 1)); // 12/1부터 B는 C 아래

        // 11/15에 C를 A 아래로: 11/15엔 C→A→B 로 문제없지만, 12/1에 C→A→B→C 순환
        expectStatus(() -> move(c, a, LocalDate.of(2026, 11, 15)), HttpStatus.BAD_REQUEST,
                m -> assertThat(m).contains("순환").contains("2026-12-01"));
    }

    @Test
    void 하위였던_부서가_먼저_빠져나간_뒤라면_그_아래로_옮길_수_있다() {
        long a = create("A", "가", null, 0, D0);
        long b = create("B", "나", a, 0, D0);
        move(b, null, LocalDate.of(2026, 3, 1)); // 3/1부터 B는 최상위

        move(a, b, LocalDate.of(2026, 4, 1));

        assertThat(codes(find(service.tree(LocalDate.of(2026, 4, 1)), "B").children())).containsExactly("A");
        assertThat(codes(find(service.tree(LocalDate.of(2026, 2, 1)), "A").children())).containsExactly("B");
    }

    @Test
    void 적용일은_마지막_변경일보다_뒤여야_한다() {
        long a = create("A", "가", null, 0, D0);
        change(a, "가2", null, null, 0, LocalDate.of(2026, 6, 1));

        expectStatus(() -> change(a, "가3", null, null, 0, LocalDate.of(2026, 6, 1)), HttpStatus.CONFLICT,
                m -> assertThat(m).contains("2026-06-01"));
        expectStatus(() -> change(a, "가3", null, null, 0, LocalDate.of(2026, 3, 1)), HttpStatus.CONFLICT, m -> { });
        expectStatus(() -> service.close(a, new DeptCloseRequest(LocalDate.of(2026, 6, 1), null)), HttpStatus.CONFLICT, m -> { });
    }

    @Test
    void 바뀐_내용이_없으면_400() {
        long a = create("A", "가", null, 3, D0);
        expectStatus(() -> change(a, " 가 ", null, null, 3, D0.plusDays(1)), HttpStatus.BAD_REQUEST,
                m -> assertThat(m).contains("바뀐 내용"));
    }

    @Test
    void 없는_부서_변경과_폐지는_404() {
        expectStatus(() -> change(42, "가", null, null, 0, D0), HttpStatus.NOT_FOUND, m -> assertThat(m).contains("42"));
        expectStatus(() -> service.close(42, new DeptCloseRequest(D0, null)), HttpStatus.NOT_FOUND, m -> { });
    }

    // ---------------------------------------------------------------- 폐지

    @Test
    void 폐지일_전날까지_보이고_폐지일부터_없다() {
        long a = create("A", "가", null, 0, D0);
        LocalDate d = LocalDate.of(2026, 9, 1);

        DeptView v = service.close(a, new DeptCloseRequest(d, " 통폐합 "));

        assertThat(v.deptValidTo()).isEqualTo(d);
        assertThat(v.validTo()).isEqualTo(d);
        assertThat(deptStore.get(a).getCloseReason()).isEqualTo("통폐합");
        assertThat(codes(service.tree(d.minusDays(1)).roots())).containsExactly("A");
        assertThat(service.tree(d).roots()).isEmpty();
        verify(members).countMembersOnOrAfter(a, d);
    }

    @Test
    void 재직자가_남아_있으면_폐지를_거부하고_아무것도_바꾸지_않는다() {
        long a = create("A", "가", null, 0, D0);
        LocalDate d = LocalDate.of(2026, 9, 1);
        when(members.countMembersOnOrAfter(a, d)).thenReturn(3L);

        expectStatus(() -> service.close(a, new DeptCloseRequest(d, null)), HttpStatus.CONFLICT,
                m -> assertThat(m).contains("재직자").contains("3명"));
        assertThat(deptStore.get(a).getValidTo()).isNull();
        assertThat(latest(a).getValidTo()).isNull();
    }

    @Test
    void 하위부서가_남아_있으면_폐지를_거부한다() {
        long a = create("A", "가", null, 0, D0);
        create("B", "나", a, 0, D0);
        expectStatus(() -> service.close(a, new DeptCloseRequest(LocalDate.of(2026, 9, 1), null)), HttpStatus.CONFLICT,
                m -> assertThat(m).contains("하위 부서").contains("B"));
        assertThat(deptStore.get(a).getValidTo()).isNull();
    }

    @Test
    void 하위부서가_폐지일_전에_빠져나가면_폐지할_수_있지만_폐지일_뒤에_들어올_예약이_있으면_거부한다() {
        long a = create("A", "가", null, 0, D0);
        long b = create("B", "나", a, 0, D0);
        long c = create("C", "다", null, 0, D0);
        move(b, null, LocalDate.of(2026, 8, 1));
        move(c, a, LocalDate.of(2026, 10, 1)); // 폐지일(9/1) 뒤에 A 아래로 들어올 예약

        expectStatus(() -> service.close(a, new DeptCloseRequest(LocalDate.of(2026, 9, 1), null)), HttpStatus.CONFLICT,
                m -> assertThat(m).contains("C").doesNotContain("B"));

        move(c, null, LocalDate.of(2026, 10, 2));
        // 10/1~10/2 하루 동안 C가 A 아래이므로 여전히 거부
        expectStatus(() -> service.close(a, new DeptCloseRequest(LocalDate.of(2026, 9, 1), null)), HttpStatus.CONFLICT, m -> { });
        // 10/2부터는 하위가 없으므로 폐지 가능
        assertThat(service.close(a, new DeptCloseRequest(LocalDate.of(2026, 10, 2), null)).deptValidTo())
                .isEqualTo(LocalDate.of(2026, 10, 2));
    }

    @Test
    void 이미_폐지된_부서는_다시_폐지하거나_바꿀_수_없다() {
        long a = create("A", "가", null, 0, D0);
        service.close(a, new DeptCloseRequest(LocalDate.of(2026, 9, 1), null));
        expectStatus(() -> service.close(a, new DeptCloseRequest(LocalDate.of(2026, 10, 1), null)), HttpStatus.CONFLICT,
                m -> assertThat(m).contains("2026-09-01"));
        expectStatus(() -> change(a, "가2", null, null, 0, LocalDate.of(2026, 10, 1)), HttpStatus.CONFLICT, m -> { });
    }

    @Test
    void 기본_기준일은_서울_날짜다() {
        assertThat(service.today()).isEqualTo(LocalDate.of(2026, 11, 12));
    }

    @Test
    void 변경유형_판정() {
        OrgDeptHist.DeptAttrs base = new OrgDeptHist.DeptAttrs("가", 1L, null, 0);
        assertThat(DeptChangeType.of(base, new OrgDeptHist.DeptAttrs("가", 2L, null, 0))).isEqualTo(DeptChangeType.MOVE);
        assertThat(DeptChangeType.of(base, new OrgDeptHist.DeptAttrs("가", null, null, 0))).isEqualTo(DeptChangeType.MOVE);
        assertThat(DeptChangeType.of(base, new OrgDeptHist.DeptAttrs("나", 1L, null, 0))).isEqualTo(DeptChangeType.RENAME);
        assertThat(DeptChangeType.of(base, new OrgDeptHist.DeptAttrs("가", 1L, 5L, 0))).isEqualTo(DeptChangeType.UPDATE);
    }
}
