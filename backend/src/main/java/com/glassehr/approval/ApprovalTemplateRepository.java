package com.glassehr.approval;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalTemplateRepository extends JpaRepository<ApprovalTemplate, Long> {

    /** 기준일 d 에 유효한 템플릿: valid_from <= d AND (valid_to IS NULL OR d < valid_to). 정상이면 0~1건. */
    @Query("select t from ApprovalTemplate t where t.requestType = :type and t.validFrom <= :d "
            + "and (t.validTo is null or t.validTo > :d)")
    List<ApprovalTemplate> findEffective(@Param("type") String requestType, @Param("d") LocalDate d);

    List<ApprovalTemplate> findByRequestTypeOrderByValidFromDesc(String requestType);

    /**
     * 신청 유형의 템플릿을 모두 잠근다(설정 변경끼리 직렬화). Oracle 은 FOR UPDATE 와 행 수 제한을 함께 쓸 수 없어
     * 전부 잠그고 가장 늦은 것은 호출 쪽에서 고른다. 첫 템플릿끼리의 경합은 ux_approval_template_open 이 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ApprovalTemplate t where t.requestType = :type")
    List<ApprovalTemplate> lockByRequestType(@Param("type") String requestType);
}
