package com.erumpay.pgpayment.dto;

import jakarta.validation.constraints.AssertTrue;
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
        Long originalAmount,

        @Positive
        Long approvedAmount
) {

    @AssertTrue(message = "approvedAmount must be less than or equal to originalAmount.")
    public boolean isApprovedAmountLessThanOrEqualToOriginalAmount() {
        return originalAmount == null || approvedAmount == null || approvedAmount <= originalAmount;
    }
}
