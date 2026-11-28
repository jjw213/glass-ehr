package com.glassehr.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

/** 고객사 마스터 조회(V051). */
public interface TenantRepository extends JpaRepository<Tenant, String> {
}
