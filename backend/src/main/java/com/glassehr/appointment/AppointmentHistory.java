package com.glassehr.appointment;

import com.glassehr.appointment.HrAppointment.Snapshot;
import com.glassehr.employee.Employee;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 한 사원의 확정 발령 이력으로 기준일의 소속·직급·직책·재직상태와 휴직 기간을 계산한다. 저장소를 쓰지 않는다.
 * 설계: docs/hr-appointment-design.md 6장.
 *
 * <ul>
 *   <li>이력 = 확정된 발령 중 취소 발령이 아니고, 확정된 취소 발령으로 되돌려지지 않은 것. 순서는 발령일 → 확정일시 → ID
 *       (같은 날 여러 발령은 먼저 확정한 것부터 적용).</li>
 *   <li>기준일 값 = 기준일까지(당일 포함) 이력 중 마지막 발령의 이후 값. 미래 날짜 발령은 그날부터 반영된다.</li>
 *   <li>기준일까지 이력이 없으면 가장 앞선 확정 발령(취소된 것 포함)의 이전 값. 확정 발령이 하나도 없으면 사원 행 값,
 *       재직상태는 퇴직일 기준(퇴직일 당일까지는 재직).</li>
 *   <li>입사일 전 날짜는 값이 없다.</li>
 * </ul>
 */
final class AppointmentHistory {

    static final Comparator<HrAppointment> ORDER = Comparator.comparing(HrAppointment::getEffectiveDate)
            .thenComparing(HrAppointment::getConfirmedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(HrAppointment::getApptId, Comparator.nullsLast(Comparator.naturalOrder()));

    /**
     * 휴직 1건. 시작일·종료일 모두 포함. {@code endDate}는 복직·퇴직 발령으로 앞당겨졌으면 그 발령일 전날,
     * 아니면 종료 예정일. {@code endedByApptId}는 휴직을 끝낸 복직·퇴직 발령(없으면 null).
     */
    record LeavePeriod(long empId, long leaveApptId, LocalDate startDate, LocalDate plannedEndDate, LocalDate endDate,
            Long endedByApptId, String reason) {

        boolean overlaps(LocalDate from, LocalDate to) {
            return !endDate.isBefore(from) && !startDate.isAfter(to);
        }
    }

    private final Employee employee;
    /** 확정된 발령 전체(취소 발령·취소된 발령 포함), 순서대로. */
    private final List<HrAppointment> confirmed;
    /** 이력(취소 발령과 취소된 원 발령 제외), 순서대로. */
    private final List<HrAppointment> active;

    /**
     * @param appointments 이 사원의 발령(미확정 포함, 순서 무관)
     * @param cancelLinks  취소 발령 ID → 원 발령 ID (이 사원 것만 들어 있지 않아도 된다)
     */
    AppointmentHistory(Employee employee, Collection<HrAppointment> appointments, Map<Long, Long> cancelLinks) {
        this.employee = employee;
        this.confirmed = appointments.stream().filter(HrAppointment::isConfirmed).sorted(ORDER).toList();
        Set<Long> cancelIds = appointments.stream().map(HrAppointment::getApptId)
                .filter(cancelLinks::containsKey).collect(Collectors.toSet());
        Set<Long> cancelledIds = confirmed.stream().map(HrAppointment::getApptId)
                .filter(cancelIds::contains).map(cancelLinks::get).collect(Collectors.toSet());
        this.active = confirmed.stream()
                .filter(a -> !cancelIds.contains(a.getApptId()))
                .filter(a -> !cancelledIds.contains(a.getApptId()))
                .toList();
    }

    /** 이력에 들어 있는(확정됐고 취소되지 않은) 발령인지. */
    boolean isActive(long apptId) {
        return active.stream().anyMatch(a -> a.getApptId() == apptId);
    }

    /** 이력의 마지막 발령. */
    Optional<HrAppointment> last() {
        return active.isEmpty() ? Optional.empty() : Optional.of(active.get(active.size() - 1));
    }

    /** 기준일 값. 입사일 전이면 빈 값. */
    Optional<Snapshot> stateOn(LocalDate date) {
        if (date.isBefore(employee.getHireDate())) {
            return Optional.empty();
        }
        HrAppointment lastOnOrBefore = null;
        for (HrAppointment a : active) {
            if (a.getEffectiveDate().isAfter(date)) {
                break;
            }
            lastOnOrBefore = a;
        }
        if (lastOnOrBefore != null) {
            return Optional.of(lastOnOrBefore.after());
        }
        if (!confirmed.isEmpty() && confirmed.get(0).before().status() != null) {
            return Optional.of(confirmed.get(0).before());
        }
        EmpStatus status = employee.getRetireDate() != null && employee.getRetireDate().isBefore(date)
                ? EmpStatus.RETIRED : EmpStatus.ACTIVE;
        return Optional.of(new Snapshot(employee.getCurrentDeptId(), employee.getJobGradeCode(),
                employee.getJobTitleCode(), status));
    }

    /**
     * 휴직 기간(확정·미취소 휴직 발령). 휴직 뒤 처음 나오는 복직·퇴직 발령의 발령일이 종료 예정일 이하이면 그 전날에 끝난다.
     * 휴직 시작 전에 복직·퇴직했으면 휴직 기간이 없다. from~to(양끝 포함)와 겹치는 것만 돌려준다.
     */
    List<LeavePeriod> leavePeriods(LocalDate from, LocalDate to) {
        List<LeavePeriod> out = new ArrayList<>();
        for (int i = 0; i < active.size(); i++) {
            HrAppointment leave = active.get(i);
            if (!AppointmentType.LEAVE.code().equals(leave.getApptTypeCode())) {
                continue;
            }
            LocalDate end = leave.getLeaveEndDate();
            Long endedBy = null;
            for (int j = i + 1; j < active.size(); j++) {
                HrAppointment next = active.get(j);
                if (AppointmentType.RETURN.code().equals(next.getApptTypeCode())
                        || AppointmentType.RETIREMENT.code().equals(next.getApptTypeCode())) {
                    LocalDate dayBefore = next.getEffectiveDate().minusDays(1);
                    if (!dayBefore.isAfter(end)) {
                        end = dayBefore;
                        endedBy = next.getApptId();
                    }
                    break;
                }
            }
            if (end.isBefore(leave.getLeaveStartDate())) {
                continue;
            }
            LeavePeriod p = new LeavePeriod(Objects.requireNonNull(leave.getEmpId()), leave.getApptId(),
                    leave.getLeaveStartDate(), leave.getLeaveEndDate(), end, endedBy, leave.getReason());
            if (p.overlaps(from, to)) {
                out.add(p);
            }
        }
        return out;
    }
}
