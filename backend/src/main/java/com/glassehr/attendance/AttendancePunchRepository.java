package com.glassehr.attendance;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 출퇴근 원시 기록 저장소. 원시 기록은 추가만 하므로 {@code JpaRepository} 대신 {@link Repository}를 써서
 * 저장·조회 메서드만 연다(삭제 메서드 없음, 엔티티는 setter 없음·모든 칼럼 updatable=false).
 */
public interface AttendancePunchRepository extends Repository<AttendancePunch, Long> {

    AttendancePunch save(AttendancePunch punch);

    List<AttendancePunch> findByEmpIdAndWorkDateOrderByPunchedAtAscPunchIdAsc(Long empId, LocalDate workDate);

    /** 그날 해당 종류로 한 번이라도 기록한 사원 ID(중복 제거). 홈 대시보드 오늘 출근 인원(#77). */
    @Query("select distinct p.empId from AttendancePunch p where p.workDate = :workDate and p.type = :type")
    List<Long> findEmpIdsByWorkDateAndType(@Param("workDate") LocalDate workDate,
            @Param("type") AttendancePunch.Type type);
}
