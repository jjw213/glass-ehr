package com.glassehr.attendance;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * 출퇴근 원시 기록 저장소. 원시 기록은 추가만 하므로 {@code JpaRepository} 대신 {@link Repository}를 써서
 * 저장·조회 메서드만 연다(삭제 메서드 없음, 엔티티는 setter 없음·모든 칼럼 updatable=false).
 */
public interface AttendancePunchRepository extends Repository<AttendancePunch, Long> {

    AttendancePunch save(AttendancePunch punch);

    List<AttendancePunch> findByEmpIdAndWorkDateOrderByPunchedAtAscPunchIdAsc(Long empId, LocalDate workDate);
}
