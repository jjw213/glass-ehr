package com.glassehr.attendance;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 서면합의서 첨부파일(#27). 테이블: V019 worker_rep_agreement_file. 목록은 내용 없이 {@link AgreementFileView}로 읽는다. */
@Entity
@Table(name = "worker_rep_agreement_file")
public class LaborAgreementFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id", nullable = false, updatable = false)
    private Long fileId;

    @Column(name = "agreement_id", nullable = false, updatable = false)
    private Long agreementId;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "file_size", nullable = false, updatable = false)
    private Long fileSize;

    @Column(name = "sha256", nullable = false, updatable = false)
    private String sha256;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "content", nullable = false, updatable = false)
    private byte[] content;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    protected LaborAgreementFile() {
    }

    public LaborAgreementFile(long agreementId, String fileName, String contentType, byte[] content, String sha256,
            String uploadedBy, LocalDateTime uploadedAt) {
        this.agreementId = agreementId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.content = content;
        this.fileSize = (long) content.length;
        this.sha256 = sha256;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    public Long getFileId() {
        return fileId;
    }

    public Long getAgreementId() {
        return agreementId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public String getSha256() {
        return sha256;
    }

    public byte[] getContent() {
        return content;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }
}
