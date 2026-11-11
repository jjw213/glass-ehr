package com.glassehr.legalrate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * Oracle 통합 테스트용: legal_rate 스키마(V001·V002·V004)가 없으면 적용한다.
 * DDL은 Oracle에서 암묵 커밋되므로 테스트 데이터 INSERT 전에 호출한다.
 * V003(항목 등록)은 데이터라서 여기서 적용하지 않는다.
 */
final class OracleLegalRateSchema {

    private OracleLegalRateSchema() {
    }

    static void ensure(JdbcTemplate jdbc) {
        if (count(jdbc, "SELECT COUNT(*) FROM user_tables WHERE table_name = 'LEGAL_RATE'") == 0) {
            populate(jdbc, "db/oracle/V001__legal_rate.sql");
        }
        if (count(jdbc, "SELECT COUNT(*) FROM user_tab_columns"
                + " WHERE table_name = 'LEGAL_RATE_ITEM' AND column_name = 'UNIT'") == 0) {
            populate(jdbc, "db/oracle/V002__legal_rate_item_unit.sql");
        }
        if (count(jdbc, "SELECT COUNT(*) FROM user_objects"
                + " WHERE object_name = 'LEGAL_RATE_API' AND object_type = 'PACKAGE BODY' AND status = 'VALID'") == 0) {
            for (String block : plsqlBlocks("db/oracle/V004__legal_rate_api.sql")) {
                jdbc.execute(block);
            }
        }
    }

    private static int count(JdbcTemplate jdbc, String sql) {
        Integer n = jdbc.queryForObject(sql, Integer.class);
        return n == null ? 0 : n;
    }

    private static void populate(JdbcTemplate jdbc, String path) {
        new ResourceDatabasePopulator(new ClassPathResource(path)).execute(jdbc.getDataSource());
    }

    /** '/' 단독 줄로 나뉜 PL/SQL 블록. 블록 밖 주석 줄은 버린다. */
    static List<String> plsqlBlocks(String path) {
        String text;
        try {
            text = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<String> blocks = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : text.split("\\R")) {
            if (line.trim().equals("/")) {
                if (!cur.toString().isBlank()) {
                    blocks.add(cur.toString().strip());
                }
                cur.setLength(0);
            } else if (!(cur.toString().isBlank() && line.trim().startsWith("--"))) {
                cur.append(line).append('\n');
            }
        }
        return blocks;
    }
}
