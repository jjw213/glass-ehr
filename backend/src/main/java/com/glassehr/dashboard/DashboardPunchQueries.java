package com.glassehr.dashboard;

import com.glassehr.attendance.AttendancePunch;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 홈 대시보드(#77)용 출퇴근 원시 기록 읽기 전용 조회. 저장·삭제 메서드는 두지 않는다
 * (원시 기록 저장소 {@code AttendancePunchRepository}는 추가·본인 조회만 열어 둔다).
 */
public interface DashboardPunchQueries extends Repository<AttendancePunch, Long> {

    /** 그 법인에서 그날 해당 종류로 한 번이라도 기록한 사원 ID(중복 제거). 법인 조건 필수(#126). */
    @Query("select distinct p.empId from AttendancePunch p"
            + " where p.enterCd = :enterCd and p.workDate = :workDate and p.type = :type")
    List<Long> findEmpIdsByWorkDateAndType(@Param("enterCd") String enterCd, @Param("workDate") LocalDate workDate,
            @Param("type") AttendancePunch.Type type);
}
