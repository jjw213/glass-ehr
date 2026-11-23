package com.glassehr.auth;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 로그인 사용자 조회·로그인 상태(실패 횟수·잠금) 저장. 사용자 등록·역할 부여는 SQL·데모 계정 시드로만 한다(관리 화면은 후속 이슈).
 * 모든 조회는 첫 인자로 법인(enter_cd)을 받는다(docs/tenant-design.md 5.2).
 */
public interface AppUserRepository extends Repository<AppUser, AppUser.Key> {

    @Query("select u from AppUser u where u.enterCd = :enterCd and u.loginId = :loginId")
    Optional<AppUser> find(@Param("enterCd") String enterCd, @Param("loginId") String loginId);

    /**
     * 그 역할을 부여받은 사용 중 계정의 사원 ID(결재자 찾기, #30). 부서장·일반 사원은 자동 역할이라 여기 없다.
     * 결재 모듈이 아직 법인을 받지 않아(결재 테이블 법인 이슈) 모든 법인에서 찾는다. emp_id 는 전체에서 유일하고,
     * 다법인 배포 잠금(glassehr.tenant.multi-company-enabled=false) 동안은 C001 계정만 로그인할 수 있다.
     */
    default List<Long> findActiveEmpIdsWithRole(String roleCode) {
        return findEmpIdsWithRole(roleCode, true);
    }

    /** active_yn 은 변환기(YnConverter) 컬럼이라 리터럴 대신 매개변수로 비교한다. */
    @Query("select distinct u.empId from AppUser u join u.roles r "
            + "where r = :role and u.active = :active and u.empId is not null")
    List<Long> findEmpIdsWithRole(@Param("role") String roleCode, @Param("active") boolean active);

    /** 로그인 실패 횟수와 잠금 판정을 같은 사용자(법인·아이디)에 대해 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.enterCd = :enterCd and u.loginId = :loginId")
    Optional<AppUser> findForUpdate(@Param("enterCd") String enterCd, @Param("loginId") String loginId);

    AppUser save(AppUser user);
}
