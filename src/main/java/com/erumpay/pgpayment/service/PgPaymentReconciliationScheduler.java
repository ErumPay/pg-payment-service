package com.erumpay.pgpayment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "pg-payment.reconciliation.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PgPaymentReconciliationScheduler {

    private final PgPaymentReconciliationService pgPaymentReconciliationService;

    @Scheduled(
            fixedDelayString = "${pg-payment.reconciliation.fixed-delay-ms:60000}",
            initialDelayString = "${pg-payment.reconciliation.initial-delay-ms:30000}")
    public void reconcile() {
        try {
            pgPaymentReconciliationService.reconcile();
        } catch (RuntimeException exception) {
            log.error("PG payment reconciliation schedule failed.", exception);
        }
    }
}
