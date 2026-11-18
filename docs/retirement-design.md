# 퇴직 처리·사용증명서·서류 보존기한 설계 (#18)

퇴직금 계산은 급여 모듈에서 한다. 이 문서는 인사·조직 쪽 퇴직 절차만 다룬다.

## 1. 범위
- 퇴직 처리 등록(`POST /api/retirements`): 근로자 명부 퇴직 기록(`EmployeeService.retire`, #14) + 유형별 체크리스트 + 해고 예고 확인을 한 트랜잭션으로 저장.
- 체크리스트 완료 표시(`PUT /api/retirements/{empId}/checklist/{itemCode}`), 유형별 미리보기(`GET /api/retirements/checklist-template`).
- 사용증명서 발급·이력·인쇄 화면(`/api/employees/{empId}/employment-certificates`, `/api/employment-certificates/{certId}`).
- 보존기한이 지난 퇴직자 목록(`GET /api/retirements/retention-expired?asOf=`).
- 스키마 V022: `retirement_case`, `retirement_checklist_item`, `employment_certificate`, legal_rate 항목 2개(값 없음).
- 화면: `퇴직 처리`(인사·관리자) — 등록 폼, 체크리스트, 사용증명서 발급·인쇄(브라우저 인쇄로 PDF 저장), 보존기한 경과 목록.

## 2. 체크리스트 (`RetirementChecklist`)
| 유형(`RETIREMENT_REASON`) | 유형별 항목 |
|---|---|
| VOLUNTARY 자진 | 사직서 접수 |
| RECOMMENDED_RESIGNATION 권고사직 | 사직서 접수, 합의 내용 서면 보관 |
| CONTRACT_EXPIRED 계약만료 | 계약 만료일·갱신 여부 확인(제17조) |
| RETIREMENT_AGE 정년 | 취업규칙상 정년 도래일 확인 |
| DISMISSAL 해고 | 정당한 이유 검토 자료(제23조 제1항), 해고 예고(제26조), 서면 통지(제27조), 예고 미달·확인 불가면 해고예고수당 검토 |
| TRANSFER_AFFILIATE 전적 | 전적 동의서 |
| DEATH 사망 | 유족 확인·금품 청산 안내 |
| 그 밖 | 공통 항목만 |

공통 항목: 인수인계, 자산 반납, 금품 청산 기한(제36조, 급여 모듈), 4대보험 상실 신고, 사용증명서 안내(제39조), 보존기한 확인(제42조).
해고의 예고·서면통지 항목은 등록 때 날짜가 필수라 등록 시 완료로 표시한다.

## 3. 해고 예고 확인 (`DismissalNoticeChecker`)
- 해고면 예고일·서면통지일 필수(400), 둘 다 해고일(퇴직일) 이하. DB 제약도 같다.
- 결과 `NOT_APPLICABLE`(해고 아님)·`OK`·`SHORT`(미달, 경고+미달 일수)·`EXEMPT`(제26조 단서)·`UNKNOWN`(기준값 없음). **경고만 하고 등록을 막지 않는다.**
- 기준값은 legal_rate `DISMISSAL_NOTICE_DAYS`(DAYS), `DISMISSAL_NOTICE_EXEMPT_SERVICE_MONTHS`(MONTHS)를 **해고일 기준**으로 조회. 값이 없으면 기본값을 쓰지 않고 `UNKNOWN`.
- 날짜 셈(정답 fixture 확인 전 가정):
  - 예고 일수 = `DAYS.between(예고일, 해고일)`(예고일 다음 날부터 해고일까지). 예고 일수 ≥ 기준 일수면 충족.
  - 단서 제1호(계속 근로 M개월 미만) = 해고일이 M개월 기간의 만료일보다 앞. 만료일은 민법 제157조 단서(초일 산입)·제160조 제2항(해당일 전일)·제3항(해당일이 없으면 그 월 말일)로 정한다. 예: 1월 1일 입사 3개월 → 3월 31일, 1월 31일 입사 3개월 → 4월 30일, 2024-02-29 입사 3개월 → 5월 28일. 정답은 `tests/legal-fixtures/dismissal-notice.csv`(`DismissalNoticeFixtureTest`).
  - 법률 제16270호 부칙 제2조: 개정 단서 제1호는 시행(2019-01-15, legal_rate `DISMISSAL_NOTICE_EXEMPT_SERVICE_MONTHS` 적용 시작일) 후 근로계약 체결자부터 적용한다. 입사일을 계약 체결일로 보고, 입사일에 그 기준값이 없으면 제1호 적용 제외를 인정하지 않고 예고 기간으로 다시 확인하며 구법 사유는 따로 확인하라고 경고한다.
- 단서 제2호(부득이한 사유)·제3호(근로자 귀책)는 사람이 선택한 기록으로만 남기고 증빙 보관 경고를 띄운다.

## 4. 사용증명서 (`issueCertificate`)
- 근로기준법 제39조 제1항 항목(사용 기간·업무 종류·지위·임금) + 퇴직 사유 중 **근로자가 요구한 항목만** 기재(같은 조 제2항). 요구하지 않은 항목은 값을 비워 저장한다.
- 성명은 증명 대상 표시로 항상 적는다. 주민등록번호 등 고유식별정보는 적지 않는다.
- 임금은 요청일 기준 최신 근로계약 조건(원 단위 정수). 재직 중이면 사용 기간 끝을 비우고 '현재 재직 중'으로 표시.
- 발급 당시 기재 값을 그대로 남긴다(이후 사원 정보가 바뀌어도 재출력 결과 동일). 행을 지우지 않는다.

## 5. 보존기한
- 보존기한은 #14의 `employee.record_retention_until`(퇴직일 + legal_rate `EMPLOYEE_RECORD_RETENTION_YEARS`)을 그대로 쓴다.
- 보존기한일은 보존하는 마지막 날이라 `record_retention_until < asOf`인 퇴직자만 목록에 넣는다(경과 일수 포함). 자동 파기는 하지 않는다.

## 6. 권한
조회는 `EMP_READ_ALL`(관리자·인사·급여), 등록·발급·완료 표시는 `EMP_WRITE`(관리자·인사).

## 7. 미결·미확인
- 근로기준법 제26조·제27조·제39조·제42조 원문을 law.go.kr에서 아직 다시 확인하지 못했다(PR 전 확인 필요).
- legal_rate `DISMISSAL_NOTICE_DAYS`·`DISMISSAL_NOTICE_EXEMPT_SERVICE_MONTHS` 값과 예고 일수 셈의 정답 fixture가 `tests/legal-fixtures/`에 없다. 들어오면 fixture 테스트를 추가한다.
- 퇴직 정정·취소, 보존기한 경과 후 파기 절차는 후속 이슈.
