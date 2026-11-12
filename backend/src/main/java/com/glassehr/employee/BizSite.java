package com.glassehr.employee;

import com.glassehr.code.YnConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 사업장. 테이블: V009__employee.sql. 사번 앞부분(사업장코드, S01 형식)의 출처다(#9, #14 답변: 근무지 공통코드와 분리).
 * 사업장 추가는 마이그레이션으로 한다.
 */
@Entity
@Table(name = "biz_site")
public class BizSite {

    @Id
    @Column(name = "site_code", nullable = false, updatable = false)
    private String siteCode;

    @Column(name = "site_name", nullable = false)
    private String siteName;

    @Convert(converter = YnConverter.class)
    @Column(name = "use_yn", nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected BizSite() {
    }

    BizSite(String siteCode, String siteName, boolean active) {
        this.siteCode = siteCode;
        this.siteName = siteName;
        this.active = active;
        this.createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
    }

    public String getSiteCode() {
        return siteCode;
    }

    public String getSiteName() {
        return siteName;
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }
}
