package com.erumpay.pgpayment.dto;

import com.erumpay.pgpayment.domain.enums.PgPaymentStatus;
import java.time.LocalDateTime;

public record PgPaymentLedgerSearchCondition(
        LocalDateTime from,
        LocalDateTime to,
        Long merchantId,
        PgPaymentStatus status,
        Long minAmount,
        Long maxAmount
) {
}
