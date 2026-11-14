# 근로자대표 서면합의서 관리 (#27)

## 1. 목적
탄력적·선택적 근로시간제, 재량근로, 보상휴가제, 연차 유급휴가 대체, 휴일 대체는 근로자대표와의 서면합의가 전제다.
합의서를 등록·보관하고, 제도를 켜거나 배정하는 API가 유효한 합의서가 있는지 같은 함수로 확인한다.

## 2. 합의 유형 (`AgreementType`)
| 코드 | 제도 | 근거 |
|---|---|---|
| `FLEXIBLE_WRITTEN` | 탄력적 근로시간제(3개월 이내) | 근로기준법 제51조 제2항 |
| `FLEXIBLE_LONG` | 탄력적 근로시간제(3개월 초과) | 근로기준법 제51조의2 |
| `SELECTIVE` | 선택적 근로시간제 | 근로기준법 제52조 |
| `HOLIDAY_SUBSTITUTION` | 휴일 대체 | 근로기준법 제55조 제2항 단서 |
| `COMPENSATORY_LEAVE` | 보상휴가제 | 근로기준법 제57조 |
| `DISCRETIONARY` | 재량근로 | 근로기준법 제58조 제3항 |
| `ANNUAL_LEAVE_SUBSTITUTE` | 연차 유급휴가 대체 | 근로기준법 제62조 |

근로자대표(근로기준법 제24조 제3항): `MAJORITY_UNION`(근로자 과반수로 조직된 노동조합, 노조 이름) /
`MAJORITY_REP`(근로자 과반수를 대표하는 자, 사원 참조 `rep_emp_id`).

## 3. 저장 (V020)
- `worker_rep_agreement`(V017 #26이 휴일 대체용으로 만든 것)를 넓힌다: 유형 CHECK 확대, 대상 범위(`scope_type` ALL/SITE,
  `scope_site_code`, `target_desc`), 근로자대표, 비고, 수정자·수정일시.
- 유효기간 저장은 V017 그대로 `[effective_from, effective_to)`. API·화면은 마지막 유효일 `validUntil`(포함)로 주고받는다.
- 첨부파일은 `worker_rep_agreement_file`에 BLOB + SHA-256. PDF·PNG·JPG만, 서버가 파일 앞부분(매직 바이트)으로 다시 판정.
  1건 최대 크기는 `glassehr.agreement.max-file-bytes`(운영 설정).
- 합의 유형은 등록 후 바꿀 수 없다(바꾸려면 새 합의서).

## 4. 유효 합의서 확인 (공통 함수)
`LaborAgreementGuard.check(type, from, until, siteCode)` / `requireValid(...)`(없으면 422).
판정은 `LaborAgreementRules.covers`:
- 유형이 같고, 합의 시작일 ≤ 적용 시작일, 합의가 끝이 없거나 적용 종료일 ≤ 합의 마지막 유효일(양 끝 포함).
  끝 없는 적용은 끝 없는 합의만 덮는다.
- 대상: ALL 합의는 모든 사업장, SITE 합의는 같은 사업장만. 회사 전체 설정(siteCode 없음)은 ALL 합의만.
- 여러 건이면 사업장 한정 → 늦게 시작한 합의 순으로 고른다.
화면 확인용 `GET /api/attendance/agreements/check`도 같은 함수를 쓴다.
휴일 대체(#26 `HolidayService`)는 이미 같은 테이블의 합의서를 검사한다.
근무유형(#25 `work_pattern`)에는 아직 합의서 연결 열이 없어 이 이슈에서는 연결하지 않았다(후속 이슈).

## 5. 만료 예정 알림
`GET /api/attendance/agreements/expiring?days=` — 오늘(Asia/Seoul)부터 days일 안(오늘·마지막 날 포함)에 마지막 유효일이 오는 합의서.
기본 일수는 `glassehr.agreement.expiry-alert-days`(기본 30, 법정 수치가 아닌 운영 설정). 화면 상단에 알림 카드로 보인다.

## 6. 권한
조회는 `EMP_READ_ALL`, 등록·수정·첨부는 `EMP_WRITE`(근무유형 #25와 같은 구분). 메뉴는 인사담당자·관리자에게만 보인다.

## 7. 미확인 사항
- law.go.kr 점검 등으로 이 이슈에서 인용한 조문(제24조 제3항, 제51조 제2항, 제51조의2, 제52조, 제55조 제2항 단서,
  제57조, 제58조 제3항, 제62조)의 현행 원문 대조를 아직 하지 못했다. PR 본문에 확인 날짜를 적기 전에 사람이 확인해야 한다.
- 조문별 서면합의 필수 기재 사항(예: 제51조 제2항 각 호, 제52조 각 호) 검사는 하지 않는다. 각 제도 구현 이슈에서 다룬다.
- 이 기능은 법령 수치를 계산하지 않아 `legal_rate`·`tests/legal-fixtures/`가 필요 없다.
