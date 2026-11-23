package com.glassehr.code;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 공통코드. 법인별 테이블이므로 상속 메서드(findById·existsById 등)는 쓰지 않고
 * 첫 인자로 enterCd 를 받는 메서드만 쓴다(docs/tenant-design.md 5.2).
 */
public interface CommonCodeRepository extends JpaRepository<CommonCode, CommonCodeId> {

    Optional<CommonCode> findByEnterCdAndGroupCodeAndCode(String enterCd, String groupCode, String code);

    boolean existsByEnterCdAndGroupCodeAndCode(String enterCd, String groupCode, String code);

    List<CommonCode> findByEnterCdAndGroupCodeOrderBySortOrderAscCodeAsc(String enterCd, String groupCode);

    /** active 는 파라미터로 넘겨 {@link YnConverter}를 거치게 한다('Y'/'N'). */
    List<CommonCode> findByEnterCdAndGroupCodeInAndActiveOrderByGroupCodeAscSortOrderAscCodeAsc(
            String enterCd, Collection<String> groupCodes, Boolean active);
}
