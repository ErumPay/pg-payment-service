package com.erumpay.pgpayment.service;

import com.erumpay.pgpayment.client.billingkey.BillingKeyClient;
import com.erumpay.pgpayment.client.billingkey.dto.BillingKeyTokenRetrieveRequest;
import com.erumpay.pgpayment.client.billingkey.dto.BillingKeyTokenRetrieveResponse;
import com.erumpay.pgpayment.client.cardsimulator.CardSimulatorClient;
import com.erumpay.pgpayment.client.cardsimulator.dto.PaymentApproveRequest;
import com.erumpay.pgpayment.client.cardsimulator.dto.PaymentApproveResponse;
import com.erumpay.pgpayment.client.cardsimulator.dto.PaymentCancelRequest;
import com.erumpay.pgpayment.client.cardsimulator.dto.PaymentCancelResponse;
import com.erumpay.pgpayment.client.cardsimulator.dto.PaymentInquireRequest;
import com.erumpay.pgpayment.client.cardsimulator.dto.PaymentInquireResponse;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalCancelRequest;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalCancelResponse;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalCaptureRequest;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalCaptureResponse;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalInquireRequest;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalInquireResponse;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalRequest;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PgExternalClientGateway {

    private final BillingKeyClient billingKeyClient;
    private final CardSimulatorClient cardSimulatorClient;

    @CircuitBreaker(name = "billingKeyTokenRetrieve")
    @Retry(name = "billingKeyTokenRetrieve")
    public BillingKeyTokenRetrieveResponse retrieveCardToken(String billingKey) {
        return billingKeyClient.retrieveCardToken(new BillingKeyTokenRetrieveRequest(billingKey));
    }

    @CircuitBreaker(name = "cardPaymentApprove")
    public PaymentApproveResponse approvePayment(
            String authorization,
            String idempotencyKey,
            PaymentApproveRequest request
    ) {
        return cardSimulatorClient.approvePayment(authorization, idempotencyKey, request);
    }

    @CircuitBreaker(name = "cardPreApprovalRequest")
    public PreApprovalResponse requestPreApproval(
            String authorization,
            String idempotencyKey,
            PreApprovalRequest request
    ) {
        return cardSimulatorClient.requestPreApproval(authorization, idempotencyKey, request);
    }

    @CircuitBreaker(name = "cardPaymentCancel")
    public PaymentCancelResponse cancelPayment(
            String authorization,
            String idempotencyKey,
            PaymentCancelRequest request
    ) {
        return cardSimulatorClient.cancelPayment(authorization, idempotencyKey, request);
    }

    @CircuitBreaker(name = "cardPreApprovalCancel")
    public PreApprovalCancelResponse cancelPreApproval(
            String authorization,
            String idempotencyKey,
            PreApprovalCancelRequest request
    ) {
        return cardSimulatorClient.cancelPreApproval(authorization, idempotencyKey, request);
    }

    @CircuitBreaker(name = "cardPreApprovalCapture")
    public PreApprovalCaptureResponse capturePreApproval(
            String authorization,
            String idempotencyKey,
            PreApprovalCaptureRequest request
    ) {
        return cardSimulatorClient.capturePreApproval(authorization, idempotencyKey, request);
    }

    @CircuitBreaker(name = "cardPaymentInquire")
    @Retry(name = "cardPaymentInquire")
    public PaymentInquireResponse inquirePayment(
            String authorization,
            String pgId,
            String cardCompany,
            String targetIdempotencyKey
    ) {
        return cardSimulatorClient.inquirePayment(
                authorization,
                new PaymentInquireRequest(pgId, cardCompany, targetIdempotencyKey)
        );
    }

    @CircuitBreaker(name = "cardPreApprovalInquire")
    @Retry(name = "cardPreApprovalInquire")
    public PreApprovalInquireResponse inquirePreApproval(
            String authorization,
            String pgId,
            String cardCompany,
            String targetIdempotencyKey
    ) {
        return cardSimulatorClient.inquirePreApproval(
                authorization,
                new PreApprovalInquireRequest(pgId, cardCompany, targetIdempotencyKey)
        );
    }
}
