package com.glassehr.code;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** 사용 여부 컬럼(use_yn 'Y'/'N') ↔ boolean. */
@Converter
public class YnConverter implements AttributeConverter<Boolean, String> {

    /**
     * null 은 null 로 통과시킨다. Hibernate 가 부팅 시 CHECK 조건을 만들려고
     * {@code BooleanJavaType.getPossibleStringValues}에서 null 을 넣어 호출하므로, 여기서 예외를 던지면
     * EntityManagerFactory 생성이 실패한다. 빈 값 저장은 엔티티(원시 boolean 생성자·변경 메서드)와
     * DDL {@code use_yn NOT NULL} 이 막는다.
     */
    @Override
    public String convertToDatabaseColumn(Boolean value) {
        if (value == null) {
            return null;
        }
        return value ? "Y" : "N";
    }

    @Override
    public Boolean convertToEntityAttribute(String db) {
        if (db == null) {
            return null;
        }
        if ("Y".equals(db)) {
            return Boolean.TRUE;
        }
        if ("N".equals(db)) {
            return Boolean.FALSE;
        }
        throw new IllegalStateException("use_yn 값이 Y/N이 아닙니다: " + db);
    }
}
