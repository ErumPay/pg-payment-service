package com.erumpay.pgpayment.client.cardsimulator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PreApprovalCancelRequest(
        @JsonProperty("pg_id")
        String pgId,

        @JsonProperty("origin_idempotency_key")
        String originIdempotencyKey,

        @JsonProperty("pg_txn_id")
        Long pgTxnId,

        @JsonProperty("origin_pg_txn_id")
        Long originPgTxnId,

        @JsonProperty("card_company")
        String cardCompany,

        @JsonProperty("card_token")
        String cardToken,

        @JsonProperty("pre_approval_number")
        String preApprovalNumber
) {
}
