-- 여러 법인 7단계: 출퇴근·근로시간 집계·유연근무 (#126). 설계: docs/tenant-design.md 3.2·8.1
--
-- 이 마이그레이션이 하는 일
--   1. attendance_punch PK 를 (enter_cd, punch_id) 로, 조회 인덱스 앞에 enter_cd 를 넣는다.
--   2. attendance_daily_summary PK 를 (enter_cd, emp_id, work_date) 로, 날짜 인덱스 앞에 enter_cd 를 넣는다.
--   3. flex_work_assign PK 를 (enter_cd, flex_id) 로, 사원·서면합의 인덱스 앞에 enter_cd 를 넣는다.
--   4. flex_work_plan PK 를 (enter_cd, flex_id, work_date) 로 바꾸고,
--      fk_flex_work_plan_assign 을 (enter_cd, flex_id) 복합 FK 로 바꿔 다른 법인 배정을 가리킬 수 없게 한다.
--   5. 네 테이블의 enter_cd DEFAULT 'C001' 을 지운다(8.1 2-3).
--   사원 FK(fk_punch_emp·fk_daily_summary_emp·fk_flex_work_assign_emp)는 V036, 근무유형·서면합의 FK
--   (fk_daily_summary_pattern·fk_flex_work_assign_agreement)는 V039 에서 이미 복합 FK 로 바꿨다.
--   IDENTITY 열(punch_id·flex_id)은 그대로 전체에서 유일하다.
--
-- 법령 수치 계산 없음.
-- JPA 엔티티: com.glassehr.attendance.AttendancePunch·FlexWorkAssign·FlexWorkPlan (ddl-auto: validate)
-- MyBatis: mapper/attendance/DailyWorkSummaryMapper.xml

-- 1) 자식 FK 먼저
ALTER TABLE flex_work_plan DROP CONSTRAINT fk_flex_work_plan_assign;

-- 2) 출퇴근 기록
ALTER TABLE attendance_punch MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE attendance_punch DROP CONSTRAINT pk_attendance_punch DROP INDEX;
ALTER TABLE attendance_punch ADD CONSTRAINT pk_attendance_punch PRIMARY KEY (enter_cd, punch_id);
DROP INDEX ix_punch_emp_date;
CREATE INDEX ix_punch_emp_date ON attendance_punch (enter_cd, emp_id, work_date, punched_at);

-- 3) 일별 근로시간 집계
ALTER TABLE attendance_daily_summary MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE attendance_daily_summary DROP CONSTRAINT pk_attendance_daily_summary DROP INDEX;
ALTER TABLE attendance_daily_summary ADD CONSTRAINT pk_attendance_daily_summary
    PRIMARY KEY (enter_cd, emp_id, work_date);
DROP INDEX ix_daily_summary_date;
CREATE INDEX ix_daily_summary_date ON attendance_daily_summary (enter_cd, work_date);

-- 4) 유연근무 배정
ALTER TABLE flex_work_assign MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE flex_work_assign DROP CONSTRAINT pk_flex_work_assign DROP INDEX;
ALTER TABLE flex_work_assign ADD CONSTRAINT pk_flex_work_assign PRIMARY KEY (enter_cd, flex_id);
DROP INDEX ix_flex_work_assign_emp;
DROP INDEX ix_flex_work_assign_agreement;
CREATE INDEX ix_flex_work_assign_emp ON flex_work_assign (enter_cd, emp_id, period_start);
CREATE INDEX ix_flex_work_assign_agreement ON flex_work_assign (enter_cd, agreement_id);

-- 5) 유연근무 계획
ALTER TABLE flex_work_plan MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE flex_work_plan DROP CONSTRAINT pk_flex_work_plan DROP INDEX;
ALTER TABLE flex_work_plan ADD CONSTRAINT pk_flex_work_plan PRIMARY KEY (enter_cd, flex_id, work_date);

-- 6) 복합 FK
ALTER TABLE flex_work_plan ADD CONSTRAINT fk_flex_work_plan_assign FOREIGN KEY (enter_cd, flex_id)
    REFERENCES flex_work_assign (enter_cd, flex_id);
