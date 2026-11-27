-- 실제 근로시간은 법정 한도 판정용으로 보존하고, 회사 정책으로 단위 보정한 인정 시간은 별도로 저장한다.
ALTER TABLE attendance_daily_summary ADD (
    recognized_minutes NUMBER(6) DEFAULT 0 NOT NULL
);

UPDATE attendance_daily_summary
   SET recognized_minutes = regular_minutes + extra_minutes + overtime_minutes
                          + holiday_minutes + holiday_excess_minutes;

ALTER TABLE attendance_daily_summary DROP CONSTRAINT ck_daily_summary_sum;
ALTER TABLE attendance_daily_summary ADD CONSTRAINT ck_daily_summary_sum
    CHECK (recognized_minutes = regular_minutes + extra_minutes + overtime_minutes
        + holiday_minutes + holiday_excess_minutes);

ALTER TABLE attendance_daily_summary DROP CONSTRAINT ck_daily_summary_nonneg;
ALTER TABLE attendance_daily_summary ADD CONSTRAINT ck_daily_summary_nonneg
    CHECK (contract_minutes >= 0 AND break_minutes >= 0 AND worked_minutes >= 0 AND recognized_minutes >= 0
        AND regular_minutes >= 0 AND extra_minutes >= 0 AND overtime_minutes >= 0 AND night_minutes >= 0
        AND holiday_minutes >= 0 AND holiday_excess_minutes >= 0);
