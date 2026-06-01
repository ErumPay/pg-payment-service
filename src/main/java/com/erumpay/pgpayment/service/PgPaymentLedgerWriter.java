package com.erumpay.pgpayment.service;

import com.erumpay.pgpayment.domain.entity.PgPaymentLedger;
import com.erumpay.pgpayment.domain.enums.PgFailureCode;
import com.erumpay.pgpayment.domain.enums.PgPaymentStatus;
import com.erumpay.pgpayment.domain.enums.PgTxnType;
import com.erumpay.pgpayment.global.exception.ErrorCode;
import com.erumpay.pgpayment.global.exception.PgPaymentException;
import com.erumpay.pgpayment.repository.PgPaymentLedgerRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PgPaymentLedgerWriter {

    private final PgPaymentLedgerRepository pgPaymentLedgerRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgPaymentLedger createRequested(
            Long originalTxnId,
            Long payPaymentId,
            Long holdTxnId,
            String idempotencyKey,
            String billingKey,
            Long merchantId,
            Long amount,
            PgTxnType txnType,
            String cardCompany) {
        PgPaymentLedger ledger = PgPaymentLedger.builder()
                .originalTxnId(originalTxnId)
                .payPaymentId(payPaymentId)
                .holdTxnId(holdTxnId)
                .idempotencyKey(idempotencyKey)
                .billingKey(billingKey)
                .merchantId(merchantId)
                .amount(amount)
                .txnType(txnType)
                .cardCompany(cardCompany)
                .build();
        return pgPaymentLedgerRepository.saveAndFlush(ledger);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgPaymentLedger approve(
            Long pgTxnId,
            String cardCompany,
            String pgApprovalNumber,
            String cardApprovalNumber,
            LocalDateTime approvedAt) {
        PgPaymentLedger ledger = findLedger(pgTxnId);
        ledger.updateCardCompany(cardCompany);
        ledger.approve(pgApprovalNumber, cardApprovalNumber, approvedAt);
        return ledger;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgPaymentLedger reject(
            Long pgTxnId,
            String cardCompany,
            String rejectReason,
            LocalDateTime processedAt) {
        PgPaymentLedger ledger = findLedger(pgTxnId);
        ledger.updateCardCompany(cardCompany);
        ledger.reject(rejectReason, processedAt);
        return ledger;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgPaymentLedger cancel(
            Long pgTxnId,
            String pgApprovalNumber,
            String cardApprovalNumber,
            LocalDateTime processedAt) {
        PgPaymentLedger ledger = findLedger(pgTxnId);
        ledger.cancel(pgApprovalNumber, cardApprovalNumber, processedAt);
        return ledger;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgPaymentLedger voidHold(
            Long pgTxnId,
            String pgApprovalNumber,
            String cardApprovalNumber,
            LocalDateTime processedAt) {
        PgPaymentLedger ledger = findLedger(pgTxnId);
        ledger.voidHold(pgApprovalNumber, cardApprovalNumber, processedAt);
        return ledger;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgPaymentLedger fail(
            Long pgTxnId,
            String cardCompany,
            PgFailureCode failureCode,
            String failureMessage,
            LocalDateTime processedAt) {
        PgPaymentLedger ledger = findLedger(pgTxnId);
        if (cardCompany != null && !cardCompany.isBlank()) {
            ledger.updateCardCompany(cardCompany);
        }
        ledger.fail(failureCode, failureMessage, processedAt);
        return ledger;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgPaymentLedger markRecoveryRequired(Long pgTxnId, String failureMessage) {
        PgPaymentLedger ledger = findLedger(pgTxnId);
        ledger.markRecoveryRequired(failureMessage);
        return ledger;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgPaymentLedger markRecoveryRequired(
            Long pgTxnId,
            String cardCompany,
            PgFailureCode failureCode,
            String failureMessage) {
        PgPaymentLedger ledger = findLedger(pgTxnId);
        ledger.markRecoveryRequired(cardCompany, failureCode, failureMessage);
        return ledger;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PgPaymentLedger> approveRequested(
            Long pgTxnId,
            String cardCompany,
            String pgApprovalNumber,
            String cardApprovalNumber,
            LocalDateTime approvedAt) {
        PgPaymentLedger ledger = findLedger(pgTxnId);
        if (ledger.getStatus() != PgPaymentStatus.REQUESTED) {
            return Optional.empty();
        }
        if (cardCompany != null && !cardCompany.isBlank()) {
            ledger.updateCardCompany(cardCompany);
        }
        ledger.approve(pgApprovalNumber, cardApprovalNumber, approvedAt);
        return Optional.of(ledger);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PgPaymentLedger> rejectRequested(
            Long pgTxnId,
            String cardCompany,
            String rejectReason,
            LocalDateTime processedAt) {
        PgPaymentLedger ledger = findLedger(pgTxnId);
        if (ledger.getStatus() != PgPaymentStatus.REQUESTED) {
            return Optional.empty();
        }
        if (cardCompany != null && !cardCompany.isBlank()) {
            ledger.updateCardCompany(cardCompany);
        }
        ledger.reject(rejectReason, processedAt);
        return Optional.of(ledger);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PgPaymentLedger> markReconciliationUnresolved(
            Long pgTxnId,
            PgFailureCode failureCode,
            String failureMessage,
            int maxAttempts) {
        PgPaymentLedger ledger = findLedger(pgTxnId);
        if (ledger.getStatus() != PgPaymentStatus.REQUESTED) {
            return Optional.empty();
        }
        if (ledger.getRetryCount() + 1 >= maxAttempts) {
            ledger.markReconciliationRetryExhausted(failureMessage);
        } else {
            ledger.markRecoveryRequired(null, failureCode, failureMessage);
        }
        return Optional.of(ledger);
    }

    private PgPaymentLedger findLedger(Long pgTxnId) {
        return pgPaymentLedgerRepository.findById(pgTxnId)
                .orElseThrow(() -> new PgPaymentException(
                        ErrorCode.PG_PAYMENT_NOT_FOUND,
                        "PG 결제 거래를 찾을 수 없습니다. pgTxnId=" + pgTxnId));
    }
}
