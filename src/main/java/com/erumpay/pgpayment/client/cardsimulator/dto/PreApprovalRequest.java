package com.erumpay.pgpayment.client.cardsimulator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PreApprovalRequest(
        @JsonProperty("pg_id")
        String pgId,

        @JsonProperty("card_company")
        String cardCompany,

        @JsonProperty("card_token")
        String cardToken,

        @JsonProperty("original_amount")
        Long originalAmount,

        @JsonProperty("approved_amount")
        Long approvedAmount,

        @JsonProperty("pg_txn_id")
        Long pgTxnId
) {
}
