package com.glassehr.auth;

import com.glassehr.org.OrgDtos.DeptNode;
import com.glassehr.org.OrgDtos.OrgTree;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 부서장 데이터 범위 계산. 기준일 조직 트리에서 그 사원이 부서장인 부서와 모든 하위부서를 모은다. */
public final class DeptScope {

    private DeptScope() {
    }

    public static Set<Long> managedBy(OrgTree tree, long headEmpId) {
        Set<Long> out = new LinkedHashSet<>();
        walk(tree.roots(), headEmpId, false, out);
        return out;
    }

    private static void walk(List<DeptNode> nodes, long headEmpId, boolean underManaged, Set<Long> out) {
        for (DeptNode n : nodes) {
            boolean managed = underManaged || (n.headEmpId() != null && n.headEmpId() == headEmpId);
            if (managed) {
                out.add(n.deptId());
            }
            walk(n.children(), headEmpId, managed, out);
        }
    }
}
