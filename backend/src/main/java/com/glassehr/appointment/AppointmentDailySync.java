package com.glassehr.appointment;

import com.glassehr.employee.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 미래 날짜 발령 반영(#59). 매일 서울 시각 00:05(설정 {@code glassehr.appointment.sync-cron})에 모든 사원의 현재
 * 소속·직급·직책·재직상태를 오늘 기준 발령 이력으로 맞춘다. 사원마다 따로 트랜잭션을 연다.
 * {@code glassehr.appointment.sync-enabled=false}면 켜지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "glassehr.appointment.sync-enabled", havingValue = "true", matchIfMissing = true)
public class AppointmentDailySync {

    private static final Logger log = LoggerFactory.getLogger(AppointmentDailySync.class);

    private final AppointmentService service;
    private final EmployeeRepository employees;

    public AppointmentDailySync(AppointmentService service, EmployeeRepository employees) {
        this.service = service;
        this.employees = employees;
    }

    @Scheduled(cron = "${glassehr.appointment.sync-cron:0 5 0 * * *}", zone = "Asia/Seoul")
    public void run() {
        int changed = 0;
        int failed = 0;
        for (Long empId : employees.findAllEmpIds()) {
            try {
                if (service.syncEmployee(empId)) {
                    changed++;
                }
            } catch (RuntimeException ex) {
                // 한 사원 실패로 나머지를 멈추지 않는다. 사원 ID만 남긴다(개인정보 없음)
                failed++;
                log.warn("발령 반영 실패 empId={}: {}", empId, ex.getClass().getSimpleName());
            }
        }
        log.info("발령 반영 완료: 바뀐 사원 {}명, 실패 {}명", changed, failed);
    }
}
