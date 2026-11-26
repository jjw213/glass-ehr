package com.glassehr.attendance;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 일별 근로시간 집계(V023, 법인 키 V043). SQL: resources/mapper/attendance/DailyWorkSummaryMapper.xml
 * 모든 SQL 에 법인(enterCd) 조건을 건다(#126).
 */
@Mapper
public interface DailyWorkSummaryMapper {

    /** [from, to] 기간(양 끝 포함)의 한 사원 집계 행을 지운다. 재계산 전에 쓴다. */
    int deleteRange(@Param("enterCd") String enterCd, @Param("empId") long empId, @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    int insert(@Param("enterCd") String enterCd, @Param("row") DailyWorkSummaryRow row);

    /** [from, to] 기간(양 끝 포함)의 한 사원 집계 행, 날짜 순. */
    List<DailyWorkSummaryRow> findRange(@Param("enterCd") String enterCd, @Param("empId") long empId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 집계 원자료: [from, to] 근무일(양 끝 포함)의 출퇴근 기록(attendance_punch, 읽기만), 시각·ID 순. */
    List<PunchRecord> findPunches(@Param("enterCd") String enterCd, @Param("empId") long empId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 출퇴근 기록 중 집계에 쓰는 값. type 은 {@link AttendancePunch.Type} 이름(IN/OUT). */
    record PunchRecord(String type, java.time.LocalDateTime punchedAt, LocalDate workDate) {
        boolean isIn() {
            return AttendancePunch.Type.IN.name().equals(type);
        }

        boolean isOut() {
            return AttendancePunch.Type.OUT.name().equals(type);
        }
    }
}
