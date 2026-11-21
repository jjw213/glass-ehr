package com.glassehr.attendance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlexWorkPlanRepository extends JpaRepository<FlexWorkPlan, FlexWorkPlan.Key> {

    List<FlexWorkPlan> findByFlexIdOrderByWorkDateAsc(long flexId);

    /** 계획 전체 교체용 일괄 삭제(영속성 컨텍스트를 거치지 않는다). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from FlexWorkPlan p where p.flexId = :flexId")
    int deleteAllOfAssign(@Param("flexId") long flexId);
}
