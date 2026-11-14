package com.glassehr.attendance;

import com.glassehr.attendance.PunchDtos.PunchRequest;
import com.glassehr.attendance.PunchDtos.PunchView;
import com.glassehr.attendance.PunchDtos.TodayView;
import com.glassehr.employee.Employee;
import com.glassehr.employee.EmployeeRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 출퇴근 기록(#28, docs/attendance-punch-design.md).
 *
 * <ul>
 *   <li>근로기준법 제48조(임금대장)·시행령 제27조: 임금대장에 근로시간수 등을 적어야 하므로, 그 근거가 되는 출퇴근
 *       원시 기록은 추가만 하고 고치거나 지우지 않는다(정정은 별도 이슈의 정정 행).</li>
 *   <li>기록 시각은 서버 시각(Asia/Seoul)이다. 단말 시각은 참고로만 저장한다(단말 시계 조작 방지, #22 결정).</li>
 *   <li>중복 출근·출근 없는 퇴근은 막지 않고 저장하며 판정을 남긴다({@link PunchRules}).</li>
 *   <li>본인 기록만 남긴다. 사원은 서버가 확인한 로그인 사용자의 사원 ID로 정한다(요청 본문으로 받지 않음).</li>
 * </ul>
 * 위치 확인(GPS)·동의 이력은 설계 §7 미결 사항이 정해진 뒤 별도 이슈에서 붙인다.
 */
@Service
public class PunchService {

    private final AttendancePunchRepository punches;
    private final EmployeeRepository employees;
    private final Clock clock;

    @Autowired
    public PunchService(AttendancePunchRepository punches, EmployeeRepository employees) {
        this(punches, employees, Clock.system(PunchDtos.SEOUL));
    }

    PunchService(AttendancePunchRepository punches, EmployeeRepository employees, Clock clock) {
        this.punches = punches;
        this.employees = employees;
        this.clock = clock;
    }

    /** 서버 시각(Asia/Seoul). DB TIMESTAMP(6)에 맞춰 마이크로초까지. */
    LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(PunchDtos.SEOUL)).truncatedTo(ChronoUnit.MICROS);
    }

    /** 출근·퇴근 한 건 추가. 같은 사원의 기록은 사원 행 잠금으로 줄 세워 판정이 엇갈리지 않게 한다. */
    @Transactional
    public PunchView punch(Long empId, String loginId, PunchRequest req) {
        long id = requireEmployee(empId);
        Employee e = employees.findForUpdate(id)
                .orElseThrow(() -> AttendanceException.notFound("사원을 찾을 수 없어요: " + id));
        // 잠금을 잡은 뒤의 서버 시각으로 기록한다(먼저 온 요청보다 이른 시각이 저장되지 않게).
        LocalDateTime now = now();
        LocalDate today = now.toLocalDate();
        requireEmployed(e, today);
        List<AttendancePunch> sameDay = punches.findByEmpIdAndWorkDateOrderByPunchedAtAscPunchIdAsc(id, today);
        AttendancePunch.Check check = PunchRules.check(sameDay, req.type());
        LocalDateTime client = req.clientTime() == null ? null
                : req.clientTime().atZoneSameInstant(PunchDtos.SEOUL).toLocalDateTime().truncatedTo(ChronoUnit.MICROS);
        AttendancePunch saved = punches.save(
                new AttendancePunch(id, req.type(), now, client, check, AttendancePunch.Source.WEB, loginId));
        return PunchView.of(saved);
    }

    /** 오늘(서버 날짜) 내 기록과 요약. */
    @Transactional(readOnly = true)
    public TodayView today(Long empId) {
        long id = requireEmployee(empId);
        LocalDateTime now = now();
        List<AttendancePunch> list = punches.findByEmpIdAndWorkDateOrderByPunchedAtAscPunchIdAsc(id, now.toLocalDate());
        PunchRules.Summary s = PunchRules.summarize(list, now);
        return new TodayView(PunchDtos.seoul(now), now.toLocalDate(), PunchDtos.seoul(s.firstInAt()),
                PunchDtos.seoul(s.lastOutAt()), s.working(), s.elapsedMinutes(),
                list.stream().map(PunchView::of).toList());
    }

    private static long requireEmployee(Long empId) {
        if (empId == null) {
            throw new AttendanceException(HttpStatus.FORBIDDEN,
                    "사원과 연결된 계정만 출퇴근을 기록할 수 있어요", List.of());
        }
        return empId;
    }

    /** 입사일 전이나 퇴직일 다음 날부터는 기록하지 않는다(퇴직일 당일까지 재직, 사원 테이블 규칙과 같음). */
    private static void requireEmployed(Employee e, LocalDate today) {
        if (e.getHireDate() != null && today.isBefore(e.getHireDate())) {
            throw AttendanceException.conflict("입사일(" + e.getHireDate() + ") 전에는 출퇴근을 기록할 수 없어요");
        }
        if (e.getRetireDate() != null && today.isAfter(e.getRetireDate())) {
            throw AttendanceException.conflict("퇴직 처리된 사원은 출퇴근을 기록할 수 없어요");
        }
    }
}
