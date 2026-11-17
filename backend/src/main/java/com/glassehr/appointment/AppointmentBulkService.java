package com.glassehr.appointment;

import com.glassehr.appointment.AppointmentDtos.AppointmentView;
import com.glassehr.appointment.AppointmentDtos.BulkAppointmentRequest;
import com.glassehr.appointment.AppointmentDtos.BulkAppointmentResult;
import com.glassehr.appointment.AppointmentDtos.BulkItemResult;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 일괄 발령(#60). 여러 사원에게 같은 유형·발령일·이후 값·사유로 발령을 등록하고, 요청하면 바로 확정한다.
 * 사원마다 별도 트랜잭션에서 {@link AppointmentService#create}·{@link AppointmentService#confirm}을 그대로 불러
 * 단건 발령과 같은 규칙(사유 필수 — 근로기준법 제23조 제1항, 유형별 검사, 확정 순서·조직 잠금)을 쓴다.
 * 한 사원이 실패해도 그 사원의 등록·확정만 되돌리고 나머지는 계속 처리해 사원별 결과를 돌려준다.
 */
@Service
public class AppointmentBulkService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentBulkService.class);

    private final AppointmentService service;
    private final TransactionTemplate tx;

    public AppointmentBulkService(AppointmentService service, PlatformTransactionManager txManager) {
        this.service = service;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 같은 사원이 두 번 오면 한 번만 처리한다(요청 순서 유지). 이 메서드 자체는 트랜잭션 밖에서 돈다. */
    public BulkAppointmentResult createAll(BulkAppointmentRequest req) {
        List<BulkItemResult> items = new ArrayList<>();
        for (Long empId : new LinkedHashSet<>(req.empIds())) {
            if (empId == null) {
                continue;
            }
            try {
                AppointmentView v = tx.execute(status -> {
                    AppointmentView created = service.create(empId, req.appointment());
                    return req.confirm() ? service.confirm(created.apptId()) : created;
                });
                items.add(new BulkItemResult(empId, true, v, null));
            } catch (AppointmentException e) {
                items.add(new BulkItemResult(empId, false, null, e.getMessage()));
            } catch (RuntimeException e) {
                log.warn("일괄 발령 처리 실패 empId={}", empId, e);
                items.add(new BulkItemResult(empId, false, null, "처리하지 못했어요. 잠시 뒤 이 사원만 다시 시도해 주세요"));
            }
        }
        int ok = (int) items.stream().filter(BulkItemResult::ok).count();
        return new BulkAppointmentResult(ok, items.size() - ok, items);
    }
}
