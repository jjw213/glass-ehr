package com.glassehr.retirement;

import com.glassehr.code.CodeSysTypes.RetirementReason;
import com.glassehr.retirement.DismissalNoticeChecker.NoticeCheck;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 퇴직 유형별 체크리스트 템플릿. 업무 절차 목록이라 법정 수치를 담지 않는다.
 * 근거 조문이 있는 항목만 {@code legalBasis}를 적는다. 목록 구성은 docs/retirement-design.md 2장.
 * 유형은 퇴직사유 코드값이 아니라 그 코드의 처리방식(#141, {@link RetirementReason})으로 고른다.
 */
public final class RetirementChecklist {

    public record Item(String code, int sortOrder, String label, String legalBasis) {
    }

    private RetirementChecklist() {
    }

    private static final List<Item> COMMON = List.of(
            new Item("HANDOVER", 900, "업무 인수인계 완료", null),
            new Item("ASSET_RETURN", 910, "회사 자산·출입증·계정 반납", null),
            new Item("SETTLEMENT", 920, "임금·퇴직금 등 금품 청산 기한 확인(급여 모듈)", "근로기준법 제36조"),
            new Item("INSURANCE_LOSS", 930, "4대보험 자격상실 신고", null),
            new Item("CERTIFICATE_GUIDE", 940, "사용증명서 청구 방법 안내", "근로기준법 제39조"),
            new Item("RECORD_RETENTION", 950, "근로자 명부·계약 서류 보존기한 확인", "근로기준법 제42조"));

    /**
     * 유형별 항목 + 공통 항목(정렬 순). 해고는 예고·서면통지가 입력 필수라 등록 때 완료로 표시한다({@link #autoDone}).
     *
     * @param type        퇴직사유 코드의 처리방식
     * @param noticeCheck 해고 예고 확인 결과. 미달·확인 불가면 해고예고수당 검토 항목을 넣는다.
     */
    public static List<Item> forReason(RetirementReason type, NoticeCheck noticeCheck) {
        List<Item> items = new ArrayList<>();
        switch (type) {
            case VOLUNTARY -> items.add(new Item("RESIGNATION_LETTER", 10, "사직서 접수 확인", null));
            case RECOMMENDED -> {
                items.add(new Item("RESIGNATION_LETTER", 10, "사직서 접수 확인", null));
                items.add(new Item("RESIGNATION_AGREEMENT", 20, "권고사직 합의 내용 서면 보관", null));
            }
            case CONTRACT_END -> items.add(
                    new Item("CONTRACT_END_CHECK", 10, "근로계약 만료일·갱신 여부 확인", "근로기준법 제17조"));
            case RETIREMENT_AGE -> items.add(new Item("RETIREMENT_AGE_CHECK", 10, "취업규칙상 정년 도래일 확인", null));
            case DISMISSAL -> {
                items.add(new Item("DISMISSAL_CAUSE", 10, "해고의 정당한 이유 검토 자료 보관", "근로기준법 제23조 제1항"));
                items.add(new Item("DISMISSAL_NOTICE", 20, "해고 예고(예고일 기록)", "근로기준법 제26조"));
                items.add(new Item("DISMISSAL_WRITTEN", 30, "해고사유·해고시기 서면 통지(통지일 기록)", "근로기준법 제27조"));
                if (noticeCheck == NoticeCheck.SHORT || noticeCheck == NoticeCheck.UNKNOWN) {
                    items.add(new Item("NOTICE_ALLOWANCE", 40, "해고예고수당 지급 대상 검토(급여 모듈)", "근로기준법 제26조"));
                }
            }
            case TRANSFER -> items.add(new Item("TRANSFER_CONSENT", 10, "전적 동의서 확인", null));
            case DEATH -> items.add(new Item("HEIR_SETTLEMENT", 10, "유족 확인과 금품 청산 안내", null));
            case OTHER -> {
                // 기타: 공통 항목만
            }
        }
        items.addAll(COMMON);
        items.sort(Comparator.comparingInt(Item::sortOrder));
        return List.copyOf(items);
    }

    /** 등록 입력으로 이미 확인된 항목(해고 예고일·서면통지일 입력 필수). */
    public static boolean autoDone(String itemCode) {
        return "DISMISSAL_NOTICE".equals(itemCode) || "DISMISSAL_WRITTEN".equals(itemCode);
    }
}
