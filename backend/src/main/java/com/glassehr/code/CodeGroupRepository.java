package com.glassehr.code;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 공통코드 그룹. 법인별 테이블이므로 상속 조회 메서드는 쓰지 않는다(docs/tenant-design.md 5.2). */
public interface CodeGroupRepository extends JpaRepository<CodeGroup, CodeGroup.Key> {

    List<CodeGroup> findByEnterCdOrderBySortOrderAscGroupCodeAsc(String enterCd);

    List<CodeGroup> findByEnterCdAndGroupCodeIn(String enterCd, Collection<String> groupCodes);

    boolean existsByEnterCdAndGroupCode(String enterCd, String groupCode);

    Optional<CodeGroup> findByEnterCdAndGroupCode(String enterCd, String groupCode);
}
