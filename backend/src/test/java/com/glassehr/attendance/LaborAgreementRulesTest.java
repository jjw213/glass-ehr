package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 서면합의서 형식 검사와 유효 판정. 법령 수치를 쓰지 않는다(날짜 포함 관계만). */
class LaborAgreementRulesTest {

    private static final LocalDate JAN1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate DEC31 = LocalDate.of(2026, 12, 31);

    static LaborAgreementDtos.Fields fields(LocalDate from, LocalDate until, LaborAgreement.Scope scope, String site) {
        return new LaborAgreementDtos.Fields("2026 선택적 근로시간제 합의", from.minusDays(10), from, until, scope, site,
                "연구개발 직군 전원", LaborAgreement.RepType.MAJORITY_UNION, "가상노동조합", null, null, "문서함 A-1");
    }

    static LaborAgreement agreement(long id, AgreementType type, LocalDate from, LocalDate until,
            LaborAgreement.Scope scope, String site) {
        LaborAgreement a = new LaborAgreement(type, fields(from, until, scope, site), "hr1", LocalDateTime.of(2026, 1, 1, 0, 0));
        try {
            Field f = LaborAgreement.class.getDeclaredField("agreementId");
            f.setAccessible(true);
            f.set(a, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return a;
    }

    private static LaborAgreement all(LocalDate from, LocalDate until) {
        return agreement(1, AgreementType.SELECTIVE, from, until, LaborAgreement.Scope.ALL, null);
    }

    @Test
    void 유효기간_양_끝_날짜를_포함한다() {
        LaborAgreement a = all(JAN1, DEC31);
        assertThat(LaborAgreementRules.covers(a, AgreementType.SELECTIVE, JAN1, DEC31, null)).isTrue();
        assertThat(LaborAgreementRules.covers(a, AgreementType.SELECTIVE, DEC31, DEC31, null)).isTrue();
    }

    @Test
    void 시작_전날이나_종료_다음날까지_걸치면_유효하지_않다() {
        LaborAgreement a = all(JAN1, DEC31);
        assertThat(LaborAgreementRules.covers(a, AgreementType.SELECTIVE, JAN1.minusDays(1), DEC31, null)).isFalse();
        assertThat(LaborAgreementRules.covers(a, AgreementType.SELECTIVE, JAN1, DEC31.plusDays(1), null)).isFalse();
    }

    @Test
    void 윤년_2월_29일까지인_합의는_3월_1일_적용을_덮지_않는다() {
        LocalDate feb29 = LocalDate.of(2028, 2, 29);
        LaborAgreement a = all(LocalDate.of(2027, 3, 1), feb29);
        assertThat(LaborAgreementRules.covers(a, AgreementType.SELECTIVE, feb29, feb29, null)).isTrue();
        assertThat(LaborAgreementRules.covers(a, AgreementType.SELECTIVE, feb29, feb29.plusDays(1), null)).isFalse();
    }

    @Test
    void 끝_없는_적용은_끝_없는_합의만_덮는다() {
        assertThat(LaborAgreementRules.covers(all(JAN1, DEC31), AgreementType.SELECTIVE, JAN1, null, null)).isFalse();
        assertThat(LaborAgreementRules.covers(all(JAN1, null), AgreementType.SELECTIVE, JAN1, null, null)).isTrue();
        assertThat(LaborAgreementRules.covers(all(JAN1, null), AgreementType.SELECTIVE, DEC31, DEC31, null)).isTrue();
    }

    @Test
    void 다른_유형의_합의서는_인정하지_않는다() {
        assertThat(LaborAgreementRules.covers(all(JAN1, DEC31), AgreementType.DISCRETIONARY, JAN1, DEC31, null)).isFalse();
    }

    @Test
    void 사업장_한정_합의는_그_사업장만_덮고_회사_전체_설정은_덮지_않는다() {
        LaborAgreement s01 = agreement(2, AgreementType.SELECTIVE, JAN1, DEC31, LaborAgreement.Scope.SITE, "S01");
        assertThat(LaborAgreementRules.covers(s01, AgreementType.SELECTIVE, JAN1, DEC31, "S01")).isTrue();
        assertThat(LaborAgreementRules.covers(s01, AgreementType.SELECTIVE, JAN1, DEC31, "S02")).isFalse();
        assertThat(LaborAgreementRules.covers(s01, AgreementType.SELECTIVE, JAN1, DEC31, null)).isFalse();
        assertThat(LaborAgreementRules.covers(all(JAN1, DEC31), AgreementType.SELECTIVE, JAN1, DEC31, "S02")).isTrue();
    }

    @Test
    void 여러_합의서가_덮으면_사업장_한정_다음_최근_시작을_고른다() {
        LaborAgreement oldAll = agreement(1, AgreementType.SELECTIVE, JAN1, null, LaborAgreement.Scope.ALL, null);
        LaborAgreement newAll = agreement(2, AgreementType.SELECTIVE, JAN1.plusMonths(1), null, LaborAgreement.Scope.ALL, null);
        LaborAgreement site = agreement(3, AgreementType.SELECTIVE, JAN1, null, LaborAgreement.Scope.SITE, "S01");
        LocalDate d = LocalDate.of(2026, 6, 1);
        assertThat(LaborAgreementRules.pick(List.of(oldAll, newAll, site), AgreementType.SELECTIVE, d, d, "S01"))
                .contains(site);
        assertThat(LaborAgreementRules.pick(List.of(oldAll, newAll, site), AgreementType.SELECTIVE, d, d, "S02"))
                .contains(newAll);
        assertThat(LaborAgreementRules.pick(List.of(), AgreementType.SELECTIVE, d, d, null)).isEmpty();
    }

    @Test
    void 형식_검사() {
        assertThat(LaborAgreementRules.validate(fields(JAN1, DEC31, LaborAgreement.Scope.ALL, null))).isEmpty();
        assertThat(LaborAgreementRules.validate(fields(JAN1, JAN1.minusDays(1), LaborAgreement.Scope.ALL, null)))
                .contains("유효기간 종료일은 시작일 이후여야 해요");
        assertThat(LaborAgreementRules.validate(fields(JAN1, DEC31, LaborAgreement.Scope.SITE, null)))
                .contains("대상 사업장을 골라 주세요");
        LaborAgreementDtos.Fields noRep = new LaborAgreementDtos.Fields("t", JAN1, JAN1, null,
                LaborAgreement.Scope.ALL, null, "대상", LaborAgreement.RepType.MAJORITY_REP, null, null, null, "D");
        assertThat(LaborAgreementRules.validate(noRep)).containsExactly("근로자 과반수를 대표하는 사원을 골라 주세요");
        LaborAgreementDtos.Fields noUnion = new LaborAgreementDtos.Fields("t", JAN1, JAN1, null,
                LaborAgreement.Scope.ALL, null, "대상", LaborAgreement.RepType.MAJORITY_UNION, null, null, null, "D");
        assertThat(LaborAgreementRules.validate(noUnion)).containsExactly("노동조합 이름을 입력해 주세요");
        LaborAgreementDtos.Fields noDoc = new LaborAgreementDtos.Fields("t", JAN1, JAN1, null,
                LaborAgreement.Scope.ALL, null, "대상", LaborAgreement.RepType.MAJORITY_UNION, "노조", null, null, null);
        assertThat(LaborAgreementRules.validate(noDoc)).containsExactly("합의서 문서(파일 번호·보관 위치)를 입력해 주세요");
        // 시작일 하루짜리 합의(종료일 = 시작일)는 허용한다(경계).
        assertThat(LaborAgreementRules.validate(fields(JAN1, JAN1, LaborAgreement.Scope.ALL, null))).isEmpty();
    }

    @Test
    void 요청의_대표_구분과_맞지_않는_값은_버린다() {
        LaborAgreementDtos.AgreementRequest req = new LaborAgreementDtos.AgreementRequest(AgreementType.SELECTIVE,
                " 합의 ", JAN1, JAN1, null, LaborAgreement.Scope.ALL, "S01", " 전원 ",
                LaborAgreement.RepType.MAJORITY_REP, "노조", 7L, "  ", " 문서함 A-1 ");
        LaborAgreementDtos.Fields f = req.fields();
        assertThat(f.title()).isEqualTo("합의");
        assertThat(f.scopeSiteCode()).isNull();
        assertThat(f.repUnionName()).isNull();
        assertThat(f.repEmpId()).isEqualTo(7L);
        assertThat(f.note()).isNull();
        assertThat(f.documentRef()).isEqualTo("문서함 A-1");
    }
}
