package com.glassehr.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.attendance.AgreementType;
import com.glassehr.attendance.AttendanceException;
import com.glassehr.attendance.HolidayDtos.CompanyHolidayRequest;
import com.glassehr.attendance.HolidayDtos.HolidayView;
import com.glassehr.attendance.HolidayDtos.SubstitutionRequest;
import com.glassehr.attendance.HolidayService;
import com.glassehr.attendance.LaborAgreement;
import com.glassehr.attendance.LaborAgreementDtos.AgreementRequest;
import com.glassehr.attendance.LaborAgreementDtos.AgreementView;
import com.glassehr.attendance.LaborAgreementGuard;
import com.glassehr.attendance.LaborAgreementService;
import com.glassehr.attendance.WorkPatternDtos.AssignRequest;
import com.glassehr.attendance.WorkPatternDtos.WorkPatternRequest;
import com.glassehr.attendance.WorkPatternDtos.WorkPatternView;
import com.glassehr.attendance.WorkPatternService;
import com.glassehr.attendance.WorkPolicyDtos.AllowanceDto;
import com.glassehr.attendance.WorkPolicyDtos.WorkPolicyRequest;
import com.glassehr.attendance.WorkPolicyDtos.WorkPolicyView;
import com.glassehr.attendance.WorkPolicyRules;
import com.glassehr.attendance.WorkPolicyRules.AllowMode;
import com.glassehr.attendance.WorkPolicyService;
import com.glassehr.employee.EmployeeDtos.EmployeeCreateRequest;
import com.glassehr.employee.EmployeeService;
import com.glassehr.employee.RequestActor;
import java.time.LocalDate;
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
 * V039(#125): 근무유형·사원 배정·휴일·근로자대표 서면합의·첨부의 법인 분리를 Oracle 실DB에서 검증한다.
 * "법인 A(C001) 세션으로 법인 B(ZZIT2) 데이터 조회·수정 → 404/거부"와 복합 FK 를 본다(docs/tenant-design.md 5.2).
 * 실행 조건: {@code ORACLE_IT=true}. 각 테스트는 롤백한다. 1900년대 날짜와 ZZIT 코드만 쓴다.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class AttendanceBaseTenantOracleTest {

    private static final String A = "C001";
    private static final String B = "ZZIT2";
    private static final LocalDate D0 = LocalDate.of(1904, 1, 1);
    private static final RequestActor HR = new RequestActor("it-hr", RequestActor.Role.HR, "127.0.0.1", "C001");

    @Autowired
    private WorkPatternService patterns;

    @Autowired
    private HolidayService holidays;

    @Autowired
    private LaborAgreementService agreements;

    @Autowired
    private LaborAgreementGuard guard;

    @Autowired
    private EmployeeService employees;

    @Autowired
    private WorkPolicyService policies;

    @Autowired
    private JdbcTemplate jdbc;

    private long bPattern;
    private AgreementView bAgreement;

    @BeforeEach
    void 법인_B와_B의_근무유형_서면합의를_넣는다() {
        jdbc.update("INSERT INTO enter_company (enter_cd, enter_name, biz_reg_no, use_yn, sort_order)"
                + " VALUES (?, 'IT 법인 나(예시)', '0000000093', 'Y', 0)", B);
        jdbc.update("INSERT INTO work_pattern (enter_cd, pattern_code, pattern_name, work_start_min, work_end_min,"
                + " break_start_min, break_minutes, day_types, daily_contract_minutes, weekly_contract_minutes)"
                + " VALUES (?, 'ZZIT_WP', 'B 근무유형(예시)', 540, 1080, 720, 60, 'WWWWWOH', 480, 2400)", B);
        bPattern = jdbc.queryForObject(
                "SELECT pattern_id FROM work_pattern WHERE enter_cd = ? AND pattern_code = 'ZZIT_WP'", Long.class, B);
        bAgreement = agreements.create(B, agreement(AgreementType.HOLIDAY_SUBSTITUTION), "it-hr");
    }

    @Test
    void 법인_A_세션은_법인_B_근무유형을_목록에서_보지_못하고_수정_배정은_404() {
        assertThat(patterns.list(A)).extracting(WorkPatternView::patternId).doesNotContain(bPattern);
        assertThat(patterns.list(B)).extracting(WorkPatternView::patternCode).containsExactly("ZZIT_WP");

        WorkPatternRequest same = new WorkPatternRequest("ZZIT_WP", "바꿈", 540, 1080, 720, 60, "WWWWWOH", true, null);
        assertStatus(() -> patterns.update(A, bPattern, same), HttpStatus.NOT_FOUND);
        long emp = employee();
        assertStatus(() -> patterns.assign(A, emp, new AssignRequest(bPattern, D0, "섞임"), "it-hr"),
                HttpStatus.NOT_FOUND);
        // 법인 B 세션은 법인 A 사원 배정 이력을 볼 수 없다.
        assertStatus(() -> patterns.history(B, emp), HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT pattern_name FROM work_pattern WHERE pattern_id = ?", String.class,
                bPattern)).isEqualTo("B 근무유형(예시)");
        // 근무유형 코드는 법인 안에서만 유일하다(UNIQUE (enter_cd, pattern_code)).
        jdbc.update("INSERT INTO work_pattern (enter_cd, pattern_code, pattern_name, work_start_min, work_end_min,"
                + " break_start_min, break_minutes, day_types, daily_contract_minutes, weekly_contract_minutes)"
                + " VALUES (?, 'ZZIT_WP', 'A 근무유형(예시)', 540, 1080, 720, 60, 'WWWWWOH', 480, 2400)", A);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_pattern WHERE pattern_code = 'ZZIT_WP'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void 법인_A_세션은_법인_B_서면합의를_보거나_첨부하지_못하고_합의_확인에서도_인정하지_않는다() {
        long id = bAgreement.agreementId();
        assertThat(agreements.list(A)).extracting(AgreementView::agreementId).doesNotContain(id);
        assertStatus(() -> agreements.get(A, id), HttpStatus.NOT_FOUND);
        assertStatus(() -> agreements.update(A, id, agreement(AgreementType.HOLIDAY_SUBSTITUTION), "it-hr"),
                HttpStatus.NOT_FOUND);
        assertStatus(() -> agreements.addFile(A, id, "a.pdf", "%PDF-1.7 it".getBytes(), "it-hr"),
                HttpStatus.NOT_FOUND);
        long fileId = agreements.addFile(B, id, "a.pdf", "%PDF-1.7 it".getBytes(), "it-hr").fileId();
        assertStatus(() -> agreements.file(A, id, fileId), HttpStatus.NOT_FOUND);
        assertStatus(() -> agreements.deleteFile(A, id, fileId), HttpStatus.NOT_FOUND);
        assertThat(agreements.get(B, id).files()).hasSize(1);

        // LaborAgreementGuard 는 같은 법인 합의만 인정한다.
        assertThat(guard.check(B, AgreementType.HOLIDAY_SUBSTITUTION, D0, D0.plusMonths(1), null).ok()).isTrue();
        assertThat(guard.check(A, AgreementType.HOLIDAY_SUBSTITUTION, D0, D0.plusMonths(1), null).agreementId())
                .isNull();
        assertThatThrownBy(() -> guard.requireLinked(A, id, AgreementType.HOLIDAY_SUBSTITUTION, D0, D0.plusMonths(1),
                null)).isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void 휴일_달력은_법인마다_따로이고_법인_A는_법인_B_휴일을_지우거나_B_합의로_대체하지_못한다() {
        HolidayView b = holidays.createCompanyHoliday(B, new CompanyHolidayRequest(D0, "B 회사휴일(예시)", true, null),
                "it-hr");
        assertThat(holidays.year(A, 1904)).extracting(HolidayView::holidayId).doesNotContain(b.holidayId());
        assertThat(holidays.year(B, 1904)).extracting(HolidayView::holidayId).containsExactly(b.holidayId());
        assertThat(holidays.holidayDates(A, D0, D0.plusDays(1))).isEmpty();
        assertThat(holidays.holidayDates(B, D0, D0.plusDays(1))).containsExactly(D0);

        assertStatus(() -> holidays.delete(A, b.holidayId()), HttpStatus.NOT_FOUND);
        // 같은 날 회사휴일을 법인 A 도 따로 둘 수 있다(ux_holiday_reserved_date 에 법인 포함).
        HolidayView a = holidays.createCompanyHoliday(A, new CompanyHolidayRequest(D0, "A 회사휴일(예시)", true, null),
                "it-hr");
        assertThat(a.holidayId()).isNotEqualTo(b.holidayId());

        // 법인 A 의 휴일 대체에 법인 B 합의서·휴일은 쓸 수 없다.
        jdbc.update("INSERT INTO holiday (enter_cd, holiday_date, holiday_name, holiday_kind, paid_yn, source_ref,"
                + " verified_date, created_by) VALUES (?, DATE '1904-01-10', 'IT 시험 공휴일', 'PUBLIC', 'Y',"
                + " 'IT 가상 자료', DATE '1904-01-01', 'it-hr')", A);
        long aPublic = jdbc.queryForObject("SELECT holiday_id FROM holiday WHERE enter_cd = ?"
                + " AND holiday_name = 'IT 시험 공휴일'", Long.class, A);
        assertStatus(() -> holidays.substitute(A, new SubstitutionRequest(aPublic, D0.plusDays(11),
                bAgreement.agreementId(), null), "it-hr"), HttpStatus.BAD_REQUEST);
        assertStatus(() -> holidays.substitute(B, new SubstitutionRequest(aPublic, D0.plusDays(11),
                bAgreement.agreementId(), null), "it-hr"), HttpStatus.NOT_FOUND);
    }

    @Test
    void DB_복합_FK는_다른_법인의_근무유형_서면합의_배정을_참조하지_못하게_한다() {
        long emp = employee();
        // 법인 A 사원 배정이 법인 B 근무유형을 가리킬 수 없다.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO emp_work_pattern (enter_cd, emp_id, pattern_id, valid_from,"
                + " created_by) VALUES (?, ?, ?, DATE '1904-01-01', 'it-hr')", A, emp, bPattern))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 법인 A 첨부가 법인 B 합의서를 가리킬 수 없다.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO worker_rep_agreement_file (enter_cd, agreement_id, file_name,"
                + " content_type, file_size, content, sha256, uploaded_by) VALUES (?, ?, 'a.pdf', 'application/pdf', 1,"
                + " HEXTORAW('25'), RPAD('0', 64, '0'), 'it-hr')", A, bAgreement.agreementId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 법인 A 휴일 대체가 법인 B 합의서를 가리킬 수 없다.
        jdbc.update("INSERT INTO holiday (enter_cd, holiday_date, holiday_name, holiday_kind, paid_yn, source_ref,"
                + " verified_date, created_by) VALUES (?, DATE '1904-02-10', 'IT 시험 공휴일2', 'PUBLIC', 'Y',"
                + " 'IT 가상 자료', DATE '1904-01-01', 'it-hr')", A);
        long aPublic = jdbc.queryForObject("SELECT holiday_id FROM holiday WHERE enter_cd = ?"
                + " AND holiday_name = 'IT 시험 공휴일2'", Long.class, A);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO holiday (enter_cd, holiday_date, holiday_name, holiday_kind,"
                + " paid_yn, replaces_holiday_id, agreement_id, created_by) VALUES (?, DATE '1904-02-12', '섞임',"
                + " 'REPLACEMENT', 'Y', ?, ?, 'it-hr')", A, aPublic, bAgreement.agreementId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 법인 B 휴일 대체가 법인 A 공휴일을 대체할 수 없다(자기 참조 복합 FK).
        assertThatThrownBy(() -> jdbc.update("INSERT INTO holiday (enter_cd, holiday_date, holiday_name, holiday_kind,"
                + " paid_yn, replaces_holiday_id, agreement_id, created_by) VALUES (?, DATE '1904-02-12', '섞임',"
                + " 'REPLACEMENT', 'Y', ?, ?, 'it-hr')", B, aPublic, bAgreement.agreementId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 법인을 빠뜨린 근무유형은 DEFAULT 가 없어 거부한다.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO work_pattern (pattern_code, pattern_name, work_start_min,"
                + " work_end_min, break_start_min, break_minutes, day_types, daily_contract_minutes,"
                + " weekly_contract_minutes) VALUES ('ZZIT_WQ', '법인 없음', 540, 1080, 720, 60, 'WWWWWOH', 480, 2400)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 근무_정책은_법인마다_따로이고_다른_법인_정책_근무유형은_404() {
        // 이 테스트는 법인 격리만 검증하므로 실행일의 legal_rate 시드에 의존하지 않게 정책 행을 직접 준비한다.
        jdbc.update("INSERT INTO work_policy (enter_cd, pattern_id, policy_name, overtime_mode, night_mode,"
                + " holiday_mode, created_by, created_at, updated_by, updated_at) VALUES (?, ?, 'IT 정책(예시)',"
                + " 'DENY', 'UNLIMITED', 'UNLIMITED', 'it-hr', SYSTIMESTAMP, 'it-hr', SYSTIMESTAMP)", B, bPattern);
        WorkPolicyView b = policies.list(B, true).getFirst();
        assertThat(policies.list(A, true)).extracting(WorkPolicyView::policyId).doesNotContain(b.policyId());
        assertStatus(() -> policies.update(A, b.policyId(), policyRequest(null), "it-hr"), HttpStatus.NOT_FOUND);
        assertStatus(() -> policies.delete(A, b.policyId(), "it-hr"), HttpStatus.NOT_FOUND);
        // 법인 A 정책이 법인 B 근무유형을 재정의할 수 없다(서비스 404, DB 복합 FK).
        assertStatus(() -> policies.create(A, policyRequest(bPattern), "it-hr"), HttpStatus.NOT_FOUND);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO work_policy (enter_cd, pattern_id, policy_name, created_by,"
                + " created_at, updated_by, updated_at) VALUES (?, ?, '섞임', 'it-hr', SYSTIMESTAMP, 'it-hr',"
                + " SYSTIMESTAMP)", A, bPattern)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(policies.list(B, false)).extracting(WorkPolicyView::policyId).containsExactly(b.policyId());
    }

    private static WorkPolicyRequest policyRequest(Long patternId) {
        AllowanceDto none = new AllowanceDto(AllowMode.UNLIMITED, null, null, null);
        return new WorkPolicyRequest(patternId, "IT 정책(예시)", new AllowanceDto(AllowMode.DENY, null, null, null),
                none, none, true, 15, 0, WorkPolicyRules.Rounding.UP, 0, 0, true,
                WorkPolicyRules.MissingOut.SCHEDULED_END, true);
    }

    private static AgreementRequest agreement(AgreementType type) {
        return new AgreementRequest(type, "IT 합의서(예시)", D0, D0, D0.plusMonths(1), LaborAgreement.Scope.ALL, null,
                "IT 대상", LaborAgreement.RepType.MAJORITY_UNION, "가상노동조합IT", null, null, "IT 문서함-1");
    }

    private long employee() {
        return employees.create(A, new EmployeeCreateRequest("S01", "예시사원근태가", "F", LocalDate.of(1880, 1, 1),
                "가상시 예시구 샘플로 0", null, "시험", null, LocalDate.of(1902, 1, 1), null, null, null, "REGULAR", 2400,
                null, null, null, null, "STAFF", null, null), HR).empId();
    }

    private static void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call).isInstanceOfSatisfying(AttendanceException.class,
                e -> assertThat(e.status()).isEqualTo(status));
    }
}
