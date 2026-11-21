package com.glassehr.approval;

import java.util.List;
import org.springframework.data.repository.Repository;

/** 처리 이력은 추가·조회만 한다(수정·삭제 메서드를 두지 않음). */
public interface ApprovalActionLogRepository extends Repository<ApprovalActionLog, Long> {

    ApprovalActionLog save(ApprovalActionLog log);

    List<ApprovalActionLog> findByApprovalIdOrderByActionId(long approvalId);
}
