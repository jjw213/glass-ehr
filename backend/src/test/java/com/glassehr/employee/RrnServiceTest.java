package com.glassehr.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.glassehr.employee.RrnAccessLog.Purpose;
import com.glassehr.employee.RrnAccessLog.Result;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 저장(평문 없음)·역할별 원문 조회·조회 로그·법인 범위(#123). 저장소는 메모리 가짜. */
class RrnServiceTest {

    private static final String A = "C001";
    private static final String B = "C002";
    private static final Rrn R1 = Rrn.parse("000000-0000001");
    private static final Rrn R2 = Rrn.parse("000000-0000002");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final Map<Long, EmpRrn> store = new HashMap<>();
    private final List<RrnAccessLog> logs = new ArrayList<>();
    private RrnService service;

    private static RequestActor actor(RequestActor.Role role) {
        return new RequestActor("tester", role, "127.0.0.1", A);
    }

    @BeforeEach
    void setUp() {
        EmpRrnRepository repo = mock(EmpRrnRepository.class);
        when(repo.findByEnterCdAndEmpId(any(), anyLong())).thenAnswer(inv -> Optional
                .ofNullable(store.get(inv.<Long>getArgument(1))).filter(r -> r.getEnterCd().equals(inv.getArgument(0))));
        when(repo.saveAndFlush(any())).thenAnswer(inv -> {
            EmpRrn r = inv.getArgument(0);
            store.put(r.getEmpId(), r);
            return r;
        });
        when(repo.existsByEnterCdAndRrnHash(any(), any())).thenAnswer(inv -> {
            String ent = inv.getArgument(0);
            byte[] h = inv.getArgument(1);
            return store.values().stream().anyMatch(r -> r.getEnterCd().equals(ent) && Arrays.equals(r.getRrnHash(), h));
        });
        when(repo.existsByEnterCdAndRrnHashAndEmpIdNot(any(), any(), any())).thenAnswer(inv -> {
            String ent = inv.getArgument(0);
            byte[] h = inv.getArgument(1);
            Long id = inv.getArgument(2);
            return store.values().stream().anyMatch(r -> r.getEnterCd().equals(ent) && !r.getEmpId().equals(id)
                    && Arrays.equals(r.getRrnHash(), h));
        });
        RrnAccessLogRepository logRepo = mock(RrnAccessLogRepository.class);
        when(logRepo.save(any())).thenAnswer(inv -> {
            logs.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        service = new RrnService(repo, RrnCryptoTest.crypto(), new RrnAccessLogger(logRepo), CLOCK);
    }

    @Test
    void 저장하면_암호문_마스킹_해시만_남고_평문은_없다() {
        assertThat(service.save(A, 1L, R1, actor(RequestActor.Role.HR))).isEqualTo("000000-*******");
        EmpRrn row = store.get(1L);
        assertThat(row.getEnterCd()).isEqualTo(A);
        assertThat(row.getRrnMasked()).isEqualTo("000000-*******");
        assertThat(new String(row.getRrnEnc(), StandardCharsets.ISO_8859_1)).doesNotContain("0000001");
        assertThat(new String(row.getRrnHash(), StandardCharsets.ISO_8859_1)).doesNotContain("0000001");
        assertThat(row.toString()).doesNotContain("0000001");
        assertThat(row.getUpdatedBy()).isEqualTo("tester");
        assertThat(service.masked(A, 1L)).contains("000000-*******");
        assertThat(logs).isEmpty(); // 저장은 원문 조회가 아니다
    }

    @Test
    void 다른_사원과_같은_번호는_거부하고_본인_정정은_허용한다() {
        service.save(A, 1L, R1, actor(RequestActor.Role.HR));
        assertThatThrownBy(() -> service.save(A, 2L, R1, actor(RequestActor.Role.HR)))
                .isInstanceOfSatisfying(EmployeeException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).doesNotContain("0000001");
                });
        service.save(A, 1L, R1, actor(RequestActor.Role.HR));
        service.save(A, 1L, R2, actor(RequestActor.Role.HR));
        assertThat(service.reveal(A, 1L, actor(RequestActor.Role.HR), Purpose.VIEW)).isEqualTo(R2);
    }

    @Test
    void 중복_검사는_법인_안에서만_한다() {
        // 전적(퇴직 + 다른 법인 신규 입사): 같은 사람이 두 법인에 하나씩 있을 수 있다(docs/tenant-design.md 3.2)
        service.save(A, 1L, R1, actor(RequestActor.Role.HR));
        assertThat(service.isRegistered(A, R1)).isTrue();
        assertThat(service.isRegistered(B, R1)).isFalse();
        assertThat(service.save(B, 2L, R1, actor(RequestActor.Role.HR))).isEqualTo("000000-*******");
        assertThat(store.get(2L).getEnterCd()).isEqualTo(B);
    }

    @Test
    void 다른_법인_사원의_번호는_마스킹도_원문도_보이지_않고_404_로그는_대상_법인으로_남는다() {
        service.save(B, 7L, R1, actor(RequestActor.Role.HR));
        assertThat(service.masked(A, 7L)).isEmpty();
        assertThatThrownBy(() -> service.reveal(A, 7L, actor(RequestActor.Role.HR), Purpose.VIEW))
                .isInstanceOfSatisfying(EmployeeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(logs).singleElement().satisfies(l -> {
            assertThat(l.getResult()).isEqualTo(Result.NOT_FOUND);
            assertThat(l.getEnterCd()).isEqualTo(A);
            assertThat(l.getAccessorEnterCd()).isEqualTo(A);
        });
    }

    @Test
    void 인사_급여_담당은_원문을_보고_한_번에_로그_한_행() {
        service.save(A, 1L, R1, actor(RequestActor.Role.HR));
        assertThat(service.reveal(A, 1L, actor(RequestActor.Role.HR), Purpose.VIEW)).isEqualTo(R1);
        assertThat(service.reveal(A, 1L, actor(RequestActor.Role.PAYROLL), Purpose.WITHHOLDING)).isEqualTo(R1);
        assertThat(logs).hasSize(2);
        RrnAccessLog first = logs.get(0);
        assertThat(first.getResult()).isEqualTo(Result.SUCCESS);
        assertThat(first.getEnterCd()).isEqualTo(A);
        assertThat(first.getAccessorEnterCd()).isEqualTo(A);
        assertThat(first.getAccessorId()).isEqualTo("tester");
        assertThat(first.getAccessorRole()).isEqualTo("HR");
        assertThat(first.getEmpId()).isEqualTo(1L);
        assertThat(first.getPurpose()).isEqualTo(Purpose.VIEW);
        assertThat(first.getClientIp()).isEqualTo("127.0.0.1");
        assertThat(first.getAccessedAt()).isEqualTo(LocalDateTime.of(2026, 11, 12, 10, 0)); // 서울 기준
        assertThat(logs.get(1).getPurpose()).isEqualTo(Purpose.WITHHOLDING);
    }

    @Test
    void 관리자_일반직원_역할없음은_거부되고_거부도_로그에_남는다() {
        service.save(A, 1L, R1, actor(RequestActor.Role.HR));
        for (RequestActor.Role role : new RequestActor.Role[] {RequestActor.Role.ADMIN, RequestActor.Role.EMPLOYEE, null}) {
            assertThatThrownBy(() -> service.reveal(A, 1L, actor(role), Purpose.VIEW))
                    .isInstanceOfSatisfying(EmployeeException.class,
                            e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
        }
        assertThat(logs).hasSize(3).allSatisfy(l -> assertThat(l.getResult()).isEqualTo(Result.DENIED));
        assertThat(logs.get(2).getAccessorRole()).isNull();
    }

    @Test
    void 등록되지_않은_사원_조회도_로그에_남는다() {
        assertThatThrownBy(() -> service.reveal(A, 5L, actor(RequestActor.Role.HR), Purpose.VIEW))
                .isInstanceOfSatisfying(EmployeeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(logs).singleElement().satisfies(l -> {
            assertThat(l.getResult()).isEqualTo(Result.NOT_FOUND);
            assertThat(l.getEmpId()).isEqualTo(5L);
        });
    }

    @Test
    void 로그_엔티티에는_주민등록번호_필드가_없다() {
        assertThat(Arrays.stream(RrnAccessLog.class.getDeclaredFields()).map(f -> f.getName().toLowerCase()))
                .noneMatch(n -> n.contains("rrn") || n.contains("masked"));
    }
}
