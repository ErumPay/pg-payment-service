package com.erumpay.pgpayment.client.config;

import feign.Request;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;

public class CardSimulatorFeignConfig {

    @Bean
    public Request.Options cardSimulatorRequestOptions() {
        return new Request.Options(2, TimeUnit.SECONDS, 2, TimeUnit.SECONDS, true);
    }
}
