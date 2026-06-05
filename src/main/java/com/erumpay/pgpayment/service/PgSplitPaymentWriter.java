package com.erumpay.pgpayment.service;

import com.erumpay.pgpayment.domain.entity.PgPaymentGroup;
import com.erumpay.pgpayment.domain.entity.PgPaymentLedger;
import com.erumpay.pgpayment.domain.enums.PgFailureCode;
import com.erumpay.pgpayment.domain.enums.PgPaymentGroupStatus;
import com.erumpay.pgpayment.domain.enums.PgTxnType;
import com.erumpay.pgpayment.repository.PgPaymentGroupRepository;
import com.erumpay.pgpayment.repository.PgPaymentLedgerRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PgSplitPaymentWriter {

    private static final String UNKNOWN_CARD_COMPANY = "UNKNOWN";

    private final PgPaymentGroupRepository pgPaymentGroupRepository;
    private final PgPaymentLedgerRepository pgPaymentLedgerRepository;
    private final PgInternalIdempotencyKeyGenerator idempotencyKeyGenerator;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgPaymentGroup createGroup(
            Long payPaymentId,
            Long merchantId,
            String idempotencyKey,
            Long totalAmount) {
        PgPaymentGroup group = PgPaymentGroup.builder()
                .payPaymentId(payPaymentId)
                .merchantId(merchantId)
                .idempotencyKey(idempotencyKey)
                .totalAmount(totalAmount)
                .build();
        return pgPaymentGroupRepository.saveAndFlush(group);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgPaymentLedger createLedgerWithPaymentKey(
            Long pgGroupId,
            Integer splitSeq,
            Long originalTxnId,
            Long payPaymentId,
            String billingKey,
            Long merchantId,
            Long amount,
            PgTxnType txnType,
            String cardCompany) {
        PgPaymentLedger ledger = PgPaymentLedger.builder()
                .pgGroupId(pgGroupId)
                .splitSeq(splitSeq)
                .originalTxnId(originalTxnId)
                .payPaymentId(payPaymentId)
                .idempotencyKey("pg:payment:pending:" + UUID.randomUUID())
                .billingKey(billingKey)
                .merchantId(merchantId)
                .amount(amount)
                .txnType(txnType)
                .cardCompany(cardCompany == null || cardCompany.isBlank() ? UNKNOWN_CARD_COMPANY : cardCompany)
                .build();
        PgPaymentLedger saved = pgPaymentLedgerRepository.saveAndFlush(ledger);
        saved.updateIdempotencyKey(idempotencyKeyGenerator.paymentKey(saved.getPgTxnId()));
        return pgPaymentLedgerRepository.saveAndFlush(saved);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgPaymentLedger createLedgerWithCancelKey(
            Long pgGroupId,
            Integer splitSeq,
            Long originalTxnId,
            Long payPaymentId,
            String billingKey,
            Long merchantId,
            Long amount,
            PgTxnType txnType,
            String cardCompany) {
        PgPaymentLedger ledger = PgPaymentLedger.builder()
                .pgGroupId(pgGroupId)
                .splitSeq(splitSeq)
                .originalTxnId(originalTxnId)
                .payPaymentId(payPaymentId)
                .idempotencyKey("pg:cancel:pending:" + UUID.randomUUID())
                .billingKey(billingKey)
                .merchantId(merchantId)
                .amount(amount)
                .txnType(txnType)
                .cardCompany(cardCompany == null || cardCompany.isBlank() ? UNKNOWN_CARD_COMPANY : cardCompany)
                .build();
        PgPaymentLedger saved = pgPaymentLedgerRepository.saveAndFlush(ledger);
        saved.updateIdempotencyKey(idempotencyKeyGenerator.cancelKey(saved.getPgTxnId()));
        return pgPaymentLedgerRepository.saveAndFlush(saved);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgPaymentLedger hold(
            Long pgTxnId,
            String cardCompany,
            String pgApprovalNumber,
            String cardApprovalNumber,
            LocalDateTime approvedAt) {
        PgPaymentLedger ledger = pgPaymentLedgerRepository.findById(pgTxnId).orElseThrow();
        ledger.updateCardCompany(cardCompany);
        ledger.hold(pgApprovalNumber, cardApprovalNumber, approvedAt);
        return ledger;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgPaymentLedger capture(
            Long pgTxnId,
            String pgApprovalNumber,
            String cardApprovalNumber,
            LocalDateTime processedAt) {
        PgPaymentLedger ledger = pgPaymentLedgerRepository.findById(pgTxnId).orElseThrow();
        ledger.capture(pgApprovalNumber, cardApprovalNumber, processedAt);
        return ledger;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgPaymentLedger reject(Long pgTxnId, String cardCompany, String rejectReason, LocalDateTime processedAt) {
        PgPaymentLedger ledger = pgPaymentLedgerRepository.findById(pgTxnId).orElseThrow();
        ledger.updateCardCompany(cardCompany);
        ledger.reject(rejectReason, processedAt);
        return ledger;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgPaymentLedger fail(
            Long pgTxnId,
            String cardCompany,
            PgFailureCode failureCode,
            String failureMessage,
            LocalDateTime processedAt) {
        PgPaymentLedger ledger = pgPaymentLedgerRepository.findById(pgTxnId).orElseThrow();
        if (cardCompany != null && !cardCompany.isBlank()) {
            ledger.updateCardCompany(cardCompany);
        }
        ledger.fail(failureCode, failureMessage, processedAt);
        return ledger;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgPaymentLedger markRecoveryRequired(Long pgTxnId, String cardCompany, PgFailureCode failureCode,
            String failureMessage) {
        PgPaymentLedger ledger = pgPaymentLedgerRepository.findById(pgTxnId).orElseThrow();
        ledger.markRecoveryRequired(cardCompany, failureCode, failureMessage);
        return ledger;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgPaymentLedger markCompensationRequired(Long pgTxnId, PgFailureCode failureCode, String failureMessage) {
        PgPaymentLedger ledger = pgPaymentLedgerRepository.findById(pgTxnId).orElseThrow();
        ledger.markCompensationRequired(failureCode, failureMessage);
        return ledger;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgPaymentLedger cancel(Long pgTxnId, String pgApprovalNumber, String cardApprovalNumber,
            LocalDateTime processedAt) {
        PgPaymentLedger ledger = pgPaymentLedgerRepository.findById(pgTxnId).orElseThrow();
        ledger.cancel(pgApprovalNumber, cardApprovalNumber, processedAt);
        return ledger;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgPaymentLedger voidHold(Long pgTxnId, String pgApprovalNumber, String cardApprovalNumber,
            LocalDateTime processedAt) {
        PgPaymentLedger ledger = pgPaymentLedgerRepository.findById(pgTxnId).orElseThrow();
        ledger.voidHold(pgApprovalNumber, cardApprovalNumber, processedAt);
        return ledger;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgPaymentGroup changeGroupStatus(Long pgGroupId, PgPaymentGroupStatus status) {
        PgPaymentGroup group = pgPaymentGroupRepository.findById(pgGroupId).orElseThrow();
        group.changeStatus(status);
        return group;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgPaymentGroup rejectGroup(Long pgGroupId, String rejectReason) {
        PgPaymentGroup group = pgPaymentGroupRepository.findById(pgGroupId).orElseThrow();
        group.reject(rejectReason);
        return group;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgPaymentGroup failGroup(Long pgGroupId, PgFailureCode failureCode, String failureMessage) {
        PgPaymentGroup group = pgPaymentGroupRepository.findById(pgGroupId).orElseThrow();
        group.fail(failureCode, failureMessage);
        return group;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgPaymentGroup markGroupRecoveryRequired(Long pgGroupId, PgFailureCode failureCode, String failureMessage) {
        PgPaymentGroup group = pgPaymentGroupRepository.findById(pgGroupId).orElseThrow();
        group.markRecoveryRequired(failureCode, failureMessage);
        return group;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgPaymentGroup markGroupCompensationRequired(Long pgGroupId, PgFailureCode failureCode,
            String failureMessage) {
        PgPaymentGroup group = pgPaymentGroupRepository.findById(pgGroupId).orElseThrow();
        group.markCompensationRequired(failureCode, failureMessage);
        return group;
    }
}
