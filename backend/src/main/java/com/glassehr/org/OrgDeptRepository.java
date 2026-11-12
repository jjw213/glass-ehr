package com.glassehr.org;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgDeptRepository extends JpaRepository<OrgDept, Long> {

    boolean existsByDeptCode(String deptCode);
}
