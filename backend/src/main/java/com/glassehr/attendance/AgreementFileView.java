package com.glassehr.attendance;

import java.time.LocalDateTime;

/** 서면합의서 첨부파일 목록 항목(내용 제외). JPQL 생성자 표현식으로 만든다(LaborAgreementFileRepository). */
public record AgreementFileView(long fileId, long agreementId, String fileName, String contentType, long fileSize,
        String sha256, String uploadedBy, LocalDateTime uploadedAt) {
}
