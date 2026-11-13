package com.glassehr.auth;

import com.glassehr.employee.RrnAccessLog;
import com.glassehr.employee.RrnAccessLogRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개인정보 조회 로그 화면(관리자) 조회. privacy_access_log(V013)와 주민등록번호 원문 조회 로그 rrn_access_log(V009)를
 * 같은 형식으로 합쳐 최근 순으로 돌려준다.
 */
@Service
@Transactional(readOnly = true)
public class PrivacyLogService {

    /** 한 번에 보여 주는 최대 행 수(화면 성능용, 법령 기준값 아님). 더 있으면 truncated=true. */
    static final int MAX_ROWS = 500;
    /** 한 번에 조회할 수 있는 최대 기간(일). 화면 성능용, 법령 기준값 아님. */
    static final int MAX_DAYS = 366;

    private final PrivacyAccessLogRepository privacyLogs;
    private final RrnAccessLogRepository rrnLogs;

    public PrivacyLogService(PrivacyAccessLogRepository privacyLogs, RrnAccessLogRepository rrnLogs) {
        this.privacyLogs = privacyLogs;
        this.rrnLogs = rrnLogs;
    }

    /** 로그 한 줄. source = PRIVACY(privacy_access_log) 또는 RRN(rrn_access_log). purpose 는 RRN 로그에만 있다. */
    public record LogRow(String source, LocalDateTime accessedAt, String accessorId, String accessorRoles,
            Long targetEmpId, String item, String action, String purpose, String result, String clientIp,
            String requestPath) {
    }

    public record LogPage(LocalDate from, LocalDate to, List<LogRow> rows, boolean truncated) {
    }

    /** 기간 [from, to] (둘 다 포함, Asia/Seoul 날짜). */
    public LogPage search(LocalDate from, LocalDate to, String accessorId, Long targetEmpId) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("끝 날짜가 시작 날짜보다 빨라요");
        }
        if (from.plusDays(MAX_DAYS).isBefore(to)) {
            throw new IllegalArgumentException("한 번에 " + MAX_DAYS + "일까지만 조회할 수 있어요");
        }
        String who = accessorId == null || accessorId.isBlank() ? null : accessorId.strip();
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        PageRequest page = PageRequest.of(0, MAX_ROWS + 1);
        List<LogRow> rows = new ArrayList<>();
        for (PrivacyAccessLog l : privacyLogs.search(start, end, who, targetEmpId, page)) {
            rows.add(new LogRow("PRIVACY", l.getAccessedAt(), l.getAccessorId(), l.getAccessorRoles(),
                    l.getTargetEmpId(), l.getItem().name(), l.getAction().name(), null, l.getResult().name(),
                    l.getClientIp(), l.getRequestPath()));
        }
        for (RrnAccessLog l : rrnLogs.search(start, end, who, targetEmpId, page)) {
            rows.add(new LogRow("RRN", l.getAccessedAt(), l.getAccessorId(), l.getAccessorRole(), l.getEmpId(),
                    PrivacyAccessLog.Item.RRN.name(), PrivacyAccessLog.Action.VIEW.name(), l.getPurpose().name(),
                    l.getResult().name(), l.getClientIp(), null));
        }
        rows.sort(Comparator.comparing(LogRow::accessedAt).reversed());
        boolean truncated = rows.size() > MAX_ROWS;
        return new LogPage(from, to, truncated ? List.copyOf(rows.subList(0, MAX_ROWS)) : rows, truncated);
    }
}
