package com.glassehr.attendance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerRepAgreementRepository extends JpaRepository<WorkerRepAgreement, Long> {

    List<WorkerRepAgreement> findByKindOrderBySignedDateDescAgreementIdDesc(WorkerRepAgreement.Kind kind);
}
