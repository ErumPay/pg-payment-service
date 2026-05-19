package com.erumpay.pgpayment.service;

import com.erumpay.pgpayment.client.billingkey.BillingKeyClient;
import com.erumpay.pgpayment.client.billingkey.dto.BillingKeyTokenRetrieveRequest;
import com.erumpay.pgpayment.client.billingkey.dto.BillingKeyTokenRetrieveResponse;
import com.erumpay.pgpayment.client.cardsimulator.CardSimulatorClient;
import com.erumpay.pgpayment.client.cardsimulator.dto.PaymentInquireRequest;
import com.erumpay.pgpayment.client.cardsimulator.dto.PaymentInquireResponse;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalInquireRequest;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalInquireResponse;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PgExternalClientGateway {

    private final BillingKeyClient billingKeyClient;
    private final CardSimulatorClient cardSimulatorClient;

    @Retry(name = "billingKeyTokenRetrieve")
    public BillingKeyTokenRetrieveResponse retrieveCardToken(String billingKey) {
        return billingKeyClient.retrieveCardToken(new BillingKeyTokenRetrieveRequest(billingKey));
    }

    @Retry(name = "cardPaymentInquire")
    public PaymentInquireResponse inquirePayment(
            String authorization,
            String pgId,
            String cardCompany,
            Long pgTxnId
    ) {
        return cardSimulatorClient.inquirePayment(
                authorization,
                new PaymentInquireRequest(pgId, cardCompany, pgTxnId)
        );
    }

    @Retry(name = "cardPreApprovalInquire")
    public PreApprovalInquireResponse inquirePreApproval(
            String authorization,
            String pgId,
            String cardCompany,
            Long pgTxnId
    ) {
        return cardSimulatorClient.inquirePreApproval(
                authorization,
                new PreApprovalInquireRequest(pgId, cardCompany, pgTxnId)
        );
    }
}
