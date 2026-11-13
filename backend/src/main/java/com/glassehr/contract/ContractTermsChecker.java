package com.glassehr.contract;

import com.glassehr.contract.EmploymentContract.Terms;
import java.util.ArrayList;
import java.util.List;

/**
 * 근로계약 명시사항 누락 검사. 누락이 있어도 저장은 막지 않고 경고 목록을 돌려준다(이슈 #17).
 *
 * <p>조문 대응(2026-11-12 law.go.kr 자동 조회 실패로 원문 미확인. 사람이 원문으로 다시 확인해야 한다)
 * <ul>
 *   <li>근로기준법 제17조 제1항 제1호 임금, 제2호 소정근로시간, 제3호 제55조 휴일, 제4호 제60조 연차 유급휴가</li>
 *   <li>근로기준법 시행령 제8조 제1호: 취업의 장소와 종사하여야 할 업무</li>
 *   <li>근로기준법 제17조 제2항: 임금의 구성항목·계산방법·지급방법을 서면으로 명시</li>
 *   <li>기간제법 제17조(기간제·단시간근로자): 제2호 근로시간·휴게, 제6호 근로일 및 근로일별 근로시간(단시간근로자)</li>
 * </ul>
 * 기간제법 제17조 제1호 근로계약기간은 시작일이 필수이고 기간제는 종료일도 필수라(저장 거부) 여기서 다시 검사하지 않는다.
 * 시행령 제8조 제2호(취업규칙 사항)·제3호(기숙사 규칙)는 취업규칙·기숙사 기능이 없어 검사하지 않는다(other_terms 자유 기재).
 */
public final class ContractTermsChecker {

    static final String FIXED_TERM = "FIXED_TERM";
    static final String PART_TIME = "PART_TIME";

    /** 누락된 명시사항. code 는 화면이 쓰는 키, basis 는 근거 조문. */
    public record MissingTerm(String code, String label, String basis) {
    }

    private ContractTermsChecker() {
    }

    public static List<MissingTerm> check(Terms t) {
        List<MissingTerm> out = new ArrayList<>();
        boolean fixedOrPart = FIXED_TERM.equals(t.contractTypeCode()) || PART_TIME.equals(t.contractTypeCode());
        if (t.wageAmount() == null || t.wageUnitCode() == null) {
            out.add(new MissingTerm("WAGE", "임금(금액·지급 단위)", "근로기준법 제17조 제1항 제1호"));
        }
        if (blank(t.wageComponents()) || blank(t.wageCalcMethod()) || blank(t.wagePaymentMethod())) {
            out.add(new MissingTerm("WAGE_DETAIL", "임금의 구성항목·계산방법·지급방법",
                    fixedOrPart ? "근로기준법 제17조 제2항, 기간제법 제17조 제3호" : "근로기준법 제17조 제2항"));
        }
        if (t.weeklyContractMinutes() == null) {
            out.add(new MissingTerm("CONTRACT_HOURS", "소정근로시간", "근로기준법 제17조 제1항 제2호"));
        }
        if (fixedOrPart && blank(t.workHoursText())) {
            out.add(new MissingTerm("WORK_HOURS_BREAK", "근로시간·휴게", "기간제법 제17조 제2호"));
        }
        if (blank(t.holidayText())) {
            out.add(new MissingTerm("HOLIDAY", "휴일", fixedOrPart
                    ? "근로기준법 제17조 제1항 제3호, 기간제법 제17조 제4호" : "근로기준법 제17조 제1항 제3호"));
        }
        if (blank(t.leaveText())) {
            out.add(new MissingTerm("ANNUAL_LEAVE", "연차 유급휴가", fixedOrPart
                    ? "근로기준법 제17조 제1항 제4호, 기간제법 제17조 제4호" : "근로기준법 제17조 제1항 제4호"));
        }
        if (blank(t.workPlace())) {
            out.add(new MissingTerm("WORK_PLACE", "취업의 장소", fixedOrPart
                    ? "근로기준법 시행령 제8조 제1호, 기간제법 제17조 제5호" : "근로기준법 시행령 제8조 제1호"));
        }
        if (blank(t.jobDuty())) {
            out.add(new MissingTerm("JOB_DUTY", "종사하여야 할 업무", fixedOrPart
                    ? "근로기준법 시행령 제8조 제1호, 기간제법 제17조 제5호" : "근로기준법 시행령 제8조 제1호"));
        }
        if (PART_TIME.equals(t.contractTypeCode()) && blank(t.workDaysText())) {
            out.add(new MissingTerm("WORK_DAYS", "근로일 및 근로일별 근로시간", "기간제법 제17조 제6호"));
        }
        return List.copyOf(out);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
