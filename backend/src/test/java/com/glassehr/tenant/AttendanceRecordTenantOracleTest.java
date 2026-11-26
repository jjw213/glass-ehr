package com.glassehr.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.attendance.AttendanceException;
import com.glassehr.attendance.AttendancePunch;
import com.glassehr.attendance.FlexibleWorkCalculator.WorkSystem;
import com.glassehr.attendance.FlexibleWorkService;
import com.glassehr.attendance.FlexibleWorkService.AssignRequest;
import com.glassehr.attendance.FlexibleWorkService.AssignView;
import com.glassehr.attendance.PunchDtos.PunchRequest;
import com.glassehr.attendance.PunchService;
import com.glassehr.attendance.WorkHoursService;
import com.glassehr.auth.AuthUser;
import com.glassehr.auth.Permission;
import com.glassehr.dashboard.DashboardPunchQueries;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * V043(#126): 출퇴근 기록·일별 근로시간 집계·유연근무 배정·계획의 법인 분리를 Oracle 실DB에서 검증한다.
 * "법인 A(C001) 세션으로 법인 B(ZZIT3) 데이터 조회·수정 → 404/거부"와 복합 PK·FK 를 본다(docs/tenant-design.md 3.2).
 * 실행 조건: {@code ORACLE_IT=true}. 각 테스트는 롤백한다. 유연근무는 1900년대 날짜, ZZIT 코드만 쓴다.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class AttendanceRecordTenantOracleTest {

    private static final String A = "C001";
    private static final String B = "ZZIT3";
    private static final LocalDate D0 = LocalDate.of(1904, 1, 4);

    @Autowired
    private PunchService punches;

    @Autowired
    private WorkHoursService workHours;

    @Autowired
    private FlexibleWorkService flex;

    @Autowired
    private DashboardPunchQueries dashboardPunches;

    @Autowired
    private JdbcTemplate jdbc;

    private long aEmp;
    private long bEmp;
    private long bFlex;
    private LocalDate today;

    @BeforeEach
    void 법인_B와_B의_출퇴근_유연근무를_넣는다() {
        jdbc.update("INSERT INTO enter_company (enter_cd, enter_name, biz_reg_no, use_yn, sort_order)"
                + " VALUES (?, 'IT 법인 다(예시)', '0000000093', 'Y', 0)", B);
        jdbc.update("INSERT INTO biz_site (enter_cd, site_code, site_name) VALUES (?, 'S01', 'B 본사(예시)')", B);
        aEmp = insertEmployee(A, "S01-1903-931");
        bEmp = insertEmployee(B, "S01-1903-932");

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul")).truncatedTo(ChronoUnit.SECONDS);
        today = now.toLocalDate();
        jdbc.update("INSERT INTO attendance_punch (enter_cd, emp_id, punch_type, punched_at, work_date, source,"
                + " created_by) VALUES (?, ?, 'IN', ?, ?, 'WEB', 'it')", B, bEmp, Timestamp.valueOf(now),
                java.sql.Date.valueOf(today));

        // 1주 단위 탄력적 근로시간제(2주 이내)는 서면합의가 없어도 된다(ck_flex_work_assign_agreement).
        jdbc.update("INSERT INTO flex_work_assign (enter_cd, emp_id, work_system, period_start, period_end, created_by)"
                + " VALUES (?, ?, 'FLEXIBLE_SHORT', ?, ?, 'it')", B, bEmp, java.sql.Date.valueOf(D0),
                java.sql.Date.valueOf(D0.plusDays(6)));
        bFlex = jdbc.queryForObject("SELECT flex_id FROM flex_work_assign WHERE enter_cd = ? AND emp_id = ?",
                Long.class, B, bEmp);
        jdbc.update("INSERT INTO flex_work_plan (enter_cd, flex_id, work_date, plan_minutes, updated_by)"
                + " VALUES (?, ?, ?, 0, 'it')", B, bFlex, java.sql.Date.valueOf(D0));
    }

    @Test
    void 법인_A_세션은_법인_B_사원으로_출퇴근을_기록하거나_오늘_기록을_보지_못한다() {
        assertStatus(() -> punches.punch(A, bEmp, "it-a", new PunchRequest(AttendancePunch.Type.IN, null)),
                HttpStatus.NOT_FOUND);
        assertThat(punches.today(A, bEmp).punches()).isEmpty();
        assertThat(punches.today(B, bEmp).punches()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendance_punch WHERE emp_id = ?", Integer.class, bEmp))
                .isEqualTo(1);

        // 홈 대시보드 오늘 출근 인원도 세션 법인 기록만 센다.
        assertThat(dashboardPunches.findEmpIdsByWorkDateAndType(A, today, AttendancePunch.Type.IN))
                .doesNotContain(bEmp);
        assertThat(dashboardPunches.findEmpIdsByWorkDateAndType(B, today, AttendancePunch.Type.IN))
                .containsExactly(bEmp);
    }

    @Test
    void 법인_A_세션은_법인_B_사원의_근로시간_집계를_조회하거나_재계산하지_못한다() {
        assertStatus(() -> workHours.find(A, bEmp, D0, D0), HttpStatus.NOT_FOUND);
        assertStatus(() -> workHours.recalculate(A, bEmp, D0, D0, "it-a"), HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendance_daily_summary WHERE emp_id = ?",
                Integer.class, bEmp)).isZero();
    }

    @Test
    void 법인_A_세션은_법인_B_유연근무_배정을_보지_못하고_상세_계획_정산_배정은_404() {
        AuthUser hrA = hr(A);
        assertThat(flex.list(A, null)).extracting(AssignView::flexId).doesNotContain(bFlex);
        assertThat(flex.list(A, bEmp)).isEmpty();
        assertThat(flex.list(B, bEmp)).extracting(AssignView::flexId).containsExactly(bFlex);

        assertStatus(() -> flex.detail(bFlex, hrA), HttpStatus.NOT_FOUND);
        assertStatus(() -> flex.savePlan(bFlex, List.of(), hrA), HttpStatus.NOT_FOUND);
        assertStatus(() -> flex.settle(bFlex, hrA), HttpStatus.NOT_FOUND);
        assertStatus(() -> flex.assign(A, new AssignRequest(bEmp, WorkSystem.FLEXIBLE_SHORT, D0.plusDays(7),
                D0.plusDays(13), null, null, null, null, "섞임"), "it-a"), HttpStatus.NOT_FOUND);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flex_work_plan WHERE flex_id = ?", Integer.class, bFlex))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flex_work_assign WHERE emp_id = ?", Integer.class, bEmp))
                .isEqualTo(1);
    }

    @Test
    void DB_복합_키는_다른_법인의_사원_배정을_참조하지_못하게_하고_법인을_빠뜨리면_거부한다() {
        // 법인 A 계획이 법인 B 배정을 가리킬 수 없다(fk_flex_work_plan_assign).
        assertThatThrownBy(() -> jdbc.update("INSERT INTO flex_work_plan (enter_cd, flex_id, work_date, plan_minutes,"
                + " updated_by) VALUES (?, ?, ?, 0, 'it')", A, bFlex, java.sql.Date.valueOf(D0.plusDays(1))))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 법인 A 출퇴근·배정이 법인 B 사원을 가리킬 수 없다(V036 사원 복합 FK).
        assertThatThrownBy(() -> jdbc.update("INSERT INTO attendance_punch (enter_cd, emp_id, punch_type, punched_at,"
                + " work_date, source, created_by) VALUES (?, ?, 'IN', TIMESTAMP '1904-01-04 09:00:00',"
                + " DATE '1904-01-04', 'WEB', 'it')", A, bEmp)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO flex_work_assign (enter_cd, emp_id, work_system,"
                + " period_start, period_end, created_by) VALUES (?, ?, 'FLEXIBLE_SHORT', DATE '1904-02-01',"
                + " DATE '1904-02-07', 'it')", A, bEmp)).isInstanceOf(DataIntegrityViolationException.class);
        // DEFAULT 를 지웠으므로 법인을 빠뜨린 행은 거부한다.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO attendance_punch (emp_id, punch_type, punched_at,"
                + " work_date, source, created_by) VALUES (?, 'IN', TIMESTAMP '1904-01-04 09:00:00',"
                + " DATE '1904-01-04', 'WEB', 'it')", aEmp)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO flex_work_assign (emp_id, work_system, period_start,"
                + " period_end, created_by) VALUES (?, 'FLEXIBLE_SHORT', DATE '1904-02-01', DATE '1904-02-07', 'it')",
                aEmp)).isInstanceOf(DataIntegrityViolationException.class);
        // PK 에 법인이 들어갔다.
        for (String pk : List.of("PK_ATTENDANCE_PUNCH", "PK_ATTENDANCE_DAILY_SUMMARY", "PK_FLEX_WORK_ASSIGN",
                "PK_FLEX_WORK_PLAN")) {
            assertThat(jdbc.queryForObject("SELECT column_name FROM user_cons_columns WHERE constraint_name = ?"
                    + " AND position = 1", String.class, pk)).isEqualTo("ENTER_CD");
        }
    }

    private long insertEmployee(String enterCd, String empNo) {
        jdbc.update("INSERT INTO employee (enter_cd, emp_no, site_code, emp_name, gender, birth_date, address,"
                + " job_description, hire_date, employment_type_code) VALUES (?, ?, 'S01', '예시사원근태기록', 'M',"
                + " DATE '1880-01-01', '가상시 예시구 샘플로 0', '시험', DATE '1903-01-01', 'REGULAR')", enterCd, empNo);
        return jdbc.queryForObject("SELECT emp_id FROM employee WHERE enter_cd = ? AND emp_no = ?", Long.class,
                enterCd, empNo);
    }

    private static AuthUser hr(String enterCd) {
        return new AuthUser(enterCd, enterCd, "it-hr", null, Set.of("HR"),
                Set.of(Permission.EMP_READ_ALL, Permission.EMP_WRITE), Set.of(), "127.0.0.1");
    }

    private static void assertStatus(ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call).isInstanceOfSatisfying(AttendanceException.class,
                e -> assertThat(e.status()).isEqualTo(status));
    }
}
