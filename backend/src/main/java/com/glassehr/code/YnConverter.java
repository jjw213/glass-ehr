package com.glassehr.code;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** 사용 여부 컬럼(use_yn 'Y'/'N') ↔ boolean. */
@Converter
public class YnConverter implements AttributeConverter<Boolean, String> {

    @Override
    public String convertToDatabaseColumn(Boolean value) {
        if (value == null) {
            throw new IllegalArgumentException("사용 여부는 비워 둘 수 없습니다");
        }
        return value ? "Y" : "N";
    }

    @Override
    public Boolean convertToEntityAttribute(String db) {
        if ("Y".equals(db)) {
            return Boolean.TRUE;
        }
        if ("N".equals(db)) {
            return Boolean.FALSE;
        }
        throw new IllegalStateException("use_yn 값이 Y/N이 아닙니다: " + db);
    }
}
