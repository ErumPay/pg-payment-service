package com.erumpay.pgpayment.dto;

import com.erumpay.pgpayment.domain.enums.PgPaymentStatus;
import java.time.LocalDate;

public record PgPaymentLedgerSearchCondition(
        LocalDate from,
        LocalDate to,
        Long merchantId,
        PgPaymentStatus status,
        Long minAmount,
        Long maxAmount
) {
}
