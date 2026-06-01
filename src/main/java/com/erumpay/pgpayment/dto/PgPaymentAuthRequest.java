package com.erumpay.pgpayment.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PgPaymentAuthRequest(
        @NotNull
        @Positive
        Long payPaymentId,

        @NotNull
        @Positive
        Long merchantId,

        @NotBlank
        String billingKey,

        @NotNull
        @Positive
        Long originalAmount,

        @NotNull
        @Positive
        Long approvedAmount
) {

    @AssertTrue(message = "approvedAmount must be less than or equal to originalAmount.")
    public boolean isApprovedAmountLessThanOrEqualToOriginalAmount() {
        return originalAmount == null || approvedAmount == null || approvedAmount <= originalAmount;
    }
}
