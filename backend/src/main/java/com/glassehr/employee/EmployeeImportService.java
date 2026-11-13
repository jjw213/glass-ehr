package com.glassehr.employee;

import com.glassehr.employee.EmployeeDtos.EmployeeCreateRequest;
import com.glassehr.employee.EmployeeDtos.EmployeeView;
import com.glassehr.employee.EmployeeImportCsv.Column;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사원 엑셀(CSV) 일괄 등록(#20). 초기 데이터 이관용.
 *
 * <ol>
 *   <li>{@link #preview}: 파일을 읽어 행마다 입사 등록({@link EmployeeService#create})과 같은 검사를 하고 오류를 돌려준다. 저장하지 않는다.</li>
 *   <li>{@link #confirm}: 같은 파일을 다시 받아 처음부터 다시 검사한다. 오류가 한 행이라도 있으면 아무것도 저장하지 않고 422.
 *       오류가 없으면 한 트랜잭션에서 모두 등록한다(중간에 실패하면 전부 되돌림).</li>
 * </ol>
 * 미리보기 결과를 서버에 보관하지 않으므로, 미리보기와 확정 사이에 코드·부서가 바뀌어도 확정 때 다시 막힌다.
 * 응답·오류 메시지에는 주민등록번호·생년월일·주소 값을 넣지 않는다(성명·사업장·입사일만 행 확인용으로 보여 줌).
 */
@Service
@Transactional(readOnly = true)
public class EmployeeImportService {

    private static final Pattern DATE = Pattern.compile("^(\\d{4})[-./](\\d{1,2})[-./](\\d{1,2})\\.?$");

    private final EmployeeService employees;
    private final RrnService rrns;
    private final BizSiteRepository sites;
    private final Validator validator;

    public EmployeeImportService(EmployeeService employees, RrnService rrns, BizSiteRepository sites,
            Validator validator) {
        this.employees = employees;
        this.rrns = rrns;
        this.sites = sites;
        this.validator = validator;
    }

    /** 한 행 검사 결과. rowNo 는 파일의 줄 번호(머리글이 1행). */
    public record RowResult(int rowNo, String name, String siteCode, LocalDate hireDate, List<String> errors) {
    }

    /** 미리보기. fileErrors(머리글·행 수 등)나 행 오류가 하나라도 있으면 확정할 수 없다. */
    public record Preview(int totalRows, int errorRows, boolean confirmable, List<String> fileErrors,
            List<RowResult> rows) {
    }

    /** 확정 결과. 등록된 사번(파일 행 순서). */
    public record Confirmed(int created, List<String> empNos) {
    }

    /** 확정 거부(오류가 있는 파일). 미리보기 결과를 함께 돌려준다. */
    public static class NotConfirmableException extends RuntimeException {
        private final transient Preview preview;

        NotConfirmableException(Preview preview) {
            super("오류가 있는 행이 있어 등록하지 않았어요");
            this.preview = preview;
        }

        public Preview preview() {
            return preview;
        }
    }

    private record Parsed(Preview preview, List<EmployeeCreateRequest> requests) {
    }

    public Preview preview(byte[] file) {
        return parseAndCheck(file).preview();
    }

    @Transactional
    public Confirmed confirm(byte[] file, RequestActor actor) {
        Parsed p = parseAndCheck(file);
        if (!p.preview().confirmable()) {
            throw new NotConfirmableException(p.preview());
        }
        List<String> empNos = new ArrayList<>();
        List<RowResult> rows = p.preview().rows();
        for (int i = 0; i < p.requests().size(); i++) {
            try {
                EmployeeView v = employees.create(p.requests().get(i), actor);
                empNos.add(v.empNo());
            } catch (EmployeeException e) {
                // 검사 뒤 다른 변경과 엇갈린 경우. 예외가 트랜잭션 밖으로 나가므로 앞 행 등록도 모두 되돌린다.
                throw new EmployeeException(e.status(), rows.get(i).rowNo() + "행: " + e.getMessage()
                        + " (아무 사원도 등록하지 않았어요)");
            }
        }
        return new Confirmed(empNos.size(), empNos);
    }

    // ---------------------------------------------------------------- 검사

    private Parsed parseAndCheck(byte[] file) {
        List<String> fileErrors = new ArrayList<>();
        List<RowResult> results = new ArrayList<>();
        List<EmployeeCreateRequest> requests = new ArrayList<>();
        if (file == null || file.length == 0) {
            fileErrors.add("파일이 비어 있어요");
            return new Parsed(new Preview(0, 0, false, fileErrors, results), requests);
        }
        List<List<String>> table;
        try {
            table = EmployeeImportCsv.parse(EmployeeImportCsv.decode(file));
        } catch (EmployeeImportCsv.MalformedCsvException e) {
            fileErrors.add(e.getMessage());
            return new Parsed(new Preview(0, 0, false, fileErrors, results), requests);
        }
        if (table.isEmpty()) {
            fileErrors.add("머리글 행이 없어요. 양식을 내려받아 써 주세요");
            return new Parsed(new Preview(0, 0, false, fileErrors, results), requests);
        }

        Map<Column, Integer> index = new EnumMap<>(Column.class);
        List<String> header = table.get(0);
        for (int i = 0; i < header.size(); i++) {
            String h = header.get(i);
            if (h.isBlank()) {
                continue;
            }
            Column c = EmployeeImportCsv.columnOf(h);
            if (c == null) {
                fileErrors.add("모르는 열이에요: " + h.strip());
            } else if (index.putIfAbsent(c, i) != null) {
                fileErrors.add("같은 열이 두 번 있어요: " + c.label);
            }
        }
        for (Column c : Column.values()) {
            if (c.required && !index.containsKey(c)) {
                fileErrors.add("필수 열이 없어요: " + c.label);
            }
        }

        List<Integer> dataRows = new ArrayList<>();
        for (int r = 1; r < table.size(); r++) {
            if (table.get(r).stream().anyMatch(s -> !s.isBlank())) {
                dataRows.add(r);
            }
        }
        if (dataRows.isEmpty() && fileErrors.isEmpty()) {
            fileErrors.add("등록할 행이 없어요");
        }
        if (dataRows.size() > EmployeeImportCsv.MAX_ROWS) {
            fileErrors.add("한 번에 " + EmployeeImportCsv.MAX_ROWS + "행까지 올릴 수 있어요(지금 " + dataRows.size()
                    + "행). 파일을 나눠 주세요");
        }
        if (!fileErrors.isEmpty()) {
            return new Parsed(new Preview(dataRows.size(), 0, false, fileErrors, results), requests);
        }

        Map<Rrn, Integer> rrnRows = new HashMap<>();
        int errorRows = 0;
        for (int r : dataRows) {
            List<String> cells = table.get(r);
            int rowNo = r + 1;
            List<String> errors = new ArrayList<>();
            RowReader rd = new RowReader(cells, index, errors);
            EmployeeCreateRequest req = new EmployeeCreateRequest(rd.text(Column.SITE_CODE), rd.text(Column.NAME),
                    rd.text(Column.GENDER), rd.date(Column.BIRTH_DATE), rd.text(Column.ADDRESS),
                    rd.text(Column.CAREER_HISTORY), rd.text(Column.JOB_DESCRIPTION), rd.text(Column.JOB_FAMILY_CODE),
                    rd.date(Column.HIRE_DATE), rd.date(Column.RENEWAL_DATE), rd.date(Column.CONTRACT_START_DATE),
                    rd.date(Column.CONTRACT_END_DATE), rd.text(Column.EMPLOYMENT_TYPE_CODE),
                    rd.integer(Column.WEEKLY_CONTRACT_MINUTES), rd.text(Column.WORK_LOCATION_CODE),
                    rd.text(Column.EMPLOYMENT_NOTE), rd.text(Column.OTHER_NOTE), rd.longValue(Column.DEPT_ID),
                    rd.text(Column.JOB_GRADE_CODE), rd.text(Column.JOB_TITLE_CODE), rd.rrn());

            boolean parsedOk = errors.isEmpty();
            List<String> beanErrors = validator.validate(req).stream()
                    .map(ConstraintViolation::getMessage)
                    .sorted()
                    .toList();
            errors.addAll(beanErrors);
            if (parsedOk && beanErrors.isEmpty()) {
                checkSite(req.siteCode(), errors);
                try {
                    employees.checkCreate(req);
                } catch (EmployeeException e) {
                    errors.add(e.getMessage());
                }
            }
            if (req.rrn() != null) {
                Integer first = rrnRows.putIfAbsent(req.rrn(), rowNo);
                if (first != null) {
                    errors.add("주민등록번호가 " + first + "행과 같아요");
                } else if (rrns.isRegistered(req.rrn())) {
                    errors.add("이미 다른 사원에게 등록된 주민등록번호예요");
                }
            }
            if (!errors.isEmpty()) {
                errorRows++;
            }
            results.add(new RowResult(rowNo, req.name(), req.siteCode(), req.hireDate(), List.copyOf(errors)));
            requests.add(req);
        }
        return new Parsed(new Preview(dataRows.size(), errorRows, errorRows == 0, fileErrors, results), requests);
    }

    private void checkSite(String siteCode, List<String> errors) {
        BizSite site = sites.findById(siteCode).orElse(null);
        if (site == null) {
            errors.add("사업장이 없어요: " + siteCode);
        } else if (!site.isActive()) {
            errors.add("사용하지 않는 사업장이에요: " + siteCode);
        }
    }

    /** 한 행의 칸을 형식에 맞게 읽고, 형식 오류는 errors 에 열 이름과 함께 넣는다(값은 넣지 않음). */
    private static final class RowReader {
        private final List<String> cells;
        private final Map<Column, Integer> index;
        private final List<String> errors;

        RowReader(List<String> cells, Map<Column, Integer> index, List<String> errors) {
            this.cells = cells;
            this.index = index;
            this.errors = errors;
        }

        /** 빈 칸은 null. 앞뒤 공백은 뗀다. */
        String text(Column c) {
            Integer i = index.get(c);
            if (i == null || i >= cells.size()) {
                return null;
            }
            String s = cells.get(i).strip();
            return s.isEmpty() ? null : s;
        }

        /** YYYY-MM-DD(엑셀이 바꿔 저장하는 YYYY/M/D·YYYY.M.D 도 허용). */
        LocalDate date(Column c) {
            String s = text(c);
            if (s == null) {
                return null;
            }
            Matcher m = DATE.matcher(s);
            if (m.matches()) {
                try {
                    return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                            Integer.parseInt(m.group(3)));
                } catch (DateTimeException e) {
                    // 아래에서 오류로 남긴다(예: 2월 30일)
                }
            }
            errors.add(c.label + " 날짜가 올바르지 않아요(YYYY-MM-DD)");
            return null;
        }

        Integer integer(Column c) {
            String s = text(c);
            if (s == null) {
                return null;
            }
            try {
                return Integer.valueOf(s.replace(",", ""));
            } catch (NumberFormatException e) {
                errors.add(c.label + "은(는) 정수로 써 주세요");
                return null;
            }
        }

        Long longValue(Column c) {
            String s = text(c);
            if (s == null) {
                return null;
            }
            try {
                return Long.valueOf(s);
            } catch (NumberFormatException e) {
                errors.add(c.label + "은(는) 숫자로 써 주세요");
                return null;
            }
        }

        Rrn rrn() {
            String s = text(Column.RRN);
            if (s == null) {
                return null;
            }
            try {
                return Rrn.parse(s);
            } catch (IllegalArgumentException e) {
                errors.add(e.getMessage()); // Rrn.parse 메시지에는 값이 없다
                return null;
            }
        }
    }

    /** 컨트롤러 오류 처리용: 확정 거부 상태 코드. */
    static final HttpStatus NOT_CONFIRMABLE = HttpStatus.UNPROCESSABLE_ENTITY;
}
