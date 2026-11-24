package com.glassehr.org;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 부서. 법인별 테이블이므로 상속 메서드(findById·findAllById 등)는 쓰지 않고
 * 첫 인자로 enterCd 를 받는 메서드만 쓴다(docs/tenant-design.md 5.2, #122).
 * 다른 법인의 dept_id 는 없는 부서처럼 보인다.
 */
public interface OrgDeptRepository extends JpaRepository<OrgDept, Long> {

    Optional<OrgDept> findByEnterCdAndDeptId(String enterCd, Long deptId);

    List<OrgDept> findByEnterCdAndDeptIdIn(String enterCd, Collection<Long> deptIds);

    boolean existsByEnterCdAndDeptCode(String enterCd, String deptCode);
}
