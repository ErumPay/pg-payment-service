package com.erumpay.pgpayment.client.billingkey;

import com.erumpay.pgpayment.client.billingkey.dto.BillingKeyTokenRetrieveRequest;
import com.erumpay.pgpayment.client.billingkey.dto.BillingKeyTokenRetrieveResponse;
import com.erumpay.pgpayment.client.config.BillingKeyFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@FeignClient(name = "billing-key-service", url = "${pg-payment.clients.billing-key-service.url:http://localhost:8092}", configuration = BillingKeyFeignConfig.class)
public interface BillingKeyClient {

    @PostMapping(value = "/api/v1/billing-key/token-retrieve", consumes = APPLICATION_JSON_VALUE)
    BillingKeyTokenRetrieveResponse retrieveCardToken(@RequestBody BillingKeyTokenRetrieveRequest request);
}
