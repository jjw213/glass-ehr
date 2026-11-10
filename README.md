# Glass HR — 글래스 스타일 인사(EHR) 시스템

React + TypeScript 화면과 Node.js + Oracle 서버로 된 인사 시스템입니다.

```
glass-ehr/
├─ frontend/   화면 (React + Vite, 테스트는 Vitest)
├─ server/     API 서버 (Node.js + TypeScript, Oracle 연결) — backend/로 이전될 때까지 유지
├─ backend/    새 백엔드 (Spring Boot, docs/DECISIONS.md 참고)
└─ shared/     화면과 서버가 같이 쓰는 데이터 타입 (types.ts)
```

```
브라우저 ──(/api, 쿠키 세션)──▶ Node 서버 ──(oracledb)──▶ Oracle DB
```

---

## 1. Oracle 준비 (처음 한 번)

**① 앱 전용 사용자 만들기.** SQL Developer나 SQLcl에서 SYSTEM으로 PDB에 접속해 `server/sql/create-user.sql`을 실행합니다. 비밀번호는 꼭 바꾸세요.

```sql
CREATE USER GLASSHR IDENTIFIED BY "change-me";
GRANT CREATE SESSION, CREATE TABLE TO GLASSHR;
ALTER USER GLASSHR QUOTA UNLIMITED ON USERS;
```

| 설치한 버전 | 접속 문자열 (`ORACLE_CONNECT_STRING`) |
|---|---|
| Oracle 23ai Free | `localhost:1521/FREEPDB1` |
| Oracle 21c XE | `localhost:1521/XEPDB1` |
| 그 외 | `호스트:포트/서비스명` (`lsnrctl status`로 서비스명 확인) |

**② 서버 설정 파일 만들기.**

```bash
cd server
cp .env.example .env      # Windows: copy .env.example .env
```

`.env`에서 `ORACLE_USER`, `ORACLE_PASSWORD`, `ORACLE_CONNECT_STRING`을 채웁니다.

**③ 테이블 만들기 + 샘플 데이터 넣기.**

```bash
npm install
npm run db:init                  # 테이블 13개 생성 + 샘플 데이터 (직원 24명 등)
npm run db:init -- --schema-only # 샘플 없이 테이블만
```

> ⚠️ `db:init`은 `HR_`로 시작하는 테이블을 **지우고 다시 만듭니다.** 실제 데이터를 넣은 뒤에는 실행하지 마세요.
> 테이블 정의만 보고 싶으면 `server/sql/schema.oracle.sql`을 보면 됩니다 (SQL Developer에서 바로 실행 가능).

Oracle 드라이버는 `oracledb` Thin 모드라서 **Instant Client 설치가 필요 없습니다.** Oracle 12.1 이상이면 됩니다.

## 2. 실행

Node.js **22.13 이상**이 필요합니다. 화면(`frontend/`)만 다룰 때는 20.19 이상이어도 됩니다.

**개발할 때** (터미널 2개)

```bash
# 터미널 1 — 서버 (http://localhost:4000)
cd server && npm install && npm run dev

# 터미널 2 — 화면 (http://localhost:5173, /api는 서버로 자동 전달)
cd frontend && npm install && npm run dev
```

**배포할 때** (서버 하나로 화면까지 같이 서비스)

```bash
cd frontend && npm run build  # 화면 → frontend/dist/
cd ../server && npm start     # http://서버주소:4000 에서 화면 + API 모두 제공
```

서버는 기본으로 `frontend/dist`를 제공합니다. 다른 폴더를 쓰려면 `STATIC_DIR` 환경변수로 바꿉니다.

로그인: `hr` / `1234`. 다른 직원 계정은 `sy.park`, `dy.lee`, `he.choi` … 이고, 초기 비밀번호는 모두 `.env`의 `SEED_PASSWORD`입니다.

**서버 없이 화면만 볼 때:** `cd frontend && npm run dev:demo`를 실행하면 브라우저 안의 샘플 데이터로 동작합니다.

### 화면 검사 명령 (`frontend/`에서 실행)

| 명령 | 내용 |
|---|---|
| `npm run lint` | ESLint 9 (TypeScript, React Hooks 규칙) |
| `npm run typecheck` | TypeScript 타입 검사 (`tsc --noEmit`) |
| `npm test` | Vitest 테스트 한 번 실행 (`npm run test:watch`는 감시 모드) |
| `npm run build` | 타입 검사 후 `frontend/dist/`로 빌드 (`build:demo`는 서버 없는 데모 빌드) |

테스트 파일은 `frontend/src/**/*.test.ts(x)`에 둡니다. CI는 `frontend/`에서 위 명령을 차례로 실행합니다.

## 3. 서버 구조

```
server/
├─ .env.example          설정 견본
├─ sql/
│  ├─ create-user.sql    Oracle 사용자 생성
│  └─ schema.oracle.sql  테이블 DDL (자동 생성본, npm run db:sql)
├─ src/
│  ├─ index.ts           서버 시작
│  ├─ app.ts             HTTP 처리, 오류 응답, 화면 파일 제공
│  ├─ routes.ts          API 목록 ← 새 API는 여기에
│  ├─ repo.ts            모든 SQL ← 쿼리 수정은 여기에
│  ├─ auth.ts            비밀번호 해시(scrypt), 세션 쿠키, 로그인 시도 제한
│  ├─ db/oracle.ts       Oracle 연결 풀
│  ├─ db/schema.ts       테이블 정의 (DDL 원본)
│  ├─ db/seed.ts         샘플 데이터
│  └─ db/sqlite.ts       Oracle 없이 테스트할 때 쓰는 내장 SQLite
└─ test/api.test.ts      API 자동 테스트 20개 (npm test)
```

### API

| 메서드 | 경로 | 내용 |
|---|---|---|
| POST | `/api/login` · `/api/logout` | 로그인 / 로그아웃 (HttpOnly 쿠키 세션) |
| GET | `/api/bootstrap` | 로그인 직후 화면에 필요한 데이터 전부 |
| POST | `/api/attendance/toggle` | 출근 → 퇴근 → 퇴근 취소 |
| POST | `/api/leaves` | 휴가 신청 → 결재 문서 자동 생성 |
| POST | `/api/approvals/:id/approve` · `/reject` | 승인 / 반려 |
| GET | `/api/payslips` | **본인** 급여명세서만 |
| GET · POST | `/api/threads` · `/api/threads/:id/messages` · `/read` | 메시지 |
| GET | `/api/health` | DB 연결 확인 |

### 서버가 지키는 규칙

- **급여**: 로그인한 본인 것만 조회됩니다.
- **결재**: 결재선에서 지금 차례인 사람만 승인·반려할 수 있습니다.
  - 여러 단계 결재는 순서대로 넘어갑니다.
  - 반려하면 그 자리에서 문서가 끝납니다.
- **휴가 신청**: 결재 문서가 자동으로 만들어지고, 문서가 승인·반려되면 휴가 상태도 같이 바뀝니다.
  - 결재선은 팀장 → 인사팀장 순서입니다 (본인이면 건너뜀).
  - 인사팀장 본인의 휴가는 경영지원팀장에게 갑니다.
  - 남은 연차보다 많이 신청하면 막힙니다.
- **메시지**: 자기가 속한 대화만 읽고 쓸 수 있습니다.
- **보안**:
  - 비밀번호는 scrypt 해시로만 저장합니다.
  - 세션 토큰은 DB에 해시로만 저장합니다.
  - 같은 아이디로 로그인을 5번 실패하면 5분간 막힙니다.
  - POST 요청은 JSON만 받습니다 (CSRF 방어).

### 테이블

| 테이블 | 내용 |
|---|---|
| `HR_DEPTS` | 부서 (부서장 = `LEAD_ID`) |
| `HR_EMPLOYEES` | 직원 + 로그인 계정 (`LOGIN_ID`, `PASSWORD_HASH`, `ROLE`) |
| `HR_SESSIONS` | 로그인 세션 |
| `HR_ATTENDANCE` | 출퇴근 (직원·날짜당 1행) |
| `HR_LEAVES` | 휴가 신청 (`APPROVAL_ID`로 결재 문서와 연결) |
| `HR_APPROVALS` | 결재 문서 |
| `HR_APPROVAL_STEPS` | 결재선 (순번, 결재자, 상태) |
| `HR_PAYSLIPS`, `HR_PAYSLIP_ITEMS` | 급여명세서와 지급·공제 항목 |
| `HR_THREADS`, `HR_MESSAGES` | 메시지 |
| `HR_NOTICES`, `HR_EVENTS` | 공지, 일정 |

### SQL 작성 규칙 (Oracle 호환)

`repo.ts`의 쿼리는 Oracle과 SQLite 모두에서 도는 문법만 씁니다.

- 바인드 변수는 `:이름` 형식입니다.
- `LIMIT`, `ROWNUM`, `RETURNING`은 쓰지 않습니다.
- 날짜는 `'YYYY-MM-DD'` 문자열, 시각은 `TIMESTAMP`로 저장합니다.

이 규칙 덕분에 Oracle 없이도 `npm test`로 같은 쿼리를 검증할 수 있습니다. 테스트는 Oracle이 거부하는 경우도 잡아냅니다.

- 빠진 바인드 (ORA-01008)
- 남는 바인드 (ORA-01036)
- 예약어 바인드 (ORA-01745)

Oracle 전용 기능(시퀀스, PL/SQL 등)이 필요하면 `repo.ts`에서 `db.dialect === 'oracle'`로 분기하면 됩니다.

## 4. 화면 구조

```
frontend/src/
├─ api/                서버 호출 (http.ts) + 서버 없는 데모용 (mock.ts)
├─ state/store.tsx     로그인 상태와 데이터, 서버 호출 동작
├─ lib/cursorBuilder.ts 커서 인트로 엔진
├─ components/         배경, 로그인, PC 메뉴, 모바일 사이드바, 공통 UI
├─ pages/              홈 · 근태휴가 · 급여 · 전자결재 · 메시지 · 인사정보 · 조직도 · 설정
└─ styles/global.css   글래스 디자인 전체
```

## 5. 실서비스 전에 할 일

- [ ] 서버를 둘 곳 정하기. 사내 PC나 서버에서 `npm start` 후 pm2·NSSM 같은 도구로 상시 실행합니다.
- [ ] HTTPS 적용. 앞단에 Nginx나 Caddy를 두고 `COOKIE_SECURE=true`로 설정합니다.
- [ ] 초기 비밀번호 변경 기능, 관리자용 직원 등록·수정 화면 추가
- [ ] 실제 급여 데이터 연동. 지금 공제액은 요율을 단순 적용한 예시입니다.
- [ ] Oracle 백업 정책 설정
