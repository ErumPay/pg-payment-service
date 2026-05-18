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
}
