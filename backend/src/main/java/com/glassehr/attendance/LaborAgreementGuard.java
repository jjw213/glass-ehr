package com.glassehr.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제도 설정 API 공통 함수(#27): 서면합의가 전제인 제도(탄력적·선택적 근로시간제, 재량근로, 보상휴가제, 연차 대체,
 * 휴일 대체 — {@link AgreementType})를 켜거나 배정하기 전에 호출한다.
 *
 * <pre>
 *   LaborAgreement a = guard.requireValid(AgreementType.SELECTIVE, from, until, siteCode); // 없으면 422
 * </pre>
 * 판정 규칙은 {@link LaborAgreementRules#covers}. 제도 저장 행에 합의서 ID(a.getAgreementId())를 함께 남긴다.
 */
@Component
public class LaborAgreementGuard {

    private final LaborAgreementRepository agreements;

    public LaborAgreementGuard(LaborAgreementRepository agreements) {
        this.agreements = agreements;
    }

    /**
     * 적용 기간 전체를 덮는 유효한 합의서 확인. 없으면 ok=false 와 이유.
     * enterCd: 대상 법인(세션 현재 법인 또는 대상 사원 법인). 같은 법인 합의서만 인정한다(#125).
     */
    @Transactional(readOnly = true)
    public LaborAgreementDtos.CheckResult check(String enterCd, AgreementType type, LocalDate from, LocalDate until,
            String siteCode) {
        Objects.requireNonNull(enterCd, "enterCd");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(from, "from");
        if (until != null && until.isBefore(from)) {
            throw AttendanceException.badRequest("적용 종료일은 시작일 이후여야 해요");
        }
        List<LaborAgreement> all = agreements.findByEnterCdAndAgreementType(enterCd, type);
        return LaborAgreementRules.pick(all, type, from, until, siteCode)
                .map(a -> new LaborAgreementDtos.CheckResult(true, type, a.getAgreementId(), a.getTitle(),
                        a.getValidFrom(), a.getValidUntil(), null))
                .orElseGet(() -> new LaborAgreementDtos.CheckResult(false, type, null, null, null, null,
                        missingMessage(type, from, until, all.isEmpty())));
    }

    /** {@link #check}가 실패하면 422 로 저장을 막는다. 성공하면 근거 합의서를 돌려준다. */
    @Transactional(readOnly = true)
    public LaborAgreement requireValid(String enterCd, AgreementType type, LocalDate from, LocalDate until,
            String siteCode) {
        LaborAgreementDtos.CheckResult r = check(enterCd, type, from, until, siteCode);
        if (!r.ok()) {
            throw new AttendanceException(HttpStatus.UNPROCESSABLE_ENTITY, r.message(), List.of());
        }
        return agreements.findByEnterCdAndAgreementId(enterCd, r.agreementId()).orElseThrow();
    }

    /** 이미 배정에 연결된 합의서가 수정된 경우에도 현재 적용 범위와 기간을 확인한다. 다른 법인 합의서는 없는 것으로 본다. */
    @Transactional(readOnly = true)
    public void requireLinked(String enterCd, Long agreementId, AgreementType type, LocalDate from, LocalDate until,
            String siteCode) {
        if (agreementId == null || agreements.findByEnterCdAndAgreementId(enterCd, agreementId)
                .filter(a -> LaborAgreementRules.covers(a, type, from, until, siteCode)).isEmpty()) {
            throw new AttendanceException(HttpStatus.UNPROCESSABLE_ENTITY,
                    missingMessage(type, from, until, false), List.of());
        }
    }

    static String missingMessage(AgreementType type, LocalDate from, LocalDate until, boolean none) {
        String period = from + " ~ " + (until == null ? "기간 정함 없음" : until.toString());
        return type.label() + "는 근로자대표와의 서면합의가 있어야 해요(" + type.article() + "). "
                + (none ? "등록된 합의서가 없어요" : "적용 기간(" + period + ")과 대상을 모두 덮는 유효한 합의서가 없어요");
    }
}
