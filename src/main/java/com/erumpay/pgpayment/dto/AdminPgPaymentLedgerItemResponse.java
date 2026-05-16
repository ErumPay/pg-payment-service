package com.erumpay.pgpayment.dto;

import com.erumpay.pgpayment.domain.entity.PgPaymentLedger;
import com.erumpay.pgpayment.domain.enums.PgPaymentStatus;
import com.erumpay.pgpayment.domain.enums.PgTxnType;
import java.time.LocalDateTime;

public record AdminPgPaymentLedgerItemResponse(
        Long pgTxnId,
        Long originalTxnId,
        Long payPaymentId,
        Long merchantId,
        Long amount,
        PgTxnType txnType,
        PgPaymentStatus status,
        String pgApprovalNumber,
        String cardApprovalNumber,
        LocalDateTime processedAt
) {

    public static AdminPgPaymentLedgerItemResponse from(PgPaymentLedger ledger) {
        return new AdminPgPaymentLedgerItemResponse(
                ledger.getPgTxnId(),
                ledger.getOriginalTxnId(),
                ledger.getPayPaymentId(),
                ledger.getMerchantId(),
                ledger.getAmount(),
                ledger.getTxnType(),
                ledger.getStatus(),
                ledger.getPgApprovalNumber(),
                ledger.getCardApprovalNumber(),
                ledger.getProcessedAt()
        );
    }
}
