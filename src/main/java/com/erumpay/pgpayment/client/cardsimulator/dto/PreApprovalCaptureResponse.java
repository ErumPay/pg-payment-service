package com.erumpay.pgpayment.client.cardsimulator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PreApprovalCaptureResponse(
        @JsonProperty("pg_id")
        String pgId,

        @JsonProperty("idempotency_key")
        String idempotencyKey,

        @JsonProperty("pg_txn_id")
        Long pgTxnId,

        @JsonProperty("payment_status")
        String paymentStatus,

        @JsonProperty("approval_number")
        String approvalNumber,

        @JsonProperty("approved_at")
        String approvedAt,

        @JsonProperty("response_http")
        Integer responseHttp,

        @JsonProperty("response_code")
        String responseCode,

        @JsonProperty("response_reason")
        String responseReason,

        @JsonProperty("response_message")
        String responseMessage
) {
}
