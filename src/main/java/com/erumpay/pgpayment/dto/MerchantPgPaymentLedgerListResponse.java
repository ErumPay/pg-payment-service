package com.erumpay.pgpayment.dto;

import java.time.LocalDate;
import java.util.List;

public record MerchantPgPaymentLedgerListResponse(
        Long merchantId,
        LocalDate from,
        LocalDate to,
        int page,
        int size,
        long totalCount,
        List<MerchantPgPaymentLedgerItemResponse> items
) {
}
