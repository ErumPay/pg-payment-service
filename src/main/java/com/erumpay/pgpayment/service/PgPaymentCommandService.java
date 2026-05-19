package com.erumpay.pgpayment.service;

import com.erumpay.pgpayment.client.billingkey.dto.BillingKeyTokenRetrieveResponse;
import com.erumpay.pgpayment.client.cardsimulator.CardSimulatorClient;
import com.erumpay.pgpayment.client.cardsimulator.dto.PaymentApproveRequest;
import com.erumpay.pgpayment.client.cardsimulator.dto.PaymentApproveResponse;
import com.erumpay.pgpayment.client.cardsimulator.dto.PaymentCancelRequest;
import com.erumpay.pgpayment.client.cardsimulator.dto.PaymentCancelResponse;
import com.erumpay.pgpayment.client.cardsimulator.dto.PaymentInquireResponse;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalCancelRequest;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalCancelResponse;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalInquireResponse;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalRequest;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalResponse;
import com.erumpay.pgpayment.domain.entity.PgPaymentLedger;
import com.erumpay.pgpayment.domain.enums.PgPaymentStatus;
import com.erumpay.pgpayment.domain.enums.PgTxnType;
import com.erumpay.pgpayment.dto.PgPaymentAuthOnlyRequest;
import com.erumpay.pgpayment.dto.PgPaymentAuthRequest;
import com.erumpay.pgpayment.dto.PgPaymentCancelRequest;
import com.erumpay.pgpayment.dto.PgPaymentResultResponse;
import com.erumpay.pgpayment.dto.PgPaymentVoidRequest;
import com.erumpay.pgpayment.global.config.PgPaymentProperties;
import com.erumpay.pgpayment.global.exception.ErrorCode;
import com.erumpay.pgpayment.global.exception.PgPaymentException;
import com.erumpay.pgpayment.repository.PgPaymentLedgerRepository;
import feign.RetryableException;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PgPaymentCommandService {

    private static final String UNKNOWN_CARD_COMPANY = "UNKNOWN";

    private final PgPaymentLedgerRepository pgPaymentLedgerRepository;
    private final PgPaymentLedgerWriter pgPaymentLedgerWriter;
    private final PgApprovalNumberGenerator pgApprovalNumberGenerator;
    private final CardSimulatorDateTimeParser cardSimulatorDateTimeParser;
    private final PgExternalClientGateway pgExternalClientGateway;
    private final CardSimulatorClient cardSimulatorClient;
    private final PgPaymentProperties pgPaymentProperties;

    public PgPaymentResultResponse authorize(
            PgPaymentAuthRequest request,
            String authorization,
            String idempotencyKey
    ) {
        Optional<PgPaymentLedger> existing = pgPaymentLedgerRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return PgPaymentResultResponse.from(existing.get());
        }

        PgPaymentLedger ledger = createRequestedOrReturnExisting(
                null,
                request.payPaymentId(),
                null,
                idempotencyKey,
                request.billingKey(),
                request.merchantId(),
                request.amount(),
                PgTxnType.AUTH,
                UNKNOWN_CARD_COMPANY
        ).orElse(null);
        if (ledger == null) {
            return PgPaymentResultResponse.from(pgPaymentLedgerRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new PgPaymentException(ErrorCode.LEDGER_SAVE_FAILED)));
        }

        BillingKeyTokenRetrieveResponse token = retrieveCardTokenOrFail(ledger, request.billingKey());
        if (token == null) {
            return failAndReturn(
                    ledger,
                    null,
                    "BILLING_KEY_LOOKUP_FAILED",
                    "Failed to retrieve card token from billing-key-service."
            );
        }

        PaymentApproveRequest cardRequest = new PaymentApproveRequest(
                pgPaymentProperties.getPgId(),
                token.cardCompany(),
                token.cardToken(),
                request.amount(),
                request.amount(),
                ledger.getPgTxnId()
        );

        try {
            PaymentApproveResponse response = cardSimulatorClient.approvePayment(
                    authorization,
                    idempotencyKey,
                    cardRequest
            );
            return applyPaymentApproveResponse(ledger, authorization, token.cardCompany(), response);
        } catch (RetryableException exception) {
            log.warn("Card payment approve timed out. pgTxnId={}", ledger.getPgTxnId(), exception);
            return recoverPaymentApproveByInquiry(ledger, authorization, token.cardCompany(), "CARD_RESULT_UNKNOWN");
        } catch (RuntimeException exception) {
            log.warn("Card payment approve request failed. pgTxnId={}", ledger.getPgTxnId(), exception);
            return failAndReturn(
                    ledger,
                    token.cardCompany(),
                    "CARD_REQUEST_FAILED",
                    "Card payment approve request failed."
            );
        }
    }

    public PgPaymentResultResponse authorizeOnly(
            PgPaymentAuthOnlyRequest request,
            String authorization,
            String idempotencyKey
    ) {
        Optional<PgPaymentLedger> existing = pgPaymentLedgerRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return PgPaymentResultResponse.from(existing.get());
        }

        PgPaymentLedger ledger = createRequestedOrReturnExisting(
                null,
                request.payPaymentId(),
                null,
                idempotencyKey,
                request.billingKey(),
                request.merchantId(),
                request.amount(),
                PgTxnType.AUTH_ONLY,
                UNKNOWN_CARD_COMPANY
        ).orElse(null);
        if (ledger == null) {
            return PgPaymentResultResponse.from(pgPaymentLedgerRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new PgPaymentException(ErrorCode.LEDGER_SAVE_FAILED)));
        }

        BillingKeyTokenRetrieveResponse token = retrieveCardTokenOrFail(ledger, request.billingKey());
        if (token == null) {
            return failAndReturn(
                    ledger,
                    null,
                    "BILLING_KEY_LOOKUP_FAILED",
                    "Failed to retrieve card token from billing-key-service."
            );
        }

        PreApprovalRequest cardRequest = new PreApprovalRequest(
                pgPaymentProperties.getPgId(),
                token.cardCompany(),
                token.cardToken(),
                request.amount(),
                request.amount(),
                ledger.getPgTxnId()
        );

        try {
            PreApprovalResponse response = cardSimulatorClient.requestPreApproval(
                    authorization,
                    idempotencyKey,
                    cardRequest
            );
            return applyPreApprovalResponse(ledger, authorization, token.cardCompany(), response);
        } catch (RetryableException exception) {
            log.warn("Card pre-approval request timed out. pgTxnId={}", ledger.getPgTxnId(), exception);
            return recoverPreApprovalByInquiry(ledger, authorization, token.cardCompany(), "CARD_AUTH_ONLY_RESULT_UNKNOWN");
        } catch (RuntimeException exception) {
            log.warn("Card pre-approval request failed. pgTxnId={}", ledger.getPgTxnId(), exception);
            return failAndReturn(
                    ledger,
                    token.cardCompany(),
                    "CARD_AUTH_ONLY_FAILED",
                    "Card pre-approval request failed."
            );
        }
    }

    public PgPaymentResultResponse cancel(
            Long originalPgTxnId,
            PgPaymentCancelRequest request,
            String authorization,
            String idempotencyKey
    ) {
        Optional<PgPaymentLedger> existing = pgPaymentLedgerRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return PgPaymentResultResponse.from(existing.get());
        }

        PgPaymentLedger original = findOriginalForCancel(originalPgTxnId, request);
        PgPaymentLedger ledger = createRequestedOrReturnExisting(
                original.getPgTxnId(),
                request.payPaymentId(),
                null,
                idempotencyKey,
                original.getBillingKey(),
                request.merchantId(),
                original.getAmount(),
                PgTxnType.CANCEL,
                original.getCardCompany()
        ).orElse(null);
        if (ledger == null) {
            return PgPaymentResultResponse.from(pgPaymentLedgerRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new PgPaymentException(ErrorCode.LEDGER_SAVE_FAILED)));
        }

        BillingKeyTokenRetrieveResponse token = retrieveCardTokenOrFail(ledger, original.getBillingKey());
        if (token == null) {
            return failAndReturn(
                    ledger,
                    null,
                    "BILLING_KEY_LOOKUP_FAILED",
                    "Failed to retrieve card token from billing-key-service."
            );
        }

        PaymentCancelRequest cardRequest = new PaymentCancelRequest(
                pgPaymentProperties.getPgId(),
                token.cardCompany(),
                token.cardToken(),
                original.getCardApprovalNumber(),
                original.getPgTxnId(),
                ledger.getPgTxnId()
        );

        try {
            PaymentCancelResponse response = cardSimulatorClient.cancelPayment(
                    authorization,
                    idempotencyKey,
                    cardRequest
            );
            return applyPaymentCancelResponse(ledger, authorization, token.cardCompany(), response);
        } catch (RetryableException exception) {
            log.warn("Card payment cancel timed out. pgTxnId={}", ledger.getPgTxnId(), exception);
            return recoverPaymentCancelByInquiry(ledger, authorization, token.cardCompany(), "CARD_CANCEL_RESULT_UNKNOWN");
        } catch (RuntimeException exception) {
            log.warn("Card payment cancel request failed. pgTxnId={}", ledger.getPgTxnId(), exception);
            return failAndReturn(
                    ledger,
                    token.cardCompany(),
                    "CARD_CANCEL_FAILED",
                    "Card payment cancel request failed."
            );
        }
    }

    public PgPaymentResultResponse voidHold(
            Long originalPgTxnId,
            PgPaymentVoidRequest request,
            String authorization,
            String idempotencyKey
    ) {
        Optional<PgPaymentLedger> existing = pgPaymentLedgerRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return PgPaymentResultResponse.from(existing.get());
        }

        PgPaymentLedger original = findOriginalForVoid(originalPgTxnId, request);
        PgPaymentLedger ledger = createRequestedOrReturnExisting(
                original.getPgTxnId(),
                request.payPaymentId(),
                null,
                idempotencyKey,
                original.getBillingKey(),
                request.merchantId(),
                original.getAmount(),
                PgTxnType.VOID,
                original.getCardCompany()
        ).orElse(null);
        if (ledger == null) {
            return PgPaymentResultResponse.from(pgPaymentLedgerRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new PgPaymentException(ErrorCode.LEDGER_SAVE_FAILED)));
        }

        BillingKeyTokenRetrieveResponse token = retrieveCardTokenOrFail(ledger, original.getBillingKey());
        if (token == null) {
            return failAndReturn(
                    ledger,
                    null,
                    "BILLING_KEY_LOOKUP_FAILED",
                    "Failed to retrieve card token from billing-key-service."
            );
        }

        PreApprovalCancelRequest cardRequest = new PreApprovalCancelRequest(
                pgPaymentProperties.getPgId(),
                token.cardCompany(),
                token.cardToken(),
                original.getCardApprovalNumber(),
                original.getPgTxnId(),
                ledger.getPgTxnId()
        );

        try {
            PreApprovalCancelResponse response = cardSimulatorClient.cancelPreApproval(
                    authorization,
                    idempotencyKey,
                    cardRequest
            );
            return applyPreApprovalCancelResponse(ledger, authorization, token.cardCompany(), response);
        } catch (RetryableException exception) {
            log.warn("Card pre-approval void timed out. pgTxnId={}", ledger.getPgTxnId(), exception);
            return recoverPreApprovalCancelByInquiry(ledger, authorization, token.cardCompany(), "CARD_VOID_RESULT_UNKNOWN");
        } catch (RuntimeException exception) {
            log.warn("Card pre-approval void request failed. pgTxnId={}", ledger.getPgTxnId(), exception);
            return failAndReturn(
                    ledger,
                    token.cardCompany(),
                    "CARD_VOID_FAILED",
                    "Card pre-approval void request failed."
            );
        }
    }

    private PgPaymentResultResponse applyPaymentApproveResponse(
            PgPaymentLedger ledger,
            String authorization,
            String cardCompany,
            PaymentApproveResponse response
    ) {
        if (!isSuccess(response.responseCode())) {
            return rejectAndReturn(ledger, cardCompany, response.responseMessage());
        }
        String pgApprovalNumber = pgApprovalNumberGenerator.generate(PgTxnType.AUTH, ledger.getPgTxnId());
        LocalDateTime approvedAt = cardSimulatorDateTimeParser.parseOrNow(response.approvedAt());
        return approveWithRecovery(
                ledger,
                authorization,
                cardCompany,
                pgApprovalNumber,
                response.approvalNumber(),
                approvedAt
        );
    }

    private PgPaymentResultResponse applyPreApprovalResponse(
            PgPaymentLedger ledger,
            String authorization,
            String cardCompany,
            PreApprovalResponse response
    ) {
        if (!isSuccess(response.responseCode())) {
            return rejectAndReturn(ledger, cardCompany, response.responseMessage());
        }
        String pgApprovalNumber = pgApprovalNumberGenerator.generate(PgTxnType.AUTH_ONLY, ledger.getPgTxnId());
        LocalDateTime approvedAt = cardSimulatorDateTimeParser.parseOrNow(response.preApprovedAt());
        return approvePreApprovalWithRecovery(
                ledger,
                authorization,
                cardCompany,
                pgApprovalNumber,
                response.preApprovalNumber(),
                approvedAt
        );
    }

    private PgPaymentResultResponse applyPaymentCancelResponse(
            PgPaymentLedger ledger,
            String authorization,
            String cardCompany,
            PaymentCancelResponse response
    ) {
        if (!isSuccess(response.responseCode())) {
            return failAndReturn(ledger, cardCompany, "CARD_CANCEL_FAILED", response.responseMessage());
        }
        String pgApprovalNumber = pgApprovalNumberGenerator.generate(PgTxnType.CANCEL, ledger.getPgTxnId());
        LocalDateTime processedAt = cardSimulatorDateTimeParser.parseOrNow(response.cancelledAt());
        return cancelWithRecovery(
                ledger,
                authorization,
                cardCompany,
                pgApprovalNumber,
                response.approvalNumber(),
                processedAt
        );
    }

    private PgPaymentResultResponse applyPreApprovalCancelResponse(
            PgPaymentLedger ledger,
            String authorization,
            String cardCompany,
            PreApprovalCancelResponse response
    ) {
        if (!isSuccess(response.responseCode())) {
            return failAndReturn(ledger, cardCompany, "CARD_VOID_FAILED", response.responseMessage());
        }
        String pgApprovalNumber = pgApprovalNumberGenerator.generate(PgTxnType.VOID, ledger.getPgTxnId());
        LocalDateTime processedAt = cardSimulatorDateTimeParser.parseOrNow(response.cancelledAt());
        return voidWithRecovery(
                ledger,
                authorization,
                cardCompany,
                pgApprovalNumber,
                response.preApprovalNumber(),
                processedAt
        );
    }

    private PgPaymentResultResponse approveWithRecovery(
            PgPaymentLedger ledger,
            String authorization,
            String cardCompany,
            String pgApprovalNumber,
            String cardApprovalNumber,
            LocalDateTime approvedAt
    ) {
        try {
            PgPaymentLedger approved = pgPaymentLedgerWriter.approve(
                    ledger.getPgTxnId(),
                    cardCompany,
                    pgApprovalNumber,
                    cardApprovalNumber,
                    approvedAt
            );
            return PgPaymentResultResponse.from(approved);
        } catch (RuntimeException exception) {
            log.error("Failed to save APPROVED ledger. pgTxnId={}", ledger.getPgTxnId(), exception);
            return recoverPaymentApproveByInquiry(ledger, authorization, cardCompany, "LEDGER_RECOVERY_REQUIRED");
        }
    }

    private PgPaymentResultResponse approvePreApprovalWithRecovery(
            PgPaymentLedger ledger,
            String authorization,
            String cardCompany,
            String pgApprovalNumber,
            String cardApprovalNumber,
            LocalDateTime approvedAt
    ) {
        try {
            PgPaymentLedger approved = pgPaymentLedgerWriter.approve(
                    ledger.getPgTxnId(),
                    cardCompany,
                    pgApprovalNumber,
                    cardApprovalNumber,
                    approvedAt
            );
            return PgPaymentResultResponse.from(approved);
        } catch (RuntimeException exception) {
            log.error("Failed to save AUTH_ONLY APPROVED ledger. pgTxnId={}", ledger.getPgTxnId(), exception);
            return recoverPreApprovalByInquiry(ledger, authorization, cardCompany, "LEDGER_RECOVERY_REQUIRED");
        }
    }

    private PgPaymentResultResponse cancelWithRecovery(
            PgPaymentLedger ledger,
            String authorization,
            String cardCompany,
            String pgApprovalNumber,
            String cardApprovalNumber,
            LocalDateTime processedAt
    ) {
        try {
            PgPaymentLedger cancelled = pgPaymentLedgerWriter.cancel(
                    ledger.getPgTxnId(),
                    pgApprovalNumber,
                    cardApprovalNumber,
                    processedAt
            );
            return PgPaymentResultResponse.from(cancelled);
        } catch (RuntimeException exception) {
            log.error("Failed to save CANCELLED ledger. pgTxnId={}", ledger.getPgTxnId(), exception);
            return recoverPaymentCancelByInquiry(ledger, authorization, cardCompany, "LEDGER_RECOVERY_REQUIRED");
        }
    }

    private PgPaymentResultResponse voidWithRecovery(
            PgPaymentLedger ledger,
            String authorization,
            String cardCompany,
            String pgApprovalNumber,
            String cardApprovalNumber,
            LocalDateTime processedAt
    ) {
        try {
            PgPaymentLedger voided = pgPaymentLedgerWriter.voidHold(
                    ledger.getPgTxnId(),
                    pgApprovalNumber,
                    cardApprovalNumber,
                    processedAt
            );
            return PgPaymentResultResponse.from(voided);
        } catch (RuntimeException exception) {
            log.error("Failed to save VOIDED ledger. pgTxnId={}", ledger.getPgTxnId(), exception);
            return recoverPreApprovalCancelByInquiry(ledger, authorization, cardCompany, "LEDGER_RECOVERY_REQUIRED");
        }
    }

    private PgPaymentResultResponse recoverPaymentApproveByInquiry(
            PgPaymentLedger ledger,
            String authorization,
            String cardCompany,
            String failureCode
    ) {
        try {
            PaymentInquireResponse inquiry = pgExternalClientGateway.inquirePayment(
                    authorization,
                    pgPaymentProperties.getPgId(),
                    cardCompany,
                    ledger.getPgTxnId()
            );
            if (isSuccess(inquiry.responseCode())) {
                String pgApprovalNumber = pgApprovalNumberGenerator.generate(PgTxnType.AUTH, ledger.getPgTxnId());
                LocalDateTime approvedAt = cardSimulatorDateTimeParser.parseOrNow(inquiry.approvedAt());
                try {
                    PgPaymentLedger approved = pgPaymentLedgerWriter.approve(
                            ledger.getPgTxnId(),
                            cardCompany,
                            pgApprovalNumber,
                            inquiry.approvalNumber(),
                            approvedAt
                    );
                    return PgPaymentResultResponse.from(approved);
                } catch (RuntimeException updateException) {
                    log.error("Failed to save recovered APPROVED ledger. pgTxnId={}", ledger.getPgTxnId(), updateException);
                    return markRecoveryOrFail(
                            ledger,
                            cardCompany,
                            "LEDGER_RECOVERY_REQUIRED",
                            "Card payment was approved, but PG ledger update failed."
                    );
                }
            }
        } catch (RuntimeException exception) {
            log.error("Failed to recover payment approve by inquiry. pgTxnId={}", ledger.getPgTxnId(), exception);
        }
        return markRecoveryOrFail(ledger, cardCompany, failureCode, "Card payment approve result is unknown.");
    }

    private PgPaymentResultResponse recoverPreApprovalByInquiry(
            PgPaymentLedger ledger,
            String authorization,
            String cardCompany,
            String failureCode
    ) {
        try {
            PreApprovalInquireResponse inquiry = pgExternalClientGateway.inquirePreApproval(
                    authorization,
                    pgPaymentProperties.getPgId(),
                    cardCompany,
                    ledger.getPgTxnId()
            );
            if (isSuccess(inquiry.responseCode())) {
                String pgApprovalNumber = pgApprovalNumberGenerator.generate(PgTxnType.AUTH_ONLY, ledger.getPgTxnId());
                LocalDateTime approvedAt = cardSimulatorDateTimeParser.parseOrNow(inquiry.preApprovedAt());
                try {
                    PgPaymentLedger approved = pgPaymentLedgerWriter.approve(
                            ledger.getPgTxnId(),
                            cardCompany,
                            pgApprovalNumber,
                            inquiry.preApprovalNumber(),
                            approvedAt
                    );
                    return PgPaymentResultResponse.from(approved);
                } catch (RuntimeException updateException) {
                    log.error("Failed to save recovered AUTH_ONLY APPROVED ledger. pgTxnId={}", ledger.getPgTxnId(), updateException);
                    return markRecoveryOrFail(
                            ledger,
                            cardCompany,
                            "LEDGER_RECOVERY_REQUIRED",
                            "Card pre-approval was approved, but PG ledger update failed."
                    );
                }
            }
        } catch (RuntimeException exception) {
            log.error("Failed to recover pre-approval by inquiry. pgTxnId={}", ledger.getPgTxnId(), exception);
        }
        return markRecoveryOrFail(ledger, cardCompany, failureCode, "Card pre-approval result is unknown.");
    }

    private PgPaymentResultResponse recoverPaymentCancelByInquiry(
            PgPaymentLedger ledger,
            String authorization,
            String cardCompany,
            String failureCode
    ) {
        try {
            PaymentInquireResponse inquiry = pgExternalClientGateway.inquirePayment(
                    authorization,
                    pgPaymentProperties.getPgId(),
                    cardCompany,
                    ledger.getPgTxnId()
            );
            if (isSuccess(inquiry.responseCode())) {
                String pgApprovalNumber = pgApprovalNumberGenerator.generate(PgTxnType.CANCEL, ledger.getPgTxnId());
                LocalDateTime processedAt = cardSimulatorDateTimeParser.parseOrNow(inquiry.approvedAt());
                try {
                    PgPaymentLedger cancelled = pgPaymentLedgerWriter.cancel(
                            ledger.getPgTxnId(),
                            pgApprovalNumber,
                            inquiry.approvalNumber(),
                            processedAt
                    );
                    return PgPaymentResultResponse.from(cancelled);
                } catch (RuntimeException updateException) {
                    log.error("Failed to save recovered CANCELLED ledger. pgTxnId={}", ledger.getPgTxnId(), updateException);
                    return markRecoveryOrFail(
                            ledger,
                            cardCompany,
                            "LEDGER_RECOVERY_REQUIRED",
                            "Card payment was cancelled, but PG ledger update failed."
                    );
                }
            }
        } catch (RuntimeException exception) {
            log.error("Failed to recover payment cancel by inquiry. pgTxnId={}", ledger.getPgTxnId(), exception);
        }
        return markRecoveryOrFail(ledger, cardCompany, failureCode, "Card payment cancel result is unknown.");
    }

    private PgPaymentResultResponse recoverPreApprovalCancelByInquiry(
            PgPaymentLedger ledger,
            String authorization,
            String cardCompany,
            String failureCode
    ) {
        try {
            PreApprovalInquireResponse inquiry = pgExternalClientGateway.inquirePreApproval(
                    authorization,
                    pgPaymentProperties.getPgId(),
                    cardCompany,
                    ledger.getPgTxnId()
            );
            if (isSuccess(inquiry.responseCode())) {
                String pgApprovalNumber = pgApprovalNumberGenerator.generate(PgTxnType.VOID, ledger.getPgTxnId());
                LocalDateTime processedAt = cardSimulatorDateTimeParser.parseOrNow(inquiry.preApprovedAt());
                try {
                    PgPaymentLedger voided = pgPaymentLedgerWriter.voidHold(
                            ledger.getPgTxnId(),
                            pgApprovalNumber,
                            inquiry.preApprovalNumber(),
                            processedAt
                    );
                    return PgPaymentResultResponse.from(voided);
                } catch (RuntimeException updateException) {
                    log.error("Failed to save recovered VOIDED ledger. pgTxnId={}", ledger.getPgTxnId(), updateException);
                    return markRecoveryOrFail(
                            ledger,
                            cardCompany,
                            "LEDGER_RECOVERY_REQUIRED",
                            "Card pre-approval was voided, but PG ledger update failed."
                    );
                }
            }
        } catch (RuntimeException exception) {
            log.error("Failed to recover pre-approval void by inquiry. pgTxnId={}", ledger.getPgTxnId(), exception);
        }
        return markRecoveryOrFail(ledger, cardCompany, failureCode, "Card pre-approval void result is unknown.");
    }

    private PgPaymentResultResponse markRecoveryOrFail(
            PgPaymentLedger ledger,
            String cardCompany,
            String failureCode,
            String failureMessage
    ) {
        if ("LEDGER_RECOVERY_REQUIRED".equals(failureCode)) {
            try {
                PgPaymentLedger recoveryRequired = pgPaymentLedgerWriter.markRecoveryRequired(
                        ledger.getPgTxnId(),
                        failureMessage
                );
                return PgPaymentResultResponse.from(recoveryRequired);
            } catch (RuntimeException exception) {
                log.error("Failed to mark ledger recovery required. pgTxnId={}", ledger.getPgTxnId(), exception);
                return currentResult(ledger);
            }
        }
        return failAndReturn(ledger, cardCompany, failureCode, failureMessage);
    }

    private PgPaymentResultResponse rejectAndReturn(PgPaymentLedger ledger, String cardCompany, String rejectReason) {
        try {
            PgPaymentLedger rejected = pgPaymentLedgerWriter.reject(
                    ledger.getPgTxnId(),
                    cardCompany,
                    rejectReason,
                    LocalDateTime.now()
            );
            return PgPaymentResultResponse.from(rejected);
        } catch (RuntimeException exception) {
            log.error("Failed to save REJECTED ledger. pgTxnId={}", ledger.getPgTxnId(), exception);
            return currentResult(ledger);
        }
    }

    private PgPaymentResultResponse failAndReturn(
            PgPaymentLedger ledger,
            String cardCompany,
            String failureCode,
            String failureMessage
    ) {
        try {
            PgPaymentLedger failed = pgPaymentLedgerWriter.fail(
                    ledger.getPgTxnId(),
                    cardCompany,
                    failureCode,
                    failureMessage,
                    LocalDateTime.now()
            );
            return PgPaymentResultResponse.from(failed);
        } catch (RuntimeException exception) {
            log.error("Failed to save FAILED ledger. pgTxnId={}", ledger.getPgTxnId(), exception);
            return currentResult(ledger);
        }
    }

    private BillingKeyTokenRetrieveResponse retrieveCardTokenOrFail(PgPaymentLedger ledger, String billingKey) {
        try {
            BillingKeyTokenRetrieveResponse token = pgExternalClientGateway.retrieveCardToken(billingKey);
            if (token == null || token.cardToken() == null || token.cardToken().isBlank()
                    || token.cardCompany() == null || token.cardCompany().isBlank()) {
                throw new IllegalStateException("Billing-key service returned an invalid card token response.");
            }
            return token;
        } catch (RuntimeException exception) {
            log.warn("Billing-key lookup failed. pgTxnId={}", ledger.getPgTxnId(), exception);
            return null;
        }
    }

    private Optional<PgPaymentLedger> createRequestedOrReturnExisting(
            Long originalTxnId,
            Long payPaymentId,
            Long holdTxnId,
            String idempotencyKey,
            String billingKey,
            Long merchantId,
            Long amount,
            PgTxnType txnType,
            String cardCompany
    ) {
        try {
            return Optional.of(pgPaymentLedgerWriter.createRequested(
                    originalTxnId,
                    payPaymentId,
                    holdTxnId,
                    idempotencyKey,
                    billingKey,
                    merchantId,
                    amount,
                    txnType,
                    cardCompany
            ));
        } catch (DataIntegrityViolationException exception) {
            log.info("Idempotency key already exists. idempotencyKey={}", idempotencyKey);
            return Optional.empty();
        }
    }

    private PgPaymentLedger findOriginalForCancel(Long originalPgTxnId, PgPaymentCancelRequest request) {
        PgPaymentLedger original = findOriginal(originalPgTxnId);
        if (original.getTxnType() != PgTxnType.AUTH || original.getStatus() != PgPaymentStatus.APPROVED) {
            throw new PgPaymentException(ErrorCode.INVALID_REQUEST, "Only APPROVED AUTH transaction can be cancelled.");
        }
        validateOriginalRequest(original, request.payPaymentId(), request.merchantId());
        if (pgPaymentLedgerRepository.existsByOriginalTxnIdAndTxnTypeAndStatus(
                originalPgTxnId,
                PgTxnType.CANCEL,
                PgPaymentStatus.CANCELLED
        )) {
            throw new PgPaymentException(ErrorCode.INVALID_REQUEST, "Payment transaction was already cancelled.");
        }
        return original;
    }

    private PgPaymentLedger findOriginalForVoid(Long originalPgTxnId, PgPaymentVoidRequest request) {
        PgPaymentLedger original = findOriginal(originalPgTxnId);
        if (original.getTxnType() != PgTxnType.AUTH_ONLY || original.getStatus() != PgPaymentStatus.APPROVED) {
            throw new PgPaymentException(ErrorCode.INVALID_REQUEST, "Only APPROVED AUTH_ONLY transaction can be voided.");
        }
        validateOriginalRequest(original, request.payPaymentId(), request.merchantId());
        if (pgPaymentLedgerRepository.existsByOriginalTxnIdAndTxnTypeAndStatus(
                originalPgTxnId,
                PgTxnType.VOID,
                PgPaymentStatus.VOIDED
        )) {
            throw new PgPaymentException(ErrorCode.INVALID_REQUEST, "Auth-only transaction was already voided.");
        }
        return original;
    }

    private PgPaymentLedger findOriginal(Long originalPgTxnId) {
        return pgPaymentLedgerRepository.findById(originalPgTxnId)
                .orElseThrow(() -> new PgPaymentException(
                        ErrorCode.PG_PAYMENT_NOT_FOUND,
                        "Original PG transaction was not found. pgTxnId=" + originalPgTxnId
                ));
    }

    private void validateOriginalRequest(PgPaymentLedger original, Long payPaymentId, Long merchantId) {
        if (!original.getPayPaymentId().equals(payPaymentId) || !original.getMerchantId().equals(merchantId)) {
            throw new PgPaymentException(
                    ErrorCode.INVALID_REQUEST,
                    "payPaymentId and merchantId must match the original transaction."
            );
        }
    }

    private PgPaymentResultResponse currentResult(PgPaymentLedger fallbackLedger) {
        return pgPaymentLedgerRepository.findById(fallbackLedger.getPgTxnId())
                .map(PgPaymentResultResponse::from)
                .orElseGet(() -> PgPaymentResultResponse.from(fallbackLedger));
    }

    private boolean isSuccess(Integer responseCode) {
        return Integer.valueOf(0).equals(responseCode);
    }
}
