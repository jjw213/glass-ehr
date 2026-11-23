package com.glassehr.tenant;

import com.glassehr.code.YnConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 법인 마스터(전체 공통). 테이블: V031 enter_company. 설계: docs/tenant-design.md 3.3.
 * 관리 화면·API는 후속 이슈(GROUP_ADMIN 전용)라 지금은 읽기 전용으로 쓴다. 사업자등록번호 등 나머지 열은
 * 아직 매핑하지 않는다(ddl-auto: validate 는 남는 열을 허용한다).
 */
@Entity
@Table(name = "enter_company")
public class EnterCompany {

    @Id
    @Column(name = "enter_cd", nullable = false, updatable = false)
    private String enterCd;

    @Column(name = "enter_name", nullable = false)
    private String enterName;

    @Convert(converter = YnConverter.class)
    @Column(name = "use_yn", nullable = false)
    private Boolean active;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    protected EnterCompany() {
    }

    public String getEnterCd() {
        return enterCd;
    }

    public String getEnterName() {
        return enterName;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
