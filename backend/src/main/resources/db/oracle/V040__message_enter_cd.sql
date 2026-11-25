-- 여러 법인 6단계: 사내 메시지 (#128). 설계: docs/tenant-design.md 3.2·8.1
--
-- 이 마이그레이션이 하는 일
--   1. msg_thread PK 를 (enter_cd, thread_id), msg_thread_member PK 를 (enter_cd, thread_id, emp_id),
--      msg_message PK 를 (enter_cd, message_id), msg_read PK 를 (enter_cd, message_id, emp_id) 로 바꾼다.
--      thread_id·message_id(IDENTITY)는 그대로 전체에서 유일하다.
--   2. 메시지 FK 를 법인이 들어간 복합 FK 로 바꿔 다른 법인 행을 가리킬 수 없게 한다:
--      fk_msg_member_thread (enter_cd, thread_id), fk_msg_message_member (enter_cd, thread_id, sender_emp_id),
--      fk_msg_read_message (enter_cd, message_id), fk_msg_read_member (enter_cd, thread_id, emp_id).
--      참여자 → 사원 FK fk_msg_member_emp 는 V036 에서 이미 (enter_cd, emp_id) 다.
--      그래서 대화방과 참여 사원의 법인이 같아야 하고, 다른 법인 사원과 대화방을 만들 수 없다.
--   3. 조회 인덱스 앞에 enter_cd 를 넣는다.
--   4. 네 테이블의 enter_cd DEFAULT 'C001' 을 지운다(8.1 2-3). 새 행이 법인을 빠뜨리면 오류가 난다.
--
-- 법령 수치 계산 없음.
-- 조회·저장: com.glassehr.message.MessageRepository (JdbcTemplate, 모든 쿼리에 enter_cd 조건)

-- 1) 자식 FK 먼저
ALTER TABLE msg_read DROP CONSTRAINT fk_msg_read_message;
ALTER TABLE msg_read DROP CONSTRAINT fk_msg_read_member;
ALTER TABLE msg_message DROP CONSTRAINT fk_msg_message_member;
ALTER TABLE msg_thread_member DROP CONSTRAINT fk_msg_member_thread;

-- 2) 대화방
ALTER TABLE msg_thread MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE msg_thread DROP CONSTRAINT pk_msg_thread DROP INDEX;
ALTER TABLE msg_thread ADD CONSTRAINT pk_msg_thread PRIMARY KEY (enter_cd, thread_id);

-- 3) 참여자
ALTER TABLE msg_thread_member MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE msg_thread_member DROP CONSTRAINT pk_msg_thread_member DROP INDEX;
ALTER TABLE msg_thread_member ADD CONSTRAINT pk_msg_thread_member PRIMARY KEY (enter_cd, thread_id, emp_id);
DROP INDEX ix_msg_member_emp;
CREATE INDEX ix_msg_member_emp ON msg_thread_member (enter_cd, emp_id);

-- 4) 메시지
ALTER TABLE msg_message MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE msg_message DROP CONSTRAINT pk_msg_message DROP INDEX;
ALTER TABLE msg_message ADD CONSTRAINT pk_msg_message PRIMARY KEY (enter_cd, message_id);
DROP INDEX ix_msg_message_thread;
CREATE INDEX ix_msg_message_thread ON msg_message (enter_cd, thread_id, message_id);

-- 5) 읽음
ALTER TABLE msg_read MODIFY (enter_cd DEFAULT NULL);
ALTER TABLE msg_read DROP CONSTRAINT pk_msg_read DROP INDEX;
ALTER TABLE msg_read ADD CONSTRAINT pk_msg_read PRIMARY KEY (enter_cd, message_id, emp_id);
DROP INDEX ix_msg_read_member;
CREATE INDEX ix_msg_read_member ON msg_read (enter_cd, thread_id, emp_id);

-- 6) 복합 FK
ALTER TABLE msg_thread_member ADD CONSTRAINT fk_msg_member_thread FOREIGN KEY (enter_cd, thread_id)
    REFERENCES msg_thread (enter_cd, thread_id);
ALTER TABLE msg_message ADD CONSTRAINT fk_msg_message_member FOREIGN KEY (enter_cd, thread_id, sender_emp_id)
    REFERENCES msg_thread_member (enter_cd, thread_id, emp_id);
ALTER TABLE msg_read ADD CONSTRAINT fk_msg_read_message FOREIGN KEY (enter_cd, message_id)
    REFERENCES msg_message (enter_cd, message_id);
ALTER TABLE msg_read ADD CONSTRAINT fk_msg_read_member FOREIGN KEY (enter_cd, thread_id, emp_id)
    REFERENCES msg_thread_member (enter_cd, thread_id, emp_id);
