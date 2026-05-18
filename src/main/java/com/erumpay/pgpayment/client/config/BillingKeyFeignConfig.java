package com.erumpay.pgpayment.client.config;

import feign.Request;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;

public class BillingKeyFeignConfig {

    @Bean
    public Request.Options billingKeyRequestOptions() {
        return new Request.Options(1, TimeUnit.SECONDS, 1, TimeUnit.SECONDS, true);
    }
}
