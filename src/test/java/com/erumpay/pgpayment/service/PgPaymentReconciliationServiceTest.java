package com.erumpay.pgpayment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erumpay.pgpayment.client.cardsimulator.dto.PaymentInquireResponse;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalInquireResponse;
import com.erumpay.pgpayment.domain.entity.PgPaymentLedger;
import com.erumpay.pgpayment.domain.enums.PgFailureCode;
import com.erumpay.pgpayment.domain.enums.PgPaymentStatus;
import com.erumpay.pgpayment.domain.enums.PgTxnType;
import com.erumpay.pgpayment.global.config.PgPaymentProperties;
import com.erumpay.pgpayment.repository.PgPaymentLedgerRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PgPaymentReconciliationServiceTest {

        @Mock
        private PgPaymentLedgerRepository pgPaymentLedgerRepository;

        @Mock
        private PgPaymentLedgerWriter pgPaymentLedgerWriter;

        @Mock
        private PgExternalClientGateway pgExternalClientGateway;

        @Mock
        private PgApprovalNumberGenerator pgApprovalNumberGenerator;

        @Mock
        private CardSimulatorDateTimeParser cardSimulatorDateTimeParser;

        private PgPaymentProperties pgPaymentProperties;
        private PgPaymentReconciliationService pgPaymentReconciliationService;

        @BeforeEach
        void setUp() {
                pgPaymentProperties = new PgPaymentProperties();
                pgPaymentProperties.getReconciliation().setAuthorization("Bearer reconciliation-test-token");
                pgPaymentReconciliationService = new PgPaymentReconciliationService(
                                pgPaymentLedgerRepository,
                                pgPaymentLedgerWriter,
                                pgExternalClientGateway,
                                pgPaymentProperties,
                                pgApprovalNumberGenerator,
                                cardSimulatorDateTimeParser);
        }

        @Test
        void recoversApprovedAuthFromCardInquiry() {
                PgPaymentLedger ledger = requestedLedger(
                                10L,
                                PgTxnType.AUTH,
                                PgFailureCode.CARD_RESULT_UNKNOWN.getCode());
                LocalDateTime approvedAt = LocalDateTime.of(2026, 5, 22, 10, 30);

                stubTargets(ledger);
                when(pgExternalClientGateway.inquirePayment(
                                "Bearer reconciliation-test-token",
                                "001",
                                "SHINHAN",
                                "reconciliation-10")).thenReturn(new PaymentInquireResponse(
                                                "SIM-PAYMENT-300",
                                                "SUCCESS",
                                                "001",
                                                10L,
                                                "CARD-AUTH-10",
                                                "20260522103000",
                                                40000L));
                when(pgApprovalNumberGenerator.generate(PgTxnType.AUTH, 10L)).thenReturn("PG20260522000010");
                when(cardSimulatorDateTimeParser.parseOrNow("20260522103000")).thenReturn(approvedAt);
                when(pgPaymentLedgerWriter.approveRequested(
                                10L,
                                "SHINHAN",
                                "PG20260522000010",
                                "CARD-AUTH-10",
                                approvedAt)).thenReturn(Optional.of(ledger));

                PgPaymentReconciliationService.ReconciliationSummary summary = pgPaymentReconciliationService
                                .reconcile();

                assertThat(summary.targets()).isEqualTo(1);
                assertThat(summary.recovered()).isEqualTo(1);
                assertThat(summary.unresolved()).isZero();
                verify(pgPaymentLedgerWriter).approveRequested(
                                10L,
                                "SHINHAN",
                                "PG20260522000010",
                                "CARD-AUTH-10",
                                approvedAt);
        }

        @Test
        void recoversRejectedAuthOnlyFromCardInquiry() {
                PgPaymentLedger ledger = requestedLedger(
                                20L,
                                PgTxnType.AUTH_ONLY,
                                PgFailureCode.CARD_AUTH_ONLY_RESULT_UNKNOWN.getCode());

                stubTargets(ledger);
                when(pgExternalClientGateway.inquirePreApproval(
                                "Bearer reconciliation-test-token",
                                "001",
                                "SHINHAN",
                                "reconciliation-20")).thenReturn(new PreApprovalInquireResponse(
                                                "SIM-PAYMENT-302",
                                                "PAYMENT_REJECTED",
                                                "001",
                                                20L,
                                                null,
                                                null,
                                                null,
                                                40000L));
                when(pgPaymentLedgerWriter.rejectRequested(
                                eq(20L),
                                eq("SHINHAN"),
                                eq("PAYMENT_REJECTED"),
                                any(LocalDateTime.class))).thenReturn(Optional.of(ledger));

                PgPaymentReconciliationService.ReconciliationSummary summary = pgPaymentReconciliationService
                                .reconcile();

                assertThat(summary.targets()).isEqualTo(1);
                assertThat(summary.recovered()).isEqualTo(1);
                assertThat(summary.failed()).isZero();
                verify(pgPaymentLedgerWriter).rejectRequested(
                                eq(20L),
                                eq("SHINHAN"),
                                eq("PAYMENT_REJECTED"),
                                any(LocalDateTime.class));
        }

        private void stubTargets(PgPaymentLedger ledger) {
                when(pgPaymentLedgerRepository.findReconciliationTargets(
                                eq(PgPaymentStatus.REQUESTED),
                                anyList(),
                                anyList(),
                                eq(5),
                                any(LocalDateTime.class),
                                any(Pageable.class))).thenReturn(List.of(ledger));
                when(pgPaymentLedgerRepository.findById(ledger.getPgTxnId())).thenReturn(Optional.of(ledger));
        }

        private PgPaymentLedger requestedLedger(Long pgTxnId, PgTxnType txnType, String failureCode) {
                return PgPaymentLedger.builder()
                                .pgTxnId(pgTxnId)
                                .payPaymentId(10000L + pgTxnId)
                                .idempotencyKey("reconciliation-" + pgTxnId)
                                .billingKey("billing-key-" + pgTxnId)
                                .merchantId(3001L)
                                .amount(40000L)
                                .txnType(txnType)
                                .status(PgPaymentStatus.REQUESTED)
                                .cardCompany("SHINHAN")
                                .failureCode(failureCode)
                                .retryCount(1)
                                .build();
        }
}
