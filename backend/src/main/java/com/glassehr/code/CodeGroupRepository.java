package com.glassehr.code;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeGroupRepository extends JpaRepository<CodeGroup, String> {

    List<CodeGroup> findAllByOrderBySortOrderAscGroupCodeAsc();
}
