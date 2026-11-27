package com.glassehr.approval;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 결재함·문서 조회는 현재 법인 문서만(#127). 다른 법인 문서 ID 는 없는 것처럼 empty. */
public interface ApprovalDocRepository extends JpaRepository<ApprovalDoc, Long> {

    Optional<ApprovalDoc> findByEnterCdAndApprovalId(String enterCd, long approvalId);

    /** 처리(승인·반려·회수) 전에 문서를 잠가 동시 처리를 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ApprovalDoc d where d.enterCd = :enterCd and d.approvalId = :id")
    Optional<ApprovalDoc> findForUpdate(@Param("enterCd") String enterCd, @Param("id") long approvalId);

    Optional<ApprovalDoc> findByEnterCdAndRequestTypeAndRequestRef(String enterCd, String requestType,
            long requestRef);

    /** 결재함: 진행 중이고 지금 차례 단계의 후보에 내가 있는 문서. */
    @Query("select distinct d from ApprovalDoc d join d.steps s join s.approvers a "
            + "where d.enterCd = :enterCd "
            + "and d.status = com.glassehr.approval.ApprovalDoc.Status.IN_PROGRESS "
            + "and s.stepNo = d.currentStepNo "
            + "and s.stepStatus = com.glassehr.approval.ApprovalDocStep.Status.WAITING and a.empId = :emp "
            + "order by d.submittedAt")
    List<ApprovalDoc> findInbox(@Param("enterCd") String enterCd, @Param("emp") long empId);

    /** 내가 승인·반려한 문서(최근 순). */
    @Query("select distinct d from ApprovalDoc d join d.steps s where d.enterCd = :enterCd "
            + "and s.actedEmpId = :emp order by d.submittedAt desc")
    List<ApprovalDoc> findProcessedBy(@Param("enterCd") String enterCd, @Param("emp") long empId, Pageable page);

    /** 내가 올린 문서(최근 순). */
    List<ApprovalDoc> findByEnterCdAndRequesterEmpIdOrderBySubmittedAtDesc(String enterCd, long requesterEmpId,
            Pageable page);
}
