# 역할 권한과 개인정보 조회 로그 (#19)

근거: 개인정보 보호법 제29조(안전조치의무), 개인정보의 안전성 확보조치 기준(접근 권한 관리, 접속기록 보관 및 점검).
고시의 세부 조항 번호와 접속기록 보관 기간은 law.go.kr 원문을 자동으로 확인하지 못해 코드에 넣지 않았다(6장).

## 1. 테이블 (V013)

| 테이블 | 내용 |
|---|---|
| `app_role` | ADMIN 관리자, HR 인사담당, PAYROLL 급여담당, DEPT_HEAD 부서장, EMPLOYEE 일반 사원 |
| `app_permission` | API 권한 코드. `com.glassehr.auth.Permission` enum 과 같아야 한다(`AuthSchemaScriptTest`) |
| `app_role_permission` | 역할별 권한. 바꾸려면 새 마이그레이션을 추가한다 |
| `app_user` | 로그인 아이디 ↔ 사원(`emp_id`, 관리자 계정은 비울 수 있음), 사용 여부 |
| `app_user_role` | 직접 부여 역할. DEPT_HEAD·EMPLOYEE 는 CHECK 로 넣을 수 없다(자동 역할) |
| `privacy_access_log` | 개인정보 조회·다운로드 로그(추가만, `@Immutable`, 삭제 API 없음) |

역할별 권한

| 권한 | ADMIN | HR | PAYROLL | DEPT_HEAD | EMPLOYEE |
|---|---|---|---|---|---|
| EMP_READ_ALL 전체 사원 조회 | O | O | O | | |
| EMP_READ_DEPT 자기 부서(하위 포함) | | | | O | |
| EMP_READ_SELF 본인 | | | | | O |
| EMP_PERSONAL_VIEW 다른 사원 인적사항 원문 | O | O | O | | |
| EMP_WRITE 사원 등록·수정·퇴직·주민번호 등록 | O | O | | | |
| EMP_EXPORT 사원 목록 내려받기 | O | O | O | | |
| RRN_REVEAL 주민등록번호 원문 | | O | O | | |
| ORG_WRITE 부서 신설·변경·폐지 | O | O | | | |
| CODE_WRITE 공통코드 추가·수정 | O | O | | | |
| PRIVACY_LOG_READ 개인정보 조회 로그 | O | | | | |

주민등록번호 원문은 관리자도 볼 수 없다(`docs/rrn-design.md` 3장).

## 2. 사용자 식별과 API 단 검사

- 요청 헤더 `X-Actor-Id`(로그인 아이디)로 `app_user`를 찾는다. 없거나 사용 중지면 401.
  **역할은 DB에서 읽고 `X-Actor-Role` 헤더는 무시한다**(헤더로 역할을 올리는 우회 차단).
- 인증(비밀번호·세션·토큰)은 아직 없다. 이 헤더는 인증 이슈 전까지의 임시 방식이며, 바꿀 때는
  `AuthInterceptor.loginIdOf`와 프론트 `api/http.ts`의 `setActorId`만 바꾼다.
- `AuthInterceptor`가 `/api/**` 모든 컨트롤러 메서드를 검사한다.
  - `@PublicApi`(헬스 체크)만 통과.
  - `@RequiresPermission`이 없는 메서드는 403(새 API를 만들고 권한을 빠뜨려도 열리지 않음).
    `ControllerAuthCoverageTest`가 모든 매핑에 둘 중 하나가 붙었는지 검사한다.
  - 권한 중 하나라도 있으면 통과. 데이터 범위는 컨트롤러가 `AuthUser`로 다시 검사한다.
- 화면 메뉴 숨김(`frontend/src/nav.ts`)은 보조 수단이다.

## 3. 데이터 범위

- 부서장: 오늘(Asia/Seoul) 조직도에서 `head_emp_id`가 본인인 부서와 **모든 하위부서**(`DeptScope.managedBy`).
  직접 부여하지 않고 조직 이력으로 자동 결정된다.
- 일반 사원: 본인만.
- 목록 API는 범위 밖 사원을 빼고 돌려주고, 상세 API는 범위 밖이면 403(+거부 로그).
- 다른 사원의 인적사항(생년월일·주소·이력·메모)은 EMP_PERSONAL_VIEW 가 없으면 서버가 가려서 보낸다.

## 4. 개인정보 조회 로그

- 누가(`accessor_id`, 역할), 언제(`accessed_at`), 누구의(`target_emp_id`), 무엇을(`item_code` + `action_code`), 결과, 접속 IP, 요청 경로.
- 남기는 경우: 인적사항 상세 조회(`EMP_PERSONAL`/VIEW), 사원 목록 CSV 내려받기(`EMP_LIST`/DOWNLOAD, 파일에 든 사원마다 한 행),
  주민등록번호 원문 조회(기존 `rrn_access_log`). **거부·대상 없음도 남긴다**(미로그인 시도 포함).
- 원문 값은 로그에 남기지 않는다. 존재하지 않는 사원 조회 시도도 남기려고 대상 사원 FK 를 걸지 않았다.

## 5. 로그 조회 화면 (관리자)

- API: `GET /api/admin/privacy-logs?from=&to=&accessorId=&targetEmpId=` (PRIVACY_LOG_READ). 기본 기간 30일, 한 번에 366일,
  최대 500행(넘으면 `truncated`). 두 로그 테이블을 합쳐 최근 순. 응답은 `Cache-Control: no-store`.
  기간·행 수 상한은 화면 성능용 값이며 법령 기준값이 아니다.
- 화면: `frontend/src/pages/PrivacyLogsPage.tsx` (메뉴 `개인정보 조회 로그`, 관리자만 표시). 서버가 403 을 주면 오류만 보인다.

## 6. 미결

- 접속기록 보관 기간·점검 주기(안전성 확보조치 기준 해당 조항) — 원문 확인 후 `legal_rate` 항목으로 넣고 보관·파기 작업을 별도 이슈로.
- 로그인 인증(비밀번호·세션), 사용자·역할 관리 화면 — 지금은 SQL 로 `app_user`·`app_user_role`을 넣는다. 사용자 시드는 없다.
- 부서장 범위는 오늘 조직도 기준. 과거 기준일 조회 시 그때의 부서장 범위를 쓸지는 인사발령 이슈에서 정한다.
