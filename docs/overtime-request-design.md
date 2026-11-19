# 연장·휴일근로 사전 신청과 승인 (#30)

## 1. 흐름
1. 직원이 `POST /api/overtime-requests`로 신청한다(시작 시각이 지금 이후인 사전 신청만, 같은 시간대 결재 중·승인 건이 있으면 409).
2. 신청을 저장하고 공통 결재(`ApprovalService.submit`, 유형 `OVERTIME`)로 올린다. 결재선이 없거나 결재자를 못 찾으면 신청도 되돌린다(422).
3. 결재자는 `/api/approvals`(결재함)에서 승인·반려, 신청자는 첫 승인 전 회수한다.
4. 마지막 승인 때 `OvertimeApprovalHandler.onApproved`가 같은 트랜잭션에서 한도를 검사하고, 넘으면 예외로 승인 자체를 되돌린다.

## 2. 테이블 (V025)
- `overtime_request`: 근로 구분(OVERTIME/HOLIDAY), 근로일, 시작·종료, 휴게, 예정 근로분(=종료−시작−휴게, CHECK로 재확인), 사유, 상태(결재 결과).
- `special_overtime_auth`(+`_emp`): 특별연장근로 인가 정보(근로기준법 제53조 제4항). 사전 인가/사후 승인 구분, 인가 번호, 관할 관서, 인가일, 기간, 사유, 근로자 동의(Y만 허용), 대상 사원. **입력·조회만** 하고 한도 검사와 연결하지 않는다(이슈 범위).

## 3. 집계 엔진 참조
`ApprovedOvertimeProvider`(attendance)를 `JpaApprovedOvertimeProvider`(overtime)가 구현한다. 재계산 시 연장·휴일근로 실적이 있는데 그날 승인된 신청이 없으면 `OVERTIME_NOT_APPROVED` 확인 표시를 남긴다(계산값은 바꾸지 않음).

## 4. 1주 연장근로 한도 검사
- 근거: 근로기준법 제53조 제1항(1주 12시간 한도), legal_rate `WEEKLY_OVERTIME_LIMIT_HOURS`(근로일 기준 적용값). 값이 없으면 409로 승인하지 않는다.
- 1주: 제2조 제1항 제7호(휴일 포함 7일). 집계 엔진과 같은 월~일.
- 합계 = 날마다 max(실적 `overtime_minutes`, 승인된 OVERTIME 신청 + 이번 신청 예정분)의 주 합. 같은 근로를 두 번 세지 않는다.
- **미확정**: 휴일근로(HOLIDAY 신청·`holiday_minutes`)를 한도 합계에 넣을지, 실적·예정 합산 방식이 법령 해석과 맞는지는 정답 fixture(`tests/legal-fixtures/weekly-overtime-limit.csv`)를 받아 확정한다. 그 전까지 HOLIDAY 신청은 한도 검사 없이 승인된다.

## 5. 결재 정책 (2026-11-19 사람 답변 1번안)
결재자를 못 찾으면 신청 거부, ROLE·POSITION 지정은 후보 중 아무나 한 명이 결재. 대결·위임·전결·병렬 결재는 넣지 않음.

## 6. 조문 확인
law.go.kr 원문 대조는 이 실행 환경에서 하지 못했다(미확인). PR 본문에 확인 날짜와 함께 적어야 한다.
