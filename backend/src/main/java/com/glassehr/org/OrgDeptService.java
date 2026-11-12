package com.glassehr.org;

import com.glassehr.org.OrgDeptHist.DeptAttrs;
import com.glassehr.org.OrgDtos.DeptChangeRequest;
import com.glassehr.org.OrgDtos.DeptCloseRequest;
import com.glassehr.org.OrgDtos.DeptCreateRequest;
import com.glassehr.org.OrgDtos.DeptNode;
import com.glassehr.org.OrgDtos.DeptView;
import com.glassehr.org.OrgDtos.OrgTree;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조직(부서) 신설·변경·폐지와 기준일 조직 트리 조회. 이력 규칙은 docs/org-history-design.md(#8 결정).
 *
 * <ul>
 *   <li>기간은 [valid_from, valid_to) 반개구간, 날짜는 Asia/Seoul 기준.</li>
 *   <li>이력은 추가로만 쌓는다. 변경·폐지는 부서의 마지막 이력 행보다 뒤 날짜에만 할 수 있다
 *       (중간 끼워 넣기·같은 날 정정은 별도 정정 기능에서 다룬다).</li>
 *   <li>상위부서는 적용일에 존속 중이고 폐지일이 정해지지 않은 부서여야 한다. 새 이력 행은 종료일이 없으므로
 *       상위부서도 그 뒤로 계속 유효해야 하기 때문이다.</li>
 *   <li>순환 참조 검사는 적용일과 그 뒤의 모든 변경 시점(미래로 예약된 다른 부서의 이동 포함)에서 한다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class OrgDeptService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static final Comparator<DeptNode> NODE_ORDER =
            Comparator.comparingInt(DeptNode::sortOrder).thenComparing(DeptNode::deptCode);

    private final OrgDeptRepository depts;
    private final OrgDeptHistRepository hists;
    private final OrgWriteLock lock;
    private final DeptMemberChecker members;
    private final Clock clock;

    @Autowired
    public OrgDeptService(OrgDeptRepository depts, OrgDeptHistRepository hists, OrgWriteLock lock,
            DeptMemberChecker members) {
        this(depts, hists, lock, members, Clock.system(SEOUL));
    }

    OrgDeptService(OrgDeptRepository depts, OrgDeptHistRepository hists, OrgWriteLock lock,
            DeptMemberChecker members, Clock clock) {
        this.depts = depts;
        this.hists = hists;
        this.lock = lock;
        this.members = members;
        this.clock = clock.withZone(SEOUL);
    }

    /** 서울 기준 오늘. 트리 조회 기준일 기본값. */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    // ---------------------------------------------------------------- 조회

    /** 기준일 {@code baseDate}의 조직 트리. 형제는 정렬순서, 부서코드 순. */
    public OrgTree tree(LocalDate baseDate) {
        List<OrgDeptHist> rows = hists.findEffectiveOn(baseDate);
        Map<Long, OrgDept> deptById = loadDepts(rows.stream().map(OrgDeptHist::getDeptId).collect(Collectors.toSet()));
        Map<Long, OrgDeptHist> rowById = rows.stream()
                .collect(Collectors.toMap(OrgDeptHist::getDeptId, Function.identity(), (a, b) -> {
                    // 같은 부서의 기간 겹침은 쓰기에서 막는다. 여기 오면 데이터가 깨진 것이다.
                    throw new IllegalStateException("부서 이력 기간이 겹칩니다: dept_id=" + a.getDeptId());
                }));
        Map<Long, List<OrgDeptHist>> byParent = new HashMap<>();
        List<OrgDeptHist> roots = new ArrayList<>();
        for (OrgDeptHist h : rows) {
            Long p = h.getParentDeptId();
            if (p == null || !rowById.containsKey(p)) {
                roots.add(h);
            } else {
                byParent.computeIfAbsent(p, k -> new ArrayList<>()).add(h);
            }
        }
        Set<Long> visited = new HashSet<>();
        List<DeptNode> rootNodes = roots.stream().map(h -> node(h, deptById, byParent, visited)).sorted(NODE_ORDER).toList();
        return new OrgTree(baseDate, rootNodes);
    }

    private DeptNode node(OrgDeptHist h, Map<Long, OrgDept> deptById, Map<Long, List<OrgDeptHist>> byParent,
            Set<Long> visited) {
        if (!visited.add(h.getDeptId())) {
            throw new IllegalStateException("조직에 순환 참조가 있습니다: dept_id=" + h.getDeptId());
        }
        List<DeptNode> children = byParent.getOrDefault(h.getDeptId(), List.of()).stream()
                .map(c -> node(c, deptById, byParent, visited))
                .sorted(NODE_ORDER)
                .toList();
        OrgDept d = deptById.get(h.getDeptId());
        return new DeptNode(h.getDeptId(), d.getDeptCode(), h.getDeptName(), h.getParentDeptId(), h.getHeadEmpId(),
                h.getSortOrder(), h.getValidFrom(), h.getValidTo(), children);
    }

    // ---------------------------------------------------------------- 신설

    @Transactional
    public DeptView create(DeptCreateRequest req) {
        lock.acquire();
        if (depts.existsByDeptCode(req.deptCode())) {
            throw OrgException.conflict("이미 있는 부서코드예요: " + req.deptCode());
        }
        LocalDate d = req.effectiveDate();
        if (req.parentDeptId() != null) {
            requireUsableParent(req.parentDeptId(), d);
        }
        LocalDateTime now = now();
        OrgDept dept = depts.saveAndFlush(new OrgDept(req.deptCode(), d, now));
        DeptAttrs attrs = new DeptAttrs(req.name().strip(), req.parentDeptId(), req.headEmpId(), req.sortOrder());
        OrgDeptHist h = hists.saveAndFlush(
                new OrgDeptHist(dept.getDeptId(), d, attrs, DeptChangeType.CREATE, blankToNull(req.reason()), now));
        return DeptView.of(dept, h);
    }

    // ---------------------------------------------------------------- 변경

    /** 이름·상위부서·부서장·정렬순서 변경. 마지막 이력 행을 적용일로 닫고 새 행을 추가한다. */
    @Transactional
    public DeptView change(long deptId, DeptChangeRequest req) {
        lock.acquire();
        OrgDept dept = depts.findById(deptId).orElseThrow(() -> OrgException.deptNotFound(deptId));
        if (!dept.isOpenEnded()) {
            throw OrgException.conflict("폐지되었거나 폐지가 예정된 부서는 바꿀 수 없어요(폐지일 " + dept.getValidTo() + ")");
        }
        LocalDate d = req.effectiveDate();
        OrgDeptHist latest = latestRow(deptId);
        requireAfterLatest(latest, d);

        DeptAttrs before = latest.attrs();
        DeptAttrs after = new DeptAttrs(req.name().strip(), req.parentDeptId(), req.headEmpId(), req.sortOrder());
        if (before.equals(after)) {
            throw OrgException.badRequest("바뀐 내용이 없어요");
        }
        if (!before.sameParent(after) && after.parentDeptId() != null) {
            if (after.parentDeptId() == deptId) {
                throw OrgException.badRequest("자기 자신을 상위부서로 둘 수 없어요");
            }
            requireUsableParent(after.parentDeptId(), d);
            requireNoCycle(deptId, after.parentDeptId(), d);
        }

        LocalDateTime now = now();
        latest.closeAt(d);
        hists.saveAndFlush(latest);
        OrgDeptHist h = hists.saveAndFlush(
                new OrgDeptHist(deptId, d, after, DeptChangeType.of(before, after), blankToNull(req.reason()), now));
        return DeptView.of(dept, h);
    }

    // ---------------------------------------------------------------- 폐지

    /**
     * 폐지. 폐지일부터 조직도에 나오지 않는다.
     * 폐지일 당일이나 그 뒤에 하위부서가 있거나 재직자가 소속되어 있으면 거부한다(먼저 옮긴다).
     */
    @Transactional
    public DeptView close(long deptId, DeptCloseRequest req) {
        lock.acquire();
        OrgDept dept = depts.findById(deptId).orElseThrow(() -> OrgException.deptNotFound(deptId));
        if (!dept.isOpenEnded()) {
            throw OrgException.conflict("이미 폐지되었거나 폐지가 예정된 부서예요(폐지일 " + dept.getValidTo() + ")");
        }
        LocalDate d = req.effectiveDate();
        OrgDeptHist latest = latestRow(deptId);
        requireAfterLatest(latest, d);

        List<Long> childIds = hists.findEffectiveOnOrAfter(d).stream()
                .filter(h -> !h.getDeptId().equals(deptId) && Long.valueOf(deptId).equals(h.getParentDeptId()))
                .map(OrgDeptHist::getDeptId)
                .distinct()
                .toList();
        if (!childIds.isEmpty()) {
            String codes = loadDepts(childIds).values().stream().map(OrgDept::getDeptCode).sorted()
                    .collect(Collectors.joining(", "));
            throw OrgException.conflict("폐지일 이후에도 하위 부서가 있어요: " + codes + ". 하위 부서를 먼저 옮기거나 폐지해 주세요");
        }
        long count = members.countMembersOnOrAfter(deptId, d);
        if (count > 0) {
            throw OrgException.conflict("폐지일 이후에도 소속 재직자가 " + count + "명 있어요. 인사발령으로 먼저 옮겨 주세요");
        }

        LocalDateTime now = now();
        dept.close(d, blankToNull(req.reason()), now);
        depts.saveAndFlush(dept);
        latest.closeAt(d);
        hists.saveAndFlush(latest);
        return DeptView.of(dept, latest);
    }

    // ---------------------------------------------------------------- 검사

    private OrgDeptHist latestRow(long deptId) {
        return hists.findFirstByDeptIdOrderByValidFromDesc(deptId)
                .orElseThrow(() -> new IllegalStateException("부서 이력이 없습니다: dept_id=" + deptId));
    }

    /** 추가만 하므로 적용일은 마지막 행 시작일보다 뒤여야 한다(같은 날이면 0일짜리 행이 생긴다). */
    private static void requireAfterLatest(OrgDeptHist latest, LocalDate d) {
        if (!d.isAfter(latest.getValidFrom())) {
            throw OrgException.conflict("적용일은 이 부서의 마지막 변경일(" + latest.getValidFrom() + ")보다 뒤여야 해요");
        }
    }

    /** 상위부서는 적용일에 이미 신설되어 있고, 폐지일이 정해지지 않은 부서여야 한다. */
    private void requireUsableParent(long parentId, LocalDate d) {
        OrgDept p = depts.findById(parentId)
                .orElseThrow(() -> OrgException.badRequest("상위부서가 없어요: " + parentId));
        if (p.getValidFrom().isAfter(d)) {
            throw OrgException.badRequest("상위부서 " + p.getDeptCode() + "는 " + p.getValidFrom() + "에 신설되어 그 전 날짜로는 지정할 수 없어요");
        }
        if (!p.isOpenEnded()) {
            throw OrgException.badRequest("상위부서 " + p.getDeptCode() + "는 폐지되었거나 폐지가 예정되어 있어요(폐지일 " + p.getValidTo() + ")");
        }
    }

    /**
     * {@code deptId}를 {@code d}부터 {@code newParentId} 아래로 옮겨도 순환이 생기지 않는지 본다.
     * 미래로 예약된 다른 부서의 변경 때문에 나중에 순환이 생길 수 있으므로, d 와 그 뒤 모든 변경 시작일에서 각각 검사한다.
     * 옮기는 부서 자신은 d 뒤에 이력이 없다(requireAfterLatest).
     */
    private void requireNoCycle(long deptId, long newParentId, LocalDate d) {
        List<OrgDeptHist> rows = hists.findEffectiveOnOrAfter(d).stream()
                .filter(h -> !h.getDeptId().equals(deptId))
                .toList();
        TreeSet<LocalDate> points = new TreeSet<>();
        points.add(d);
        rows.stream().map(OrgDeptHist::getValidFrom).filter(v -> v.isAfter(d)).forEach(points::add);
        for (LocalDate t : points) {
            Map<Long, Long> parentOf = new HashMap<>();
            for (OrgDeptHist h : rows) {
                if (h.isEffectiveOn(t) && h.getParentDeptId() != null) {
                    parentOf.put(h.getDeptId(), h.getParentDeptId());
                }
            }
            Set<Long> seen = new HashSet<>();
            Long cur = newParentId;
            while (cur != null && seen.add(cur)) {
                if (cur == deptId) {
                    String when = t.equals(d) ? "" : " (" + t + "에 예약된 변경 기준)";
                    throw OrgException.badRequest("자기 하위 부서 아래로는 옮길 수 없어요. 순환 참조가 생겨요" + when);
                }
                cur = parentOf.get(cur);
            }
        }
    }

    private Map<Long, OrgDept> loadDepts(Iterable<Long> ids) {
        Map<Long, OrgDept> m = new HashMap<>();
        for (OrgDept d : depts.findAllById(ids)) {
            m.put(d.getDeptId(), d);
        }
        return m;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
