package com.glassehr.employee;

import java.time.LocalDate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사번 채번. 형식 {@code 사업장코드-입사연도-순번}(예: S01-2026-001, #9 결정, docs/employee-number-design.md).
 *
 * <ul>
 *   <li>순번은 사업장·입사연도마다 001부터. 사원 등록 트랜잭션 안에서 사업장 행과 채번 행을 잠그고 last_seq + 1을 쓴다.
 *       등록이 롤백되면 순번도 함께 롤백되어 빈 번호가 생기지 않는다.</li>
 *   <li>사번에는 개인정보를 넣지 않는다(입사연도·순번만).</li>
 *   <li>999를 넘으면 거부한다(설계 3장 미결 사항. 처리 방식은 사람 결정 대기).</li>
 * </ul>
 */
@Component
public class EmpNoGenerator {

    /** DB CHECK ck_employee_emp_no 와 같은 규칙(중괄호는 V009에서 못 쓰므로 여기도 같은 표현으로 둠). */
    public static final String EMP_NO_PATTERN = "^S[0-9][0-9]-[0-9][0-9][0-9][0-9]-[0-9][0-9][0-9]$";

    /** 사번 순번 자릿수(형식 정의. 법령 수치 아님). */
    static final int MAX_SEQ = 999;

    private final BizSiteRepository sites;
    private final EmpNoSeqRepository seqs;

    public EmpNoGenerator(BizSiteRepository sites, EmpNoSeqRepository seqs) {
        this.sites = sites;
        this.seqs = seqs;
    }

    /** 다음 사번. 호출한 트랜잭션 안에서만 쓴다(잠금은 그 트랜잭션이 끝날 때 풀림). */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(String siteCode, LocalDate hireDate) {
        BizSite site = sites.findForUpdate(siteCode)
                .orElseThrow(() -> EmployeeException.badRequest("사업장이 없어요: " + siteCode));
        if (!site.isActive()) {
            throw EmployeeException.badRequest("사용하지 않는 사업장이에요: " + siteCode);
        }
        int year = hireDate.getYear();
        EmpNoSeq seq = seqs.findForUpdate(siteCode, year).orElse(null);
        int next = seq == null ? 1 : seq.getLastSeq() + 1;
        if (next > MAX_SEQ) {
            throw EmployeeException.conflict(
                    siteCode + " 사업장의 " + year + "년 입사자 사번(순번 " + MAX_SEQ + ")이 모두 쓰였어요. 관리자에게 문의해 주세요");
        }
        if (seq == null) {
            seqs.saveAndFlush(new EmpNoSeq(siteCode, year, next));
        } else {
            seq.advanceTo(next);
            seqs.saveAndFlush(seq);
        }
        return format(siteCode, year, next);
    }

    static String format(String siteCode, int year, int seq) {
        return String.format("%s-%04d-%03d", siteCode, year, seq);
    }
}
