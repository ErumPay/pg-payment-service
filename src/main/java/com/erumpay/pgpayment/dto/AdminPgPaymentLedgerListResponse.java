package com.erumpay.pgpayment.dto;

import java.time.LocalDate;
import java.util.List;

public record AdminPgPaymentLedgerListResponse(
        LocalDate from,
        LocalDate to,
        int page,
        int size,
        long totalCount,
        List<AdminPgPaymentLedgerItemResponse> items
) {
}
