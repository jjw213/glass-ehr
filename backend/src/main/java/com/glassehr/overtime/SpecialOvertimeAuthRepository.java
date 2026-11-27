package com.glassehr.overtime;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 특별연장근로 인가는 법인마다 따로 둔다. 인가 번호도 법인 안에서만 유일하다(#127, V046 uq_special_overtime_auth_no). */
public interface SpecialOvertimeAuthRepository extends JpaRepository<SpecialOvertimeAuth, Long> {

    boolean existsByEnterCdAndAuthNo(String enterCd, String authNo);

    List<SpecialOvertimeAuth> findByEnterCdOrderByPeriodFromDesc(String enterCd);
}
