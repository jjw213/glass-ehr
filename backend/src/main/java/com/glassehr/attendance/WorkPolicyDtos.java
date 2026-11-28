package com.glassehr.attendance;

import com.glassehr.attendance.WorkPolicyRules.AllowMode;
import com.glassehr.attendance.WorkPolicyRules.Allowance;
import com.glassehr.attendance.WorkPolicyRules.MissingOut;
import com.glassehr.attendance.WorkPolicyRules.Policy;
import com.glassehr.attendance.WorkPolicyRules.Rounding;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** 근무 정책 API 요청·응답(#159). 시간은 분 단위 정수. 법인은 세션에서만 정한다(요청으로 받지 않는다). */
public final class WorkPolicyDtos {

    private WorkPolicyDtos() {
    }

    public record AllowanceDto(@NotNull AllowMode mode, Integer dayLimit, Integer weekLimit, Integer monthLimit) {
        Allowance toAllowance() {
            return new Allowance(mode, dayLimit, weekLimit, monthLimit);
        }

        static AllowanceDto of(Allowance a) {
            return new AllowanceDto(a.mode(), a.dayLimit(), a.weekLimit(), a.monthLimit());
        }
    }

    /** patternId 가 null 이면 법인 기본 정책. 수정할 때 patternId 는 바꿀 수 없다(보내도 무시). */
    public record WorkPolicyRequest(Long patternId, @NotBlank @Size(max = 100) String policyName,
            @NotNull @Valid AllowanceDto overtime, @NotNull @Valid AllowanceDto night,
            @NotNull @Valid AllowanceDto holiday, @NotNull Boolean separateStatutoryExtra,
            @NotNull Integer unitMinutes, @NotNull Integer minRecognizeMinutes, @NotNull Rounding rounding,
            @NotNull Integer onTimeInGraceMinutes, @NotNull Integer onTimeOutGraceMinutes,
            @NotNull Boolean earlyInAllowed, @NotNull MissingOut missingOut, @NotNull Boolean realtimeReflect) {
        Policy toPolicy() {
            return new Policy(overtime.toAllowance(), night.toAllowance(), holiday.toAllowance(),
                    separateStatutoryExtra, unitMinutes, minRecognizeMinutes, rounding, onTimeInGraceMinutes,
                    onTimeOutGraceMinutes, earlyInAllowed, missingOut, realtimeReflect);
        }
    }

    public record WorkPolicyView(long policyId, Long patternId, String policyName, AllowanceDto overtime,
            AllowanceDto night, AllowanceDto holiday, boolean separateStatutoryExtra, int unitMinutes,
            int minRecognizeMinutes, Rounding rounding, int onTimeInGraceMinutes, int onTimeOutGraceMinutes,
            boolean earlyInAllowed, MissingOut missingOut, boolean realtimeReflect, boolean active,
            LocalDateTime updatedAt) {
        static WorkPolicyView of(WorkPolicy w) {
            Policy p = w.toPolicy();
            return new WorkPolicyView(w.getPolicyId(), w.getPatternId(), w.getPolicyName(),
                    AllowanceDto.of(p.overtime()), AllowanceDto.of(p.night()), AllowanceDto.of(p.holiday()),
                    p.separateStatutoryExtra(), p.unitMinutes(), p.minRecognizeMinutes(), p.rounding(),
                    p.onTimeInGraceMinutes(), p.onTimeOutGraceMinutes(), p.earlyInAllowed(), p.missingOut(),
                    p.realtimeReflect(), w.isActive(), w.getUpdatedAt());
        }
    }

    /** 삭제 결과: 쓰인 정책은 숨김(hidden=true), 아니면 지움. */
    public record DeleteResult(long policyId, boolean hidden) {
    }
}
