package com.erumpay.pgpayment.service;

import com.erumpay.pgpayment.client.cardsimulator.CardSimulatorResponseCodes;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PgPaymentReconciliationService {

    private static final List<PgTxnType> TARGET_TXN_TYPES = List.of(PgTxnType.AUTH, PgTxnType.AUTH_ONLY);
    private static final List<String> TARGET_FAILURE_CODES = List.of(
            PgFailureCode.LEDGER_RECOVERY_REQUIRED.getCode(),
            PgFailureCode.CARD_RESULT_UNKNOWN.getCode(),
            PgFailureCode.CARD_AUTH_ONLY_RESULT_UNKNOWN.getCode());
    private final PgPaymentLedgerRepository pgPaymentLedgerRepository;
    private final PgPaymentLedgerWriter pgPaymentLedgerWriter;
    private final PgExternalClientGateway pgExternalClientGateway;
    private final PgPaymentProperties pgPaymentProperties;
    private final PgApprovalNumberGenerator pgApprovalNumberGenerator;
    private final CardSimulatorDateTimeParser cardSimulatorDateTimeParser;

    public ReconciliationSummary reconcile() {
        PgPaymentProperties.Reconciliation config = pgPaymentProperties.getReconciliation();
        LocalDateTime updatedBefore = LocalDateTime.now().minusSeconds(config.getMinimumAgeSeconds());
        List<PgPaymentLedger> targets = pgPaymentLedgerRepository.findReconciliationTargets(
                PgPaymentStatus.REQUESTED,
                TARGET_TXN_TYPES,
                TARGET_FAILURE_CODES,
                config.getMaxAttempts(),
                updatedBefore,
                PageRequest.of(0, config.getBatchSize()));

        int recovered = 0;
        int unresolved = 0;
        int failed = 0;
        int skipped = 0;

        for (PgPaymentLedger target : targets) {
            ReconciliationOutcome outcome = reconcileTarget(target.getPgTxnId());
            switch (outcome) {
                case RECOVERED -> recovered++;
                case UNRESOLVED -> unresolved++;
                case FAILED -> failed++;
                case SKIPPED -> skipped++;
            }
        }

        ReconciliationSummary summary = new ReconciliationSummary(
                targets.size(),
                recovered,
                unresolved,
                failed,
                skipped);
        log.info(
                "PG payment reconciliation finished. targets={}, recovered={}, unresolved={}, failed={}, skipped={}",
                summary.targets(),
                summary.recovered(),
                summary.unresolved(),
                summary.failed(),
                summary.skipped());
        return summary;
    }

    private ReconciliationOutcome reconcileTarget(Long pgTxnId) {
        Optional<PgPaymentLedger> current = pgPaymentLedgerRepository.findById(pgTxnId)
                .filter(this::isRequestedTarget);
        if (current.isEmpty()) {
            return ReconciliationOutcome.SKIPPED;
        }

        PgPaymentLedger ledger = current.get();
        return switch (ledger.getTxnType()) {
            case AUTH -> reconcilePaymentAuthorization(ledger);
            case AUTH_ONLY -> reconcilePreApproval(ledger);
            case CAPTURE, CANCEL, VOID -> ReconciliationOutcome.SKIPPED;
        };
    }

    private ReconciliationOutcome reconcilePaymentAuthorization(PgPaymentLedger ledger) {
        try {
            PaymentInquireResponse response = pgExternalClientGateway.inquirePayment(
                    reconciliationAuthorization(),
                    pgPaymentProperties.getPgId(),
                    ledger.getCardCompany(),
                    ledger.getIdempotencyKey());
            if (isApproved(response.responseCode())) {
                return approvePayment(ledger, response);
            }
            if (isRejected(response.responseCode())) {
                return rejectPayment(ledger, response.responseMessage());
            }
            return markUnresolved(ledger, unresolvedPaymentMessage(response.responseCode()));
        } catch (RuntimeException exception) {
            log.warn("Payment reconciliation inquiry failed. pgTxnId={}", ledger.getPgTxnId(), exception);
            return markUnresolved(ledger, "Card payment reconciliation inquiry failed.");
        }
    }

    private ReconciliationOutcome reconcilePreApproval(PgPaymentLedger ledger) {
        try {
            PreApprovalInquireResponse response = pgExternalClientGateway.inquirePreApproval(
                    reconciliationAuthorization(),
                    pgPaymentProperties.getPgId(),
                    ledger.getCardCompany(),
                    ledger.getIdempotencyKey());
            if (isApproved(response.responseCode())) {
                return approvePreApproval(ledger, response);
            }
            if (isRejected(response.responseCode())) {
                return rejectPayment(ledger, response.responseMessage());
            }
            return markUnresolved(ledger, unresolvedPreApprovalMessage(response.responseCode()));
        } catch (RuntimeException exception) {
            log.warn("Pre-approval reconciliation inquiry failed. pgTxnId={}", ledger.getPgTxnId(), exception);
            return markUnresolved(ledger, "Card pre-approval reconciliation inquiry failed.");
        }
    }

    private ReconciliationOutcome approvePayment(PgPaymentLedger ledger, PaymentInquireResponse response) {
        String pgApprovalNumber = pgApprovalNumberGenerator.generate(PgTxnType.AUTH, ledger.getPgTxnId());
        LocalDateTime approvedAt = cardSimulatorDateTimeParser.parseOrNow(response.approvedAt());
        try {
            return pgPaymentLedgerWriter.approveRequested(
                    ledger.getPgTxnId(),
                    ledger.getCardCompany(),
                    pgApprovalNumber,
                    response.approvalNumber(),
                    approvedAt).map(recovered -> ReconciliationOutcome.RECOVERED)
                    .orElse(ReconciliationOutcome.SKIPPED);
        } catch (RuntimeException exception) {
            log.error("Failed to save reconciled payment approval. pgTxnId={}", ledger.getPgTxnId(), exception);
            return markUnresolved(ledger, "Card payment was approved, but reconciliation ledger update failed.");
        }
    }

    private ReconciliationOutcome approvePreApproval(PgPaymentLedger ledger, PreApprovalInquireResponse response) {
        String pgApprovalNumber = pgApprovalNumberGenerator.generate(PgTxnType.AUTH_ONLY, ledger.getPgTxnId());
        LocalDateTime approvedAt = cardSimulatorDateTimeParser.parseOrNow(response.preApprovedAt());
        try {
            return pgPaymentLedgerWriter.approveRequested(
                    ledger.getPgTxnId(),
                    ledger.getCardCompany(),
                    pgApprovalNumber,
                    response.preApprovalNumber(),
                    approvedAt).map(recovered -> ReconciliationOutcome.RECOVERED)
                    .orElse(ReconciliationOutcome.SKIPPED);
        } catch (RuntimeException exception) {
            log.error("Failed to save reconciled pre-approval. pgTxnId={}", ledger.getPgTxnId(), exception);
            return markUnresolved(ledger, "Card pre-approval was approved, but reconciliation ledger update failed.");
        }
    }

    private ReconciliationOutcome rejectPayment(PgPaymentLedger ledger, String rejectReason) {
        try {
            return pgPaymentLedgerWriter.rejectRequested(
                    ledger.getPgTxnId(),
                    ledger.getCardCompany(),
                    rejectReason,
                    LocalDateTime.now()).map(rejected -> ReconciliationOutcome.RECOVERED)
                    .orElse(ReconciliationOutcome.SKIPPED);
        } catch (RuntimeException exception) {
            log.error("Failed to save reconciled rejection. pgTxnId={}", ledger.getPgTxnId(), exception);
            return markUnresolved(ledger, "Card result was rejected, but reconciliation ledger update failed.");
        }
    }

    private ReconciliationOutcome markUnresolved(PgPaymentLedger ledger, String failureMessage) {
        try {
            return pgPaymentLedgerWriter.markReconciliationUnresolved(
                    ledger.getPgTxnId(),
                    PgFailureCode.fromCode(ledger.getFailureCode()).orElse(PgFailureCode.LEDGER_RECOVERY_REQUIRED),
                    failureMessage,
                    pgPaymentProperties.getReconciliation().getMaxAttempts())
                    .map(unresolved -> ReconciliationOutcome.UNRESOLVED)
                    .orElse(ReconciliationOutcome.SKIPPED);
        } catch (RuntimeException exception) {
            log.error("Failed to save unresolved reconciliation attempt. pgTxnId={}", ledger.getPgTxnId(), exception);
            return ReconciliationOutcome.FAILED;
        }
    }

    private boolean isRequestedTarget(PgPaymentLedger ledger) {
        return ledger.getStatus() == PgPaymentStatus.REQUESTED
                && TARGET_TXN_TYPES.contains(ledger.getTxnType())
                && TARGET_FAILURE_CODES.contains(ledger.getFailureCode())
                && ledger.getRetryCount() < pgPaymentProperties.getReconciliation().getMaxAttempts();
    }

    private boolean isApproved(String responseCode) {
        return CardSimulatorResponseCodes.isSuccess(responseCode);
    }

    private boolean isRejected(String responseCode) {
        return CardSimulatorResponseCodes.isPaymentRejected(responseCode);
    }

    private String reconciliationAuthorization() {
        return pgPaymentProperties.getReconciliation().getAuthorization();
    }

    private String unresolvedPaymentMessage(String responseCode) {
        return "Card payment reconciliation result is unresolved. responseCode=" + responseCode;
    }

    private String unresolvedPreApprovalMessage(String responseCode) {
        return "Card pre-approval reconciliation result is unresolved. responseCode=" + responseCode;
    }

    private enum ReconciliationOutcome {
        RECOVERED,
        UNRESOLVED,
        FAILED,
        SKIPPED
    }

    public record ReconciliationSummary(
            int targets,
            int recovered,
            int unresolved,
            int failed,
            int skipped
    ) {
    }
}
