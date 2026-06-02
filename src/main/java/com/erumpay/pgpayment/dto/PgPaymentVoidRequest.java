package com.erumpay.pgpayment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PgPaymentVoidRequest(
        @NotNull
        @Positive
        Long payPaymentId,

        @NotNull
        @Positive
        Long merchantId,

        @NotBlank
        String voidReason
) {
}
