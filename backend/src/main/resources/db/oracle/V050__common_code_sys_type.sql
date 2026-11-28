-- #141 고용형태·퇴직사유 처리방식(sys_type). 설계: docs/common-code-design.md '처리방식'
--
-- 로직은 코드값이 아니라 처리방식을 본다. 관리자가 같은 처리방식의 코드를 새로 만들어도 동작하게 하려는 것이다.
-- 저장 컬럼은 V041 이 만든 common_code.process_type 을 그대로 쓴다(근태코드 처리방식과 같은 개념이라 컬럼을 하나 더 두지 않는다).
-- 처리방식 목록은 com.glassehr.code.CodeSysTypes(고용형태·퇴직사유)·AttendanceCodeService.PROCESS_TYPES(근태코드)와 같아야 한다.
--
-- 1. 기존 코드는 같은 이름의 처리방식으로 채운다. 시드 코드값과 이름이 다른 것은 뜻이 같은 처리방식으로:
--    RECOMMENDED_RESIGNATION → RECOMMENDED, CONTRACT_EXPIRED → CONTRACT_END, TRANSFER_AFFILIATE → TRANSFER.
-- 2. 회사가 화면에서 추가한 코드는 지금까지의 동작(코드값이 FIXED_TERM·PART_TIME 이 아니면 일반,
--    체크리스트 유형이 없으면 공통 항목만)과 같게 EMPLOYMENT_TYPE → REGULAR, RETIREMENT_REASON → OTHER 로 채운다.
-- 3. 기존 코드값에 없는 처리방식(FIXED_TERM_PART_TIME)은 시드 코드를 만들지 않는다.

-- V041 CHECK는 근태코드 외 그룹의 process_type을 금지하므로 값을 채우기 전에 먼저 해제한다.
ALTER TABLE common_code DROP CONSTRAINT ck_common_code_process;

UPDATE common_code
   SET process_type = CASE code
           WHEN 'FIXED_TERM' THEN 'FIXED_TERM'
           WHEN 'PART_TIME' THEN 'PART_TIME'
           ELSE 'REGULAR' END
 WHERE group_code = 'EMPLOYMENT_TYPE';

UPDATE common_code
   SET process_type = CASE code
           WHEN 'VOLUNTARY' THEN 'VOLUNTARY'
           WHEN 'RECOMMENDED_RESIGNATION' THEN 'RECOMMENDED'
           WHEN 'RETIREMENT_AGE' THEN 'RETIREMENT_AGE'
           WHEN 'CONTRACT_EXPIRED' THEN 'CONTRACT_END'
           WHEN 'DISMISSAL' THEN 'DISMISSAL'
           WHEN 'TRANSFER_AFFILIATE' THEN 'TRANSFER'
           WHEN 'DEATH' THEN 'DEATH'
           ELSE 'OTHER' END
 WHERE group_code = 'RETIREMENT_REASON';

-- 처리방식이 있는 그룹은 처리방식이 꼭 있고, 다른 그룹은 처리방식을 두지 않는다.
ALTER TABLE common_code ADD CONSTRAINT ck_common_code_process CHECK (
    (group_code = 'LEAVE_TYPE' AND process_type IN ('ANNUAL', 'STATUTORY', 'COMPANY', 'UNPAID'))
    OR (group_code = 'ATTENDANCE_TYPE' AND process_type IN ('BUSINESS_TRIP', 'OUTSIDE_WORK', 'REMOTE', 'EDUCATION'))
    OR (group_code = 'EMPLOYMENT_TYPE' AND process_type IN ('REGULAR', 'FIXED_TERM', 'PART_TIME', 'FIXED_TERM_PART_TIME'))
    OR (group_code = 'RETIREMENT_REASON' AND process_type IN ('VOLUNTARY', 'RECOMMENDED', 'RETIREMENT_AGE',
        'CONTRACT_END', 'DISMISSAL', 'TRANSFER', 'DEATH', 'OTHER'))
    OR (group_code NOT IN ('LEAVE_TYPE', 'ATTENDANCE_TYPE', 'EMPLOYMENT_TYPE', 'RETIREMENT_REASON')
        AND process_type IS NULL));

COMMENT ON COLUMN common_code.process_type IS '처리방식(sys_type). 로직은 코드값이 아니라 이 값을 본다. 근태코드·고용형태·퇴직사유 그룹만 값이 있다';

-- 퇴직 등록 당시 처리방식도 함께 보존한다. CHECK에는 다른 테이블을 조회할 수 없으므로 스냅샷 컬럼을 쓴다.
ALTER TABLE retirement_case ADD (retire_reason_type VARCHAR2(30 CHAR));
UPDATE retirement_case r
   SET retire_reason_type = (
       SELECT c.process_type
         FROM common_code c
        WHERE c.enter_cd = r.enter_cd
          AND c.group_code = 'RETIREMENT_REASON'
          AND c.code = r.retire_reason_code);
ALTER TABLE retirement_case MODIFY (retire_reason_type NOT NULL);

ALTER TABLE retirement_case DROP CONSTRAINT ck_retirement_dismissal;
ALTER TABLE retirement_case DROP CONSTRAINT ck_retirement_notice_only_dismissal;
ALTER TABLE retirement_case DROP CONSTRAINT ck_retirement_notice_check_type;
ALTER TABLE retirement_case ADD CONSTRAINT ck_retirement_reason_type CHECK (
    retire_reason_type IN ('VOLUNTARY', 'RECOMMENDED', 'RETIREMENT_AGE', 'CONTRACT_END',
        'DISMISSAL', 'TRANSFER', 'DEATH', 'OTHER'));
ALTER TABLE retirement_case ADD CONSTRAINT ck_retirement_dismissal CHECK (
    retire_reason_type <> 'DISMISSAL'
    OR (dismissal_notice_date IS NOT NULL AND written_notice_date IS NOT NULL));
ALTER TABLE retirement_case ADD CONSTRAINT ck_retirement_notice_only_dismissal CHECK (
    retire_reason_type = 'DISMISSAL'
    OR (dismissal_notice_date IS NULL AND written_notice_date IS NULL AND notice_exempt_code IS NULL));
ALTER TABLE retirement_case ADD CONSTRAINT ck_retirement_notice_check_type CHECK (
    (retire_reason_type = 'DISMISSAL' AND notice_check <> 'NOT_APPLICABLE')
    OR (retire_reason_type <> 'DISMISSAL' AND notice_check = 'NOT_APPLICABLE'));

COMMENT ON COLUMN retirement_case.retire_reason_type IS '등록 당시 퇴직사유 처리방식. 해고 예고·서면통지 CHECK 기준';
