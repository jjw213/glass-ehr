package com.glassehr.attendance;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 출퇴근 기록 처리 규칙(#28, docs/attendance-punch-design.md §8). DB 없이 시험한다.
 *
 * <ul>
 *   <li>같은 근무일의 출근·퇴근은 모두 저장한다(기록을 막으면 실제 근로시간이 누락될 수 있음, #22 결정).
 *       대신 이상한 기록은 {@link AttendancePunch.Check}로 표시해 관리자 확인 대상으로 둔다.</li>
 *   <li>중복 출근: 이미 출근 기록이 있는 근무일에 다시 출근 → {@code DUPLICATE_IN}. 출근 시각은 첫 출근.</li>
 *   <li>출근 없는 퇴근: 출근 기록이 없는 근무일에 퇴근 → {@code OUT_WITHOUT_IN}. 퇴근을 여러 번 누르면 정상이고
 *       마지막 퇴근이 퇴근 시각이다.</li>
 *   <li>근무 경과 시간은 첫 출근부터 지금(근무 중) 또는 마지막 퇴근까지의 분(버림)이다. 휴게시간 공제·연장 판정은
 *       집계 이슈에서 한다(이 값은 화면 표시용이며 근로시간 산정값이 아니다).</li>
 * </ul>
 */
final class PunchRules {

    private PunchRules() {
    }

    /** 이번 기록의 판정. {@code sameDay}는 같은 사원·같은 근무일의 기존 기록(시각순). */
    static AttendancePunch.Check check(List<AttendancePunch> sameDay, AttendancePunch.Type type) {
        boolean hasIn = sameDay.stream().anyMatch(p -> p.getType() == AttendancePunch.Type.IN);
        if (type == AttendancePunch.Type.IN) {
            return hasIn ? AttendancePunch.Check.DUPLICATE_IN : null;
        }
        return hasIn ? null : AttendancePunch.Check.OUT_WITHOUT_IN;
    }

    /** 하루 요약. {@code punches}는 시각순. */
    static Summary summarize(List<AttendancePunch> punches, LocalDateTime now) {
        LocalDateTime firstIn = punches.stream().filter(p -> p.getType() == AttendancePunch.Type.IN)
                .map(AttendancePunch::getPunchedAt).findFirst().orElse(null);
        LocalDateTime lastOut = null;
        for (AttendancePunch p : punches) {
            if (p.getType() == AttendancePunch.Type.OUT && firstIn != null && !p.getPunchedAt().isBefore(firstIn)) {
                lastOut = p.getPunchedAt();
            }
        }
        boolean working = !punches.isEmpty() && punches.get(punches.size() - 1).getType() == AttendancePunch.Type.IN;
        Long elapsed = null;
        if (firstIn != null) {
            LocalDateTime end = working || lastOut == null ? now : lastOut;
            elapsed = Math.max(0, Duration.between(firstIn, end).toMinutes());
        }
        return new Summary(firstIn, working ? null : lastOut, working, elapsed);
    }

    /**
     * @param firstInAt      첫 출근 시각(없으면 null)
     * @param lastOutAt      퇴근했으면 첫 출근 뒤 마지막 퇴근 시각, 근무 중이면 null
     * @param working        마지막 기록이 출근이면 true
     * @param elapsedMinutes 첫 출근부터 지금(근무 중) 또는 마지막 퇴근까지의 분(버림). 출근 기록이 없으면 null
     */
    record Summary(LocalDateTime firstInAt, LocalDateTime lastOutAt, boolean working, Long elapsedMinutes) {
    }

    static String message(AttendancePunch.Check check) {
        if (check == null) {
            return null;
        }
        return switch (check) {
            case DUPLICATE_IN -> "오늘 이미 출근 기록이 있어요. 출근 시각은 첫 출근 기록으로 보고, 이 기록은 관리자 확인 대상이에요";
            case OUT_WITHOUT_IN -> "오늘 출근 기록이 없어 퇴근만 기록했어요. 관리자 확인 대상이며 출근 누락은 정정 신청으로 고쳐 주세요";
        };
    }
}
