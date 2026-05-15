package com.erumpay.pgpayment.dto;

import com.erumpay.pgpayment.domain.entity.PgPaymentLedger;
import com.erumpay.pgpayment.domain.enums.PgPaymentStatus;
import com.erumpay.pgpayment.domain.enums.PgTxnType;
import java.time.LocalDateTime;

public record PgPaymentDetailResponse(
        Long pgTxnId,
        Long originalTxnId,
        Long payPaymentId,
        Long holdTxnId,
        String idempotencyKey,
        String billingKeyMasked,
        Long merchantId,
        Long amount,
        PgTxnType txnType,
        PgPaymentStatus status,
        String pgApprovalNumber,
        String cardCompany,
        String cardApprovalNumber,
        String rejectReason,
        String failureCode,
        String failureMessage,
        Integer retryCount,
        LocalDateTime approvedAt,
        LocalDateTime processedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static PgPaymentDetailResponse from(PgPaymentLedger ledger) {
        return new PgPaymentDetailResponse(
                ledger.getPgTxnId(),
                ledger.getOriginalTxnId(),
                ledger.getPayPaymentId(),
                ledger.getHoldTxnId(),
                ledger.getIdempotencyKey(),
                mask(ledger.getBillingKey()),
                ledger.getMerchantId(),
                ledger.getAmount(),
                ledger.getTxnType(),
                ledger.getStatus(),
                ledger.getPgApprovalNumber(),
                ledger.getCardCompany(),
                ledger.getCardApprovalNumber(),
                ledger.getRejectReason(),
                ledger.getFailureCode(),
                ledger.getFailureMessage(),
                ledger.getRetryCount(),
                ledger.getApprovedAt(),
                ledger.getProcessedAt(),
                ledger.getCreatedAt(),
                ledger.getUpdatedAt()
        );
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        int visibleLength = Math.min(4, value.length());
        return "*".repeat(Math.max(0, value.length() - visibleLength))
                + value.substring(value.length() - visibleLength);
    }
}
