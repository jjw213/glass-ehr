# 공통코드 (#11)

직급·직책·직군·고용형태·근무지·퇴직사유처럼 화면 선택지로 쓰는 코드를 한 곳에서 관리한다.

## 테이블 (`backend/src/main/resources/db/oracle/V006__common_code.sql`)

| 테이블 | 키 | 주요 컬럼 |
|---|---|---|
| `common_code_group` | `group_code` | `group_name`, `description`, `sort_order`, `use_yn` |
| `common_code` | (`group_code`, `code`) → 그룹 FK | `code_name`, `description`, `sort_order`, `use_yn` |

- 코드값·그룹코드: `^[A-Z][A-Z0-9_]{0,29}$` (백엔드 Bean Validation, 프론트 `validateCode`가 같은 규칙)
  - DB는 같은 규칙을 CHECK `REGEXP_LIKE(.., '^[A-Z][A-Z0-9_]*$', 'c')` + 컬럼 `VARCHAR2(30 CHAR)`로 건다.
    Oracle JDBC가 SQL 속 `{..}`를 SQL92 escape로 해석하므로 DDL 정규식에 중괄호를 쓰지 않는다.
- `use_yn`: `'Y'`/`'N'` (CHECK). JPA에서는 `YnConverter`로 boolean
- `sort_order`: 0 이상 정수, 같으면 코드값 순
- **삭제하지 않는다.** 사원·발령 이력이 코드를 참조하므로 사용 중지(`use_yn = 'N'`)로 끈다. 코드값(PK)도 바꾸지 않는다.
- **코드그룹은 마이그레이션으로만 추가한다.** 로직이 그룹코드를 참조한다(`CodeGroups`, 프론트 `CODE_GROUP`). 화면에서는 그룹 안의 코드만 추가·수정한다.
- 시드: `V007__common_code_seed.sql`. 직급·근무지 등은 일반적인 예시라서 회사가 화면에서 고친다. 프론트 데모 시드(`frontend/src/data/codes.ts`)는 테스트가 V007과 비교한다.

## API (`backend/` `CommonCodeController`, JPA)

| 메서드 | 경로 | 용도 |
|---|---|---|
| GET | `/api/codes?groups=A,B` | 여러 그룹의 사용 중인 코드(공통 훅). 없는 그룹이 섞이면 404, 사용 중지 그룹은 빈 목록 |
| GET | `/api/code-groups` | 코드그룹 목록 |
| GET | `/api/code-groups/{group}/codes?includeInactive=true` | 관리 화면용 코드 목록 |
| POST | `/api/code-groups/{group}/codes` | 코드 추가(201, 중복 409) |
| PUT | `/api/code-groups/{group}/codes/{code}` | 코드 수정(코드값 제외) |

오류는 `{"error": "메시지"}` 형식. 개발 서버에서는 Vite가 이 두 경로만 Spring(8080)으로, 나머지 `/api`는 기존 Node 서버(4000)로 보낸다.

## 프론트

- `useCodes(group)` (`frontend/src/hooks/useCodes.ts`): 사용 중인 코드와 `nameOf(code)`. 그룹별 캐시, 관리 화면에서 고치면 `invalidateCodes(group)`로 다시 받는다. `nameOf`는 사용 중지·모르는 코드값을 그대로 돌려준다(과거 이력 표시용).
- 관리 화면: `공통코드 관리` 메뉴(`CodesPage`). `HR`·`ADMIN` 역할에게만 메뉴가 보이고, 다른 역할이 주소로 들어오면 안내만 보인다.

## 남은 일 (후속 이슈)

- **백엔드 권한 검사**: 현재 Spring 백엔드에는 인증·권한이 없다. 추가·수정 API의 인사담당자 권한 검사는 인증·권한 이슈에서 붙인다. 그 전까지 쓰기 API는 화면 메뉴 제한만 있다.
- **변경 이력(감사 로그)**: 지금은 `updated_at`만 남긴다. 누가 바꿨는지는 SPEC 0절 감사 로그 이슈에서 다룬다.
- **고용형태 겹침**: 기간제 및 단시간근로자 보호 등에 관한 법률 제2조는 기간제근로자(제1호)와 단시간근로자(제2호)를 따로 정의하므로 한 사람이 둘 다일 수 있다. 시드는 이슈 본문대로 정규·기간제·단시간 3개다. 사원 등록 이슈에서 "기간제 단시간" 코드를 추가할지, 근로계약 기간 유무와 소정근로시간을 별도 속성으로 둘지 정한다.
- **퇴직사유 ↔ 4대보험 상실사유**: 내부 퇴직사유 코드와 4대보험 자격상실 사유코드의 대응은 급여·4대보험 이슈에서 정한다.
- 조문 확인: 제2조 제1호·제2호 위치만 인용했고 수치는 없다. law.go.kr 본문은 스크립트로 열리는 페이지라 자동 조회가 되지 않아, 2026-11-12에 원문을 확인하지 못했다. 사람이 다시 확인해야 한다.
