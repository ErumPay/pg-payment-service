package com.erumpay.pgpayment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record PgPaymentAuthOnlyRequest(
        @Positive
        Long payPaymentId,

        @Positive
        Long merchantId,

        @NotBlank
        String billingKey,

        @Positive
        Long amount
) {
}
