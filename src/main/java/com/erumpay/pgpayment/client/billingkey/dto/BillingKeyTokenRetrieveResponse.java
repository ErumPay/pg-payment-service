package com.erumpay.pgpayment.client.billingkey.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BillingKeyTokenRetrieveResponse(
        @JsonProperty("billing_key")
        String billingKey,

        @JsonProperty("card_token")
        String cardToken,

        @JsonProperty("card_company")
        String cardCompany
) {
}
