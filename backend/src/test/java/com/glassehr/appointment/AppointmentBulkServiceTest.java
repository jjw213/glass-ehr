package com.glassehr.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.appointment.AppointmentDtos.AppointmentRequest;
import com.glassehr.appointment.AppointmentDtos.AppointmentView;
import com.glassehr.appointment.AppointmentDtos.BulkAppointmentRequest;
import com.glassehr.appointment.AppointmentDtos.BulkAppointmentResult;
import com.glassehr.appointment.AppointmentDtos.BulkItemResult;
import com.glassehr.appointment.HrAppointment.Snapshot;
import com.glassehr.org.OrgWriteLock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

/** #60 일괄 발령: 사원마다 새 트랜잭션, 실패한 사원만 되돌리고 나머지는 계속, 사원별 결과. */
class AppointmentBulkServiceTest {

    private static final LocalDate D = LocalDate.of(2026, 12, 1);
    private static final AppointmentRequest REQ = new AppointmentRequest("TRANSFER", D, 20L, null, null, "조직 개편", null,
            null);

    private AppointmentService service;
    private PlatformTransactionManager tm;
    private OrgWriteLock orgLock;
    private AppointmentBulkService bulk;

    private static AppointmentView view(long apptId, long empId, boolean confirmed) {
        return new AppointmentView(apptId, "AP-2026-00000" + apptId, "TRANSFER", empId, D,
                new Snapshot(10L, "STAFF", null, EmpStatus.ACTIVE), new Snapshot(20L, "STAFF", null, EmpStatus.ACTIVE),
                "조직 개편", null, null, confirmed, confirmed ? LocalDateTime.of(2026, 11, 18, 9, 0) : null,
                LocalDateTime.of(2026, 11, 18, 9, 0), LocalDateTime.of(2026, 11, 18, 9, 0));
    }

    @BeforeEach
    void setUp() {
        service = mock(AppointmentService.class);
        orgLock = mock(OrgWriteLock.class);
        tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        bulk = new AppointmentBulkService(service, orgLock, tm);
    }

    @Test
    void 등록하고_확정하며_한_사원_실패는_그_사원만_되돌린다() {
        when(service.create(eq(1L), eq(REQ))).thenReturn(view(11, 1, false));
        when(service.create(eq(2L), eq(REQ))).thenReturn(view(12, 2, false));
        when(service.create(eq(3L), eq(REQ))).thenReturn(view(13, 3, false));
        when(service.confirm(11L)).thenReturn(view(11, 1, true));
        when(service.confirm(12L)).thenThrow(AppointmentException.conflict("발령일보다 뒤에 확정된 발령이 있어요"));
        when(service.confirm(13L)).thenReturn(view(13, 3, true));

        BulkAppointmentResult r = bulk.createAll(new BulkAppointmentRequest(List.of(1L, 2L, 3L), REQ, true));

        assertThat(r.succeeded()).isEqualTo(2);
        assertThat(r.failed()).isEqualTo(1);
        assertThat(r.items()).extracting(BulkItemResult::empId).containsExactly(1L, 2L, 3L);
        assertThat(r.items()).extracting(BulkItemResult::ok).containsExactly(true, false, true);
        assertThat(r.items().get(0).appointment().confirmed()).isTrue();
        assertThat(r.items().get(1).appointment()).isNull();
        assertThat(r.items().get(1).error()).isEqualTo("발령일보다 뒤에 확정된 발령이 있어요");
        // 사원마다 새 트랜잭션: 성공 2건 커밋, 실패 1건(등록까지 포함) 롤백
        verify(tm, times(3)).getTransaction(org.mockito.ArgumentMatchers.argThat(
                d -> d.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        verify(tm, times(2)).commit(any());
        verify(tm, times(1)).rollback(any());
        org.mockito.InOrder lockOrder = org.mockito.Mockito.inOrder(orgLock, service);
        lockOrder.verify(orgLock).acquire();
        lockOrder.verify(service).create(1L, REQ);
    }

    @Test
    void 확정하지_않으면_등록만_하고_같은_사원은_한_번만_처리한다() {
        when(service.create(anyLong(), eq(REQ))).thenAnswer(inv -> view(20 + inv.<Long>getArgument(0),
                inv.<Long>getArgument(0), false));

        BulkAppointmentResult r = bulk.createAll(new BulkAppointmentRequest(List.of(5L, 6L, 5L), REQ, false));

        assertThat(r.items()).extracting(BulkItemResult::empId).containsExactly(5L, 6L);
        assertThat(r.items()).allMatch(i -> i.ok() && !i.appointment().confirmed());
        verify(service, times(1)).create(5L, REQ);
        verify(service, never()).confirm(anyLong());
        verify(orgLock, never()).acquire();
    }

    @Test
    void 예상하지_못한_오류도_사원별_실패로_남기고_계속한다() {
        when(service.create(eq(1L), eq(REQ))).thenThrow(new IllegalStateException("db down"));
        when(service.create(eq(2L), eq(REQ))).thenThrow(AppointmentException.notFound("사원이 없어요: 2"));
        when(service.create(eq(3L), eq(REQ))).thenReturn(view(13, 3, false));

        BulkAppointmentResult r = bulk.createAll(new BulkAppointmentRequest(List.of(1L, 2L, 3L), REQ, false));

        assertThat(r.succeeded()).isEqualTo(1);
        assertThat(r.items().get(0).error()).contains("다시 시도").doesNotContain("db down");
        assertThat(r.items().get(1).error()).isEqualTo("사원이 없어요: 2");
        assertThat(r.items().get(2).ok()).isTrue();
    }
}
