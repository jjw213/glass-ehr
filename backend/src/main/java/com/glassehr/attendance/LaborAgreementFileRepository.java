package com.glassehr.attendance;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LaborAgreementFileRepository extends JpaRepository<LaborAgreementFile, Long> {

    /** 첨부 목록. 파일 내용(BLOB)은 읽지 않는다. */
    @Query("SELECT new com.glassehr.attendance.AgreementFileView(f.fileId, f.agreementId, f.fileName, "
            + "f.contentType, f.fileSize, f.sha256, f.uploadedBy, f.uploadedAt) "
            + "FROM LaborAgreementFile f WHERE f.agreementId = :agreementId ORDER BY f.fileId")
    List<AgreementFileView> findViews(@Param("agreementId") long agreementId);

    long countByAgreementId(long agreementId);

    Optional<LaborAgreementFile> findByFileIdAndAgreementId(long fileId, long agreementId);
}
