package com.glassehr.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 사번 형식·순번 규칙(#9). 동시 등록 시 잠금 동작은 EmployeeOracleTest 에서 본다. */
class EmpNoGeneratorTest {

    private final Map<String, BizSite> siteStore = new HashMap<>();
    private final Map<String, EmpNoSeq> seqStore = new HashMap<>();
    private EmpNoGenerator gen;

    @BeforeEach
    void setUp() {
        BizSiteRepository sites = mock(BizSiteRepository.class);
        when(sites.findForUpdate(anyString())).thenAnswer(inv -> Optional.ofNullable(siteStore.get(inv.<String>getArgument(0))));
        EmpNoSeqRepository seqs = mock(EmpNoSeqRepository.class);
        when(seqs.findForUpdate(anyString(), anyInt()))
                .thenAnswer(inv -> Optional.ofNullable(seqStore.get(inv.getArgument(0) + "/" + inv.getArgument(1))));
        when(seqs.saveAndFlush(any())).thenAnswer(inv -> {
            EmpNoSeq s = inv.getArgument(0);
            seqStore.put(s.getSiteCode() + "/" + s.getHireYear(), s);
            return s;
        });
        siteStore.put("S01", new BizSite("S01", "예시", true));
        siteStore.put("S02", new BizSite("S02", "예시2", true));
        siteStore.put("S09", new BizSite("S09", "중지", false));
        gen = new EmpNoGenerator(sites, seqs);
    }

    @Test
    void 사업장_입사연도별로_001부터_차례로() {
        assertThat(gen.next("S01", LocalDate.of(2026, 1, 2))).isEqualTo("S01-2026-001");
        assertThat(gen.next("S01", LocalDate.of(2026, 12, 31))).isEqualTo("S01-2026-002");
        assertThat(gen.next("S01", LocalDate.of(2027, 1, 1))).isEqualTo("S01-2027-001");
        assertThat(gen.next("S02", LocalDate.of(2026, 5, 1))).isEqualTo("S02-2026-001");
        assertThat(gen.next("S01", LocalDate.of(2026, 6, 1))).isEqualTo("S01-2026-003");
    }

    @Test
    void 사번은_DB_CHECK과_같은_형식이고_12자다() {
        String no = gen.next("S01", LocalDate.of(2026, 1, 2));
        assertThat(no).matches(EmpNoGenerator.EMP_NO_PATTERN).hasSize(12);
        assertThat(EmpNoGenerator.format("S01", 2026, 7)).isEqualTo("S01-2026-007");
    }

    @Test
    void 순번이_999를_넘으면_거부한다() {
        seqStore.put("S01/2026", new EmpNoSeq("S01", 2026, 998));
        assertThat(gen.next("S01", LocalDate.of(2026, 1, 2))).isEqualTo("S01-2026-999");
        assertThatThrownBy(() -> gen.next("S01", LocalDate.of(2026, 1, 2)))
                .isInstanceOfSatisfying(EmployeeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void 없는_사업장과_사용중지_사업장은_거부한다() {
        assertThatThrownBy(() -> gen.next("S05", LocalDate.of(2026, 1, 2))).isInstanceOf(EmployeeException.class);
        assertThatThrownBy(() -> gen.next("S09", LocalDate.of(2026, 1, 2))).isInstanceOf(EmployeeException.class);
        assertThat(seqStore).isEmpty();
    }
}
