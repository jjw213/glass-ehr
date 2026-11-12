package com.glassehr.employee;

import java.util.List;
import org.springframework.data.repository.Repository;

/** 추가·조회만 한다. 수정·삭제 메서드를 노출하지 않으려고 JpaRepository 대신 Repository 를 쓴다. */
public interface RrnAccessLogRepository extends Repository<RrnAccessLog, Long> {

    RrnAccessLog save(RrnAccessLog log);

    List<RrnAccessLog> findByEmpIdOrderByAccessedAtAsc(Long empId);
}
