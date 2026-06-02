package com.erumpay.pgpayment.dto;

import com.erumpay.pgpayment.domain.entity.PgPaymentLedger;
import com.erumpay.pgpayment.domain.enums.PgFailureCode;
import com.erumpay.pgpayment.domain.enums.PgPaymentStatus;
import com.erumpay.pgpayment.domain.enums.PgTxnType;
import java.time.LocalDateTime;

public record PgPaymentResultResponse(
        Long pgTxnId,
        Long originalTxnId,
        Long payPaymentId,
        Long merchantId,
        PgTxnType txnType,
        PgPaymentStatus status,
        Long amount,
        String pgApprovalNumber,
        String cardApprovalNumber,
        String rejectReason,
        String failureCode,
        String failureReason,
        String failureMessage,
        LocalDateTime approvedAt,
        LocalDateTime processedAt
) {

    public static PgPaymentResultResponse from(PgPaymentLedger ledger) {
        return new PgPaymentResultResponse(
                ledger.getPgTxnId(),
                ledger.getOriginalTxnId(),
                ledger.getPayPaymentId(),
                ledger.getMerchantId(),
                ledger.getTxnType(),
                ledger.getStatus(),
                ledger.getAmount(),
                ledger.getPgApprovalNumber(),
                ledger.getCardApprovalNumber(),
                ledger.getRejectReason(),
                ledger.getFailureCode(),
                failureReason(ledger.getFailureCode()),
                ledger.getFailureMessage(),
                ledger.getApprovedAt(),
                ledger.getProcessedAt()
        );
    }

    private static String failureReason(String failureCode) {
        return PgFailureCode.fromCode(failureCode)
                .map(PgFailureCode::getReason)
                .orElse(null);
    }
}
