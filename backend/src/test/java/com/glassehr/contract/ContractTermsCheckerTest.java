package com.glassehr.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassehr.contract.ContractTermsChecker.MissingTerm;
import com.glassehr.contract.EmploymentContract.Terms;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 명시사항 누락 경고 규칙. 법정 수치는 다루지 않는다(항목 유무만). */
class ContractTermsCheckerTest {

    private static final LocalDate D = LocalDate.of(2026, 3, 2);

    static Terms full(String type) {
        return new Terms(type, D, D, "REGULAR".equals(type) ? null : D.plusMonths(12).minusDays(1), 2_500_000L,
                "MONTHLY", "기본급, 식대", "월 기본급 + 수당", "매월 25일 계좌 이체", 2400, "09:00~18:00, 휴게 12:00~13:00",
                "월~금 각 8시간", "주휴일 일요일", "근로기준법에 따른 연차 유급휴가", "가상시 본사", "백엔드 개발", null);
    }

    private static List<String> codes(Terms t) {
        return ContractTermsChecker.check(t).stream().map(MissingTerm::code).toList();
    }

    @Test
    void 모두_적으면_경고가_없다() {
        assertThat(codes(full("REGULAR"))).isEmpty();
        assertThat(codes(full("FIXED_TERM"))).isEmpty();
        assertThat(codes(full("PART_TIME"))).isEmpty();
    }

    @Test
    void 정규직_필수_항목이_빠지면_항목마다_경고한다() {
        Terms t = new Terms("REGULAR", D, D, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null);
        assertThat(codes(t)).containsExactly("WAGE", "WAGE_DETAIL", "CONTRACT_HOURS", "HOLIDAY", "ANNUAL_LEAVE",
                "WORK_PLACE", "JOB_DUTY");
    }

    @Test
    void 근로시간_휴게는_기간제_단시간에만_필수다() {
        Terms base = full("REGULAR");
        Terms noHours = new Terms("REGULAR", D, D, null, base.wageAmount(), base.wageUnitCode(), base.wageComponents(),
                base.wageCalcMethod(), base.wagePaymentMethod(), 2400, " ", null, base.holidayText(), base.leaveText(),
                base.workPlace(), base.jobDuty(), null);
        assertThat(codes(noHours)).isEmpty();

        Terms fixed = full("FIXED_TERM");
        Terms fixedNoHours = new Terms("FIXED_TERM", D, D, fixed.endDate(), fixed.wageAmount(), fixed.wageUnitCode(),
                fixed.wageComponents(), fixed.wageCalcMethod(), fixed.wagePaymentMethod(), 2400, null, null,
                fixed.holidayText(), fixed.leaveText(), fixed.workPlace(), fixed.jobDuty(), null);
        assertThat(codes(fixedNoHours)).containsExactly("WORK_HOURS_BREAK");
    }

    @Test
    void 근로일별_근로시간은_단시간에만_필수다() {
        Terms p = full("PART_TIME");
        Terms noDays = new Terms("PART_TIME", D, D, p.endDate(), p.wageAmount(), p.wageUnitCode(), p.wageComponents(),
                p.wageCalcMethod(), p.wagePaymentMethod(), 1200, p.workHoursText(), null, p.holidayText(),
                p.leaveText(), p.workPlace(), p.jobDuty(), null);
        assertThat(codes(noDays)).containsExactly("WORK_DAYS");

        Terms f = full("FIXED_TERM");
        Terms fixedNoDays = new Terms("FIXED_TERM", D, D, f.endDate(), f.wageAmount(), f.wageUnitCode(),
                f.wageComponents(), f.wageCalcMethod(), f.wagePaymentMethod(), 2400, f.workHoursText(), null,
                f.holidayText(), f.leaveText(), f.workPlace(), f.jobDuty(), null);
        assertThat(codes(fixedNoDays)).isEmpty();
    }

    @Test
    void 임금_구성항목_계산_지급방법은_하나라도_빠지면_경고한다() {
        Terms f = full("REGULAR");
        Terms noPay = new Terms("REGULAR", D, D, null, f.wageAmount(), f.wageUnitCode(), f.wageComponents(),
                f.wageCalcMethod(), null, 2400, null, null, f.holidayText(), f.leaveText(), f.workPlace(), f.jobDuty(),
                null);
        assertThat(codes(noPay)).containsExactly("WAGE_DETAIL");
    }

    @Test
    void 임금은_금액과_지급단위가_모두_있어야_한다() {
        Terms f = full("REGULAR");
        Terms noUnit = new Terms("REGULAR", D, D, null, f.wageAmount(), null, f.wageComponents(), f.wageCalcMethod(),
                f.wagePaymentMethod(), 2400, null, null, f.holidayText(), f.leaveText(), f.workPlace(), f.jobDuty(),
                null);
        assertThat(codes(noUnit)).containsExactly("WAGE");
    }

    @Test
    void 모든_경고에_근거_조문이_있다() {
        Terms empty = new Terms("PART_TIME", D, D, D, null, null, null, null, null, null, null, null, null, null, null,
                null, null);
        List<MissingTerm> all = ContractTermsChecker.check(empty);
        assertThat(all).hasSize(9);
        assertThat(all).allSatisfy(m -> {
            assertThat(m.label()).isNotBlank();
            assertThat(m.basis()).containsPattern("제\\d+조");
        });
        assertThat(all.stream().filter(m -> m.code().equals("WORK_DAYS")).findFirst().orElseThrow().basis())
                .contains("기간제법 제17조");
    }
}
