package com.glassehr.code;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 휴가 종류 상세 설정. 법인별 테이블이므로 enterCd 를 받는 메서드만 쓴다(docs/tenant-design.md 5.2). */
public interface AttendanceLeaveTypeRepository extends JpaRepository<AttendanceLeaveType, AttendanceLeaveType.Key> {

    Optional<AttendanceLeaveType> findByEnterCdAndCode(String enterCd, String code);

    List<AttendanceLeaveType> findByEnterCd(String enterCd);
}
