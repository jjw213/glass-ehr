package com.glassehr.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 사원 주민등록번호 암호문. 테이블: V009__employee.sql(emp_rrn). 평문 컬럼은 없다(docs/rrn-design.md 2장).
 * 사원 기본정보 엔티티와 분리해 기본 조회에서 함께 읽히지 않게 한다.
 */
@Entity
@Table(name = "emp_rrn")
public class EmpRrn {

    @Id
    @Column(name = "emp_id", nullable = false, updatable = false)
    private Long empId;

    @Column(name = "rrn_enc", nullable = false)
    private byte[] rrnEnc;

    @Column(name = "rrn_masked", nullable = false)
    private String rrnMasked;

    @Column(name = "rrn_hash", nullable = false)
    private byte[] rrnHash;

    @Column(name = "key_version", nullable = false)
    private Integer keyVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    protected EmpRrn() {
    }

    EmpRrn(long empId, RrnCrypto.Sealed sealed, String masked, byte[] hash, String actorId, LocalDateTime now) {
        this.empId = empId;
        this.createdAt = now;
        replace(sealed, masked, hash, actorId, now);
    }

    void replace(RrnCrypto.Sealed sealed, String masked, byte[] hash, String actorId, LocalDateTime now) {
        this.rrnEnc = sealed.cipherText();
        this.keyVersion = sealed.keyVersion();
        this.rrnMasked = masked;
        this.rrnHash = hash;
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    public Long getEmpId() {
        return empId;
    }

    public byte[] getRrnEnc() {
        return rrnEnc;
    }

    public String getRrnMasked() {
        return rrnMasked;
    }

    public byte[] getRrnHash() {
        return rrnHash;
    }

    public int getKeyVersion() {
        return keyVersion;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    @Override
    public String toString() {
        return "EmpRrn[empId=" + empId + ", masked=" + rrnMasked + ", keyVersion=" + keyVersion + "]";
    }
}
