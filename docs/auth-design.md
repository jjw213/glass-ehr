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
- **#76부터 사용자는 로그인 세션으로 식별한다(8장).** 이 헤더는 `test` 프로필에서만 받는다.
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
- 근로계약(#17) API: 임금 등 개인정보가 있어 조회는 EMP_READ_ALL(관리자·인사·급여), 등록·수정·교부 기록은
  EMP_WRITE(관리자·인사)만. 부서장·본인 조회는 아직 막혀 있다(범위 검사와 함께 후속 이슈에서 연다).

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

## 7. 로그인 방식 (#75 결정, 2026-11-16)

저장소 주인 결정: **세션 쿠키** — HttpOnly 쿠키 + 서버 세션, 세션은 Spring Session JDBC로 Oracle에 저장.

- 이유: 화면과 API를 같은 출처에서 서비스한다. 지금 Node 서버(`server/src/auth.ts`, `ghr_session` HttpOnly·SameSite=Lax 쿠키 +
  `HR_SESSIONS` 테이블)와 같은 방식이라 옮기기 쉽고, 로그아웃·강제 만료는 서버에서 세션 행을 지우면 된다.
  서버를 여러 대로 늘려도 세션이 DB에 있어 공유된다.
- 토큰(JWT 등)은 쓰지 않는다. 모바일 앱이나 다른 서비스가 API를 직접 부를 계획이 생기면 그때 다시 정한다.
- 바뀌는 곳: `AuthInterceptor.loginIdOf`가 `X-Actor-Id` 헤더 대신 세션의 로그인 아이디를 읽는다. 역할은 지금처럼 DB(`app_user_role`)에서 읽는다.
  프론트 `api/http.ts`는 `X-Actor-Id`를 보내지 않고 같은 출처 쿠키로 요청한다. 운영에서는 `X-Actor-Id`를 받지 않는다.
- 구현 이슈에서 정할 것(이번 결정 범위 밖, 정할 때 질문):
  - Spring Session JDBC 테이블 마이그레이션(Oracle용 스키마), 세션 유효 시간·유휴 만료, 쿠키 이름·`Secure`·`SameSite`.
  - 비밀번호 저장 방식(`app_user`에 해시 열 추가, 해시 알고리즘)과 기존 Node `HR_` 계정을 옮길지 여부.
  - CSRF 방어(쿠키 방식이라 필요), 로그인 시도 제한, 로그인 시 세션 ID 재발급(세션 고정 방지).
  - 사용자 사용 중지·퇴직 시 그 사용자의 세션을 모두 끊는 경로.
  - 로그인·로그아웃 기록을 접속기록으로 남길지(6장 접속기록 미결과 함께).

## 8. 로그인·세션 구현 (#76)

2장의 `X-Actor-Id` 식별은 이제 **`test` 프로필 전용**이다(pom.xml surefire 가 `spring.profiles.active=test` 로 테스트를 돌린다).
그 밖의 프로필에서는 헤더를 무시하고 세션만 본다. 세션이 있으면 test 프로필에서도 세션이 우선이다.

- 마이그레이션 `V027__app_user_login_session.sql`
  - `app_user`: `password_hash`(BCrypt, `$2a/$2b/$2y$` 형식만 CHECK), `password_changed_at`, `failed_login_count`, `locked_until`.
  - `login_access_log`: 로그인 성공·실패(사유: 없는 아이디·비밀번호 틀림·잠김·사용 중지·비밀번호 미설정)·로그아웃, 시각, IP. 추가만, 비밀번호는 남기지 않는다.
  - `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES`: Spring Session JDBC Oracle 스키마. 앱은 만들지 않는다(`initialize-schema: never`).
- API (`LoginController`, `LoginService`)
  - `POST /api/auth/login {loginId, password}` → 200 `{loginId}` / 400 입력 없음 / 401 불일치(없는 아이디와 구분하지 않음) / 423 잠김.
    성공하면 기존 세션을 버리고 새 세션을 만든다(세션 고정 방지).
  - `POST /api/auth/logout` → 204(세션이 없어도). 로그아웃 기록 후 세션을 지운다.
  - 실패 횟수·잠금 시간은 운영 설정 `glassehr.auth.max-failed-attempts`(기본 5), `glassehr.auth.lock-duration`(기본 30m). 법정 수치가 아니다.
- 세션 쿠키 `GHR_SESSION`: HttpOnly, SameSite=Lax, `Secure` 는 `SESSION_COOKIE_SECURE`(운영 HTTPS 는 true), 유휴 만료 `SESSION_IDLE_TIMEOUT`(기본 30m).
- CSRF: SameSite=Lax 쿠키 + 상태를 바꾸는 요청(GET·HEAD·OPTIONS 외)에 `X-Requested-With: XMLHttpRequest` 헤더 필수(없으면 403).
  다른 출처 페이지는 CORS 사전 요청 없이 이 헤더를 붙일 수 없고, 서버는 CORS 를 열지 않는다. 화면(`api/http.ts`)이 모든 요청에 붙인다.
- 사용 중지·삭제된 사용자의 세션으로 요청하면 401 과 함께 그 세션을 지운다.
- 화면: 로그인 폼은 `/api/auth/login` 으로 로그인한다. 홈 데이터(`/api/bootstrap` 등)가 아직 Node 서버라 옮기는 동안만
  같은 아이디·비밀번호로 Node `/api/login` 에도 로그인한다(개발 중엔 `DEMO_ACCOUNT_PASSWORD` 와 Node `SEED_PASSWORD` 를 같게).
  어느 API 든 401 을 주면 로그인 화면으로 돌아간다. 데모 모드(`api/mock.ts`)는 그대로 `hr / 1234`.
- 데모 계정: `.env` 의 `DEMO_ACCOUNT_PASSWORD` 가 있으면 시작할 때 `DemoAccountSeeder` 가 `employee_demo_seed.sql` 사원과 연결된
  `admin·hr·payroll·head·emp` 계정을 만들고 그 값을 BCrypt 로 해시해 넣는다(시드 파일에 평문 없음, 이미 비밀번호가 있으면 그대로).
- 근거: 개인정보 보호법 제29조(안전조치의무), 개인정보의 안전성 확보조치 기준(개인정보보호위원회고시 제2026-9호, 시행 2026. 7. 1.,
  law.go.kr 제목 확인 2026-11-20)의 접근 권한 관리(인증 실패 시 접근 제한)·접근 통제·접속기록 보관.
  **조문 번호와 접속기록 보관 기간은 미확인**(law.go.kr 본문이 자동 조회되지 않음). 이 구현은 보관 기간을 정하지 않고 로그를 지우지도
  않으므로 값에 의존하지 않는다. 보관 기간·점검 주기는 6장 미결대로 원문 확인 후 별도 이슈에서 `legal_rate` 로 넣는다.
