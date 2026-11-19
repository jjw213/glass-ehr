# 육아휴직·육아기 근로시간 단축·가족돌봄휴직 (#37)

근거: 남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제19조(육아휴직), 제19조의2(육아기 근로시간 단축),
제19조의3(단축 중 근로조건), 제19조의4(분할 사용), 제22조의2(가족돌봄휴직).
조문 원문(law.go.kr) 재확인은 이 실행 환경에서 하지 못했다 — PR 본문에 **미확인**으로 적는다.

## 1. 테이블 (V026 — main의 V023(#29), 열린 PR #100의 V024·V025와 겹치지 않게 번호를 바꿨다. 시드 주석의 V023은 이 파일을 가리킨다)
- `family_leave_request`: 신청 1건 = 1행. 상태 `REQUESTED → APPROVED / REJECTED / CANCELLED`, 행을 지우지 않는다.
- 자녀는 `child_label`(예: 첫째)과 생년월일만 둔다. 실명·주민등록번호는 넣지 않는다.
- 승인 시 만든 발령(`leave_appt_id`, `return_appt_id`)·근무유형 배정(`reduced_assign_id`, `restore_assign_id`) ID를 남긴다.

## 2. legal_rate 항목
기존 항목: `PARENTAL_LEAVE_MAX_MONTHS`, `PARENTAL_LEAVE_EXTENSION_MONTHS`, `PARENTAL_LEAVE_SPLIT_COUNT`,
`CHILDCARE_REDUCED_HOURS_MAX_MONTHS`, `CHILDCARE_REDUCED_WEEKLY_HOURS_MIN/MAX`, `FAMILY_CARE_LEAVE_OF_ABSENCE_DAYS_PER_YEAR`.

V023 신규(코드만, 값 없음 — 법령 데이터 요청 대상):
`PARENTAL_LEAVE_CHILD_MAX_AGE`, `PARENTAL_LEAVE_MIN_SERVICE_MONTHS`, `CHILDCARE_REDUCED_CHILD_MAX_AGE`,
`CHILDCARE_REDUCED_MIN_SERVICE_MONTHS`, `CHILDCARE_REDUCED_UNUSED_LEAVE_MULTIPLIER`, `CHILDCARE_REDUCED_MIN_UNIT_MONTHS`,
`FAMILY_CARE_LEAVE_MIN_SERVICE_MONTHS`, `FAMILY_CARE_LEAVE_MIN_UNIT_DAYS`.

모두 신청 시작일 기준으로 조회한다.

## 3. 규칙
- 한도(총 기간·분할 횟수·주 소정근로시간 범위·1회 최소 기간·연간 일수) 위반은 거부한다. 필요한 기준값이 없으면 409.
- 자격(자녀 나이, 계속근로기간)은 사업주 판단·서류 확인이 남아 거부하지 않고 `warnings`로 남긴다. 기준값이 없으면 "확인 불가" 경고.
- 승인
  - 육아휴직·가족돌봄휴직: 시작일 확정 휴직(LEAVE) 발령 + 종료일 다음 날 미확정 복직(RETURN) 발령.
  - 육아기 근로시간 단축: 시작일 단축 근무유형 배정 + 종료일 다음 날 원래 근무유형 배정.
    근태 집계는 `WorkPatternService#effective`로 날짜별 근무유형을 읽으므로 단축 기간에는 단축 근무유형이 쓰인다.
- 남은 기간(`GET /api/employees/{empId}/family-leave-balance`): 자녀별 육아휴직·단축 남은 기간과 분할 횟수, 연도별 가족돌봄휴직 남은 일수.
  기준값이 없으면 남은 값 대신 안내 문구를 돌려준다.

## 4. API
`POST/GET /api/employees/{empId}/family-leaves`, `GET /api/family-leaves[?status]`, `GET /api/family-leaves/{reqId}`,
`POST /api/family-leaves/{reqId}/approve|reject|cancel`.

## 5. 남은 일
- V023 신규 항목의 legal_rate 시드 값과 `tests/legal-fixtures/` 정답(남은 기간·분할·경계일) — 법령 데이터 요청.
