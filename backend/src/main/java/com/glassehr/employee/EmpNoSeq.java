package com.glassehr.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

/**
 * 사번 채번 상태. 사업장·입사연도마다 마지막 순번(#9 설계 2장). 테이블: V009__employee.sql.
 * V034(#121): 키는 (enter_cd, site_code, hire_year). 순번은 법인 안에서 센다.
 */
@Entity
@Table(name = "emp_no_seq")
@IdClass(EmpNoSeq.Key.class)
public class EmpNoSeq {

    @Id
    @Column(name = "enter_cd", nullable = false, updatable = false)
    private String enterCd;

    @Id
    @Column(name = "site_code", nullable = false, updatable = false)
    private String siteCode;

    @Id
    @Column(name = "hire_year", nullable = false, updatable = false)
    private Integer hireYear;

    @Column(name = "last_seq", nullable = false)
    private Integer lastSeq;

    protected EmpNoSeq() {
    }

    EmpNoSeq(String enterCd, String siteCode, int hireYear, int lastSeq) {
        this.enterCd = enterCd;
        this.siteCode = siteCode;
        this.hireYear = hireYear;
        this.lastSeq = lastSeq;
    }

    public String getEnterCd() {
        return enterCd;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public int getHireYear() {
        return hireYear;
    }

    public int getLastSeq() {
        return lastSeq;
    }

    void advanceTo(int seq) {
        this.lastSeq = seq;
    }

    /** 복합키. */
    public static class Key implements Serializable {
        private String enterCd;
        private String siteCode;
        private Integer hireYear;

        protected Key() {
        }

        public Key(String enterCd, String siteCode, int hireYear) {
            this.enterCd = enterCd;
            this.siteCode = siteCode;
            this.hireYear = hireYear;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(enterCd, k.enterCd) && Objects.equals(siteCode, k.siteCode)
                    && Objects.equals(hireYear, k.hireYear);
        }

        @Override
        public int hashCode() {
            return Objects.hash(enterCd, siteCode, hireYear);
        }
    }
}
