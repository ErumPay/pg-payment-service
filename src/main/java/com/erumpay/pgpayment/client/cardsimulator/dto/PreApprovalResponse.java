package com.erumpay.pgpayment.client.cardsimulator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PreApprovalResponse(
        @JsonProperty("response_code")
        Integer responseCode,

        @JsonProperty("response_message")
        String responseMessage,

        @JsonProperty("pg_id")
        String pgId,

        @JsonProperty("pg_txn_id")
        Long pgTxnId,

        @JsonProperty("pre_approval_id")
        Long preApprovalId,

        @JsonProperty("pre_approval_number")
        String preApprovalNumber,

        @JsonProperty("pre_approved_at")
        String preApprovedAt,

        @JsonProperty("approved_amount")
        Long approvedAmount
) {
}
