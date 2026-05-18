package com.erumpay.pgpayment.client.cardsimulator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PreApprovalInquireRequest(
        @JsonProperty("pg_id")
        String pgId,

        @JsonProperty("card_company")
        String cardCompany,

        @JsonProperty("pg_txn_id")
        Long pgTxnId
) {
}
