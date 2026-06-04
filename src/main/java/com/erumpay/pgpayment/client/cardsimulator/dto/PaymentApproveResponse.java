package com.erumpay.pgpayment.client.cardsimulator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PaymentApproveResponse(
        @JsonProperty("response_code")
        String responseCode,

        @JsonProperty("response_message")
        String responseMessage,

        @JsonProperty("pg_id")
        String pgId,

        @JsonProperty("pg_txn_id")
        Long pgTxnId,

        @JsonProperty("approval_number")
        String approvalNumber,

        @JsonProperty("approved_at")
        String approvedAt,

        @JsonProperty("approved_amount")
        Long approvedAmount
) {
}
