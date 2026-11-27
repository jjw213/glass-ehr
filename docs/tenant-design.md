# 여러 법인(enter_cd) 설계 (#117)

> 법인 위에 고객사 단계가 있다 → `docs/saas-design.md` 2장

여러 법인(회사)이 한 시스템·한 DB 스키마를 함께 쓰도록 법인 코드 `enter_cd`를 도입하는 설계다.
이 문서는 설계만 정한다. 코드·마이그레이션은 아래 8장의 후속 이슈에서 바꾼다.
결정의 원문은 `docs/DECISIONS.md` 2026-11-23 행이다.

## 1. 저장소 주인 결정 (2026-11-23)

1. **키 방식**: 법인별 테이블은 PK 맨 앞에 `ENTER_CD`를 둔다(`(enter_cd, emp_id)` 등 복합키). 자식 FK도 `(enter_cd, 부모키)` 복합 FK다.
2. **로그인**: 회사 선택 + 아이디. 같은 아이디를 법인마다 따로 쓸 수 있다. 그래서 `app_user` PK는 `(enter_cd, login_id)`다.
3. **여러 법인 조회**: `GROUP_ADMIN` 역할만 화면에서 법인을 전환할 수 있다. 나머지 계정은 자기 법인만 본다.
4. **기준 데이터**(공통코드·결재 양식·근무유형·사업장·휴일 등)는 법인마다 따로 둔다. 새 법인을 만들 때 기본값을 복사해 두고 각 법인이 고친다. 법령 기준값(`legal_rate`)은 전 법인이 함께 쓴다.

## 2. 공통 규칙

- 컬럼: `enter_cd VARCHAR2(10 CHAR) NOT NULL`. 형식은 `CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]{2,10}$', 'c'))`이고, `enter_company(enter_cd)`를 참조하는 FK를 건다.
- 기존 IDENTITY 컬럼(`emp_id`, `appt_id` 등)은 그대로 둔다. 값은 전체 법인에서 유일하다. PK·UNIQUE·FK에는 `enter_cd`를 맨 앞에 넣는다.
- 자식 테이블 FK는 `(enter_cd, 부모키)`로 바꾼다. 그러면 DB가 **다른 법인 행 참조를 막는다**. 자식과 부모의 `enter_cd`가 같아야만 FK가 성립한다.
- 이름은 그대로 둔다. 제약 이름(`pk_employee`, `uq_employee_emp_no` 등)은 유지하고 컬럼 구성만 바꾼다(DROP 후 같은 이름으로 다시 만든다).
- 인덱스: 법인별 테이블의 조회용 인덱스(`ix_*`)도 `enter_cd`를 맨 앞에 넣어 다시 만든다. 함수 기반 UNIQUE 인덱스는 CASE 식 앞에 `CASE WHEN ... THEN enter_cd END`를 더한다. Oracle은 키 열이 모두 NULL인 행을 인덱스에 넣지 않으므로 기존 "조건에 맞는 행만 유일" 의미가 유지된다.
- 전환 중 임시 UNIQUE: 부모 PK를 복합으로 바꾸는 이슈와 자식 FK를 바꾸는 이슈가 다를 수 있다. 그 사이에는 부모에 `UNIQUE(기존키)`(이름 `uq_<테이블>_legacy_id`)를 임시로 둔다. 그러면 아직 바뀌지 않은 자식의 단일 컬럼 FK가 계속 성립한다. 마지막 정리 이슈(8장 7번)에서 임시 UNIQUE를 모두 지운다.
- **`enter_cd`는 요청 본문·쿼리 파라미터로 받지 않는다.** 서버는 로그인 세션의 현재 법인(4장)에서만 꺼낸다. 예외는 그룹 관리자의 법인 전환 API 하나다.
- 다른 법인의 ID로 조회·수정하면 **404**로 답한다. 존재 여부도 드러내지 않는다(403을 쓰지 않음).
- 사번(`emp_no`)·부서코드·근무유형 코드처럼 사람이 보는 코드는 법인 안에서만 유일하다. 그래서 두 법인에 같은 사번 `S01-2026-001`이 있을 수 있다.

## 3. 테이블 분류표 (기존 49개)

V001~V030 마이그레이션의 `CREATE TABLE` 49개를 분류했다. "바뀐 키"에 적지 않은 CHECK 제약은 바꾸지 않는다.

### 3.1 전체 공통 (enter_cd 없음, 7개)

| 테이블 | 이유 |
|---|---|
| `legal_rate_item` | 법령 기준값 항목. 법령은 모든 법인에 같게 적용된다(결정 4) |
| `legal_rate` | 법령 기준값. `legal_rate_api` 패키지(V004)도 그대로 둔다 |
| `app_role` | 역할 정의(관리자·인사·급여·부서장·일반 사원 + 새 `GROUP_ADMIN`). 역할 *부여*는 법인별 `app_user_role` |
| `app_permission` | 권한 코드 정의 |
| `app_role_permission` | 역할→권한 연결. 모든 법인이 같은 권한 체계를 쓴다 |
| `SPRING_SESSION` | Spring Session JDBC 테이블. 세션 속성에 법인을 저장한다(4장) |
| `SPRING_SESSION_ATTRIBUTES` | 위와 같음 |

새 법인 마스터 `enter_company`도 전체 공통이다(3.3).

### 3.2 법인별 (enter_cd 추가, 42개)

FK는 같은 법인 안의 부모를 가리키는 복합 FK가 된다. 공통 테이블(`app_role` 등)을 가리키는 FK는 단일 컬럼 그대로 둔다.

| 테이블 | 바뀐 PK | 바뀐 UNIQUE | 바뀐 FK (→ 부모) |
|---|---|---|---|
| `common_code_group` | `(enter_cd, group_code)` | — | — |
| `common_code` | `(enter_cd, group_code, code)` | — | `fk_common_code_group (enter_cd, group_code)` |
| `org_dept` | `(enter_cd, dept_id)` | `uq_org_dept_code (enter_cd, dept_code)` | — |
| `org_dept_hist` | `(enter_cd, hist_id)` | `uq_org_dept_hist_start (enter_cd, dept_id, valid_from)` | `fk_org_dept_hist_dept`·`_parent (enter_cd, dept_id/parent_dept_id)` → org_dept, `fk_org_dept_hist_head (enter_cd, head_emp_id)` → employee |
| `biz_site` | `(enter_cd, site_code)` | — | — |
| `emp_no_seq` | `(enter_cd, site_code, hire_year)` | — | `fk_emp_no_seq_site (enter_cd, site_code)` |
| `employee` | `(enter_cd, emp_id)` | `uq_employee_emp_no (enter_cd, emp_no)` | `fk_employee_site (enter_cd, site_code)`, `fk_employee_dept (enter_cd, current_dept_id)` |
| `emp_rrn` | `(enter_cd, emp_id)` | `uq_emp_rrn_hash (enter_cd, rrn_hash)` ※ | `fk_emp_rrn_emp (enter_cd, emp_id)` |
| `rrn_access_log` | `(enter_cd, log_id)` | — | 없음(로그, FK 걸지 않음). 열 추가는 4.4 |
| `employment_contract` | `(enter_cd, contract_id)` | — | `fk_employment_contract_emp (enter_cd, emp_id)` |
| `app_user` | `(enter_cd, login_id)` | `uq_app_user_emp (enter_cd, emp_id)` | `fk_app_user_emp (enter_cd, emp_id)` |
| `app_user_role` | `(enter_cd, login_id, role_code)` | — | `fk_app_user_role_user (enter_cd, login_id)`; `fk_app_user_role_role`은 그대로(공통) |
| `privacy_access_log` | `(enter_cd, log_id)` | — | 없음(로그). 열 추가는 4.4 |
| `login_access_log` | `(log_id)` 유지 ※※ | — | 없음(로그). 열 추가는 4.4 |
| `hr_appointment` | `(enter_cd, appt_id)` | `uq_hr_appointment_no (enter_cd, appt_no)` | `fk_hr_appt_emp (enter_cd, emp_id)`, `fk_hr_appt_before_dept`·`_after_dept (enter_cd, *_dept_id)` |
| `hr_appointment_cancel` | `(enter_cd, cancel_appt_id)` | `uq_hr_appt_cancel_original (enter_cd, original_appt_id)` | `fk_hr_appt_cancel_self`·`_original (enter_cd, *_appt_id)` → hr_appointment |
| `work_pattern` | `(enter_cd, pattern_id)` | `uq_work_pattern_code (enter_cd, pattern_code)` | — |
| `emp_work_pattern` | `(enter_cd, assign_id)` | `uq_emp_work_pattern_start (enter_cd, emp_id, valid_from)` | `fk_emp_work_pattern_emp (enter_cd, emp_id)`, `fk_emp_work_pattern_pattern (enter_cd, pattern_id)` |
| `worker_rep_agreement` | `(enter_cd, agreement_id)` | — | `fk_wra_site (enter_cd, scope_site_code)`, `fk_wra_rep_emp (enter_cd, rep_emp_id)` |
| `worker_rep_agreement_file` | `(enter_cd, file_id)` | — | `fk_wra_file_agreement (enter_cd, agreement_id)` |
| `holiday` | `(enter_cd, holiday_id)` | `uq_holiday_date_name (enter_cd, holiday_date, holiday_name)`, `uq_holiday_replaces (enter_cd, replaces_holiday_id)`, `ux_holiday_reserved_date` 함수 인덱스에 enter_cd 추가 | `fk_holiday_replaces (enter_cd, replaces_holiday_id)`, `fk_holiday_agreement (enter_cd, agreement_id)` |
| `attendance_punch` | `(enter_cd, punch_id)` | — | `fk_punch_emp (enter_cd, emp_id)` |
| `attendance_daily_summary` | `(enter_cd, emp_id, work_date)` | — | `fk_daily_summary_emp (enter_cd, emp_id)`, `fk_daily_summary_pattern (enter_cd, pattern_id)` |
| `retirement_case` | `(enter_cd, case_id)` | `uq_retirement_case_emp (enter_cd, emp_id)` | `fk_retirement_case_emp (enter_cd, emp_id)` |
| `retirement_checklist_item` | `(enter_cd, case_id, item_code)` | — | `fk_retirement_checklist_case (enter_cd, case_id)` |
| `employment_certificate` | `(enter_cd, cert_id)` | — | `fk_employment_cert_emp (enter_cd, emp_id)` |
| `approval_template` | `(enter_cd, template_id)` | `uq_approval_template_from (enter_cd, request_type, valid_from)`, `ux_approval_template_open` 함수 인덱스에 enter_cd 추가 | — |
| `approval_template_step` | `(enter_cd, template_id, step_no)` | — | `fk_approval_tstep_template (enter_cd, template_id)` |
| `approval_doc` | `(enter_cd, approval_id)` | `uq_approval_doc_ref (enter_cd, request_type, request_ref)` | `fk_approval_doc_requester (enter_cd, requester_emp_id)`, `fk_approval_doc_template (enter_cd, template_id)` |
| `approval_doc_step` | `(enter_cd, step_id)` | `uq_approval_doc_step (enter_cd, approval_id, step_no)` | `fk_approval_dstep_doc (enter_cd, approval_id)`, `fk_approval_dstep_actor (enter_cd, acted_emp_id)` |
| `approval_doc_step_approver` | `(enter_cd, step_id, emp_id)` | — | `fk_approval_dsa_step (enter_cd, step_id)`, `fk_approval_dsa_emp (enter_cd, emp_id)` |
| `approval_action_log` | `(enter_cd, action_id)` | — | `fk_approval_log_doc (enter_cd, approval_id)`, `fk_approval_log_actor (enter_cd, actor_emp_id)` |
| `special_overtime_auth` | `(enter_cd, auth_id)` | `uq_special_overtime_auth_no (enter_cd, auth_no)` | — |
| `special_overtime_auth_emp` | `(enter_cd, auth_id, emp_id)` | — | `fk_sot_emp_auth (enter_cd, auth_id)`, `fk_sot_emp_emp (enter_cd, emp_id)` |
| `overtime_request` | `(enter_cd, request_id)` | — | `fk_overtime_request_emp (enter_cd, emp_id)` |
| `family_leave_request` | `(enter_cd, req_id)` | — | `fk_family_leave_emp (enter_cd, emp_id)`, `fk_family_leave_pattern (enter_cd, work_pattern_id)`, `fk_family_leave_leave_appt`·`_return_appt (enter_cd, *_appt_id)`, `fk_family_leave_reduced`·`_restore (enter_cd, *_assign_id)` |
| `flex_work_assign` | `(enter_cd, flex_id)` | — | `fk_flex_work_assign_emp (enter_cd, emp_id)`, `fk_flex_work_assign_agreement (enter_cd, agreement_id)` |
| `flex_work_plan` | `(enter_cd, flex_id, work_date)` | — | `fk_flex_work_plan_assign (enter_cd, flex_id)` |
| `msg_thread` | `(enter_cd, thread_id)` | — | — |
| `msg_thread_member` | `(enter_cd, thread_id, emp_id)` | — | `fk_msg_member_thread (enter_cd, thread_id)`, `fk_msg_member_emp (enter_cd, emp_id)` |
| `msg_message` | `(enter_cd, message_id)` | — | `fk_msg_message_member (enter_cd, thread_id, sender_emp_id)` |
| `msg_read` | `(enter_cd, message_id, emp_id)` | — | `fk_msg_read_message (enter_cd, message_id)`, `fk_msg_read_member (enter_cd, thread_id, emp_id)` |

※ `uq_emp_rrn_hash`: 전적은 "퇴직 + 다른 법인 신규 입사"(6장)이므로, 같은 사람의 주민등록번호가 두 법인에 하나씩 있어야 한다. 그래서 법인 안에서만 유일하게 바꾼다.

※※ `login_access_log`: 로그인 실패 기록은 존재하지 않는 법인·아이디로 시도한 것도 남겨야 한다. 그래서 FK를 걸지 않고 PK도 `log_id` 그대로 둔다. `enter_cd`는 NULL을 허용하는 열로 더한다(4.4).

시퀀스 `hr_appointment_no_seq`(V014)는 전체 공통 그대로 둔다. 발령번호 유일성은 `(enter_cd, appt_no)`로 바뀌므로 법인마다 번호가 띄엄띄엄 매겨져도 문제가 없다.
`emp_no_seq`는 법인별 채번이므로 PK에 `enter_cd`를 넣는다.

### 3.3 법인 마스터 `enter_company` (새 테이블, 전체 공통)

```sql
CREATE TABLE enter_company (
    enter_cd      VARCHAR2(10 CHAR)   NOT NULL,
    enter_name    VARCHAR2(100 CHAR)  NOT NULL,
    biz_reg_no    VARCHAR2(10 CHAR)   NOT NULL,   -- 사업자등록번호 숫자 10자리(하이픈 없이 저장, 화면에서 000-00-00000)
    use_yn        CHAR(1)             DEFAULT 'Y' NOT NULL,
    sort_order    NUMBER(5)           DEFAULT 0 NOT NULL,
    created_at    TIMESTAMP(6)        DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at    TIMESTAMP(6)        DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT pk_enter_company PRIMARY KEY (enter_cd),
    CONSTRAINT uq_enter_company_biz_reg_no UNIQUE (biz_reg_no),
    CONSTRAINT ck_enter_company_cd CHECK (REGEXP_LIKE(enter_cd, '^[A-Z0-9]{2,10}$', 'c')),
    CONSTRAINT ck_enter_company_name CHECK (LENGTH(TRIM(enter_name)) > 0),
    CONSTRAINT ck_enter_company_biz_reg_no CHECK (REGEXP_LIKE(biz_reg_no, '^[0-9]{10}$', 'c')),
    CONSTRAINT ck_enter_company_use_yn CHECK (use_yn IN ('Y', 'N'))
);
```

- 사업자등록번호는 법인(사업자)을 알리는 공개 정보다. 고유식별정보(주민등록번호 등)가 아니므로 암호화하지 않는다. 시드에는 실제 번호가 아닌 명백한 가짜 값(`0000000000` 등)만 넣는다.
- 사업자등록번호 검증 숫자(체크섬) 검사는 넣지 않는다. 근거 규정을 원문으로 확인하지 않았기 때문이다. 필요하면 후속 이슈에서 질문한다.
- `use_yn = 'N'`인 법인은 로그인 회사 목록과 법인 전환 목록에서 빠진다. 데이터는 지우지 않는다(근로자명부 보존 의무, 근로기준법 제42조).
- 법인 행은 지우지 않는다(자식 FK가 막는다). 관리 화면·API는 8장 1번 이슈에서 `GROUP_ADMIN` 전용으로 만든다.

## 4. 로그인·세션·로그

### 4.1 로그인

- 로그인 요청은 `POST /api/auth/login {enterCd, loginId, password}`다. 이때만 `enterCd`를 본문으로 받는다(로그인 전이라 세션이 없다).
- 로그인 화면의 회사 목록은 `GET /api/auth/companies`로 받는다. 로그인 전에도 열리며, `use_yn='Y'`인 법인의 `enter_cd`·`enter_name`만 `sort_order` 순서로 준다. 사업자등록번호는 주지 않는다.
- 없는 법인, 사용 안 함 법인, 그 법인에 없는 아이디는 모두 지금의 `UNKNOWN_USER`와 같은 응답·같은 처리 시간으로 거부한다. 응답만 보고 법인이나 아이디가 있는지 알 수 없게 한다.
- 실패 횟수·잠금(`glassehr.auth.max-failed-attempts`·`lock-duration`)은 `app_user` 행 단위로 센다. 따라서 `(enter_cd, login_id)`마다 따로 센다.
- `DemoAccountSeeder`의 데모 계정은 `C001`에 만든다.

### 4.2 세션 속성

| 속성 | 값 | 바뀌는 때 |
|---|---|---|
| `glassehr.auth.enterCd` (신규) | 로그인한 법인 = **주체 법인** | 바뀌지 않음 |
| `glassehr.auth.loginId` (기존) | 로그인 아이디 = 주체 아이디 | 바뀌지 않음 |
| `glassehr.auth.currentEnterCd` (신규) | **현재 법인**: 모든 업무 조회·수정의 법인 조건 | 로그인 시 주체 법인. `GROUP_ADMIN` 전환 시에만 바뀜 |
| `PRINCIPAL_NAME_INDEX_NAME` (기존) | `enter_cd + ":" + login_id` 로 바꿈 | 바뀌지 않음 |

- `PRINCIPAL_NAME_INDEX_NAME`을 법인과 함께 적는 이유가 있다. 같은 아이디가 두 법인에 있을 때 한 사람의 강제 로그아웃(세션 일괄 만료)이 다른 법인 계정까지 끊지 않게 하려는 것이다.
- `AuthInterceptor`는 요청마다 `(enterCd, loginId)`로 `app_user`를 읽는다. 그리고 요청 범위 객체(현재 사용자)에 주체 법인·주체 아이디·현재 법인을 함께 담는다. 서비스·리포지토리는 이 객체의 **현재 법인**만 쓴다.
- test 프로필의 `X-Actor-Id` 헤더 식별은 test 전용으로 남긴다. 법인은 `X-Actor-Enter-Cd` 헤더로 받고, 없으면 `C001`을 쓴다. 이 헤더도 test 프로필에서만 쓴다.

### 4.3 그룹 관리자 법인 전환

- `app_role`에 `GROUP_ADMIN` 역할을 추가한다(전체 공통). 부여는 주체 법인의 `app_user_role`로 한다. 권한 코드 `ENTER_SWITCH`(법인 전환)와 `ENTER_COMPANY_MANAGE`(법인 마스터 관리)는 `GROUP_ADMIN`에만 연결한다.
- `PUT /api/auth/current-enter {enterCd}`가 전환 API다. `GROUP_ADMIN`이 아니면 403을 준다. 없거나 사용 안 함인 법인이면 404를 준다. 성공하면 세션의 `currentEnterCd`만 바꾼다. 주체 법인·아이디·로그인 시각은 그대로 두고 세션 ID는 다시 발급하지 않는다(로그인 주체가 바뀌지 않으므로).
- `GET /api/auth/me`는 주체 법인·현재 법인·전환 가능 여부를 함께 준다. 화면은 `GROUP_ADMIN`일 때만 상단에 법인 선택 상자를 보여 준다.
- 전환한 뒤의 권한은 주체 계정의 역할(주체 법인 `app_user_role`)로 판단한다. 부서장 자동 역할과 '본인' 판단은 주체 법인의 사원(`app_user.emp_id`)으로 한다. 그래서 다른 법인에서는 본인·부서장 범위가 비어 있고, 관리자 역할로 볼 수 있는 화면만 보인다.
- 법인 전환도 접속 기록으로 남긴다. `login_access_log.event_code`에 `ENTER_SWITCH`를 추가하고 대상 법인을 적는다.

### 4.4 로그 테이블: 주체 법인과 대상 법인을 둘 다

개인정보 조회 로그와 접속 기록에는 "누가(어느 법인의 누가)"와 "어느 법인의 데이터를"을 함께 남긴다. 둘이 다른 경우는 그룹 관리자가 전환해서 조회할 때다.

| 테이블 | 추가 열 | 의미 |
|---|---|---|
| `privacy_access_log` | `enter_cd` (NOT NULL, PK 앞) | 대상 법인 = 조회한 데이터의 법인(현재 법인) |
| | `accessor_enter_cd` (NOT NULL) | 주체 법인. `accessor_id`는 주체 법인의 login_id |
| `rrn_access_log` | `enter_cd` (NOT NULL, PK 앞) | 대상 법인 |
| | `accessor_enter_cd` (NOT NULL) | 주체 법인 |
| `login_access_log` | `enter_cd` (NULL 허용) | 로그인 시도 법인(형식이 맞으면 입력값, 아니면 NULL). 전환이면 주체 법인 |
| | `target_enter_cd` (NULL 허용) | `ENTER_SWITCH`일 때 전환 대상 법인. 나머지는 NULL |

- 로그 조회 화면은 자기 현재 법인의 대상 로그만 보여 준다. 다른 법인 관리자가 우리 법인 데이터를 본 기록도 대상 법인 기준이므로 우리 법인 관리자에게 보인다.
- 기존 로그 행은 `enter_cd = accessor_enter_cd = 'C001'`로 채운다.

## 5. 법인 조건을 강제하는 방법

### 5.1 비교

| 방법 | 장점 | 한계 |
|---|---|---|
| Hibernate 6 `@TenantId` + `CurrentTenantIdentifierResolver` | JPA 엔티티의 HQL·Criteria 조회와 저장에 법인 값이 자동으로 들어간다 | MyBatis(`LegalRateMapper`·`DailyWorkSummaryMapper`)와 JdbcTemplate(`MessageRepository`·`DemoAccountSeeder`)에는 적용되지 않는다. 그래서 별도 규칙이 어차피 필요하다. **복합키(@IdClass/@EmbeddedId)와 함께 쓸 수 있는지, `find()`·네이티브 쿼리에도 걸리는지는 이번에 원문으로 확인하지 못했다(미확인).** 이 실행 환경에서 docs.jboss.org를 열 수 없었고 로컬에는 hibernate-core 6.6.33 jar만 있다. 또 그룹 관리자 전환 때문에 resolver가 세션 값에 기대게 되어 배치·스케줄러(매일 00:05 발령 반영)에서 법인을 바꿔 가며 도는 처리가 복잡해진다 |
| 리포지토리 메서드 규칙 (JPA) | 조건이 메서드 이름·쿼리에 그대로 보인다. 배치가 법인을 돌며 넘기기 쉽다. Hibernate 동작에 기대지 않는다 | 사람이 빠뜨릴 수 있다 → 아래 검사 테스트로 막는다 |
| MyBatis·JdbcTemplate 파라미터 | SQL에 `enter_cd = #{enterCd}`가 그대로 보인다 | 위와 같음 |

### 5.2 선택: 명시적 법인 조건 + 검사 테스트 (`@TenantId`는 지금 쓰지 않음)

모든 쿼리 방식에 같은 규칙을 쓴다. 이유는 셋이다. 첫째, MyBatis·JdbcTemplate에는 `@TenantId`가 닿지 않아 명시 규칙이 어차피 필요하다. 둘째, `@TenantId`와 복합키를 함께 쓸 수 있는지 확인하지 못했다. 셋째, 배치가 법인을 넘기는 방식이 한 가지로 같아진다.

- **JPA**
  - 법인별 엔티티의 `@Id`는 기존 IDENTITY 컬럼 하나로 둔다(값이 전체에서 유일하므로 영속성 컨텍스트 식별에 충분). `enter_cd`는 `updatable = false`인 일반 컬럼으로 매핑하고, DB의 PK·FK만 복합으로 한다(`ddl-auto: validate`는 PK 구성을 검사하지 않는다).
  - 리포지토리 메서드는 `findByEnterCdAndEmpId(enterCd, empId)`처럼 **첫 인자가 `enterCd`**다. `@Query`(JPQL·네이티브 모두)에는 `e.enterCd = :enterCd`를 넣는다.
  - 법인별 리포지토리에서 `findById`·`getReferenceById`·`existsById`·`findAll()`·`deleteById` 같은 상속 메서드는 쓰지 않는다. 이 메서드들은 법인 조건 없이 ID로만 찾는다.
  - 저장 시 `enter_cd`는 서비스가 현재 사용자 객체에서 넣는다. 요청 DTO에는 `enterCd` 필드를 두지 않는다.
  - 이미 복합 ID로 매핑한 엔티티(`attendance_daily_summary`·`common_code` 등, IDENTITY 컬럼이 없는 테이블)는 `enter_cd`를 복합 ID에 포함한다.
- **MyBatis·JdbcTemplate**: 매퍼 메서드·SQL은 `enterCd` 파라미터를 받고, 법인별 테이블마다 `WHERE`·`JOIN ON`에 `enter_cd` 조건을 건다. 다른 법인별 테이블과 조인할 때는 `a.enter_cd = b.enter_cd`도 함께 쓴다.
- **검사 테스트**(새 라이브러리 없이, 8장 1번 이슈에서 추가)
  1. 법인별 엔티티의 리포지토리 소스에서 위 상속 메서드 호출을 찾으면 실패한다.
  2. 매퍼 XML·`MessageRepository` 등의 SQL 문자열에서 법인별 테이블 이름이 나오는데 `enter_cd`가 없으면 실패한다.
- **통합 테스트**: 바꾼 테이블마다 "법인 A 세션으로 법인 B 데이터 조회·수정 → 404/거부" Oracle 통합 테스트를 1건 이상 둔다. DB 복합 FK가 다른 법인 참조를 막는지도 확인한다.
- `@TenantId`는 지금 쓰지 않는다. 다시 검토하려면 따로 이슈를 만들고 복합키·`find()`·네이티브 쿼리 동작을 스파이크 테스트로 먼저 확인한다.

## 6. 법인 간 이동(전적)

- 다른 법인으로 옮기는 것은 법적으로 다른 사용자와 새로 근로계약을 맺는 것이다. 그래서 **원 법인 퇴직 + 새 법인 신규 입사**로 처리한다.
  - 원 법인: 기존 퇴직 처리(#18)를 쓴다. 사원 레코드·근로자명부 보존기한은 원 법인에 남는다.
  - 새 법인: 새 사원 레코드를 만든다. 새 `emp_id`와 그 법인의 사번이 붙고, 주민등록번호도 그 법인의 `emp_rrn`에 따로 저장한다(3.2 ※).
- 두 레코드를 "같은 사람"으로 잇는 표시(근속 통산·이력 연결)는 이번 설계 **범위 밖**이다. 필요하면 후속 제안으로 다룬다.
- 같은 사람이 두 법인에서 로그인 계정을 각각 가질 수 있다(결정 2). 하나의 계정으로 두 법인을 보는 것은 `GROUP_ADMIN`만 가능하다.

## 7. 법인·사업장 단위로 달라지는 운영 설정

지금은 `application.yml`의 `glassehr.*`에 한 회사 값으로만 있다. 법인(또는 사업장)마다 다를 수 있는 값은 아래와 같다.

| 설정 키 | 지금 기본값 | 단위 | 비고 |
|---|---|---|---|
| `glassehr.workplace.regular-headcount` | 30 | 법인(사업장별로 셀 필요가 생기면 사업장) | 상시근로자 수. 규모별 적용 조문은 legal_rate 적용 규모 하한과 비교한다. 날짜에 따라 바뀌므로 적용시작일이 필요하다 |
| `glassehr.contract.expiry-alert-days` | 30 | 법인 | 근로계약 만료 알림 일수 |
| `glassehr.contract.fixed-term-limit-alert-days` | 30 | 법인 | 기간제 사용기간 상한 알림 일수 |
| `glassehr.agreement.expiry-alert-days` | 30 | 법인 | 서면합의서 만료 예정 알림 일수 |
| `glassehr.attendance.weekly-hours-warn-remain-minutes` | 240 | 법인 | 주 52시간 한도 근접 경고 기준 |
| `glassehr.auth.max-failed-attempts` | 5 | 법인 | 로그인 실패 잠금 횟수 |
| `glassehr.auth.lock-duration` | 30m | 법인 | 잠금 시간 |

법인과 무관하게 시스템 전체 값으로 남는 것:

- `glassehr.rrn.*`(암호화 키, 비밀값)
- `glassehr.agreement.max-file-bytes`(multipart 한도와 짝)
- `glassehr.auth.demo-password`
- `server.servlet.session.*`(세션 유효 시간·쿠키)

옮기는 방식(#131에서 구현):

- V047 `enter_setting(enter_cd, setting_key, effective_from, setting_value, updated_by, updated_at)` PK `(enter_cd, setting_key, effective_from)`에 값을 둔다. 날짜 d의 값은 `effective_from <= d`인 행 중 가장 늦은 것이다. 값이 없는 법인은 `application.yml` 값을 기본값으로 쓴다(전역 값은 기본값으로만 쓴다).
- 변경 이력은 `enter_setting_hist`(PK `(enter_cd, hist_id)`, `(enter_cd, setting_key, effective_from)` 복합 FK로 같은 법인 설정만 가리킨다)에 등록·수정마다 한 행을 남긴다.
- 키 목록과 입력 범위는 코드(`EnterSettingKey`)가 정한다. 옮긴 키: 위 표의 상시근로자 수·계약 만료 알림·기간제 상한 알림·서면합의서 만료 알림·주 52시간 근접 기준. 계산·판정(`PublicHolidayPaidRule`, `ContractService.alerts`, `LaborAgreementService.expiring`, `WeeklyHoursService`)은 `EnterSettingService.value(enterCd, key, date)`로 그 법인·그 날짜 값을 읽는다.
- API `GET /api/enter-settings`, `PUT /api/enter-settings/{key}`(`{effectiveFrom, value}`), `GET /api/enter-settings/history`. 권한 `EMP_WRITE`(그 법인 관리자·인사), 법인은 세션 현재 법인만. 화면: 시스템관리 > 회사 설정.
- 로그인 실패 잠금 횟수·잠금 시간(`glassehr.auth.*`)은 아직 전역 값이다(잠금 시간이 정수가 아닌 기간 값이고 로그인 전 처리라 후속 제안).

## 8. 기존 데이터 이전과 후속 이슈 순서

### 8.1 기존 데이터 이전 (각 이슈의 마이그레이션 공통)

1. 첫 이슈의 마이그레이션에서 `enter_company`를 만들고 `C001` 행을 넣는다(이름 `기본 법인(예시)`, 가짜 사업자등록번호, `use_yn='Y'`, 정렬 1).
2. 각 이슈의 마이그레이션은 바꾸는 테이블마다 다음 순서로 진행한다.
   1. 자식 FK DROP
   2. 열 추가: `enter_cd VARCHAR2(10 CHAR) DEFAULT 'C001' NOT NULL`. 이렇게 하면 기존 행이 모두 `C001`로 채워진다.
   3. `DEFAULT` 제거: `MODIFY enter_cd DEFAULT NULL`. 새 행이 법인을 빠뜨리면 오류가 나게 하려는 것이다.
   4. PK·UNIQUE DROP 후 같은 이름으로 다시 만든다.
   5. `fk_<테이블>_enter`(→ `enter_company`) 추가
   6. 자식 복합 FK 추가(같은 이슈 안의 자식만. 다른 이슈의 자식은 임시 UNIQUE로 유지, 2장)
3. 이미 머지된 마이그레이션 파일은 고치지 않는다. 한 이슈는 마이그레이션 1개다(번호는 루프가 알려 준다).
4. 바꾼 테이블은 `seed/employee_demo_seed.sql`과 관련 테스트도 같은 이슈에서 고친다. 법령 시드(`seed/legal_rate_*`)는 바꾸지 않는다.

### 8.2 후속 이슈 순서

앞 이슈가 부모 테이블을 바꿔야 뒤 이슈의 자식이 복합 FK를 걸 수 있다. 그래서 부모 → 자식 순서로 나눈다. 49개 = 공통 7개(변경 없음) + 아래 42개.

| 순서 | 이슈 제목(제안) | 바꾸는 테이블 | 그 밖의 범위 |
|---|---|---|---|
| 1 | [법인] 법인 마스터와 인사·조직 기반 테이블 | `enter_company`(신규), `biz_site`, `emp_no_seq`, `org_dept`, `org_dept_hist`, `employee`, `emp_rrn`, `common_code_group`, `common_code` (8개 + 신규 1) | 5.2 검사 테스트, 현재 법인 요청 객체(세션 연결 전에는 `C001` 고정), 법인 마스터 조회 API. `employee`·`org_dept`에 임시 UNIQUE |
| 2 | [법인] 회사 선택 로그인·세션 현재 법인·그룹 관리자 전환 | `app_user`, `app_user_role`, `login_access_log`, `privacy_access_log`, `rrn_access_log` (5개), `app_role`·`app_permission`·`app_role_permission` 행 추가(`GROUP_ADMIN`, `ENTER_SWITCH`, `ENTER_COMPANY_MANAGE`) | 4장 전체, 로그인 화면 회사 선택, 상단 법인 전환, 법인 마스터 관리 화면. 단, 아래 배포 잠금은 유지 |
| 3 | [법인] 인사 업무 테이블(발령·계약·퇴직) | `hr_appointment`, `hr_appointment_cancel`, `employment_contract`, `retirement_case`, `retirement_checklist_item`, `employment_certificate` (6개) | 매일 00:05 발령 반영 작업을 법인별로 반복 |
| 4 | [법인] 근태 기준 테이블(근무유형·서면합의·휴일·유연근무) | `work_pattern`, `emp_work_pattern`, `worker_rep_agreement`, `worker_rep_agreement_file`, `holiday`, `flex_work_assign`, `flex_work_plan` (7개) | 휴일 CSV 불러오기는 현재 법인에만 |
| 5 | [법인] 근태 기록·신청 테이블(출퇴근·집계·연장·가족돌봄) | `attendance_punch`, `attendance_daily_summary`, `overtime_request`, `special_overtime_auth`, `special_overtime_auth_emp`, `family_leave_request` (6개) | MyBatis `DailyWorkSummaryMapper` 법인 파라미터 |
| 6 | [법인] 결재·메시지 테이블 | `approval_template`, `approval_template_step`, `approval_doc`, `approval_doc_step`, `approval_doc_step_approver`, `approval_action_log`, `msg_thread`, `msg_thread_member`, `msg_message`, `msg_read` (10개) | JdbcTemplate `MessageRepository` 법인 조건 |
| 7 | [법인] 새 법인 생성(기준 데이터 복사)·법인별 운영 설정·임시 UNIQUE 정리 | `enter_setting`(신규, 7장 제안), 1~6번의 임시 UNIQUE 제거 | 새 법인 생성 시 기준 데이터 복사(아래) |

- 2번 이슈가 `app_user`를 바꾸기 전까지 로그인은 지금처럼 동작한다. 1번 이슈의 현재 법인은 `C001`로 고정한다. 그래서 1번이 머지되어도 기존 기능이 그대로 돈다.
- **다법인 배포 잠금:** 2번 이슈에서는 서버 설정 `glassehr.tenant.multi-company-enabled`를 추가하고 기본값을 `false`로 둔다. 이 값이 `false`이면 서버가 `C001` 이외 법인의 로그인과 법인 전환 요청을 모두 거부해야 한다. 화면 숨김만으로 대체하지 않는다. 3~6번의 스키마·저장소 법인 격리가 모두 배포되고 검사 테스트가 통과한 뒤에만 운영 환경에서 이 값을 `true`로 바꾼다. 따라서 법인 마스터 관리 기능으로 다른 법인을 미리 등록하더라도, 6번 완료 전에는 그 법인으로 업무 기능에 진입할 수 없다.
- 새 법인 생성 때 복사할 기준 데이터(결정 4, 7번 이슈)
  - 복사함: `common_code_group`·`common_code`, `work_pattern`, 지금 유효한 `approval_template`·`approval_template_step`, `holiday` 중 법정공휴일·대체공휴일 행
  - 복사 안 함: `biz_site`(새 법인이 자기 사업장을 등록해야 사원을 등록할 수 있다), `worker_rep_agreement`(근로자대표 합의는 법인마다 따로 해야 한다), 회사휴일
  - 복사 원본은 기존 법인 중 하나(기본 `C001`)로 하자고 제안한다. 확정은 7번 이슈에서 한다.
- 이 표가 각 후속 이슈 본문과 다르면 이 문서를 기준으로 하고, 이슈에 댓글로 차이를 알린다.

## 9. 미확인·후속 확인 사항

- Hibernate 6.6 `@TenantId`와 복합키를 함께 쓸 수 있는지, `find()`·네이티브 쿼리에도 걸리는지: 원문 미확인(5.1). 지금 선택(5.2)은 이 동작에 기대지 않는다.
- 사업자등록번호 검증 숫자 규칙: 근거 규정 미확인이라 검사하지 않는다(3.3).
- 법인별 운영 설정 테이블 구성과 새 법인 기준 데이터 복사 원본: 7번 이슈에서 저장소 주인에게 확인한다.
- 접속기록 보관 기간 등 `docs/auth-design.md` 8장의 미결 사항은 이 설계로 바뀌지 않는다.
