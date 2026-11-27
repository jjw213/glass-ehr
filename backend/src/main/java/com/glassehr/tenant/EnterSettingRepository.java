package com.glassehr.tenant;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** enter_setting·enter_setting_hist(V047). 모든 쿼리는 enter_cd 조건을 건다(docs/tenant-design.md 5.2). */
@Repository
public class EnterSettingRepository {

    /** 한 적용시작일의 값. */
    public record Entry(String key, LocalDate effectiveFrom, int value, String updatedBy, LocalDateTime updatedAt) {
    }

    /** 변경 이력 한 행. oldValue null = 그 적용시작일에 새로 등록. */
    public record Change(long histId, String key, LocalDate effectiveFrom, Integer oldValue, int newValue,
            String changedBy, LocalDateTime changedAt) {
    }

    private final JdbcTemplate jdbc;

    public EnterSettingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 그 날짜에 적용되는 값(effective_from <= date 중 가장 늦은 것). */
    public Optional<Integer> findEffective(String enterCd, String key, LocalDate date) {
        List<Integer> rows = jdbc.queryForList("SELECT setting_value FROM enter_setting"
                + " WHERE enter_cd = ? AND setting_key = ? AND effective_from <= ?"
                + " ORDER BY effective_from DESC FETCH FIRST 1 ROWS ONLY", Integer.class, enterCd, key, Date.valueOf(date));
        return rows.stream().findFirst();
    }

    /** 그 법인의 모든 값. 키, 적용시작일 순. */
    public List<Entry> findAll(String enterCd) {
        return jdbc.query("SELECT setting_key, effective_from, setting_value, updated_by, updated_at FROM enter_setting"
                + " WHERE enter_cd = ? ORDER BY setting_key, effective_from",
                (rs, i) -> new Entry(rs.getString(1), rs.getDate(2).toLocalDate(), rs.getInt(3), rs.getString(4),
                        rs.getTimestamp(5).toLocalDateTime()), enterCd);
    }

    /** 같은 적용시작일 행을 잠그고 지금 값을 읽는다(동시 수정 시 이력이 어긋나지 않게). */
    public Optional<Integer> lockValue(String enterCd, String key, LocalDate effectiveFrom) {
        return jdbc.queryForList("SELECT setting_value FROM enter_setting"
                + " WHERE enter_cd = ? AND setting_key = ? AND effective_from = ? FOR UPDATE", Integer.class,
                enterCd, key, Date.valueOf(effectiveFrom)).stream().findFirst();
    }

    public void insert(String enterCd, String key, LocalDate effectiveFrom, int value, String actor, LocalDateTime now) {
        jdbc.update("INSERT INTO enter_setting (enter_cd, setting_key, effective_from, setting_value, updated_by, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?)", enterCd, key, Date.valueOf(effectiveFrom), value, actor, Timestamp.valueOf(now));
    }

    public void update(String enterCd, String key, LocalDate effectiveFrom, int value, String actor, LocalDateTime now) {
        jdbc.update("UPDATE enter_setting SET setting_value = ?, updated_by = ?, updated_at = ?"
                + " WHERE enter_cd = ? AND setting_key = ? AND effective_from = ?",
                value, actor, Timestamp.valueOf(now), enterCd, key, Date.valueOf(effectiveFrom));
    }

    public void insertChange(String enterCd, String key, LocalDate effectiveFrom, Integer oldValue, int newValue,
            String actor, LocalDateTime now) {
        jdbc.update("INSERT INTO enter_setting_hist (enter_cd, setting_key, effective_from, old_value, new_value,"
                + " changed_by, changed_at) VALUES (?, ?, ?, ?, ?, ?, ?)", enterCd, key, Date.valueOf(effectiveFrom),
                oldValue, newValue, actor, Timestamp.valueOf(now));
    }

    /** 변경 이력. 최근 것부터. */
    public List<Change> findChanges(String enterCd) {
        return jdbc.query("SELECT hist_id, setting_key, effective_from, old_value, new_value, changed_by, changed_at"
                + " FROM enter_setting_hist WHERE enter_cd = ? ORDER BY changed_at DESC, hist_id DESC",
                (rs, i) -> new Change(rs.getLong(1), rs.getString(2), rs.getDate(3).toLocalDate(),
                        rs.getObject(4) == null ? null : rs.getInt(4), rs.getInt(5), rs.getString(6),
                        rs.getTimestamp(7).toLocalDateTime()), enterCd);
    }
}
