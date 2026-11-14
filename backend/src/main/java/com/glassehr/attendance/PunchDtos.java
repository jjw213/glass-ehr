package com.glassehr.attendance;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/** 출퇴근 기록 API(#28) 요청·응답. 시각은 Asia/Seoul 오프셋(+09:00)을 붙여 돌려준다. */
public final class PunchDtos {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private PunchDtos() {
    }

    static OffsetDateTime seoul(LocalDateTime t) {
        return t == null ? null : t.atZone(SEOUL).toOffsetDateTime();
    }

    /**
     * @param type       IN(출근) / OUT(퇴근)
     * @param clientTime 단말 시각(선택, 참고용으로만 저장 — 기록 시각은 서버 시각)
     */
    public record PunchRequest(@NotNull(message = "출근(IN)인지 퇴근(OUT)인지 보내 주세요") AttendancePunch.Type type,
            OffsetDateTime clientTime) {
    }

    public record PunchView(long punchId, AttendancePunch.Type type, OffsetDateTime punchedAt, LocalDate workDate,
            AttendancePunch.Check check, String checkMessage) {

        static PunchView of(AttendancePunch p) {
            return new PunchView(p.getPunchId(), p.getType(), seoul(p.getPunchedAt()), p.getWorkDate(), p.getCheck(),
                    PunchRules.message(p.getCheck()));
        }
    }

    /**
     * 오늘(서버 날짜) 내 출퇴근 요약. 화면은 {@code serverNow}로 단말 시계 차이를 보정해 경과 시간을 센다.
     *
     * @param elapsedMinutes 첫 출근부터 serverNow(근무 중) 또는 마지막 퇴근까지의 분. 표시용(휴게 미공제, 근로시간 산정값 아님)
     */
    public record TodayView(OffsetDateTime serverNow, LocalDate workDate, OffsetDateTime firstInAt,
            OffsetDateTime lastOutAt, boolean working, Long elapsedMinutes, List<PunchView> punches) {
    }
}
