package com.glassehr.appointment;

import com.glassehr.appointment.HrAppointment.Snapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 인사발령 API 요청·응답 형식. 길이 제한은 V014 컬럼 길이와 같다. */
public final class AppointmentDtos {

    private AppointmentDtos() {
    }

    /**
     * 등록·수정 요청. 이후 값은 발령 유형이 바꾸는 항목만 보낸다(그 밖의 항목은 이전 값을 그대로 옮긴다).
     * 사유는 근로기준법 제23조 제1항(정당한 이유 없는 전직·휴직 등 금지)에 따라 반드시 적는다.
     */
    public record AppointmentRequest(
            @NotBlank(message = "발령 유형을 선택해 주세요")
            String apptTypeCode,
            @NotNull(message = "발령일을 입력해 주세요")
            LocalDate effectiveDate,
            Long afterDeptId,
            @Size(max = 30, message = "직급 코드는 30자 이내예요")
            String afterJobGradeCode,
            @Size(max = 30, message = "직책 코드는 30자 이내예요")
            String afterJobTitleCode,
            @NotBlank(message = "발령 사유를 입력해 주세요(근로기준법 제23조)")
            @Size(max = 1000, message = "발령 사유는 1000자 이내로 써 주세요")
            String reason,
            LocalDate leaveStartDate,
            LocalDate leaveEndDate) {
    }

    /**
     * 발령 응답. {@code cancelOfApptId}: 이 발령이 취소 발령이면 원 발령 ID.
     * {@code cancelledByApptId}: 이 발령을 되돌리는 취소 발령 ID(미확정 포함). 둘 다 없으면 null.
     */
    public record AppointmentView(long apptId, String apptNo, String apptTypeCode, long empId,
            LocalDate effectiveDate, Snapshot before, Snapshot after, String reason, LocalDate leaveStartDate,
            LocalDate leaveEndDate, boolean confirmed, LocalDateTime confirmedAt, LocalDateTime createdAt,
            LocalDateTime updatedAt, Long cancelOfApptId, Long cancelledByApptId) {

        public AppointmentView(long apptId, String apptNo, String apptTypeCode, long empId,
                LocalDate effectiveDate, Snapshot before, Snapshot after, String reason, LocalDate leaveStartDate,
                LocalDate leaveEndDate, boolean confirmed, LocalDateTime confirmedAt, LocalDateTime createdAt,
                LocalDateTime updatedAt) {
            this(apptId, apptNo, apptTypeCode, empId, effectiveDate, before, after, reason, leaveStartDate,
                    leaveEndDate, confirmed, confirmedAt, createdAt, updatedAt, null, null);
        }

        static AppointmentView of(HrAppointment a, Long cancelOfApptId, Long cancelledByApptId) {
            return new AppointmentView(a.getApptId(), a.getApptNo(), a.getApptTypeCode(), a.getEmpId(),
                    a.getEffectiveDate(), a.before(), a.after(), a.getReason(), a.getLeaveStartDate(),
                    a.getLeaveEndDate(), a.isConfirmed(), a.getConfirmedAt(), a.getCreatedAt(), a.getUpdatedAt(),
                    cancelOfApptId, cancelledByApptId);
        }
    }

    /** 취소 발령 등록 요청. 취소에도 사유를 반드시 적는다(근로기준법 제23조 제1항). */
    public record CancelRequest(
            @NotBlank(message = "발령 사유를 입력해 주세요(근로기준법 제23조)")
            @Size(max = 1000, message = "발령 사유는 1000자 이내로 써 주세요")
            String reason) {
    }

    /**
     * 일괄 발령 요청(#60). 선택한 사원 모두에게 같은 발령 내용을 등록하고, {@code confirm}이면 바로 확정한다.
     * 한 번에 200명까지(한 요청이 너무 오래 걸리지 않게).
     */
    public record BulkAppointmentRequest(
            @NotEmpty(message = "발령할 사원을 선택해 주세요")
            @Size(max = 200, message = "한 번에 200명까지 발령할 수 있어요")
            List<Long> empIds,
            @NotNull(message = "발령 내용을 입력해 주세요")
            @Valid
            AppointmentRequest appointment,
            boolean confirm) {
    }

    /** 사원 한 명의 일괄 발령 결과. 성공이면 발령, 실패면 화면에 보일 오류 문구. */
    public record BulkItemResult(long empId, boolean ok, AppointmentView appointment, String error) {
    }

    /** 일괄 발령 결과. 실패한 사원은 등록·확정 모두 되돌려져 있다. */
    public record BulkAppointmentResult(int succeeded, int failed, List<BulkItemResult> items) {
    }

    /** 기준일의 사원 소속·직급·직책·재직상태(확정 발령 이력 기준). */
    public record AssignmentView(long empId, LocalDate date, Long deptId, String jobGradeCode, String jobTitleCode,
            EmpStatus empStatus) {
    }

    /**
     * 휴직 기간(양끝 포함). {@code endDate}는 복직·퇴직 발령으로 앞당겨졌으면 그 발령일 전날, 아니면 종료 예정일.
     * 근태(연차·육아휴직)가 쓴다.
     */
    public record LeavePeriodView(long empId, long leaveApptId, LocalDate startDate, LocalDate plannedEndDate,
            LocalDate endDate, Long endedByApptId, String reason) {

        static LeavePeriodView of(AppointmentHistory.LeavePeriod p) {
            return new LeavePeriodView(p.empId(), p.leaveApptId(), p.startDate(), p.plannedEndDate(), p.endDate(),
                    p.endedByApptId(), p.reason());
        }
    }
}
