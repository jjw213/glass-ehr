package com.glassehr.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.glassehr.code.CommonCodeRepository;
import com.glassehr.employee.EmployeeImportService.Preview;
import com.glassehr.org.OrgDeptRepository;
import com.glassehr.org.OrgWriteLock;
import jakarta.validation.Validation;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SmartTransactionObject;

/**
 * 미리보기가 실제 트랜잭션 프록시를 거칠 때, {@link EmployeeService#checkCreate} 의 검사 실패가
 * 공유 트랜잭션을 rollback-only 로 만들어 UnexpectedRollbackException(500)이 나지 않는지 본다.
 * DB 없이 rollback-only 를 기록하는 테스트용 트랜잭션 관리자를 쓴다. 값은 모두 명백한 가짜 값이다.
 */
@SpringJUnitConfig(EmployeeImportTransactionTest.Config.class)
class EmployeeImportTransactionTest {

    /** rollback-only 표시를 기억하는 자원 없는 트랜잭션 관리자. 표시된 채 commit 하면 UnexpectedRollbackException. */
    static final class RecordingTxManager extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;

        static final class Tx implements SmartTransactionObject {
            boolean rollbackOnly;

            @Override
            public boolean isRollbackOnly() {
                return rollbackOnly;
            }

            @Override
            public void flush() {
            }
        }

        private final ThreadLocal<Tx> current = new ThreadLocal<>();

        @Override
        protected Object doGetTransaction() {
            Tx tx = current.get();
            return tx != null ? tx : new Tx();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return current.get() == transaction;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            current.set((Tx) transaction);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }

        @Override
        protected void doSetRollbackOnly(DefaultTransactionStatus status) {
            ((Tx) status.getTransaction()).rollbackOnly = true;
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            current.remove();
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean
        RecordingTxManager transactionManager() {
            return new RecordingTxManager();
        }

        @Bean
        CommonCodeRepository codes() {
            return mock(CommonCodeRepository.class); // 모든 코드가 '없음' → checkCreate 가 EmployeeException
        }

        @Bean
        RrnService rrns() {
            return mock(RrnService.class);
        }

        @Bean
        BizSiteRepository sites() {
            BizSiteRepository sites = mock(BizSiteRepository.class);
            BizSite site = mock(BizSite.class);
            when(site.isActive()).thenReturn(true);
            when(sites.findById("S01")).thenReturn(Optional.of(site));
            return sites;
        }

        @Bean
        EmployeeService employeeService(CommonCodeRepository codes, RrnService rrns) {
            return new EmployeeService(mock(EmployeeRepository.class), mock(EmpNoGenerator.class), codes,
                    mock(OrgDeptRepository.class), rrns, mock(RecordRetentionCalculator.class),
                    mock(OrgWriteLock.class));
        }

        @Bean
        EmployeeImportService employeeImportService(EmployeeService employees, RrnService rrns,
                BizSiteRepository sites) {
            return new EmployeeImportService(employees, rrns, sites,
                    Validation.buildDefaultValidatorFactory().getValidator(), mock(OrgWriteLock.class));
        }
    }

    @Autowired
    private EmployeeImportService svc;

    @Autowired
    private PlatformTransactionManager txManager;

    @Test
    void 검사_실패_행이_있어도_미리보기는_행_오류를_돌려주고_트랜잭션은_정상_종료된다() {
        String header = new String(EmployeeImportCsv.template(), StandardCharsets.UTF_8).replace("﻿", "").strip();
        // 사업장,성명,성별,생년월일,주소,이력,종사업무,직군,입사일,갱신일,계약시작,계약종료,고용형태,주소정,근무지,고용메모,기타,부서,직급,직책,주민
        String row = "S01,예시사원일,F,1999-01-01,가상시 예시구 샘플로 1,,개발,,2026-03-02,,,,NOPE,2400,,,,,,,";
        RecordingTxManager tm = (RecordingTxManager) txManager;
        int commitsBefore = tm.commits;

        Preview p = svc.preview((header + "\r\n" + row + "\r\n").getBytes(StandardCharsets.UTF_8));

        assertThat(p.confirmable()).isFalse();
        assertThat(p.rows()).hasSize(1);
        assertThat(p.rows().get(0).errors()).containsExactly("없는 고용형태 코드예요: NOPE");
        assertThat(tm.commits).isEqualTo(commitsBefore + 1);
        assertThat(tm.rollbacks).isZero();
    }
}
