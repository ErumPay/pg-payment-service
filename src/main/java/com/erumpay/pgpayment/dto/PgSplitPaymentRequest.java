package com.erumpay.pgpayment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record PgSplitPaymentRequest(
        @NotNull
        @Positive
        Long payPaymentId,

        @NotNull
        @Positive
        Long merchantId,

        @NotNull
        @Positive
        Long totalAmount,

        @Valid
        @NotNull
        @Size(min = 1, max = 10)
        List<PgSplitPaymentItemRequest> payments
) {

    @AssertTrue(message = "totalAmount must be equal to sum of approvedAmount.")
    public boolean isTotalAmountEqualToApprovedAmountSum() {
        if (totalAmount == null || payments == null) {
            return true;
        }
        long sum = payments.stream()
                .filter(item -> item != null && item.approvedAmount() != null)
                .mapToLong(PgSplitPaymentItemRequest::approvedAmount)
                .sum();
        return sum == totalAmount;
    }

    @AssertTrue(message = "splitSeq must be unique.")
    public boolean isSplitSeqUnique() {
        if (payments == null) {
            return true;
        }
        Set<Integer> splitSeqs = new HashSet<>();
        for (PgSplitPaymentItemRequest item : payments) {
            if (item == null || item.splitSeq() == null) {
                continue;
            }
            if (!splitSeqs.add(item.splitSeq())) {
                return false;
            }
        }
        return true;
    }
}
