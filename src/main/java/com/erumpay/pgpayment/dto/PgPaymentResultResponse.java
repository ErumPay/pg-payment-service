package com.erumpay.pgpayment.dto;

import com.erumpay.pgpayment.domain.entity.PgPaymentLedger;
import com.erumpay.pgpayment.domain.enums.PgPaymentStatus;
import com.erumpay.pgpayment.domain.enums.PgTxnType;
import java.time.LocalDateTime;

public record PgPaymentResultResponse(
        Long pgTxnId,
        Long payPaymentId,
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
        LocalDateTime approvedAt,
        LocalDateTime processedAt,
        LocalDateTime createdAt
) {

    public static PgPaymentResultResponse from(PgPaymentLedger ledger) {
        return new PgPaymentResultResponse(
                ledger.getPgTxnId(),
                ledger.getPayPaymentId(),
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
                ledger.getApprovedAt(),
                ledger.getProcessedAt(),
                ledger.getCreatedAt()
        );
    }
}
