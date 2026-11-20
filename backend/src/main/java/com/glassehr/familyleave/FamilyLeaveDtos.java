package com.glassehr.familyleave;

import com.glassehr.familyleave.FamilyLeaveRequest.Kind;
import com.glassehr.familyleave.FamilyLeaveRequest.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class FamilyLeaveDtos {

    private FamilyLeaveDtos() {
    }

    /**
     * 신청 등록. 육아휴직·단축은 자녀 구분(실명 대신 '첫째' 등) 필수, 단축은 자녀 생년월일·단축 근무유형 필수,
     * 가족돌봄휴직은 대상 가족 관계 필수. extension 은 제19조 제2항 추가 육아휴직 요건을 인사 담당자가 확인했을 때만 true.
     */
    public record CreateRequest(
            @NotNull(message = "신청 종류를 골라 주세요") Kind kind,
            @Size(max = 30, message = "자녀 구분은 30자 이내로 써 주세요") String childLabel,
            LocalDate childBirthDate,
            @Size(max = 30, message = "가족 관계는 30자 이내로 써 주세요") String familyRelation,
            @NotNull(message = "시작일을 입력해 주세요") LocalDate startDate,
            @NotNull(message = "종료일을 입력해 주세요") LocalDate endDate,
            @Positive(message = "근무유형이 올바르지 않아요") Long workPatternId,
            Boolean extension,
            @NotBlank(message = "신청 사유를 입력해 주세요")
            @Size(max = 500, message = "사유는 500자 이내로 써 주세요") String reason) {
    }

    public record DecisionRequest(@Size(max = 500, message = "메모는 500자 이내로 써 주세요") String note) {
    }

    public record RequestView(long reqId, long empId, Kind kind, String childLabel, LocalDate childBirthDate,
            String familyRelation, LocalDate startDate, LocalDate endDate, Long workPatternId, boolean extension,
            String reason, Status status, List<String> warnings, String requestedBy, LocalDateTime requestedAt,
            String decidedBy, LocalDateTime decidedAt, String decisionNote, Long leaveApptId, Long returnApptId,
            Long reducedAssignId, Long restoreAssignId) {

        static RequestView of(FamilyLeaveRequest r) {
            List<String> w = r.getWarnings() == null ? List.of() : List.of(r.getWarnings().split("\n"));
            return new RequestView(r.getReqId(), r.getEmpId(), r.getKind(), r.getChildLabel(), r.getChildBirthDate(),
                    r.getFamilyRelation(), r.getStartDate(), r.getEndDate(), r.getWorkPatternId(), r.isExtension(),
                    r.getReason(), r.getStatus(), w, r.getRequestedBy(), r.getRequestedAt(), r.getDecidedBy(),
                    r.getDecidedAt(), r.getDecisionNote(), r.getLeaveApptId(), r.getReturnApptId(),
                    r.getReducedAssignId(), r.getRestoreAssignId());
        }
    }

    /**
     * 개월 한도 사용 현황. 남은 기간은 {@code remainingMonths}개월에서 {@code remainingLessDays}일을 뺀 기간이다
     * (나누어 쓴 남는 일수를 개월로 합치는 기준이 확정되지 않아 따로 보여 준다). 한도 기준값이 없으면 limit·remaining 이 null.
     * usesMax 는 나누어 쓸 수 있는 횟수를 포함한 총 사용 횟수 한도(없으면 null = 횟수 제한 확인 안 함).
     */
    public record MonthUsage(Integer limitMonths, int usedMonths, int usedDays, Integer remainingMonths,
            int remainingLessDays, int uses, Integer usesMax) {
    }

    public record ChildBalance(String childLabel, MonthUsage parentalLeave, MonthUsage reducedHours) {
    }

    /** 연간 일수 한도 사용 현황(가족돌봄휴직, 1월 1일~12월 31일). */
    public record YearUsage(int year, Integer limitDays, int usedDays, Integer remainingDays) {
    }

    /** 남은 사용 가능 기간. notes 는 기준값 없음·확인 필요 안내. */
    public record BalanceView(long empId, LocalDate asOf, List<ChildBalance> children, YearUsage familyCareLeave,
            List<String> notes) {
    }
}
