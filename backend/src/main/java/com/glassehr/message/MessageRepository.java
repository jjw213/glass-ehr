package com.glassehr.message;

import com.glassehr.message.MessageDtos.Member;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** 메시지 테이블(V030) 조회·저장. 메시지는 추가만 하고 수정·삭제하지 않는다. */
@Repository
public class MessageRepository {

    /** 대화방 한 줄(나 기준). */
    public record ThreadRow(long threadId, String preview, LocalDateTime lastSentAt, int unread) {
    }

    public record MessageRow(long messageId, long senderEmpId, String body, LocalDateTime sentAt) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public MessageRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 내가 참여한 대화방. 마지막 메시지가 최근인 것부터(메시지 없는 방은 뒤). */
    public List<ThreadRow> threadsOf(long empId) {
        String sql = """
                SELECT t.thread_id,
                       (SELECT m.body FROM msg_message m WHERE m.thread_id = t.thread_id
                         ORDER BY m.message_id DESC FETCH FIRST 1 ROW ONLY) AS preview,
                       (SELECT MAX(m.sent_at) FROM msg_message m WHERE m.thread_id = t.thread_id) AS last_sent_at,
                       (SELECT COUNT(*) FROM msg_message m
                         WHERE m.thread_id = t.thread_id AND m.sender_emp_id <> :emp
                           AND NOT EXISTS (SELECT 1 FROM msg_read r
                                            WHERE r.message_id = m.message_id AND r.emp_id = :emp)) AS unread
                  FROM msg_thread_member t
                 WHERE t.emp_id = :emp
                 ORDER BY last_sent_at DESC NULLS LAST, t.thread_id DESC
                """;
        return jdbc.query(sql, Map.of("emp", empId), (rs, i) -> new ThreadRow(
                rs.getLong("thread_id"),
                rs.getString("preview"),
                toLocal(rs.getTimestamp("last_sent_at")),
                rs.getInt("unread")));
    }

    /** 내가 참여한 대화방마다 나를 뺀 참여자(이름순). */
    public Map<Long, List<Member>> othersInThreadsOf(long empId) {
        String sql = """
                SELECT mm.thread_id, e.emp_id, e.emp_name, e.current_dept_id
                  FROM msg_thread_member mm
                  JOIN employee e ON e.emp_id = mm.emp_id
                 WHERE mm.emp_id <> :emp
                   AND mm.thread_id IN (SELECT me.thread_id FROM msg_thread_member me WHERE me.emp_id = :emp)
                 ORDER BY mm.thread_id, e.emp_name, e.emp_id
                """;
        Map<Long, List<Member>> out = new LinkedHashMap<>();
        jdbc.query(sql, Map.of("emp", empId), rs -> {
            long deptId = rs.getLong("current_dept_id");
            Long dept = rs.wasNull() ? null : deptId;
            out.computeIfAbsent(rs.getLong("thread_id"), k -> new ArrayList<>())
                    .add(new Member(rs.getLong("emp_id"), rs.getString("emp_name"), dept));
        });
        return out;
    }

    public boolean threadExists(long threadId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM msg_thread WHERE thread_id = :t",
                Map.of("t", threadId), Integer.class);
        return n != null && n > 0;
    }

    public boolean isMember(long threadId, long empId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM msg_thread_member WHERE thread_id = :t AND emp_id = :emp",
                Map.of("t", threadId, "emp", empId), Integer.class);
        return n != null && n > 0;
    }

    /** beforeId(없으면 맨 끝)보다 앞선 메시지를 최신부터 limit 건. */
    public List<MessageRow> messagesBefore(long threadId, Long beforeId, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("t", threadId).addValue("n", limit);
        String cond = "";
        if (beforeId != null) {
            cond = " AND message_id < :before";
            p.addValue("before", beforeId);
        }
        String sql = "SELECT message_id, sender_emp_id, body, sent_at FROM msg_message WHERE thread_id = :t" + cond
                + " ORDER BY message_id DESC FETCH FIRST :n ROWS ONLY";
        return jdbc.query(sql, p, (rs, i) -> new MessageRow(
                rs.getLong("message_id"), rs.getLong("sender_emp_id"), rs.getString("body"),
                toLocal(rs.getTimestamp("sent_at"))));
    }

    public long insertMessage(long threadId, long senderEmpId, String body, LocalDateTime sentAt) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO msg_message (thread_id, sender_emp_id, body, sent_at)
                VALUES (:t, :sender, :body, :sentAt)
                """, new MapSqlParameterSource()
                .addValue("t", threadId)
                .addValue("sender", senderEmpId)
                .addValue("body", body)
                .addValue("sentAt", Timestamp.valueOf(sentAt)), keys, new String[] {"message_id"});
        return Optional.ofNullable(keys.getKey()).orElseThrow().longValue();
    }

    /** 남이 보낸 메시지 중 아직 읽지 않은 것을 모두 읽음으로. 동시에 두 번 눌려 겹치면 한 번 더 시도한다. */
    public int markRead(long threadId, long empId, LocalDateTime readAt) {
        String sql = """
                INSERT INTO msg_read (message_id, thread_id, emp_id, read_at)
                SELECT m.message_id, m.thread_id, :emp, :readAt
                  FROM msg_message m
                 WHERE m.thread_id = :t AND m.sender_emp_id <> :emp
                   AND NOT EXISTS (SELECT 1 FROM msg_read r WHERE r.message_id = m.message_id AND r.emp_id = :emp)
                """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("t", threadId).addValue("emp", empId).addValue("readAt", Timestamp.valueOf(readAt));
        try {
            return jdbc.update(sql, p);
        } catch (DuplicateKeyException e) {
            return jdbc.update(sql, p);
        }
    }

    private static LocalDateTime toLocal(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
