package com.erumpay.pgpayment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableFeignClients(basePackages = "com.erumpay.pgpayment.client")
@SpringBootApplication
public class PgpaymentApplication {

	public static void main(String[] args) {
		SpringApplication.run(PgpaymentApplication.class, args);
	}
}
