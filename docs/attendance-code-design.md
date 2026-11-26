# 근태코드 (#143)

휴가 종류와 근태 구분을 **근태코드**로 관리한다. 저장은 공통코드(`common_code_group`·`common_code`)에 하고 규칙(법인별, 기본 코드, 삭제·사용처, 참조 목록)은 `docs/common-code-design.md`를 그대로 따른다. 설정 칸이 많아 편집 화면은 **근태관리 > 근태코드 관리**(`attendanceCodes`)에 따로 둔다.

## 그룹 (V041, 시스템 그룹, `edit_page = 'attendanceCodes'`)

| 그룹 | 이름 | 처리방식(`common_code.process_type`) |
|---|---|---|
| `LEAVE_TYPE` | 휴가 종류 | `ANNUAL` 연차, `STATUTORY` 법정휴가, `COMPANY` 회사 약정휴가, `UNPAID` 무급휴가 |
| `ATTENDANCE_TYPE` | 근태 구분 | `BUSINESS_TRIP` 출장, `OUTSIDE_WORK` 외근, `REMOTE` 재택, `EDUCATION` 교육 — 모두 근무로 인정(근로시간 집계에 포함) |

- 로직은 코드값이 아니라 처리방식을 본다. 처리방식 목록은 DB CHECK `ck_common_code_process`와 `AttendanceCodeService.PROCESS_TYPES`가 같다. 다른 그룹은 처리방식이 NULL.
- 화면에서 추가할 수 있는 휴가 처리방식은 `COMPANY`·`UNPAID`뿐이다. 연차·법정휴가는 법이 정하므로 기본 코드만 둔다.
- 공통코드 화면에서는 두 그룹이 보기 전용이고 '근태관리에서 편집' 링크만 보인다. 공통코드 쓰기 API는 이 그룹을 409로 막는다.

## 기본 코드와 시드

- 기본 코드(`system_yn = 'Y'`, 삭제 불가·사용 안 함만): 연차(근로기준법 제60조), 출산전후휴가(근로기준법 제74조), 배우자 출산휴가(남녀고용평등법 제18조의2), 생리휴가(근로기준법 제73조, 무급), 가족돌봄휴가(남녀고용평등법 제22조의2 제2항), 난임치료휴가(같은 법 제18조의3).
- 회사 약정휴가 예시(삭제 가능): 경조사, 병가(무급·증빙 필요), 창립기념일. 근태 구분 4개도 삭제 가능.

## 휴가 종류 상세 설정 `attendance_leave_type` (PK `enter_cd, code`)

| 열 | 뜻 |
|---|---|
| `unit_day_yn`·`unit_half_day_yn`·`unit_hour_yn` | 사용 단위(종일·오전/오후 반차·시간 단위), 하나 이상 |
| `paid_yn` | 유급 여부. 법정휴가는 NULL(유급 기간은 legal_rate), 생리휴가는 `N` |
| `proof_required_yn` | 증빙 필요 |
| `annual_limit_days` | 연간 한도 일수, NULL = 없음 |
| `gender_limit` | 성별 제한(F/M), 법정휴가 중 해당 휴가만(출산전후휴가·생리휴가) |
| `legal_basis` | 법정 근거 문구 |

- 단위별 연차 차감량: 종일 1, 반차 0.5, 시간 단위 1/소정근로시간. 차감 계산은 휴가 신청 이슈에서 한다(여기서는 단위 허용 여부만 저장).
- 기본 코드는 처리방식·유급 여부·연간 한도를 고칠 수 없다(409). 사용 단위·증빙·이름·설명·순서·사용 여부는 고칠 수 있다.
- 무급휴가(`UNPAID`)는 유급일 수 없다(400). 약정·무급휴가는 유급 여부를 꼭 정한다.
- 코드를 지우면 상세 설정도 함께 지운다.

## 법정 기준값 (legal_rate, 화면에서 수정 불가)

조회일(Asia/Seoul) 기준으로 읽어 화면에 보인다. 값이 없으면 '기준값 미등록'으로 보인다.

| 코드 | legal_rate 키 |
|---|---|
| ANNUAL | `ANNUAL_LEAVE_BASE_DAYS`, `ANNUAL_LEAVE_MONTHLY_DAYS`, `ANNUAL_LEAVE_MAX_DAYS` |
| MATERNITY | `MATERNITY_LEAVE_DAYS`, `MATERNITY_LEAVE_MULTIPLE_DAYS`, `MATERNITY_LEAVE_PAID_DAYS`*, `MATERNITY_LEAVE_MULTIPLE_PAID_DAYS`* |
| SPOUSE_MATERNITY | `SPOUSE_MATERNITY_LEAVE_DAYS`, `SPOUSE_MATERNITY_LEAVE_PAID_DAYS`* |
| MENSTRUAL | `MENSTRUAL_LEAVE_DAYS_PER_MONTH` |
| FAMILY_CARE | `FAMILY_CARE_LEAVE_DAYS_PER_YEAR`, `FAMILY_CARE_LEAVE_PAID_DAYS`* |
| INFERTILITY_TREATMENT | `INFERTILITY_TREATMENT_LEAVE_DAYS_PER_YEAR`*, `INFERTILITY_TREATMENT_LEAVE_PAID_DAYS`* |

\* V042에서 항목을 등록하고, 값은 법령 데이터 작업(#148, law.go.kr 원문 확인)으로 `seed/legal_rate_statutory_leave.sql`에 넣었다(시드 주석의 'V039'는 V042로 바뀐 번호다). 정답은 `tests/legal-fixtures/statutory-leave-rate-boundaries.csv`이고 `StatutoryLeaveRateFixtureTest`가 시드를 이 화면 조회 경로로 읽어 비교한다.
`FAMILY_CARE_LEAVE_PAID_DAYS`는 법정 유급 규정이 없어(남녀고용평등법 제22조의2 제2항·제4항 제2호) 값 행이 없고 화면에 '기준값 미등록'으로 보인다.

## API (`AttendanceCodeController`)

| 메서드 | 경로 | 용도 |
|---|---|---|
| GET | `/api/attendance-codes/{group}` | 목록(사용 안 함 포함, 참조 건수·상세 설정·법정 기준값) |
| GET | `/api/attendance-codes/{group}/{code}` | 코드 하나(상세 설정 조회) |
| POST | `/api/attendance-codes/{group}` | 추가(201, 중복 409) |
| PUT | `/api/attendance-codes/{group}/{code}` | 수정(상세 설정 포함) |
| DELETE | `/api/attendance-codes/{group}/{code}` | 삭제(204, 기본 코드·참조 중 409) |

- `{group}`은 `LEAVE_TYPE`·`ATTENDANCE_TYPE`만(그 밖은 404). 세션 현재 법인만 다룬다.
- 조회는 로그인 사용자, 쓰기는 `CODE_WRITE`. 사용처 보기는 공통코드 API(`/api/code-groups/{group}/codes/{code}/usages`)를 쓴다.

## 화면

- 근태관리 > **근태코드 관리**(`attendanceCodes`, `HR`·`ADMIN`). [휴가 종류][근태 구분] 탭, 코드별 상세 설정, 사용처 보기·삭제는 공통코드 화면 컴포넌트를 재사용한다.

## 참조 목록

- 지금은 두 그룹을 참조하는 테이블이 없다(`attendance_leave_type`은 코드 자신의 설정이라 참조가 아니다). 휴가 신청 테이블이 생기면 그 이슈에서 `CodeReferences`에 추가한다.
