package com.glassehr.attendance;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 서면합의서 형식 검사와 "이 기간·대상에 유효한 합의서인가" 판정(#27). DB·스프링 없이 돈다.
 * 유효기간은 양 끝 포함. 제도 적용 기간 [from, until] 전체가 합의서 유효기간 안에 있어야 유효하다
 * (docs/work-schedule-types-design.md 3장: 근무제 정의의 유효기간은 합의 유효기간 안에 있어야 한다).
 */
public final class LaborAgreementRules {

    private LaborAgreementRules() {
    }

    /** 저장 전 형식 검사. 오류 메시지 목록(없으면 빈 목록). */
    public static List<String> validate(LaborAgreementDtos.Fields f) {
        List<String> errors = new ArrayList<>();
        if (f.title() == null) {
            errors.add("합의서 제목을 입력해 주세요");
        }
        if (f.targetDesc() == null) {
            errors.add("대상 근로자 범위를 입력해 주세요");
        }
        if (f.documentRef() == null) {
            errors.add("합의서 문서(파일 번호·보관 위치)를 입력해 주세요");
        }
        if (f.validUntil() != null && f.validUntil().isBefore(f.validFrom())) {
            errors.add("유효기간 종료일은 시작일 이후여야 해요");
        }
        if (f.validUntil() != null && f.validFrom() != null && f.validUntil().getYear() >= 9999) {
            errors.add("유효기간 종료일이 너무 멀어요. 기간이 없으면 비워 두세요");
        }
        if (f.signedDate() != null && f.validUntil() != null && f.signedDate().isAfter(f.validUntil())) {
            errors.add("체결일이 유효기간 종료일보다 늦어요");
        }
        if (f.scopeType() == LaborAgreement.Scope.SITE && f.scopeSiteCode() == null) {
            errors.add("대상 사업장을 골라 주세요");
        }
        // 근로기준법 제24조 제3항: 근로자대표 = 근로자 과반수로 조직된 노동조합, 없으면 근로자 과반수를 대표하는 자
        if (f.repType() == LaborAgreement.RepType.MAJORITY_UNION && f.repUnionName() == null) {
            errors.add("노동조합 이름을 입력해 주세요");
        }
        if (f.repType() == LaborAgreement.RepType.MAJORITY_REP && f.repEmpId() == null) {
            errors.add("근로자 과반수를 대표하는 사원을 골라 주세요");
        }
        return errors;
    }

    /**
     * 합의서 a 가 제도 적용 기간 [from, until](until NULL = 끝 없음)과 사업장 siteCode(NULL = 회사 전체 설정)에 유효한가.
     * <ul>
     *   <li>유형이 같아야 한다.</li>
     *   <li>a.validFrom ≤ from, 그리고 a 가 끝이 없거나 (until 이 있고 until ≤ a.validUntil). 끝 없는 적용은 끝 없는 합의만 덮는다.</li>
     *   <li>대상: ALL 합의는 모든 사업장, SITE 합의는 같은 사업장만. 회사 전체 설정(siteCode NULL)은 ALL 합의만.</li>
     * </ul>
     */
    public static boolean covers(LaborAgreement a, AgreementType type, LocalDate from, LocalDate until, String siteCode) {
        if (a.getAgreementType() != type || a.getValidFrom().isAfter(from)) {
            return false;
        }
        if (a.getValidUntil() != null && (until == null || until.isAfter(a.getValidUntil()))) {
            return false;
        }
        return a.getScopeType() == LaborAgreement.Scope.ALL
                || (siteCode != null && siteCode.equals(a.getScopeSiteCode()));
    }

    /** 덮는 합의서 중 하나. 사업장 한정 합의를 먼저, 그다음 늦게 시작한 합의(최근 체결)를 고른다. */
    public static Optional<LaborAgreement> pick(List<LaborAgreement> candidates, AgreementType type, LocalDate from,
            LocalDate until, String siteCode) {
        return candidates.stream()
                .filter(a -> covers(a, type, from, until, siteCode))
                .min(Comparator.comparing((LaborAgreement a) -> a.getScopeType() == LaborAgreement.Scope.SITE ? 0 : 1)
                        .thenComparing(LaborAgreement::getValidFrom, Comparator.reverseOrder())
                        .thenComparing(LaborAgreement::getAgreementId, Comparator.nullsLast(Comparator.reverseOrder())));
    }
}
