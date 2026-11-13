package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.attendance.LaborAgreementDtos.AgreementRequest;
import com.glassehr.attendance.LaborAgreementDtos.AgreementView;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oracle 실DB에서 V019·JPA 매핑(ddl-auto: validate)·DB 제약을 검증한다. 실행 조건: {@code ORACLE_IT=true}. 각 테스트는 롤백한다.
 * 다른 데이터와 겹치지 않게 아주 먼 과거 날짜를 쓴다.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class LaborAgreementOracleTest {

    private static final LocalDate D0 = LocalDate.of(1903, 1, 1);

    @Autowired
    private LaborAgreementService service;

    @Autowired
    private LaborAgreementGuard guard;

    @Autowired
    private JdbcTemplate jdbc;

    private AgreementView create(AgreementType type, LocalDate from, LocalDate until) {
        return service.create(new AgreementRequest(type, "IT 합의서", from, from, until, LaborAgreement.Scope.ALL,
                null, "IT 대상", LaborAgreement.RepType.MAJORITY_UNION, "가상노동조합IT", null, null), "it-hr");
    }

    @Test
    void 저장_첨부_확인이_된다() {
        AgreementView v = create(AgreementType.HOLIDAY_SUBSTITUTE, D0, D0.plusMonths(1));
        AgreementFileView f = service.addFile(v.agreementId(), "a.pdf", "%PDF-1.7 it".getBytes(), "it-hr");
        assertThat(service.get(v.agreementId()).files()).singleElement()
                .satisfies(x -> assertThat(x.fileId()).isEqualTo(f.fileId()));
        assertThat(service.file(v.agreementId(), f.fileId()).getContent()).isEqualTo("%PDF-1.7 it".getBytes());
        assertThat(guard.check(AgreementType.HOLIDAY_SUBSTITUTE, D0, D0.plusMonths(1), null).agreementId())
                .isEqualTo(v.agreementId());
        assertThat(guard.check(AgreementType.HOLIDAY_SUBSTITUTE, D0, D0.plusMonths(1).plusDays(1), null).ok())
                .isFalse();
    }

    @Test
    void DB는_거꾸로_된_기간과_대표_구분_불일치와_공백_제목을_거부한다() {
        AgreementView v = create(AgreementType.COMPENSATORY_LEAVE, D0, null);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE labor_agreement SET valid_until = DATE '1902-12-31' WHERE agreement_id = ?", v.agreementId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE labor_agreement SET rep_type = 'MAJORITY_PERSON' WHERE agreement_id = ?", v.agreementId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE labor_agreement SET title = '   ' WHERE agreement_id = ?", v.agreementId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE labor_agreement SET agreement_type = 'NOPE' WHERE agreement_id = ?", v.agreementId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
