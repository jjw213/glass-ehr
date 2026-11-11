package com.glassehr.code;

import java.io.Serializable;
import java.util.Objects;

/** {@link CommonCode} 복합키 (group_code, code). */
public class CommonCodeId implements Serializable {

    private String groupCode;
    private String code;

    protected CommonCodeId() {
    }

    public CommonCodeId(String groupCode, String code) {
        this.groupCode = groupCode;
        this.code = code;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CommonCodeId other
                && Objects.equals(groupCode, other.groupCode)
                && Objects.equals(code, other.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupCode, code);
    }
}
