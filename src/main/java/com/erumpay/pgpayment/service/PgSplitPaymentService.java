package com.erumpay.pgpayment.service;

import com.erumpay.pgpayment.client.billingkey.dto.BillingKeyTokenRetrieveResponse;
import com.erumpay.pgpayment.client.cardsimulator.CardSimulatorResponseCodes;
import com.erumpay.pgpayment.client.cardsimulator.dto.PaymentCancelRequest;
import com.erumpay.pgpayment.client.cardsimulator.dto.PaymentCancelResponse;
import com.erumpay.pgpayment.client.cardsimulator.dto.PaymentInquireResponse;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalCancelRequest;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalCancelResponse;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalCaptureRequest;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalCaptureResponse;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalInquireResponse;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalRequest;
import com.erumpay.pgpayment.client.cardsimulator.dto.PreApprovalResponse;
import com.erumpay.pgpayment.domain.entity.PgPaymentGroup;
import com.erumpay.pgpayment.domain.entity.PgPaymentLedger;
import com.erumpay.pgpayment.domain.enums.PgFailureCode;
import com.erumpay.pgpayment.domain.enums.PgPaymentGroupStatus;
import com.erumpay.pgpayment.domain.enums.PgPaymentStatus;
import com.erumpay.pgpayment.domain.enums.PgTxnType;
import com.erumpay.pgpayment.dto.PgPaymentCancelRequest;
import com.erumpay.pgpayment.dto.PgSplitPaymentItemRequest;
import com.erumpay.pgpayment.dto.PgSplitPaymentRequest;
import com.erumpay.pgpayment.dto.PgSplitPaymentResultResponse;
import com.erumpay.pgpayment.global.config.PgPaymentProperties;
import com.erumpay.pgpayment.global.exception.ErrorCode;
import com.erumpay.pgpayment.global.exception.PgPaymentException;
import com.erumpay.pgpayment.repository.PgPaymentGroupRepository;
import com.erumpay.pgpayment.repository.PgPaymentLedgerRepository;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PgSplitPaymentService {

    private static final String UNKNOWN_CARD_COMPANY = "UNKNOWN";

    private final PgPaymentGroupRepository pgPaymentGroupRepository;
    private final PgPaymentLedgerRepository pgPaymentLedgerRepository;
    private final PgSplitPaymentWriter pgSplitPaymentWriter;
    private final PgExternalClientGateway pgExternalClientGateway;
    private final PgPaymentProperties pgPaymentProperties;
    private final PgApprovalNumberGenerator pgApprovalNumberGenerator;
    private final CardSimulatorDateTimeParser cardSimulatorDateTimeParser;

    public PgSplitPaymentResultResponse splitAuthorize(
            PgSplitPaymentRequest request,
            String authorization,
            String idempotencyKey) {
        return pgPaymentGroupRepository.findByIdempotencyKey(idempotencyKey)
                .map(this::response)
                .orElseGet(() -> processNewSplitAuthorize(request, authorization, idempotencyKey));
    }

    public PgSplitPaymentResultResponse getSplitPaymentResult(Long pgGroupId) {
        return response(findGroup(pgGroupId));
    }

    public PgSplitPaymentResultResponse getSplitPaymentDetail(Long pgGroupId) {
        return response(findGroup(pgGroupId));
    }

    public PgSplitPaymentResultResponse cancelSplitPayment(
            Long pgGroupId,
            PgPaymentCancelRequest request,
            String authorization,
            String idempotencyKey) {
        PgPaymentGroup group = findGroup(pgGroupId);
        if (group.getStatus() == PgPaymentGroupStatus.CANCELLED) {
            return response(group);
        }
        if (group.getStatus() != PgPaymentGroupStatus.APPROVED) {
            throw new PgPaymentException(
                    ErrorCode.INVALID_TRANSACTION_STATE,
                    "Only APPROVED split payment group can be cancelled.");
        }
        if (!group.getPayPaymentId().equals(request.payPaymentId())
                || !group.getMerchantId().equals(request.merchantId())) {
            throw new PgPaymentException(
                    ErrorCode.ORIGINAL_TRANSACTION_MISMATCH,
                    "payPaymentId and merchantId must match the original split payment group.");
        }

        pgSplitPaymentWriter.changeGroupStatus(pgGroupId, PgPaymentGroupStatus.CANCELLING);
        List<PgPaymentLedger> captures = pgPaymentLedgerRepository
                .findByPgGroupIdAndTxnTypeAndStatusOrderBySplitSeqAscPgTxnIdAsc(
                        pgGroupId,
                        PgTxnType.CAPTURE,
                        PgPaymentStatus.CAPTURED);
        boolean compensated = cancelCaptured(captures, authorization);
        if (compensated) {
            group = pgSplitPaymentWriter.changeGroupStatus(pgGroupId, PgPaymentGroupStatus.CANCELLED);
        } else {
            group = pgSplitPaymentWriter.markGroupCompensationRequired(
                    pgGroupId,
                    PgFailureCode.COMPENSATION_CANCEL_FAILED,
                    "Split payment cancel compensation failed.");
        }
        return response(group);
    }

    private PgSplitPaymentResultResponse processNewSplitAuthorize(
            PgSplitPaymentRequest request,
            String authorization,
            String idempotencyKey) {
        PgPaymentGroup group = pgSplitPaymentWriter.createGroup(
                request.payPaymentId(),
                request.merchantId(),
                idempotencyKey,
                request.totalAmount());
        pgSplitPaymentWriter.changeGroupStatus(group.getPgGroupId(), PgPaymentGroupStatus.HOLDING);

        List<HeldPayment> heldPayments = new ArrayList<>();
        for (PgSplitPaymentItemRequest item : sortedItems(request)) {
            HoldOutcome outcome = requestHold(group, item, authorization);
            if (outcome.status() == SplitStepStatus.SUCCESS) {
                heldPayments.add(outcome.heldPayment());
                continue;
            }
            boolean voided = voidHeld(heldPayments, authorization);
            group = finishGroupAfterHoldFailure(group.getPgGroupId(), outcome, voided);
            return response(group);
        }

        pgSplitPaymentWriter.changeGroupStatus(group.getPgGroupId(), PgPaymentGroupStatus.HELD);
        pgSplitPaymentWriter.changeGroupStatus(group.getPgGroupId(), PgPaymentGroupStatus.CAPTURING);

        List<PgPaymentLedger> capturedLedgers = new ArrayList<>();
        for (HeldPayment heldPayment : heldPayments) {
            CaptureOutcome outcome = requestCapture(group, heldPayment, authorization);
            if (outcome.status() == SplitStepStatus.SUCCESS) {
                capturedLedgers.add(outcome.captureLedger());
                continue;
            }
            if (outcome.status() == SplitStepStatus.RECOVERY_REQUIRED) {
                group = pgSplitPaymentWriter.markGroupRecoveryRequired(
                        group.getPgGroupId(),
                        PgFailureCode.CARD_CAPTURE_RESULT_UNKNOWN,
                        outcome.message());
                return response(group);
            }
            boolean cancelled = cancelCaptured(capturedLedgers, authorization);
            List<HeldPayment> remainingHeld = heldPayments.stream()
                    .filter(held -> capturedLedgers.stream()
                            .noneMatch(captured -> captured.getOriginalTxnId().equals(held.ledger().getPgTxnId())))
                    .toList();
            boolean voided = voidHeld(remainingHeld, authorization);
            if (cancelled && voided) {
                group = pgSplitPaymentWriter.failGroup(
                        group.getPgGroupId(),
                        PgFailureCode.CARD_CAPTURE_FAILED,
                        outcome.message());
            } else {
                group = pgSplitPaymentWriter.markGroupCompensationRequired(
                        group.getPgGroupId(),
                        PgFailureCode.COMPENSATION_CANCEL_FAILED,
                        "Split payment capture failed and compensation did not finish.");
            }
            return response(group);
        }

        group = pgSplitPaymentWriter.changeGroupStatus(group.getPgGroupId(), PgPaymentGroupStatus.APPROVED);
        return response(group);
    }

    private HoldOutcome requestHold(PgPaymentGroup group, PgSplitPaymentItemRequest item, String authorization) {
        PgPaymentLedger ledger = pgSplitPaymentWriter.createLedgerWithPaymentKey(
                group.getPgGroupId(),
                item.splitSeq(),
                null,
                group.getPayPaymentId(),
                item.billingKey(),
                group.getMerchantId(),
                item.approvedAmount(),
                PgTxnType.AUTH_ONLY,
                UNKNOWN_CARD_COMPANY);

        BillingKeyTokenRetrieveResponse token = retrieveCardToken(ledger, item.billingKey());
        if (token == null) {
            pgSplitPaymentWriter.fail(
                    ledger.getPgTxnId(),
                    null,
                    PgFailureCode.BILLING_KEY_LOOKUP_FAILED,
                    null,
                    LocalDateTime.now());
            return HoldOutcome.failed(PgFailureCode.BILLING_KEY_LOOKUP_FAILED.getMessage(),
                    PgFailureCode.BILLING_KEY_LOOKUP_FAILED);
        }

        PreApprovalRequest cardRequest = new PreApprovalRequest(
                pgPaymentProperties.getPgId(),
                token.cardCompany(),
                token.cardToken(),
                item.originalAmount(),
                item.approvedAmount(),
                ledger.getPgTxnId());
        try {
            PreApprovalResponse response = pgExternalClientGateway.requestPreApproval(
                    authorization,
                    ledger.getIdempotencyKey(),
                    cardRequest);
            if (CardSimulatorResponseCodes.isSuccess(response.responseCode())) {
                PgPaymentLedger held = holdLedger(ledger, token.cardCompany(), response.preApprovalNumber(),
                        response.preApprovedAt());
                return HoldOutcome.success(new HeldPayment(held, token));
            }
            pgSplitPaymentWriter.reject(
                    ledger.getPgTxnId(),
                    token.cardCompany(),
                    response.responseMessage(),
                    LocalDateTime.now());
            return HoldOutcome.rejected(response.responseMessage());
        } catch (CallNotPermittedException exception) {
            log.warn("Card pre-approval circuit is open for split payment. pgTxnId={}", ledger.getPgTxnId(),
                    exception);
            pgSplitPaymentWriter.fail(
                    ledger.getPgTxnId(),
                    token.cardCompany(),
                    PgFailureCode.CARD_CIRCUIT_OPEN,
                    null,
                    LocalDateTime.now());
            return HoldOutcome.failed(PgFailureCode.CARD_CIRCUIT_OPEN.getMessage(), PgFailureCode.CARD_CIRCUIT_OPEN);
        } catch (RetryableException exception) {
            log.warn("Card pre-approval timed out for split payment. pgTxnId={}", ledger.getPgTxnId(), exception);
            return recoverHoldByInquiry(ledger, token, authorization);
        } catch (RuntimeException exception) {
            log.warn("Card pre-approval failed for split payment. pgTxnId={}", ledger.getPgTxnId(), exception);
            pgSplitPaymentWriter.fail(
                    ledger.getPgTxnId(),
                    token.cardCompany(),
                    PgFailureCode.CARD_AUTH_ONLY_FAILED,
                    null,
                    LocalDateTime.now());
            return HoldOutcome.failed(PgFailureCode.CARD_AUTH_ONLY_FAILED.getMessage(),
                    PgFailureCode.CARD_AUTH_ONLY_FAILED);
        }
    }

    private HoldOutcome recoverHoldByInquiry(
            PgPaymentLedger ledger,
            BillingKeyTokenRetrieveResponse token,
            String authorization) {
        try {
            PreApprovalInquireResponse inquiry = pgExternalClientGateway.inquirePreApproval(
                    authorization,
                    pgPaymentProperties.getPgId(),
                    token.cardCompany(),
                    ledger.getIdempotencyKey());
            if (CardSimulatorResponseCodes.isSuccess(inquiry.responseCode())) {
                PgPaymentLedger held = holdLedger(ledger, token.cardCompany(), inquiry.preApprovalNumber(),
                        inquiry.preApprovedAt());
                return HoldOutcome.success(new HeldPayment(held, token));
            }
            if (CardSimulatorResponseCodes.isPaymentRejected(inquiry.responseCode())) {
                pgSplitPaymentWriter.reject(
                        ledger.getPgTxnId(),
                        token.cardCompany(),
                        inquiry.responseMessage(),
                        LocalDateTime.now());
                return HoldOutcome.rejected(inquiry.responseMessage());
            }
        } catch (RuntimeException exception) {
            log.warn("Split payment pre-approval inquiry failed. pgTxnId={}", ledger.getPgTxnId(), exception);
        }
        pgSplitPaymentWriter.markRecoveryRequired(
                ledger.getPgTxnId(),
                token.cardCompany(),
                PgFailureCode.CARD_AUTH_ONLY_RESULT_UNKNOWN,
                PgFailureCode.CARD_AUTH_ONLY_RESULT_UNKNOWN.getMessage());
        return HoldOutcome.recoveryRequired(PgFailureCode.CARD_AUTH_ONLY_RESULT_UNKNOWN.getMessage());
    }

    private CaptureOutcome requestCapture(PgPaymentGroup group, HeldPayment heldPayment, String authorization) {
        PgPaymentLedger holdLedger = heldPayment.ledger();
        BillingKeyTokenRetrieveResponse token = heldPayment.token();
        PgPaymentLedger captureLedger = pgSplitPaymentWriter.createLedgerWithPaymentKey(
                group.getPgGroupId(),
                holdLedger.getSplitSeq(),
                holdLedger.getPgTxnId(),
                group.getPayPaymentId(),
                holdLedger.getBillingKey(),
                group.getMerchantId(),
                holdLedger.getAmount(),
                PgTxnType.CAPTURE,
                token.cardCompany());

        PreApprovalCaptureRequest cardRequest = new PreApprovalCaptureRequest(
                pgPaymentProperties.getPgId(),
                holdLedger.getIdempotencyKey(),
                captureLedger.getPgTxnId(),
                holdLedger.getPgTxnId(),
                token.cardCompany(),
                token.cardToken(),
                holdLedger.getCardApprovalNumber());
        try {
            PreApprovalCaptureResponse response = pgExternalClientGateway.capturePreApproval(
                    authorization,
                    captureLedger.getIdempotencyKey(),
                    cardRequest);
            if (CardSimulatorResponseCodes.isSuccess(response.responseCode())) {
                PgPaymentLedger captured = captureLedger(captureLedger, response.approvalNumber(),
                        response.approvedAt());
                return CaptureOutcome.success(captured);
            }
            pgSplitPaymentWriter.fail(
                    captureLedger.getPgTxnId(),
                    token.cardCompany(),
                    PgFailureCode.CARD_CAPTURE_FAILED,
                    response.responseMessage(),
                    LocalDateTime.now());
            return CaptureOutcome.failed(response.responseMessage());
        } catch (CallNotPermittedException exception) {
            log.warn("Card capture circuit is open for split payment. pgTxnId={}", captureLedger.getPgTxnId(),
                    exception);
            pgSplitPaymentWriter.fail(
                    captureLedger.getPgTxnId(),
                    token.cardCompany(),
                    PgFailureCode.CARD_CIRCUIT_OPEN,
                    null,
                    LocalDateTime.now());
            return CaptureOutcome.failed(PgFailureCode.CARD_CIRCUIT_OPEN.getMessage());
        } catch (RetryableException exception) {
            log.warn("Card capture timed out for split payment. pgTxnId={}", captureLedger.getPgTxnId(), exception);
            return recoverCaptureByInquiry(captureLedger, token, authorization);
        } catch (RuntimeException exception) {
            log.warn("Card capture failed for split payment. pgTxnId={}", captureLedger.getPgTxnId(), exception);
            pgSplitPaymentWriter.fail(
                    captureLedger.getPgTxnId(),
                    token.cardCompany(),
                    PgFailureCode.CARD_CAPTURE_FAILED,
                    null,
                    LocalDateTime.now());
            return CaptureOutcome.failed(PgFailureCode.CARD_CAPTURE_FAILED.getMessage());
        }
    }

    private CaptureOutcome recoverCaptureByInquiry(
            PgPaymentLedger captureLedger,
            BillingKeyTokenRetrieveResponse token,
            String authorization) {
        try {
            PaymentInquireResponse inquiry = pgExternalClientGateway.inquirePayment(
                    authorization,
                    pgPaymentProperties.getPgId(),
                    token.cardCompany(),
                    captureLedger.getIdempotencyKey());
            if (CardSimulatorResponseCodes.isSuccess(inquiry.responseCode())) {
                PgPaymentLedger captured = captureLedger(captureLedger, inquiry.approvalNumber(),
                        inquiry.approvedAt());
                return CaptureOutcome.success(captured);
            }
        } catch (RuntimeException exception) {
            log.warn("Split payment capture inquiry failed. pgTxnId={}", captureLedger.getPgTxnId(), exception);
        }
        pgSplitPaymentWriter.markRecoveryRequired(
                captureLedger.getPgTxnId(),
                token.cardCompany(),
                PgFailureCode.CARD_CAPTURE_RESULT_UNKNOWN,
                PgFailureCode.CARD_CAPTURE_RESULT_UNKNOWN.getMessage());
        return CaptureOutcome.recoveryRequired(PgFailureCode.CARD_CAPTURE_RESULT_UNKNOWN.getMessage());
    }

    private boolean voidHeld(List<HeldPayment> heldPayments, String authorization) {
        boolean allVoided = true;
        for (HeldPayment heldPayment : heldPayments) {
            if (!voidOneHeld(heldPayment, authorization)) {
                allVoided = false;
            }
        }
        return allVoided;
    }

    private boolean voidOneHeld(HeldPayment heldPayment, String authorization) {
        PgPaymentLedger holdLedger = heldPayment.ledger();
        BillingKeyTokenRetrieveResponse token = heldPayment.token();
        PgPaymentLedger voidLedger = pgSplitPaymentWriter.createLedgerWithCancelKey(
                holdLedger.getPgGroupId(),
                holdLedger.getSplitSeq(),
                holdLedger.getPgTxnId(),
                holdLedger.getPayPaymentId(),
                holdLedger.getBillingKey(),
                holdLedger.getMerchantId(),
                holdLedger.getAmount(),
                PgTxnType.VOID,
                token.cardCompany());
        PreApprovalCancelRequest request = new PreApprovalCancelRequest(
                pgPaymentProperties.getPgId(),
                holdLedger.getIdempotencyKey(),
                voidLedger.getPgTxnId(),
                holdLedger.getPgTxnId(),
                token.cardCompany(),
                token.cardToken(),
                holdLedger.getCardApprovalNumber());
        try {
            PreApprovalCancelResponse response = pgExternalClientGateway.cancelPreApproval(
                    authorization,
                    voidLedger.getIdempotencyKey(),
                    request);
            if (CardSimulatorResponseCodes.isSuccess(response.responseCode())) {
                LocalDateTime processedAt = cardSimulatorDateTimeParser.parseOrNow(response.cancelledAt());
                pgSplitPaymentWriter.voidHold(
                        voidLedger.getPgTxnId(),
                        pgApprovalNumberGenerator.generate(PgTxnType.VOID, voidLedger.getPgTxnId()),
                        response.preApprovalNumber(),
                        processedAt);
                return true;
            }
            pgSplitPaymentWriter.markCompensationRequired(
                    voidLedger.getPgTxnId(),
                    PgFailureCode.COMPENSATION_VOID_FAILED,
                    response.responseMessage());
        } catch (RuntimeException exception) {
            log.warn("Split payment hold void compensation failed. pgTxnId={}", voidLedger.getPgTxnId(), exception);
            pgSplitPaymentWriter.markCompensationRequired(
                    voidLedger.getPgTxnId(),
                    PgFailureCode.COMPENSATION_VOID_FAILED,
                    PgFailureCode.COMPENSATION_VOID_FAILED.getMessage());
        }
        return false;
    }

    private boolean cancelCaptured(List<PgPaymentLedger> capturedLedgers, String authorization) {
        boolean allCancelled = true;
        for (PgPaymentLedger capturedLedger : capturedLedgers) {
            if (!cancelOneCaptured(capturedLedger, authorization)) {
                allCancelled = false;
            }
        }
        return allCancelled;
    }

    private boolean cancelOneCaptured(PgPaymentLedger capturedLedger, String authorization) {
        BillingKeyTokenRetrieveResponse token = retrieveCardToken(capturedLedger, capturedLedger.getBillingKey());
        PgPaymentLedger cancelLedger = pgSplitPaymentWriter.createLedgerWithCancelKey(
                capturedLedger.getPgGroupId(),
                capturedLedger.getSplitSeq(),
                capturedLedger.getPgTxnId(),
                capturedLedger.getPayPaymentId(),
                capturedLedger.getBillingKey(),
                capturedLedger.getMerchantId(),
                capturedLedger.getAmount(),
                PgTxnType.CANCEL,
                capturedLedger.getCardCompany());
        if (token == null) {
            pgSplitPaymentWriter.markCompensationRequired(
                    cancelLedger.getPgTxnId(),
                    PgFailureCode.COMPENSATION_CANCEL_FAILED,
                    "Billing-key lookup failed for split cancel compensation.");
            return false;
        }
        PaymentCancelRequest request = new PaymentCancelRequest(
                pgPaymentProperties.getPgId(),
                capturedLedger.getIdempotencyKey(),
                cancelLedger.getPgTxnId(),
                capturedLedger.getPgTxnId(),
                token.cardCompany(),
                token.cardToken(),
                capturedLedger.getCardApprovalNumber());
        try {
            PaymentCancelResponse response = pgExternalClientGateway.cancelPayment(
                    authorization,
                    cancelLedger.getIdempotencyKey(),
                    request);
            if (CardSimulatorResponseCodes.isSuccess(response.responseCode())) {
                LocalDateTime processedAt = cardSimulatorDateTimeParser.parseOrNow(response.cancelledAt());
                pgSplitPaymentWriter.cancel(
                        cancelLedger.getPgTxnId(),
                        pgApprovalNumberGenerator.generate(PgTxnType.CANCEL, cancelLedger.getPgTxnId()),
                        response.approvalNumber(),
                        processedAt);
                return true;
            }
            pgSplitPaymentWriter.markCompensationRequired(
                    cancelLedger.getPgTxnId(),
                    PgFailureCode.COMPENSATION_CANCEL_FAILED,
                    response.responseMessage());
        } catch (RuntimeException exception) {
            log.warn("Split payment capture cancel compensation failed. pgTxnId={}", cancelLedger.getPgTxnId(),
                    exception);
            pgSplitPaymentWriter.markCompensationRequired(
                    cancelLedger.getPgTxnId(),
                    PgFailureCode.COMPENSATION_CANCEL_FAILED,
                    PgFailureCode.COMPENSATION_CANCEL_FAILED.getMessage());
        }
        return false;
    }

    private PgPaymentGroup finishGroupAfterHoldFailure(
            Long pgGroupId,
            HoldOutcome outcome,
            boolean voided) {
        if (!voided) {
            return pgSplitPaymentWriter.markGroupCompensationRequired(
                    pgGroupId,
                    PgFailureCode.COMPENSATION_VOID_FAILED,
                    "Split payment hold failed and void compensation did not finish.");
        }
        return switch (outcome.status()) {
            case REJECTED -> pgSplitPaymentWriter.rejectGroup(pgGroupId, outcome.message());
            case RECOVERY_REQUIRED -> pgSplitPaymentWriter.markGroupRecoveryRequired(
                    pgGroupId,
                    PgFailureCode.CARD_AUTH_ONLY_RESULT_UNKNOWN,
                    outcome.message());
            case FAILED, SUCCESS -> pgSplitPaymentWriter.failGroup(pgGroupId, outcome.failureCode(), outcome.message());
        };
    }

    private PgPaymentLedger holdLedger(
            PgPaymentLedger ledger,
            String cardCompany,
            String cardApprovalNumber,
            String approvedAt) {
        String pgApprovalNumber = pgApprovalNumberGenerator.generate(PgTxnType.AUTH_ONLY, ledger.getPgTxnId());
        LocalDateTime parsedApprovedAt = cardSimulatorDateTimeParser.parseOrNow(approvedAt);
        return pgSplitPaymentWriter.hold(
                ledger.getPgTxnId(),
                cardCompany,
                pgApprovalNumber,
                cardApprovalNumber,
                parsedApprovedAt);
    }

    private PgPaymentLedger captureLedger(PgPaymentLedger ledger, String cardApprovalNumber, String processedAt) {
        String pgApprovalNumber = pgApprovalNumberGenerator.generate(PgTxnType.CAPTURE, ledger.getPgTxnId());
        LocalDateTime parsedProcessedAt = cardSimulatorDateTimeParser.parseOrNow(processedAt);
        return pgSplitPaymentWriter.capture(
                ledger.getPgTxnId(),
                pgApprovalNumber,
                cardApprovalNumber,
                parsedProcessedAt);
    }

    private BillingKeyTokenRetrieveResponse retrieveCardToken(PgPaymentLedger ledger, String billingKey) {
        try {
            BillingKeyTokenRetrieveResponse token = pgExternalClientGateway.retrieveCardToken(billingKey);
            if (token == null || token.cardToken() == null || token.cardToken().isBlank()
                    || token.cardCompany() == null || token.cardCompany().isBlank()) {
                throw new IllegalStateException("Billing-key service returned an invalid card token response.");
            }
            return token;
        } catch (CallNotPermittedException exception) {
            log.warn("Billing-key circuit is open for split payment. pgTxnId={}", ledger.getPgTxnId(), exception);
            return null;
        } catch (RuntimeException exception) {
            log.warn("Billing-key lookup failed for split payment. pgTxnId={}", ledger.getPgTxnId(), exception);
            return null;
        }
    }

    private PgPaymentGroup findGroup(Long pgGroupId) {
        return pgPaymentGroupRepository.findById(pgGroupId)
                .orElseThrow(() -> new PgPaymentException(
                        ErrorCode.PG_PAYMENT_NOT_FOUND,
                        "Split payment group was not found. pgGroupId=" + pgGroupId));
    }

    private PgSplitPaymentResultResponse response(PgPaymentGroup group) {
        List<PgPaymentLedger> ledgers = pgPaymentLedgerRepository
                .findByPgGroupIdOrderBySplitSeqAscPgTxnIdAsc(group.getPgGroupId());
        return PgSplitPaymentResultResponse.from(group, ledgers);
    }

    private List<PgSplitPaymentItemRequest> sortedItems(PgSplitPaymentRequest request) {
        return request.payments().stream()
                .sorted(Comparator.comparing(PgSplitPaymentItemRequest::splitSeq))
                .toList();
    }

    private record HeldPayment(
            PgPaymentLedger ledger,
            BillingKeyTokenRetrieveResponse token
    ) {
    }

    private enum SplitStepStatus {
        SUCCESS,
        REJECTED,
        FAILED,
        RECOVERY_REQUIRED
    }

    private record HoldOutcome(
            SplitStepStatus status,
            HeldPayment heldPayment,
            String message,
            PgFailureCode failureCode
    ) {

        static HoldOutcome success(HeldPayment heldPayment) {
            return new HoldOutcome(SplitStepStatus.SUCCESS, heldPayment, null, null);
        }

        static HoldOutcome rejected(String message) {
            return new HoldOutcome(SplitStepStatus.REJECTED, null, message, null);
        }

        static HoldOutcome failed(String message, PgFailureCode failureCode) {
            return new HoldOutcome(SplitStepStatus.FAILED, null, message, failureCode);
        }

        static HoldOutcome recoveryRequired(String message) {
            return new HoldOutcome(SplitStepStatus.RECOVERY_REQUIRED, null, message,
                    PgFailureCode.CARD_AUTH_ONLY_RESULT_UNKNOWN);
        }
    }

    private record CaptureOutcome(
            SplitStepStatus status,
            PgPaymentLedger captureLedger,
            String message
    ) {

        static CaptureOutcome success(PgPaymentLedger captureLedger) {
            return new CaptureOutcome(SplitStepStatus.SUCCESS, captureLedger, null);
        }

        static CaptureOutcome failed(String message) {
            return new CaptureOutcome(SplitStepStatus.FAILED, null, message);
        }

        static CaptureOutcome recoveryRequired(String message) {
            return new CaptureOutcome(SplitStepStatus.RECOVERY_REQUIRED, null, message);
        }
    }
}
