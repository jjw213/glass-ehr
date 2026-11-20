package com.glassehr.auth;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.Repository;

/** 로그인 사용자 조회·로그인 상태(실패 횟수·잠금) 저장. 사용자 등록·역할 부여는 SQL·데모 계정 시드로만 한다(관리 화면은 후속 이슈). */
public interface AppUserRepository extends Repository<AppUser, String> {

    Optional<AppUser> findById(String loginId);

    /** 로그인 실패 횟수와 잠금 판정을 같은 사용자에 대해 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.loginId = :loginId")
    Optional<AppUser> findByIdForUpdate(@Param("loginId") String loginId);

    AppUser save(AppUser user);
}
