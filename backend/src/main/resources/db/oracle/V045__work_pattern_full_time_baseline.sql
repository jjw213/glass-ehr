-- #158 통상근로자 비교 기준을 계산된 근로자 유형과 분리한다.
-- 기존 FULL_TIME 행은 기존 동작을 보존하도록 비교 기준으로 이관하고, 이후에는 API 입력으로 명시 지정한다.
ALTER TABLE work_pattern ADD (full_time_baseline_yn VARCHAR2(1 CHAR) DEFAULT 'N' NOT NULL);
ALTER TABLE work_pattern ADD CONSTRAINT ck_work_pattern_full_time_baseline
    CHECK (full_time_baseline_yn IN ('Y', 'N'));
UPDATE work_pattern SET full_time_baseline_yn = 'Y' WHERE worker_type_code = 'FULL_TIME';
COMMENT ON COLUMN work_pattern.full_time_baseline_yn IS
    '통상근로자 비교 기준 지정 여부. worker_type_code 판정 결과와 독립된 관리자 입력값';
