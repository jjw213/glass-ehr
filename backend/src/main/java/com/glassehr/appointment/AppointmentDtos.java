package com.glassehr.appointment;

import com.glassehr.appointment.HrAppointment.Snapshot;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

    public record AppointmentView(long apptId, String apptNo, String apptTypeCode, long empId,
            LocalDate effectiveDate, Snapshot before, Snapshot after, String reason, LocalDate leaveStartDate,
            LocalDate leaveEndDate, boolean confirmed, LocalDateTime confirmedAt, LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        static AppointmentView of(HrAppointment a) {
            return new AppointmentView(a.getApptId(), a.getApptNo(), a.getApptTypeCode(), a.getEmpId(),
                    a.getEffectiveDate(), a.before(), a.after(), a.getReason(), a.getLeaveStartDate(),
                    a.getLeaveEndDate(), a.isConfirmed(), a.getConfirmedAt(), a.getCreatedAt(), a.getUpdatedAt());
        }
    }
}
