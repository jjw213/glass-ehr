package com.glassehr.code;

import java.io.Serializable;
import java.util.Objects;

/** {@link CommonCode} 복합키 (enter_cd, group_code, code). V034(#121)부터 법인이 맨 앞이다. */
public class CommonCodeId implements Serializable {

    private String enterCd;
    private String groupCode;
    private String code;

    protected CommonCodeId() {
    }

    public CommonCodeId(String enterCd, String groupCode, String code) {
        this.enterCd = enterCd;
        this.groupCode = groupCode;
        this.code = code;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CommonCodeId other
                && Objects.equals(enterCd, other.enterCd)
                && Objects.equals(groupCode, other.groupCode)
                && Objects.equals(code, other.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enterCd, groupCode, code);
    }
}
