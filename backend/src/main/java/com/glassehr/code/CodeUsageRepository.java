package com.glassehr.code;

import com.glassehr.code.CodeReferences.Ref;
import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 공통코드 사용처 조회. 테이블·컬럼 이름은 {@link CodeReferences}의 상수만 SQL 에 넣는다(요청 값은 넣지 않음).
 * 모든 쿼리는 같은 법인(enter_cd) 행만 본다(docs/tenant-design.md 5.2).
 */
@Repository
public class CodeUsageRepository {

    /** 사용처 행 한 줄. 식별 최소 항목(사번·이름·일자)만. */
    public record UsageRow(String empNo, String name, LocalDate date) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public CodeUsageRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 이 테이블에서 코드값을 참조하는 행 수(한 행의 여러 컬럼이 같아도 한 건). */
    public long count(String enterCd, Ref ref, String code) {
        String sql = "SELECT COUNT(*) FROM " + ref.table() + " t WHERE t.enter_cd = :e AND " + matches(ref);
        Long n = jdbc.queryForObject(sql, params(enterCd, code), Long.class);
        return n == null ? 0 : n;
    }

    /** 이 테이블에서 코드값별 참조 행 수. 참조가 없는 코드는 결과에 없다. */
    public Map<String, Long> countsByCode(String enterCd, Ref ref) {
        String union = ref.columns().stream()
                .map(c -> "SELECT ROWIDTOCHAR(ROWID) AS rid, " + c + " AS code FROM " + ref.table()
                        + " WHERE enter_cd = :e")
                .collect(Collectors.joining(" UNION "));
        String sql = "SELECT code, COUNT(*) AS cnt FROM (" + union + ") WHERE code IS NOT NULL GROUP BY code";
        Map<String, Long> result = new HashMap<>();
        jdbc.query(sql, new MapSqlParameterSource("e", enterCd),
                rs -> {
                    result.put(rs.getString("code"), rs.getLong("cnt"));
                });
        return result;
    }

    /** 참조 행 목록(일자 최근 순). */
    public List<UsageRow> rows(String enterCd, Ref ref, String code, int offset, int limit) {
        String from = ref.isEmployeeTable()
                ? "employee t"
                : ref.table() + " t JOIN employee emp ON emp.enter_cd = t.enter_cd AND emp.emp_id = t.emp_id";
        String who = ref.isEmployeeTable() ? "t" : "emp";
        String sql = "SELECT " + who + ".emp_no, " + who + ".emp_name, t." + ref.dateColumn() + " AS d FROM " + from
                + " WHERE t.enter_cd = :e AND " + matches(ref)
                + " ORDER BY d DESC NULLS LAST, " + who + ".emp_no, ROWIDTOCHAR(t.ROWID)"
                + " OFFSET :o ROWS FETCH NEXT :n ROWS ONLY";
        MapSqlParameterSource p = params(enterCd, code).addValue("o", offset).addValue("n", limit);
        return jdbc.query(sql, p, (rs, i) -> {
            Date d = rs.getDate("d");
            return new UsageRow(rs.getString("emp_no"), rs.getString("emp_name"), d == null ? null : d.toLocalDate());
        });
    }

    private static String matches(Ref ref) {
        return "(" + ref.columns().stream().map(c -> "t." + c + " = :code").collect(Collectors.joining(" OR ")) + ")";
    }

    private static MapSqlParameterSource params(String enterCd, String code) {
        return new MapSqlParameterSource("e", enterCd).addValue("code", code);
    }
}
