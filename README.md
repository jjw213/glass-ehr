# Glass HR — 글래스 스타일 인사(EHR) 시스템

React + TypeScript 화면과 **Spring Boot(Java 21) + Oracle** 백엔드로 된 인사 시스템입니다.
대상은 상시근로자 30인 이상 사업장이고, 법령 기준값은 코드가 아니라 DB(`legal_rate`)에서 읽습니다.

> **이전 중:** 처음 만든 Node API 서버(`server/`)를 Spring 백엔드(`backend/`)로 옮기고 있습니다.
> 새 기능은 `backend/`에만 추가하고(`docs/DECISIONS.md` 2026-11-10), 아직 옮기지 않은 기능(로그인, 출퇴근 버튼, 휴가·결재, 급여명세서, 메시지)은 Node 서버가 처리합니다.

```
glass-ehr/
├─ backend/    API 서버 (Spring Boot 3.5, Java 21, Maven, JPA + MyBatis, Oracle)
├─ frontend/   화면 (React 19 + Vite, 테스트는 Vitest)
├─ server/     예전 API 서버 (Node.js + TypeScript) — 이전이 끝나면 삭제
├─ shared/     화면과 Node 서버가 같이 쓰는 데이터 타입 (types.ts)
├─ docs/       명세(SPEC)·결정 기록(DECISIONS)·진행 기록(PROGRESS)·기능별 설계 문서
├─ tests/legal-fixtures/  법령 정답 데이터 (보호 경로, 사람 승인으로만 변경)
└─ scripts/    보조 스크립트 (법령 시드 검사 등)
```

```
브라우저 ──▶ Vite(5173) ──┬─ /api/codes, /api/org, /api/employees, /api/auth, /api/admin,
                          │  /api/attendance/(work-patterns|employees|holidays|agreements)
                          │      ──▶ Spring 백엔드(8080) ──(JPA·MyBatis, ojdbc11)──▶ Oracle
                          └─ 나머지 /api ──▶ Node 서버(4000) ──(oracledb)──▶ Oracle (HR_ 테이블)
```

어느 경로를 Spring으로 보낼지는 `frontend/vite.config.ts`의 `proxy`에 있습니다. Spring에 새 API를 만들면 화면에서 쓰기 전에 여기에도 경로를 추가합니다. 예를 들어 `/api/appointments`(인사발령)와 `/api/contracts`(근로계약)는 아직 등록되어 있지 않습니다.

---

## 1. 준비물

| 도구 | 버전 | 용도 |
|---|---|---|
| Java (JDK) | **21** | 백엔드 (`backend/pom.xml`의 `java.version`) |
| Maven | 3.9 이상 | 백엔드 빌드 (`mvnw`는 없음) |
| Node.js | 22.13 이상 (화면만 다룰 때는 20.19 이상) | 화면, Node 서버 |
| Oracle | 23ai Free 권장 (CI도 `gvenzl/oracle-free:23-slim` 사용) | DB |

IntelliJ에서는 `backend/pom.xml`을 Maven 프로젝트로 열면 됩니다.

## 2. Oracle 준비 (처음 한 번)

**① 접속 정보.** 일반 계정은 반드시 PDB(`FREEPDB1`) 안에 만듭니다. 루트 컨테이너에서 만들면 ORA-65096이 납니다.

| 설치한 버전 | 서비스 이름 | JDBC URL (`DB_URL`) |
|---|---|---|
| Oracle 23ai Free | `FREEPDB1` | `jdbc:oracle:thin:@//localhost:1521/FREEPDB1` |
| Oracle 21c XE | `XEPDB1` | `jdbc:oracle:thin:@//localhost:1521/XEPDB1` |

**② 백엔드 계정 만들기.** SYSTEM으로 PDB에 접속해서 실행합니다. 비밀번호는 꼭 바꾸세요.

```sql
CREATE USER glass_ehr IDENTIFIED BY "change-me";
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE PROCEDURE TO glass_ehr;
ALTER USER glass_ehr QUOTA UNLIMITED ON USERS;
```

`CREATE PROCEDURE`는 V004(`legal_rate_api` 패키지)에 필요합니다.

**③ 마이그레이션 적용.** 테이블은 `backend/src/main/resources/db/oracle/V###__*.sql`을 **번호 순서대로** 직접 실행해서 만듭니다.
마이그레이션 도구(Flyway 등)는 아직 쓰지 않습니다. JPA도 테이블을 만들지 않습니다(`ddl-auto: validate`). 그래서 테이블이 하나라도 빠지면 백엔드가 시작되지 않습니다.

```bash
# SQLcl 예시 (저장소 루트에서)
for f in $(ls backend/src/main/resources/db/oracle/V*.sql | sort); do
  echo "== $f"; echo exit | sql -S "glass_ehr/change-me@localhost:1521/FREEPDB1" @"$f"
done
```

SQL Developer에서는 파일을 하나씩 열어 **스크립트 실행(F5)** 으로 돌려도 됩니다. V004는 PL/SQL 패키지라 F5로 실행해야 합니다.

**④ 시드 넣기 (선택).** `db/oracle/seed/`의 파일은 마이그레이션이 아닙니다. 각 파일 맨 위 주석에 실행 순서와 조건이 적혀 있습니다.

| 파일 | 내용 |
|---|---|
| `legal_rate_hr_current.sql`, `legal_rate_fixed_term.sql`, `legal_rate_work_pattern.sql` | 법령 기준값. 출처는 `docs/legal-rate-seed-sources.md` |
| `employee_demo_seed.sql` | 개발·시연용 가짜 부서·사원 (운영 DB에는 넣지 않음) |
| `legal_rate_seed.TODO.sql` | 아직 실행 대상이 아님 (원문 확인 전 TODO) |

앱 접속 계정을 스키마 소유 계정과 따로 둘 때는 `db/oracle/grants/legal_rate_grants.sql`을 DBA가 실행합니다(`docs/legal-rate-design.md` 3절).

## 3. 실행

**백엔드 (Spring, http://localhost:8080)**

```bash
cd backend
cp .env.example .env     # DB_URL, DB_USER, DB_PASSWORD 채우기 (Windows: copy)
mvn spring-boot:run
```

- `application.yml`이 `backend/.env`를 자동으로 읽습니다. 같은 이름의 OS 환경변수가 있으면 그쪽이 우선합니다.
- 주민등록번호를 저장·조회하려면 `.env`에 `RRN_ENC_KEYS`, `RRN_CURRENT_KEY_VERSION`, `RRN_HMAC_KEY`를 넣습니다. 비어 있으면 그 기능만 거부됩니다. 키 만드는 법은 `.env.example`과 `docs/rrn-design.md`에 있습니다.
- 확인: `curl http://localhost:8080/api/health`

**Node 서버 (예전 API, http://localhost:4000)**: 로그인·출퇴근 버튼·휴가·결재·급여명세서·메시지에 필요합니다. 설정은 아래 [6장](#6-예전-node-서버-server)을 보세요.

```bash
cd server && npm install && npm run dev
```

**화면 (http://localhost:5173)**

```bash
cd frontend && npm install && npm run dev      # /api는 vite.config.ts 규칙에 따라 8080 / 4000으로 전달
cd frontend && npm run dev:demo                # 서버 없이 브라우저 안의 샘플 데이터로 보기
```

로그인: `hr` / `1234` (Node 서버 샘플 데이터). Spring 백엔드는 요청 헤더 `X-Actor-Id`의 아이디로 사용자를 찾습니다. 역할과 권한은 헤더가 아니라 DB(`app_user_role`, `app_role_permission`)에서 읽습니다.

## 4. 테스트

| 어디서 | 명령 | 내용 |
|---|---|---|
| `backend/` | `mvn test` | 단위 테스트, 스키마 스크립트 정적 검사, 법령 fixture 테스트 (DB 없이) |
| `backend/` | `ORACLE_IT=true DB_URL=... DB_USER=... DB_PASSWORD=... mvn verify` | Oracle 통합 테스트(`*OracleTest`) 포함. 빈 DB에도 필요한 마이그레이션을 테스트가 직접 적용합니다 |
| `frontend/` | `npm run lint` · `npm run typecheck` · `npm test` · `npm run build` | ESLint, `tsc --noEmit`, Vitest(`src/**/*.test.ts(x)`), 빌드 |
| `server/` | `npm test` | Node API 테스트 (내장 SQLite) |

CI(`.github/workflows/ci.yml`)는 PR마다 프론트엔드 잡과 백엔드 잡(Oracle 23 Free 컨테이너 + `ORACLE_IT=true mvn -B verify`)을 돌립니다. 둘 다 통과해야 main에 머지할 수 있습니다.

## 5. 백엔드 구조

```
backend/src/main/
├─ java/com/glassehr/
│  ├─ auth/         역할·권한(AuthInterceptor: 모든 /api 검사, 표시 없는 API는 거부), 개인정보 조회 로그
│  ├─ code/         공통코드
│  ├─ org/          조직(부서, 유효기간 방식 이력, 조직도 트리)
│  ├─ employee/     사원(근로자 명부), 주민등록번호 암호화, CSV 일괄 등록·내려받기
│  ├─ contract/     근로계약(명시사항 검사, 서면 교부 기록, 만료·기간제 사용기간 알림)
│  ├─ appointment/  인사발령(등록·수정·확정·취소)
│  ├─ attendance/   근무유형·배정(표준·탄력·선택·재량), 휴일 달력, 근로자대표 서면합의
│  ├─ legalrate/    법령 기준값 조회 (MyBatis, 기준일로 적용시작일 조회)
│  └─ common/       헬스 체크
└─ resources/
   ├─ application.yml
   ├─ mapper/       MyBatis XML
   └─ db/oracle/    V###__*.sql 마이그레이션, seed/, grants/
```

- **JPA**는 인사·조직 CRUD에, **MyBatis**는 근태·급여 집계와 기준값(적용시작일) 조회에 씁니다(`docs/DECISIONS.md` 2026-11-10).
- **법령 기준값**(요율, 상한, 시행일)은 `legal_rate` 테이블에서 읽고, 쓰기는 `legal_rate_api` 패키지로만 합니다. 기간 겹침 검사와 잠금이 이 패키지에 있습니다.
- 시간대는 `Asia/Seoul`, 금액은 원 단위 정수입니다. JSON에 소수가 오면 400으로 거부합니다.

### Spring API

| 경로 | 내용 |
|---|---|
| `GET /api/health` | DB 연결 확인 |
| `GET /api/auth/me` | 내 역할·권한 |
| `GET /api/admin/privacy-logs` | 개인정보 조회 로그 (관리자) |
| `/api/code-groups`, `/api/codes` | 공통코드 조회·추가·수정 |
| `/api/org/tree`, `/api/org/depts` … | 기준일 조직도, 부서 추가·수정·폐지 |
| `/api/employees`, `/api/employees/{empId}` … | 사원 조회·등록·수정·퇴직, 주민등록번호(`/rrn`, 권한자만·조회마다 로그) |
| `/api/employees/import/template` · `/preview` · `/confirm`, `/api/employees/export` | 사원 CSV 양식, 일괄 등록 미리보기·확정, 목록 내려받기 |
| `/api/employees/{empId}/contracts`, `/api/contracts/...` | 근로계약, 서면 교부 기록, 만료·미교부 알림 |
| `/api/employees/{empId}/appointments`, `/api/appointments/...` | 인사발령 등록·수정·확정·삭제 |
| `/api/attendance/work-patterns` … | 근무유형 정의·검사, 사원별 배정·기준일 조회 |
| `/api/attendance/holidays` · `/import` · `/company` · `/substitutions` | 휴일 달력, 공휴일 CSV 불러오기, 회사휴일, 휴일 대체 |
| `/api/attendance/agreements` | 근로자대표 서면합의 |

## 6. 예전 Node 서버 (`server/`)

Spring으로 옮기기 전까지 아래 기능을 맡습니다. 테이블은 `HR_`로 시작하고 Spring 쪽 테이블과 별개입니다.

| 메서드 | 경로 | 내용 |
|---|---|---|
| POST | `/api/login` · `/api/logout` | 로그인 / 로그아웃 (HttpOnly 쿠키 세션) |
| GET | `/api/bootstrap` | 로그인 직후 화면에 필요한 데이터 |
| POST | `/api/attendance/toggle` | 출근 → 퇴근 → 퇴근 취소 |
| POST | `/api/leaves`, `/api/approvals/:id/approve` · `/reject` | 휴가 신청과 결재 |
| GET | `/api/payslips` | 본인 급여명세서 |
| GET · POST | `/api/threads` … | 메시지 |

**설정과 테이블 만들기**

```bash
cd server
cp .env.example .env     # ORACLE_USER, ORACLE_PASSWORD, ORACLE_CONNECT_STRING(예: localhost:1521/FREEPDB1)
npm install
npm run db:init                  # HR_ 테이블 13개 + 샘플 데이터 (직원 24명 등)
npm run db:init -- --schema-only # 샘플 없이 테이블만
```

> ⚠️ `db:init`은 `HR_`로 시작하는 테이블을 **지우고 다시 만듭니다.** 실제 데이터를 넣은 뒤에는 실행하지 마세요.

- Oracle 사용자는 `server/sql/create-user.sql`로 만듭니다.
- 드라이버는 `oracledb` Thin 모드라 Instant Client가 필요 없습니다.
- 배포 모드(`npm start`)에서는 `frontend/dist`를 함께 서비스합니다. 다른 폴더를 쓰려면 `STATIC_DIR`로 바꿉니다.
- 다른 직원 계정(`sy.park`, `dy.lee`, `he.choi` …)의 초기 비밀번호는 `.env`의 `SEED_PASSWORD`입니다.

## 7. 화면 구조

```
frontend/src/
├─ api/          서버 호출 (http.ts, X-Actor-Id 헤더) + 서버 없는 데모용 (mock.ts)
├─ state/        로그인 상태와 데이터
├─ lib/          커서 인트로 엔진 등
├─ components/   배경, 로그인·인트로, 레이아웃(PC 메뉴·모바일 사이드바), 공통 UI, 사원 CSV 등록
├─ pages/        홈 · 근태휴가 · 근무유형 · 휴일 · 급여 · 전자결재 · 메시지 · 인사정보 · 조직도
│                · 공통코드 · 개인정보 조회 로그 · 설정
└─ styles/global.css   글래스 디자인 전체
```

## 8. 개발 규칙과 문서

- **`AGENTS.md`**: 모든 AI 에이전트의 공통 규칙입니다. 법령 근거 조문 주석, 기준값 하드코딩 금지, 개인정보, 보호 경로, 리뷰 기준이 들어 있습니다. **`CLAUDE.md`**: 작성자(Claude Code) 전용 규칙입니다.
- **`docs/DECISIONS.md`**: 사람이 내린 결정입니다. 작업 전에 먼저 읽습니다.
- **`docs/SPEC.md`**: 기능 명세입니다. **`docs/PROGRESS.md`**: 진행 기록입니다. **`docs/*-design.md`**: 기능별 설계와 미결 사항입니다.
- 이 저장소는 **공개 저장소**입니다. 실존 인물의 데이터, 실제 주민등록번호·연락처, 비밀값을 넣지 않습니다. `.env`는 커밋하지 않습니다.
- 개발은 맥미니 루프가 이슈 단위로 돌립니다.
  - 구현: Claude Code가 맡고, 화면·API만 다루는 `codex-impl` 이슈는 Codex가 맡습니다.
  - 리뷰: Claude가 쓴 PR은 Codex가, Codex가 쓴 PR은 Claude가 리뷰합니다.
  - 리뷰 반영·CI 수정·충돌 해결은 Codex가 합니다.
  - Codex가 손댄 PR은 사람이 승인해야 머지됩니다.

## 9. 실서비스 전에 할 일

- [ ] Node 서버 기능(로그인·출퇴근·휴가·결재·급여명세서·메시지)을 Spring으로 옮기고 `server/`를 삭제합니다.
- [ ] Spring에 로그인·세션(또는 토큰)을 붙입니다. 지금은 `X-Actor-Id` 헤더로 사용자를 찾기 때문에 개발용으로만 써야 합니다.
- [ ] 마이그레이션 적용 방식을 정합니다(Flyway 도입 또는 배포 스크립트). 앱 계정과 스키마 소유 계정을 나누고 `grants/`를 적용합니다.
- [ ] 배포 방식을 정합니다. Spring jar(`mvn package`)와 화면 빌드(`frontend/dist`)를 어디서 서비스할지 결정합니다.
- [ ] HTTPS를 적용합니다(Nginx·Caddy 등).
- [ ] 주민등록번호 암호화 키를 비밀 저장소로 관리하고 키 교체 절차를 정합니다(`docs/rrn-design.md`).
- [ ] Oracle 백업 정책을 정하고, 접속기록·개인정보 조회 로그 보관 기간을 확정합니다(`docs/auth-design.md`).
