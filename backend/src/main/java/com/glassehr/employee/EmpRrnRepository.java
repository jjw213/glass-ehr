package com.glassehr.employee;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 주민등록번호 암호문. 법인별 테이블이므로 상속 메서드(findById 등)는 쓰지 않고 첫 인자로 enterCd 를 받는 메서드만 쓴다
 * (docs/tenant-design.md 5.2, #123). HMAC 중복 검사도 법인 안에서만 한다(같은 사람이 두 법인에 재직할 수 있음, 3.2 ※).
 */
public interface EmpRrnRepository extends JpaRepository<EmpRrn, Long> {

    Optional<EmpRrn> findByEnterCdAndEmpId(String enterCd, Long empId);

    boolean existsByEnterCdAndRrnHash(String enterCd, byte[] rrnHash);

    boolean existsByEnterCdAndRrnHashAndEmpIdNot(String enterCd, byte[] rrnHash, Long empId);
}
