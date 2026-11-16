-- 일별 근로시간 집계 (#29). 설계: docs/work-hours-design.md
--
-- 규칙
--   - 한 사원·한 근무일에 한 행. 근무일은 출근한 날(자정을 넘는 근무도 시작한 날)이다.
--   - 시간은 모두 분 단위 정수. worked = regular + overtime + holiday + holiday_excess (CHECK). night 는 겹쳐서 따로 센다.
--   - 재계산은 기간(주 단위로 넓힌 월~일)의 행을 지우고 다시 넣는다(WorkHoursService). 원자료(출퇴근 기록·근무유형·휴일 달력)는 바꾸지 않는다.
--   - 가산율(*_rate)은 근무일 기준 legal_rate 적용값(퍼센트 숫자)을 그대로 남긴다. 금액은 급여 모듈이 계산한다.
--   - day_kind: WORKDAY 소정근로일, OFFDAY 휴무일, HOLIDAY 휴일 (WorkHoursCalculator.DayKind 와 같아야 한다)
--   - leave_kind: FULL_DAY, HALF_DAY_AM, HALF_DAY_PM 또는 NULL (WorkHoursCalculator.LeaveKind 와 같아야 한다)
--   - issues: 관리자 확인이 필요한 사유 코드를 쉼표로 이은 값(NO_WORK_PATTERN, MISSING_OUT). 없으면 NULL.
--   - 정규식에 중괄호를 쓰지 않는다(V006 참고).
-- MyBatis: resources/mapper/attendance/DailyWorkSummaryMapper.xml (JPA 엔티티 없음)

CREATE TABLE attendance_daily_summary (
    emp_id                 NUMBER(10)        NOT NULL,
    work_date              DATE              NOT NULL,
    day_kind               VARCHAR2(10 CHAR) NOT NULL,
    pattern_id             NUMBER(10),
    contract_minutes       NUMBER(5)         NOT NULL,
    leave_kind             VARCHAR2(12 CHAR),
    leave_minutes          NUMBER(5)         DEFAULT 0 NOT NULL,
    work_start             TIMESTAMP(6),
    work_end               TIMESTAMP(6),
    break_minutes          NUMBER(5)         NOT NULL,
    worked_minutes         NUMBER(5)         NOT NULL,
    regular_minutes        NUMBER(5)         NOT NULL,
    overtime_minutes       NUMBER(5)         NOT NULL,
    night_minutes          NUMBER(5)         NOT NULL,
    holiday_minutes        NUMBER(5)         NOT NULL,
    holiday_excess_minutes NUMBER(5)         NOT NULL,
    overtime_rate          NUMBER(7,3)       NOT NULL,
    night_rate             NUMBER(7,3)       NOT NULL,
    holiday_rate           NUMBER(7,3)       NOT NULL,
    holiday_excess_rate    NUMBER(7,3)       NOT NULL,
    issues                 VARCHAR2(200 CHAR),
    calculated_by          VARCHAR2(50 CHAR) NOT NULL,
    calculated_at          TIMESTAMP(6)      DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT pk_attendance_daily_summary PRIMARY KEY (emp_id, work_date),
    CONSTRAINT fk_daily_summary_emp FOREIGN KEY (emp_id) REFERENCES employee (emp_id),
    CONSTRAINT fk_daily_summary_pattern FOREIGN KEY (pattern_id) REFERENCES work_pattern (pattern_id),
    CONSTRAINT ck_daily_summary_date CHECK (work_date = TRUNC(work_date)),
    CONSTRAINT ck_daily_summary_day_kind CHECK (day_kind IN ('WORKDAY', 'OFFDAY', 'HOLIDAY')),
    CONSTRAINT ck_daily_summary_leave_kind
        CHECK (leave_kind IS NULL OR leave_kind IN ('FULL_DAY', 'HALF_DAY_AM', 'HALF_DAY_PM')),
    CONSTRAINT ck_daily_summary_leave
        CHECK ((leave_kind IS NULL AND leave_minutes = 0) OR (leave_kind IS NOT NULL AND leave_minutes >= 0)),
    CONSTRAINT ck_daily_summary_nonneg
        CHECK (contract_minutes >= 0 AND break_minutes >= 0 AND worked_minutes >= 0 AND regular_minutes >= 0
            AND overtime_minutes >= 0 AND night_minutes >= 0 AND holiday_minutes >= 0
            AND holiday_excess_minutes >= 0),
    CONSTRAINT ck_daily_summary_sum
        CHECK (worked_minutes = regular_minutes + overtime_minutes + holiday_minutes + holiday_excess_minutes),
    CONSTRAINT ck_daily_summary_night CHECK (night_minutes <= worked_minutes),
    CONSTRAINT ck_daily_summary_span
        CHECK ((work_start IS NULL AND work_end IS NULL) OR (work_start IS NOT NULL AND work_end IS NULL)
            OR (work_start IS NOT NULL AND work_end > work_start)),
    CONSTRAINT ck_daily_summary_rates
        CHECK (overtime_rate >= 0 AND night_rate >= 0 AND holiday_rate >= 0 AND holiday_excess_rate >= 0)
);

CREATE INDEX ix_daily_summary_date ON attendance_daily_summary (work_date);
