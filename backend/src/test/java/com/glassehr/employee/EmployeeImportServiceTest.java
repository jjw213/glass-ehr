package com.glassehr.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.employee.EmployeeDtos.EmployeeCreateRequest;
import com.glassehr.employee.EmployeeDtos.EmployeeView;
import com.glassehr.employee.EmployeeImportService.NotConfirmableException;
import com.glassehr.employee.EmployeeImportService.Preview;
import jakarta.validation.Validation;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/** 사원 일괄 등록(#20) 미리보기·확정. 값은 모두 명백한 가짜 값이다. */
class EmployeeImportServiceTest {

    private static final String HEADER = new String(EmployeeImportCsv.template(), StandardCharsets.UTF_8)
            .replace("﻿", "").strip();
    private static final RequestActor ACTOR = new RequestActor("hr1", RequestActor.Role.HR, "127.0.0.1");

    private EmployeeService employees;
    private RrnService rrns;
    private BizSiteRepository sites;
    private EmployeeImportService svc;

    @BeforeEach
    void setUp() {
        employees = mock(EmployeeService.class);
        rrns = mock(RrnService.class);
        sites = mock(BizSiteRepository.class);
        BizSite site = mock(BizSite.class);
        when(site.isActive()).thenReturn(true);
        when(sites.findById("S01")).thenReturn(Optional.of(site));
        when(sites.findById("S99")).thenReturn(Optional.empty());
        svc = new EmployeeImportService(employees, rrns, sites,
                Validation.buildDefaultValidatorFactory().getValidator());
    }

    /** 양식 열 순서대로 한 행. rrn 은 마지막 열. */
    private static String row(String site, String name, String gender, String birth, String hire, String type,
            String end, String dept, String rrn) {
        // 사업장,성명,성별,생년월일,주소,이력,종사업무,직군,입사일,갱신일,계약시작,계약종료,고용형태,주소정,근무지,고용메모,기타,부서,직급,직책,주민
        return String.join(",", site, name, gender, birth, "\"가상시 예시구, 샘플로 1\"", "", "개발", "", hire, "",
                end.isEmpty() ? "" : hire, end, type, "2400", "", "", "", dept, "STAFF", "", rrn);
    }

    private static byte[] file(String... rows) {
        return ("﻿" + HEADER + "\r\n" + String.join("\r\n", rows) + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private static final String OK1 = row("S01", "예시사원일", "F", "1999-01-01", "2026-03-02", "REGULAR", "", "10",
            "000000-0000001");
    private static final String OK2 = row("S01", "예시사원이", "M", "1998/2/3", "2026.3.2", "REGULAR", "", "", "");

    @Test
    void 양식은_필수_표시가_있는_머리글만_있고_예시_행이_없다() {
        String t = new String(EmployeeImportCsv.template(), StandardCharsets.UTF_8);
        assertThat(t).startsWith("﻿사업장코드*,성명*,성별(M/F)*,생년월일*,주소*,이력,");
        assertThat(t.strip().lines()).hasSize(1);
        assertThat(EmployeeImportCsv.parse(t)).hasSize(1);
    }

    @Test
    void 오류가_없으면_확정_가능이고_저장하지_않는다() {
        Preview p = svc.preview(file(OK1, OK2));
        assertThat(p.fileErrors()).isEmpty();
        assertThat(p.totalRows()).isEqualTo(2);
        assertThat(p.errorRows()).isZero();
        assertThat(p.confirmable()).isTrue();
        assertThat(p.rows()).extracting(EmployeeImportService.RowResult::rowNo).containsExactly(2, 3);
        assertThat(p.rows().get(1).hireDate()).isEqualTo(LocalDate.of(2026, 3, 2));
        verify(employees, times(2)).checkCreate(any());
        verify(employees, never()).create(any(), any());
    }

    @Test
    void 행마다_오류를_보이고_값은_되돌려주지_않는다() {
        when(rrns.isRegistered(Rrn.parse("000000-0000009"))).thenReturn(true);
        doThrow(EmployeeException.badRequest("없는 고용형태 코드예요: NOPE")).when(employees)
                .checkCreate(org.mockito.ArgumentMatchers.argThat(r -> r != null && "NOPE".equals(r.employmentTypeCode())));
        Preview p = svc.preview(file(
                OK1,
                row("S01", "", "X", "1999-02-30", "2026-03-02", "REGULAR", "", "abc", "12345-6789012"),
                row("S99", "예시사원삼", "F", "1999-01-01", "2026-03-02", "REGULAR", "", "", ""),
                row("S01", "예시사원사", "F", "1999-01-01", "2026-03-02", "NOPE", "", "", ""),
                row("S01", "예시사원오", "F", "1999-01-01", "2026-03-02", "REGULAR", "", "", "000000-0000001"),
                row("S01", "예시사원육", "F", "1999-01-01", "2026-03-02", "REGULAR", "", "", "000000-0000009")));
        assertThat(p.confirmable()).isFalse();
        assertThat(p.errorRows()).isEqualTo(5);
        List<List<String>> errs = p.rows().stream().map(EmployeeImportService.RowResult::errors).toList();
        assertThat(errs.get(0)).isEmpty();
        assertThat(errs.get(1)).contains("생년월일 날짜가 올바르지 않아요(YYYY-MM-DD)", "부서ID은(는) 숫자로 써 주세요",
                "주민등록번호 형식이 올바르지 않아요(숫자 6자리-숫자 7자리)", "성명을 입력해 주세요", "성별은 M 또는 F예요");
        assertThat(errs.get(2)).containsExactly("사업장이 없어요: S99");
        assertThat(errs.get(3)).containsExactly("없는 고용형태 코드예요: NOPE");
        assertThat(errs.get(4)).containsExactly("주민등록번호가 2행과 같아요");
        assertThat(errs.get(5)).containsExactly("이미 다른 사원에게 등록된 주민등록번호예요");
        assertThat(p.toString()).doesNotContain("0000001").doesNotContain("6789012").doesNotContain("가상시")
                .doesNotContain("1999");
    }

    @Test
    void 머리글이_틀리면_파일_오류() {
        Preview p = svc.preview("성명,모르는열\r\n예시,1\r\n".getBytes(StandardCharsets.UTF_8));
        assertThat(p.confirmable()).isFalse();
        assertThat(p.fileErrors()).contains("모르는 열이에요: 모르는열", "필수 열이 없어요: 사업장코드", "필수 열이 없어요: 입사일");
        assertThat(svc.preview(new byte[0]).fileErrors()).containsExactly("파일이 비어 있어요");
        assertThat(svc.preview(file()).fileErrors()).containsExactly("등록할 행이 없어요");
    }

    @Test
    void 행_수_상한을_넘으면_거부() {
        String[] rows = new String[EmployeeImportCsv.MAX_ROWS + 1];
        java.util.Arrays.fill(rows, OK2);
        Preview p = svc.preview(file(rows));
        assertThat(p.confirmable()).isFalse();
        assertThat(p.fileErrors()).hasSize(1);
        assertThat(p.fileErrors().get(0)).contains("1000행까지");
    }

    @Test
    void 열_순서가_달라도_되고_빈_줄은_건너뛴다() {
        String csv = "입사일*,성명*,사업장코드,성별(M/F),생년월일,주소,종사업무,고용형태코드\n"
                + "2026-03-02,예시사원일,S01,F,1999-01-01,가상시,개발,REGULAR\n\n,,,,,,,\n";
        Preview p = svc.preview(csv.getBytes(StandardCharsets.UTF_8));
        assertThat(p.fileErrors()).isEmpty();
        assertThat(p.totalRows()).isEqualTo(1);
        assertThat(p.confirmable()).isTrue();
    }

    @Test
    void 한글_윈도우_엑셀의_MS949_CSV도_읽는다() {
        String csv = HEADER + "\r\n" + OK2 + "\r\n";
        Preview p = svc.preview(csv.getBytes(Charset.forName("MS949")));
        assertThat(p.fileErrors()).isEmpty();
        assertThat(p.rows().get(0).name()).isEqualTo("예시사원이");
    }

    @Test
    void 확정은_오류가_있으면_아무것도_저장하지_않는다() {
        byte[] f = file(OK1, row("S99", "예시사원삼", "F", "1999-01-01", "2026-03-02", "REGULAR", "", "", ""));
        assertThatThrownBy(() -> svc.confirm(f, ACTOR))
                .isInstanceOfSatisfying(NotConfirmableException.class, e -> {
                    assertThat(e.preview().errorRows()).isEqualTo(1);
                    assertThat(e.preview().rows().get(1).errors()).containsExactly("사업장이 없어요: S99");
                });
        verify(employees, never()).create(any(), any());
    }

    @Test
    void 확정은_오류가_없으면_파일_순서대로_모두_등록한다() {
        when(employees.create(any(), any())).thenReturn(view("S01-2026-001"), view("S01-2026-002"));
        EmployeeImportService.Confirmed c = svc.confirm(file(OK1, OK2), ACTOR);
        assertThat(c.created()).isEqualTo(2);
        assertThat(c.empNos()).containsExactly("S01-2026-001", "S01-2026-002");
        ArgumentCaptor<EmployeeCreateRequest> req = ArgumentCaptor.forClass(EmployeeCreateRequest.class);
        verify(employees, times(2)).create(req.capture(), any());
        EmployeeCreateRequest first = req.getAllValues().get(0);
        assertThat(first.address()).isEqualTo("가상시 예시구, 샘플로 1");
        assertThat(first.deptId()).isEqualTo(10L);
        assertThat(first.weeklyContractMinutes()).isEqualTo(2400);
        assertThat(first.rrn()).isEqualTo(Rrn.parse("000000-0000001"));
        assertThat(req.getAllValues().get(1).birthDate()).isEqualTo(LocalDate.of(1998, 2, 3));
    }

    @Test
    void 확정_중_한_행이라도_실패하면_행_번호와_함께_예외를_던져_트랜잭션을_되돌린다() {
        when(employees.create(any(), any())).thenReturn(view("S01-2026-001"))
                .thenThrow(EmployeeException.conflict("다른 변경과 충돌"));
        assertThatThrownBy(() -> svc.confirm(file(OK1, OK2), ACTOR))
                .isInstanceOfSatisfying(EmployeeException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).startsWith("3행: ").contains("아무 사원도 등록하지 않았어요");
                });
    }

    @Test
    void CSV_따옴표_안의_쉼표와_줄바꿈() {
        List<List<String>> t = EmployeeImportCsv.parse("a,\"b,\"\"c\"\"\nd\",e\r\nf\rg");
        assertThat(t).containsExactly(List.of("a", "b,\"c\"\nd", "e"), List.of("f"), List.of("g"));
    }

    private static EmployeeView view(String empNo) {
        return new EmployeeView(1, empNo, "S01", "예시사원", "F", LocalDate.of(1999, 1, 1), "가상시", null, "개발", null,
                LocalDate.of(2026, 3, 2), null, null, null, "REGULAR", 2400, null, null, 10L, "STAFF", null, null, null,
                null, null, null, null);
    }
}
