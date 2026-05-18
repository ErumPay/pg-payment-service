package com.erumpay.pgpayment.client.cardsimulator;

import com.erumpay.pgpayment.client.cardsimulator.dto.PaymentApproveRequest;
import com.erumpay.pgpayment.client.cardsimulator.dto.PaymentApproveResponse;
import com.erumpay.pgpayment.client.cardsimulator.dto.PaymentCancelRequest;
import com.erumpay.pgpayment.client.cardsimulator.dto.PaymentCancelResponse;
import com.erumpay.pgpayment.client.cardsimulator.dto.PaymentInquireRequest;
import com.erumpay.pgpayment.client.cardsimulator.dto.PaymentInquireResponse;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalCancelRequest;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalCancelResponse;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalInquireRequest;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalInquireResponse;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalRequest;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalResponse;
import com.erumpay.pgpayment.client.config.CardSimulatorFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@FeignClient(
        name = "card-simulator-service",
        url = "${pg-payment.clients.card-simulator-service.url:http://card-simulator-service:8080}",
        configuration = CardSimulatorFeignConfig.class
)
public interface CardSimulatorClient {

    @PostMapping(value = "/api/v1/card-simulator/payment/approve", consumes = APPLICATION_JSON_VALUE)
    PaymentApproveResponse approvePayment(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentApproveRequest request
    );

    @PostMapping(value = "/api/v1/card-simulator/payment/cancel", consumes = APPLICATION_JSON_VALUE)
    PaymentCancelResponse cancelPayment(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentCancelRequest request
    );

    @PostMapping(value = "/api/v1/card-simulator/payment/inquire", consumes = APPLICATION_JSON_VALUE)
    PaymentInquireResponse inquirePayment(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody PaymentInquireRequest request
    );

    @PostMapping(value = "/api/v1/card-simulator/pre-approval/request", consumes = APPLICATION_JSON_VALUE)
    PreApprovalResponse requestPreApproval(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PreApprovalRequest request
    );

    @PostMapping(value = "/api/v1/card-simulator/pre-approval/cancel", consumes = APPLICATION_JSON_VALUE)
    PreApprovalCancelResponse cancelPreApproval(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PreApprovalCancelRequest request
    );

    @PostMapping(value = "/api/v1/card-simulator/pre-approval/inquire", consumes = APPLICATION_JSON_VALUE)
    PreApprovalInquireResponse inquirePreApproval(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody PreApprovalInquireRequest request
    );
}
