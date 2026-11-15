-- 인사발령 2: 사원 정보 반영·미래 발령·취소 발령 (#59, #16을 나눈 2번). 설계: docs/hr-appointment-design.md 6장
--
-- 근거 (2026-11-14 law.go.kr 자동 조회가 되지 않아 원문 미확인. 사람이 원문으로 다시 확인해야 한다)
--   - 근로기준법 제23조 제1항: 정당한 이유 없는 해고·휴직·정직·전직 등 금지 → 취소 발령도 사유를 반드시 적는다(hr_appointment.reason).
--   - 남녀고용평등과 일·가정 양립 지원에 관한 법률 제19조: 육아휴직은 휴직(LEAVE) 발령으로 등록되고, 휴직 기간 조회가 이를 돌려준다.
--
-- 정규식에 중괄호를 쓰지 않는다(Oracle JDBC SQL92 escape 문제, V006 참고).
-- JPA 엔티티: com.glassehr.employee.Employee(emp_status), com.glassehr.appointment.HrAppointmentCancel (ddl-auto: validate)

-- 사원 현재 재직상태. 소속·직급·직책과 함께 확정된 인사발령으로만 바뀐다(AppointmentService.syncEmployee).
-- 값은 com.glassehr.appointment.EmpStatus 와 같다(AppointmentApplySchemaScriptTest).
ALTER TABLE employee ADD (
    emp_status              VARCHAR2(10 CHAR)   DEFAULT 'ACTIVE' NOT NULL
);
ALTER TABLE employee ADD CONSTRAINT ck_employee_emp_status CHECK (emp_status IN ('ACTIVE', 'ON_LEAVE', 'RETIRED'));

-- 기존 사원: 발령이 없으면 퇴직일 다음 날부터 퇴직(퇴직일 당일까지는 재직, V014 이전 값 규칙과 같음).
-- 정확한 값은 매일 도는 반영 작업(AppointmentDailySync)이 서울 날짜 기준으로 다시 맞춘다.
UPDATE employee SET emp_status = 'RETIRED' WHERE retire_date IS NOT NULL AND retire_date < TRUNC(SYSDATE);

-- 취소 발령과 원 발령의 연결. 취소 발령 자체는 hr_appointment 한 행(유형은 원 발령과 같고, 이전·이후 값은 원 발령의 반대)이다.
-- 원 발령 하나에 취소 발령은 하나만 둔다(미확정 취소 발령 포함). 취소 발령이 확정되면 원 발령과 취소 발령은 이력 계산에서 빠진다.
CREATE TABLE hr_appointment_cancel (
    -- 취소 발령
    cancel_appt_id          NUMBER(10)          NOT NULL,
    -- 되돌리는 원 발령(확정된 발령)
    original_appt_id        NUMBER(10)          NOT NULL,
    created_at              TIMESTAMP(6)        DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT pk_hr_appointment_cancel PRIMARY KEY (cancel_appt_id),
    CONSTRAINT uq_hr_appt_cancel_original UNIQUE (original_appt_id),
    CONSTRAINT fk_hr_appt_cancel_self FOREIGN KEY (cancel_appt_id) REFERENCES hr_appointment (appt_id),
    CONSTRAINT fk_hr_appt_cancel_original FOREIGN KEY (original_appt_id) REFERENCES hr_appointment (appt_id),
    CONSTRAINT ck_hr_appt_cancel_not_self CHECK (cancel_appt_id <> original_appt_id)
);
