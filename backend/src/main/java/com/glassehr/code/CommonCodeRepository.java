package com.glassehr.code;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommonCodeRepository extends JpaRepository<CommonCode, CommonCodeId> {

    List<CommonCode> findByGroupCodeOrderBySortOrderAscCodeAsc(String groupCode);

    /** active 는 파라미터로 넘겨 {@link YnConverter}를 거치게 한다('Y'/'N'). */
    List<CommonCode> findByGroupCodeInAndActiveOrderByGroupCodeAscSortOrderAscCodeAsc(
            Collection<String> groupCodes, Boolean active);
}
