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
- 코드값·그룹코드(PK)는 바꾸지 않는다. 공통코드는 법인별이고(#121) 모든 API는 세션의 현재 법인 안에서만 다룬다. 다른 법인 그룹·코드는 404.
- **코드그룹 (#140, V037)**: 화면에서 추가·수정·삭제한다.
  - `system_yn = 'Y'` **시스템 그룹**: 로직이 그룹코드를 참조한다(`CodeGroups`, 프론트 `CODE_GROUP`). JOB_GRADE·JOB_TITLE·JOB_FAMILY·EMPLOYMENT_TYPE·WORK_LOCATION·RETIREMENT_REASON·APPOINTMENT_TYPE. 삭제·사용 여부 변경 불가, 이름·설명·순서만 수정. 화면에 '시스템' 표시.
  - `system_yn = 'N'` **사용자 그룹**: 관리자가 화면에서 만든 그룹. 삭제는 그룹 안 코드가 하나도 참조되지 않고 기본 코드가 없을 때만(아니면 409), 코드와 함께 지운다.
  - `edit_page`: 다른 모듈 화면에서 편집하는 그룹의 화면 키(예: 근태코드). NULL이면 공통코드 화면에서 편집. 값이 있으면 공통코드 화면은 보기·사용처만 보이고 '○○에서 편집' 링크를 띄운다.
  - **edit_page 규칙 (#143)**: 값이 있는 그룹은 공통코드 API(`/api/code-groups/{group}` 수정, 그 그룹의 코드 추가·수정·삭제)가 409로 막는다. 그 화면이 처리방식·상세 설정을 함께 저장하기 때문이다. 조회·사용처 API는 그대로 쓴다. 삭제 규칙(기본 코드·참조 중 409)과 참조 목록은 전용 API도 공통코드와 같은 것(`CommonCodeService.requireDeletable`, `CodeReferences`)을 쓴다. 지금 값: `attendanceCodes`(LEAVE_TYPE·ATTENDANCE_TYPE, `docs/attendance-code-design.md`).
- **코드 삭제 (#140)**: (1) 어떤 테이블에서도 참조하지 않는 코드만 물리 삭제 (2) 참조 중이면 409 "사용 중이라 삭제할 수 없어요. 사용 안 함으로 바꿔 주세요" + 사용처 건수 (3) `system_yn = 'Y'` **기본 코드**(로직이 코드값을 직접 참조: APPOINTMENT_TYPE 7개, `RetirementChecklist`가 참조하는 RETIREMENT_REASON 7개, EMPLOYMENT_TYPE FIXED_TERM·PART_TIME)는 삭제 불가, 사용 안 함만.
- **참조 목록**: 참조 검사·사용처는 `CodeReferences`(그룹 → 테이블·컬럼) 한 곳에서 한다. 새 테이블이 공통코드를 참조하면 이 목록에 추가한다. `CodeReferencesSchemaScriptTest`가 마이그레이션의 `*_code` 컬럼을 찾아 목록(또는 '공통코드 아님' 목록)과 비교해 누락을 잡는다.
- **사용처**: 테이블별 건수는 코드 조회 권한이면 누구나, 행 목록(사번·이름·일자)은 `EMP_READ_ALL` 권한이 있을 때만(없으면 403, 화면은 건수만). 행 목록은 사원 목록 화면과 같은 식별 최소 항목이라 기존 규칙(다운로드·인적사항 조회만 기록)대로 개인정보 조회 로그 대상이 아니다.
- 시드: `V007__common_code_seed.sql`. 직급·근무지 등은 일반적인 예시라서 회사가 화면에서 고친다. 프론트 데모 시드(`frontend/src/data/codes.ts`)는 테스트가 V007과 비교한다.

## API (`backend/` `CommonCodeController`, JPA)

| 메서드 | 경로 | 용도 |
|---|---|---|
| GET | `/api/codes?groups=A,B` | 여러 그룹의 사용 중인 코드(공통 훅). 없는 그룹이 섞이면 404, 사용 중지 그룹은 빈 목록 |
| GET | `/api/code-groups` | 코드그룹 목록(`system`, `editPage`) |
| POST | `/api/code-groups` | 사용자 그룹 추가(201, 중복 409) |
| PUT | `/api/code-groups/{group}` | 그룹 이름·설명·순서·사용 여부 수정(그룹코드 제외, 시스템 그룹은 사용 여부 불가) |
| DELETE | `/api/code-groups/{group}` | 사용자 그룹 삭제(시스템·참조 중 409) |
| GET | `/api/code-groups/{group}/codes?includeInactive=true` | 관리 화면용 코드 목록(`system`, `inUse`, `usageCount`) |
| POST | `/api/code-groups/{group}/codes` | 코드 추가(201, 중복 409) |
| PUT | `/api/code-groups/{group}/codes/{code}` | 코드 수정(코드값 제외) |
| DELETE | `/api/code-groups/{group}/codes/{code}` | 코드 삭제(204, 기본 코드·참조 중 409) |
| GET | `/api/code-groups/{group}/codes/{code}/usages[?table=..&page=..]` | 사용처 테이블별 건수 / 행 목록(`EMP_READ_ALL`) |

쓰기 API는 `CODE_WRITE` 권한.

오류는 `{"error": "메시지"}` 형식. 개발 서버에서는 Vite가 이 두 경로만 Spring(8080)으로, 나머지 `/api`는 기존 Node 서버(4000)로 보낸다.

## 프론트

- `useCodes(group)` (`frontend/src/hooks/useCodes.ts`): 사용 중인 코드와 `nameOf(code)`. 그룹별 캐시, 관리 화면에서 고치면 `invalidateCodes(group)`로 다시 받는다. `nameOf`는 사용 중지·모르는 코드값을 그대로 돌려준다(과거 이력 표시용).
- 관리 화면: `공통코드 관리` 메뉴(`CodesPage`). `HR`·`ADMIN` 역할에게만 메뉴가 보이고, 다른 역할이 주소로 들어오면 안내만 보인다.

## 남은 일 (후속 이슈)

- **변경 이력(감사 로그)**: 지금은 `updated_at`만 남긴다. 누가 바꿨는지는 SPEC 0절 감사 로그 이슈에서 다룬다.
- **고용형태 겹침**: 기간제 및 단시간근로자 보호 등에 관한 법률 제2조는 기간제근로자(제1호)와 단시간근로자(제2호)를 따로 정의하므로 한 사람이 둘 다일 수 있다. 시드는 이슈 본문대로 정규·기간제·단시간 3개다. 사원 등록 이슈에서 "기간제 단시간" 코드를 추가할지, 근로계약 기간 유무와 소정근로시간을 별도 속성으로 둘지 정한다.
- **퇴직사유 ↔ 4대보험 상실사유**: 내부 퇴직사유 코드와 4대보험 자격상실 사유코드의 대응은 급여·4대보험 이슈에서 정한다.
- 조문 확인: 제2조 제1호·제2호 위치만 인용했고 수치는 없다. law.go.kr 본문은 스크립트로 열리는 페이지라 자동 조회가 되지 않아, 2026-11-12에 원문을 확인하지 못했다. 사람이 다시 확인해야 한다.
