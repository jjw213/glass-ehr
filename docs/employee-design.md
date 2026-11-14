# 사원 기본정보(근로자 명부) 설계 (#14)

선행 결정: 사번 체계(#9, `docs/employee-number-design.md`), 주민등록번호 처리(#10, `docs/rrn-design.md`), 조직(#12, `docs/org-history-design.md`).
SPEC 1장의 "사원: 기본정보(고유식별정보 암호화·마스킹), 입사일, 고용형태(정규·계약·단시간), 소정근로시간" 정의를 따른다.

> 법령 원문 확인: 2026-11-12 기준 law.go.kr 자동 조회가 되지 않아(본문이 스크립트로 로드됨) 조문 원문을 확인하지 못했다.
> 아래 조문 대응은 이슈 #14 본문 목록 기준이며, **사람이 원문으로 다시 확인해야 한다.**

## 1. 테이블 (V009__employee.sql)
| 테이블 | 용도 |
|---|---|
| `biz_site` | 사업장(`S01` 형식). 사번 앞부분의 출처. 근무지 공통코드(`WORK_LOCATION`)와 분리(#14 사람 답변, 2026-11-12) |
| `emp_no_seq` | 사업장·입사연도별 마지막 순번. 등록 트랜잭션에서 사업장 행과 함께 잠그고 +1 |
| `employee` | 근로자 명부 기재사항 + 현재 소속·직급·직책 + 보존기한 |
| `emp_rrn` | 주민등록번호 암호문·마스킹 값·HMAC(평문 없음) |
| `rrn_access_log` | 원문 조회 로그(추가만, 원문·마스킹 값 없음) |

### 근로기준법 시행령 제20조 기재사항 ↔ 컬럼
| 기재사항 | 컬럼 |
|---|---|
| 성명 | `emp_name` |
| 성별 | `gender` (M/F) |
| 생년월일 | `birth_date` |
| 주소 | `address` |
| 이력 | `career_history` |
| 종사하는 업무의 종류 | `job_description` (+ 직군 `job_family_code`) |
| 고용 또는 고용갱신 연월일, 계약기간, 그 밖의 고용에 관한 사항 | `hire_date`, `renewal_date`, `contract_start_date`, `contract_end_date`, `employment_type_code`, `weekly_contract_minutes`, `work_location_code`, `employment_note` |
| 해고·퇴직·사망한 경우 그 연월일과 사유 | `retire_date`, `retire_reason_code`(공통코드 `RETIREMENT_REASON`, 사망 포함), `retire_reason_detail` |
| 그 밖에 필요한 사항 | `other_note` |

## 2. 현재 소속·직급·직책
- `current_dept_id`, `job_grade_code`, `job_title_code`는 입사 등록 때 첫 값만 받는다.
- 수정 API(`PUT /api/employees/{id}`)의 요청 형식에는 이 필드가 없고, 보내면 400으로 거부한다(조용히 무시하지 않음).
- JPA 매핑도 `updatable = false`, 엔티티에 변경 메서드가 없다. **인사발령 이슈에서 발령 반영 경로를 추가**한다.
- 부서 폐지 시 재직자 확인(`DeptMemberChecker`)은 `EmployeeDeptMemberChecker`가 현재 소속으로 센다(퇴직일 당일까지 재직으로 봄). 발령 이슈에서 발령 이력 기준으로 바꾼다.
- 입사 등록의 첫 소속 부서는 입사일에 신설되어 있고 **폐지일이 정해지지 않은 부서**여야 한다. 등록은 기한 없는 배정이라 폐지 예정 부서에 넣으면
  폐지일 뒤에도 재직자가 남기 때문이다(폐지 쪽 규칙과 대칭). 등록은 부서 폐지와 같은 조직 쓰기 잠금(`OrgWriteLock`)을 잡아 두 검사가 엇갈려 통과하지 않게 한다.
- 수정·퇴직 처리는 사원 행을 잠가(`SELECT ... FOR UPDATE`) 읽는다. 퇴직 처리 전에 읽은 수정 요청이 퇴직 정보를 덮어쓰지 않게 하기 위해서다.

## 3. 보존기한 (근로기준법 제42조)
- `record_retention_until = retire_date + N년`, N은 `legal_rate` 항목 `EMPLOYEE_RECORD_RETENTION_YEARS`(단위 YEARS, V010)의 **퇴직일 기준 적용값**.
- 값은 마이그레이션에 넣지 않았다. 사람이 원문 확인 후 `legal_rate_api`로 입력해야 하며, 값이 없으면 퇴직 처리가 409로 거부된다(기본값 없음).
- 기산일을 퇴직일로 둔 근거: 이슈 #14 "퇴직 후 보존기한". 시행령의 보존기간 기산일 조문(제22조로 추정)은 원문 재확인 대상.
- 2월 29일 퇴직이고 N년 뒤가 평년이면 2월 28일(`LocalDate.plusYears`).

## 4. 주민등록번호 (개인정보 보호법 제24조의2·제29조)
- AES-256-GCM(무작위 IV, AAD = emp_id), 저장 형식 IV‖암호문‖태그(`RAW(64)`), 키 버전 컬럼으로 키 교체 대비.
- 중복 검사는 HMAC-SHA256(암호화 키와 다른 키). `rrn_hash` UNIQUE.
- 키는 환경변수 `RRN_ENC_KEYS`(`버전:base64,...`), `RRN_CURRENT_KEY_VERSION`, `RRN_HMAC_KEY`로만 받는다(`.env.example`에는 이름만).
  키가 없으면 앱은 뜨지만 주민등록번호 저장·조회는 503.
- 마스킹: 앞 6자리만 보이고 뒷자리 7자리를 모두 가린다(`000000-*******`). rrn-design.md 6장의 "뒷자리 첫 자리 노출 여부"는
  더 보수적인 쪽으로 구현했다. 바꾸려면 `Rrn.masked()`와 V009 `ck_emp_rrn_masked`를 함께 고친다.
- 원문 조회 `GET /api/employees/{id}/rrn?purpose=VIEW|SOCIAL_INSURANCE|WITHHOLDING`: 인사담당·급여담당만. 성공·거부·대상 없음 모두
  별도 트랜잭션으로 로그를 커밋한 뒤 응답한다. 응답에 `Cache-Control: no-store`.
- `Rrn.toString()`·DTO `toString()`은 원문을 찍지 않는다. 오류 메시지에도 입력값을 되돌려 넣지 않는다.

## 5. 엑셀 일괄 등록·목록 내려받기 (#20)
- 파일 형식은 **CSV**(엑셀에서 열고 저장). 새 라이브러리 없이 기존 목록 내려받기(`EmployeeCsv`)와 같은 형식을 쓴다.
  읽을 때 BOM·UTF-8이면 UTF-8, 아니면 MS949(한글 Windows 엑셀 기본 CSV)로 읽는다.
- API(`EmployeeImportController`, 권한 EMP_WRITE = 관리자·인사):
  - `GET /api/employees/import/template` — 양식(머리글만, 필수 열은 `*`). 예시 행은 넣지 않는다(지우지 않고 올리면 가짜 사원이 생김).
  - `POST /api/employees/import/preview` (multipart `file`) — 행마다 입사 등록과 같은 검사(Bean Validation + `EmployeeService.checkCreate`
    + 사업장 사용 여부 + 주민등록번호 파일 안/DB 중복)를 하고 행 번호별 오류를 돌려준다. 저장하지 않는다.
  - `POST /api/employees/import/confirm` — 같은 파일을 다시 받아 처음부터 다시 검사한다. 오류가 한 행이라도 있으면 422 + 미리보기,
    없으면 한 트랜잭션으로 모두 등록(201, 사번 목록). 중간 실패 시 전부 되돌린다. 미리보기 결과를 서버에 보관하지 않는다.
- 열은 머리글 이름으로 찾으므로 순서를 바꿔도 된다. 날짜는 YYYY-MM-DD(엑셀이 바꾸는 YYYY/M/D·YYYY.M.D 허용). 한 파일 최대 1000행
  (법령 기준값이 아닌 운영 상한, `EmployeeImportCsv.MAX_ROWS`).
- 응답·오류 메시지에는 성명·사업장·입사일만 넣고 주민등록번호·생년월일·주소 값은 넣지 않는다.
- 목록 내려받기(`GET /api/employees/export`, EMP_EXPORT = 관리자·인사·급여)는 #19 그대로: 데이터 범위 안의 사원만, 인적사항
  (생년월일·주소·이력·메모·주민등록번호)은 어느 역할에도 넣지 않고, 파일에 든 사원마다 다운로드 로그(`EMP_LIST`/DOWNLOAD)를 남긴다.
- 화면: 인사정보 상단 `목록 내려받기`(관리자·인사·급여), `엑셀 일괄 등록`(관리자·인사) → `components/EmployeeImport.tsx`.
- 미결: 기존 사원 일괄 수정, 인적사항을 넣은 명부 내려받기(필요 시 역할별 열 정책을 따로 정함), xlsx 직접 읽기.

## 6. 남은 일 / 미결
- **요청자 식별은 임시로 `X-Actor-Id`·`X-Actor-Role` 헤더**를 쓴다(위조 가능). 백엔드 인증·권한 이슈에서 반드시 인증 정보로 바꾼다.
  등록·수정·퇴직 API의 쓰기 권한 검사도 그 이슈에서 붙인다.
- 사번 미결 사항(전보·재입사·999명 초과·입사일 정정)은 `employee-number-design.md` 3장 그대로. 999 초과는 지금 409로 거부한다.
- 퇴직 정정·취소, 보존기한 경과 후 파기 절차, 조회 로그 보관 기간은 후속 이슈.
- 데모 시드 `db/oracle/seed/employee_demo_seed.sql`: 가짜 사원 32명(성명 `예시사원NN`, 주소 `가상시 …`), 주민등록번호 없음. 마이그레이션 아님.
