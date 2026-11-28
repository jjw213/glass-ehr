-- #158 근로자 유형(통상·단시간·초단시간).
-- 1) 초단시간근로자 기준 legal_rate 항목코드 등록(값은 seed/legal_rate_part_time.sql, Codex 법령 데이터 PR #163).
--    코드 목록은 com.glassehr.legalrate.LegalRateItem 과 같아야 한다(LegalRateItemRegistrationTest).
-- 2) 근무유형의 적용 근로자 유형. 저장할 때 1주 소정근로시간으로 계산해 넣는다(com.glassehr.attendance.WorkerTypeRules).
--    이 마이그레이션 전에 만든 근무유형은 NULL(미지정)이고, 시각·휴게·요일을 다시 저장하면 계산된다.

-- 근로기준법 제18조 제3항: 4주 동안(4주 미만이면 그 기간) 평균 1주 소정근로시간이 15시간 미만이면 제55조·제60조 미적용
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('SHORT_TIME_WORKER_WEEKLY_HOURS_THRESHOLD', '초단시간근로자 기준 1주 소정근로시간(미만)', 'HOURS', '근로기준법 제18조 제3항');
INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES
    ('SHORT_TIME_WORKER_AVERAGE_WEEKS', '초단시간근로자 판정 평균 주 수', 'WEEKS', '근로기준법 제18조 제3항');

ALTER TABLE work_pattern ADD (worker_type_code VARCHAR2(20 CHAR));
ALTER TABLE work_pattern ADD CONSTRAINT ck_work_pattern_worker_type
    CHECK (worker_type_code IS NULL OR worker_type_code IN ('FULL_TIME', 'PART_TIME', 'ULTRA_SHORT'));
COMMENT ON COLUMN work_pattern.worker_type_code IS
    '적용 근로자 유형(FULL_TIME 통상, PART_TIME 단시간, ULTRA_SHORT 초단시간). 근로기준법 제2조 제1항 제9호, 제18조';
