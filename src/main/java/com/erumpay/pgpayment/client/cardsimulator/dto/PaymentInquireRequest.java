package com.erumpay.pgpayment.client.cardsimulator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PaymentInquireRequest(
        @JsonProperty("pg_id")
        String pgId,

        @JsonProperty("card_company")
        String cardCompany,

        @JsonProperty("target_idempotency_key")
        String targetIdempotencyKey
) {
}
