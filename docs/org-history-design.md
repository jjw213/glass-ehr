# 조직 변경 이력 관리 설계안 (#8)

결정: **유효기간 방식**. 부서마다 시작일·종료일을 두어 과거 어느 날짜의 조직도도 조회할 수 있게 한다.
(2026-11-11 사람 답변. 인사발령·근태 집계와 기준일을 맞추기 쉬움)

## 1. 기간 규칙 (`legal_rate`와 동일)
- 날짜 단위, 시작일 포함·종료일 제외 반개구간 `[valid_from, valid_to)`. 날짜는 `Asia/Seoul` 기준.
- `valid_to = NULL`은 현재 유효(종료일 미정).
- 같은 부서의 기간은 겹치지 않는다. 바뀔 때는 기존 행의 `valid_to`를 새 행의 `valid_from`과 같은 날로 맞춘다.
- 기준일 `d`의 조회 조건: `valid_from <= d AND (valid_to IS NULL OR d < valid_to)`.

## 2. 테이블 구조 (초안)
부서의 **정체성**과 **시점별 속성**을 나눈다.

- `org_dept` — 부서 정체성. `dept_id`(불변), `dept_code`, `valid_from`(신설일), `valid_to`(폐지일, NULL이면 존속).
- `org_dept_hist` — 시점별 속성. `dept_id`, `valid_from`, `valid_to`, `dept_name`, `parent_dept_id`, `sort_order`, `change_type`, `change_reason`, `created_at`, `created_by`.
  - `org_dept_hist`의 기간은 `org_dept`의 기간 안에 있어야 한다.
  - 상위 부서(`parent_dept_id`)도 그 기간에 유효해야 한다(쓰기 시 검사).
- `org_dept_succession` — 통폐합 계보. `from_dept_id`, `to_dept_id`, `effective_date`, `change_type`(MERGE/SPLIT).

이력은 수정하지 않고 추가로 쌓는다(SPEC 1장 "이력은 추가로 관리"와 같음). 잘못 입력한 경우의 정정도 감사 로그를 남긴다.

## 3. 변경 유형별 처리
| 유형 | 처리 |
|---|---|
| 신설 | `org_dept` 새 행 + `org_dept_hist` 첫 행 (`change_type = CREATE`) |
| 이름 변경 | `dept_id` 유지. 현재 hist 행을 닫고 새 이름으로 새 행 (`RENAME`) |
| 상위 부서 이동 | `dept_id` 유지. 현재 hist 행을 닫고 새 `parent_dept_id`로 새 행 (`MOVE`) |
| 폐지 | `org_dept.valid_to`와 마지막 hist 행의 `valid_to`를 폐지일로 (`CLOSE`) |
| 통폐합 (A+B→C) | A·B를 폐지하고 C를 신설(또는 존속 부서 C의 이력 추가). `org_dept_succession`에 A→C, B→C 기록 |
| 분할 (A→B+C) | A를 폐지하고 B·C 신설. `org_dept_succession`에 A→B, A→C 기록 |

폐지되는 부서에 소속 사원이 남아 있으면 폐지를 막는다. 소속 사원은 인사발령(부서이동)으로 먼저 옮긴다.

## 4. 다른 모듈과의 연결
- 인사발령·근태·급여 집계는 `dept_id`와 기준일을 함께 저장하고, 부서명은 기준일로 `org_dept_hist`에서 찾는다. 이름이 바뀌어도 과거 기록은 그 당시 이름으로 보인다.
- 통폐합 전후 비교 집계는 `org_dept_succession`을 따라 묶는다.

## 5. 구현 시 테스트할 것 (구현 이슈에서)
- 경계일: 조회일 = `valid_from`이면 포함, = `valid_to`이면 제외.
- 겹치는 기간 쓰기 거부, 맞닿는 기간 쓰기 허용.
- 이름 변경 전날·당일 조회 결과가 각각 옛 이름·새 이름.
- 폐지일 이후 조직도에 그 부서가 없음. 유효하지 않은 상위 부서 지정 거부.

DDL·JPA 엔티티·조회 SQL은 별도 구현 이슈에서 만든다.
