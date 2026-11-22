package com.glassehr.legalrate;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** SQL: resources/mapper/legalrate/LegalRateMapper.xml */
@Mapper
public interface LegalRateMapper {

    /**
     * 조회일에 유효한 행을 모두 돌려준다. 정상 데이터라면 0건 또는 1건이며,
     * 2건 이상은 기간 겹침 데이터 오류다. 중복을 감추지 않도록 FETCH FIRST를 쓰지 않는다.
     */
    List<LegalRateRow> findEffective(
            @Param("itemCode") String itemCode,
            @Param("asOfDate") LocalDate asOfDate);

    /** 항목의 가장 이른 적용시작일. 행이 없으면 null. 법 적용 전 날짜와 기준값 누락을 구분할 때 쓴다. */
    LocalDate findFirstEffectiveFrom(@Param("itemCode") String itemCode);
}
