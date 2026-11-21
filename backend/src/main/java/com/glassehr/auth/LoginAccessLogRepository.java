package com.glassehr.auth;

import org.springframework.data.repository.Repository;

/** 접속기록은 추가만 한다(수정·삭제 메서드를 두지 않는다). */
public interface LoginAccessLogRepository extends Repository<LoginAccessLog, Long> {

    LoginAccessLog save(LoginAccessLog log);
}
