package com.erumpay.pgpayment.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "pg-payment")
public class PgPaymentProperties {

    private String pgId = "001";
    private Reconciliation reconciliation = new Reconciliation();

    @Getter
    @Setter
    public static class Reconciliation {

        private boolean enabled = true;
        private long fixedDelayMs = 60000;
        private long initialDelayMs = 30000;
        private long minimumAgeSeconds = 30;
        private int maxAttempts = 5;
        private int batchSize = 100;
        private String authorization = "Bearer local-pg-reconciliation-token";
    }
}
