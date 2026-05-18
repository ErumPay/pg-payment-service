package com.erumpay.pgpayment.client.cardsimulator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PreApprovalCancelResponse(
        @JsonProperty("response_code")
        Integer responseCode,

        @JsonProperty("response_message")
        String responseMessage,

        @JsonProperty("pg_id")
        String pgId,

        @JsonProperty("pg_txn_id")
        Long pgTxnId,

        @JsonProperty("pre_approval_number")
        String preApprovalNumber,

        @JsonProperty("cancelled_at")
        String cancelledAt
) {
}
