package com.glassehr.approval;

/**
 * 결재 결과를 받는 모듈 쪽 구현(docs/approval-line-design.md §5). 신청 유형마다 하나의 빈을 둔다.
 * 결재 기능은 모듈 데이터를 직접 바꾸지 않고, 이 콜백으로 알린다. 콜백은 결재 처리와 같은 트랜잭션에서 돈다.
 * 콜백이 예외를 던지면 결재 처리도 되돌려진다(예: 승인 시점 법정 한도 검사에 걸리면 승인되지 않음).
 * enterCd 는 결재 문서의 법인이다(#127). 원본 신청도 이 법인 안에서만 찾는다.
 */
public interface ApprovalCompletionHandler {

    /** 신청 유형 코드(대문자·밑줄). 템플릿·결재 문서의 request_type. */
    String requestType();

    /** 화면 표시 이름. */
    String label();

    /** 결재자가 검토할 원본 신청 내용. 원본이 없으면 {@code null}. */
    default Object requestDetail(String enterCd, long requestRef) {
        return null;
    }

    /** 마지막 단계까지 승인됐다. */
    void onApproved(String enterCd, long requestRef);

    /** 반려됐다. */
    void onRejected(String enterCd, long requestRef);

    /** 신청자가 회수했다. */
    void onWithdrawn(String enterCd, long requestRef);
}
