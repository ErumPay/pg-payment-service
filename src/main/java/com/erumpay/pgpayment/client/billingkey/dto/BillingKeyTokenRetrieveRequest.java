package com.erumpay.pgpayment.client.billingkey.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BillingKeyTokenRetrieveRequest(
        @JsonProperty("billing_key")
        String billingKey
) {
}
