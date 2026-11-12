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

/** 저장(평문 없음)·역할별 원문 조회·조회 로그. 저장소는 메모리 가짜. */
class RrnServiceTest {

    private static final Rrn R1 = Rrn.parse("000000-0000001");
    private static final Rrn R2 = Rrn.parse("000000-0000002");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final Map<Long, EmpRrn> store = new HashMap<>();
    private final List<RrnAccessLog> logs = new ArrayList<>();
    private RrnService service;

    private static RequestActor actor(RequestActor.Role role) {
        return new RequestActor("tester", role, "127.0.0.1");
    }

    @BeforeEach
    void setUp() {
        EmpRrnRepository repo = mock(EmpRrnRepository.class);
        when(repo.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.<Long>getArgument(0))));
        when(repo.saveAndFlush(any())).thenAnswer(inv -> {
            EmpRrn r = inv.getArgument(0);
            store.put(r.getEmpId(), r);
            return r;
        });
        when(repo.existsByRrnHashAndEmpIdNot(any(), any())).thenAnswer(inv -> {
            byte[] h = inv.getArgument(0);
            Long id = inv.getArgument(1);
            return store.values().stream().anyMatch(r -> !r.getEmpId().equals(id) && Arrays.equals(r.getRrnHash(), h));
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
        assertThat(service.save(1L, R1, actor(RequestActor.Role.HR))).isEqualTo("000000-*******");
        EmpRrn row = store.get(1L);
        assertThat(row.getRrnMasked()).isEqualTo("000000-*******");
        assertThat(new String(row.getRrnEnc(), StandardCharsets.ISO_8859_1)).doesNotContain("0000001");
        assertThat(new String(row.getRrnHash(), StandardCharsets.ISO_8859_1)).doesNotContain("0000001");
        assertThat(row.toString()).doesNotContain("0000001");
        assertThat(row.getUpdatedBy()).isEqualTo("tester");
        assertThat(service.masked(1L)).contains("000000-*******");
        assertThat(logs).isEmpty(); // 저장은 원문 조회가 아니다
    }

    @Test
    void 다른_사원과_같은_번호는_거부하고_본인_정정은_허용한다() {
        service.save(1L, R1, actor(RequestActor.Role.HR));
        assertThatThrownBy(() -> service.save(2L, R1, actor(RequestActor.Role.HR)))
                .isInstanceOfSatisfying(EmployeeException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).doesNotContain("0000001");
                });
        service.save(1L, R1, actor(RequestActor.Role.HR));
        service.save(1L, R2, actor(RequestActor.Role.HR));
        assertThat(service.reveal(1L, actor(RequestActor.Role.HR), Purpose.VIEW)).isEqualTo(R2);
    }

    @Test
    void 인사_급여_담당은_원문을_보고_한_번에_로그_한_행() {
        service.save(1L, R1, actor(RequestActor.Role.HR));
        assertThat(service.reveal(1L, actor(RequestActor.Role.HR), Purpose.VIEW)).isEqualTo(R1);
        assertThat(service.reveal(1L, actor(RequestActor.Role.PAYROLL), Purpose.WITHHOLDING)).isEqualTo(R1);
        assertThat(logs).hasSize(2);
        RrnAccessLog first = logs.get(0);
        assertThat(first.getResult()).isEqualTo(Result.SUCCESS);
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
        service.save(1L, R1, actor(RequestActor.Role.HR));
        for (RequestActor.Role role : new RequestActor.Role[] {RequestActor.Role.ADMIN, RequestActor.Role.EMPLOYEE, null}) {
            assertThatThrownBy(() -> service.reveal(1L, actor(role), Purpose.VIEW))
                    .isInstanceOfSatisfying(EmployeeException.class,
                            e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
        }
        assertThat(logs).hasSize(3).allSatisfy(l -> assertThat(l.getResult()).isEqualTo(Result.DENIED));
        assertThat(logs.get(2).getAccessorRole()).isNull();
    }

    @Test
    void 등록되지_않은_사원_조회도_로그에_남는다() {
        assertThatThrownBy(() -> service.reveal(5L, actor(RequestActor.Role.HR), Purpose.VIEW))
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
