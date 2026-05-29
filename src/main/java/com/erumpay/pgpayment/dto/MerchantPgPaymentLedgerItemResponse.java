package com.erumpay.pgpayment.dto;

import com.erumpay.pgpayment.domain.entity.PgPaymentLedger;
import com.erumpay.pgpayment.domain.enums.PgPaymentStatus;
import com.erumpay.pgpayment.domain.enums.PgTxnType;
import java.time.LocalDateTime;

public record MerchantPgPaymentLedgerItemResponse(
        Long pgTxnId,
        Long originalTxnId,
        Long payPaymentId,
        Long amount,
        PgTxnType txnType,
        PgPaymentStatus status,
        String pgApprovalNumber,
        String cardApprovalNumber,
        LocalDateTime processedAt
) {

    public static MerchantPgPaymentLedgerItemResponse from(PgPaymentLedger ledger) {
        return new MerchantPgPaymentLedgerItemResponse(
                ledger.getPgTxnId(),
                ledger.getOriginalTxnId(),
                ledger.getPayPaymentId(),
                ledger.getAmount(),
                ledger.getTxnType(),
                ledger.getStatus(),
                ledger.getPgApprovalNumber(),
                ledger.getCardApprovalNumber(),
                ledger.getProcessedAt()
        );
    }
}
