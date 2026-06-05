package com.erumpay.pgpayment.controller;

import com.erumpay.pgpayment.dto.PgPaymentAuthOnlyRequest;
import com.erumpay.pgpayment.dto.PgPaymentAuthRequest;
import com.erumpay.pgpayment.dto.PgPaymentCancelRequest;
import com.erumpay.pgpayment.dto.PgPaymentResultResponse;
import com.erumpay.pgpayment.dto.PgSplitPaymentRequest;
import com.erumpay.pgpayment.dto.PgSplitPaymentResultResponse;
import com.erumpay.pgpayment.dto.PgPaymentVoidRequest;
import com.erumpay.pgpayment.global.exception.ErrorCode;
import com.erumpay.pgpayment.global.exception.PgPaymentException;
import com.erumpay.pgpayment.service.PgPaymentCommandService;
import com.erumpay.pgpayment.service.PgSplitPaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/pg")
public class PgPaymentCommandController {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    private final PgPaymentCommandService pgPaymentCommandService;
    private final PgSplitPaymentService pgSplitPaymentService;

    @PostMapping("/payments")
    public PgPaymentResultResponse authorize(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PgPaymentAuthRequest request) {
        validateAuthorization(authorization);
        validateIdempotencyKey(idempotencyKey);
        return pgPaymentCommandService.authorize(request, authorization, idempotencyKey);
    }

    @PostMapping("/payments/auth-only")
    public PgPaymentResultResponse authorizeOnly(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PgPaymentAuthOnlyRequest request) {
        validateAuthorization(authorization);
        validateIdempotencyKey(idempotencyKey);
        return pgPaymentCommandService.authorizeOnly(request, authorization, idempotencyKey);
    }

    @PostMapping("/payments/{pgTxnId}/cancel")
    public PgPaymentResultResponse cancel(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable @Positive Long pgTxnId,
            @Valid @RequestBody PgPaymentCancelRequest request) {
        validateAuthorization(authorization);
        validateIdempotencyKey(idempotencyKey);
        return pgPaymentCommandService.cancel(pgTxnId, request, authorization, idempotencyKey);
    }

    @PostMapping("/payments/{pgTxnId}/void")
    public PgPaymentResultResponse voidHold(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable @Positive Long pgTxnId,
            @Valid @RequestBody PgPaymentVoidRequest request) {
        validateAuthorization(authorization);
        validateIdempotencyKey(idempotencyKey);
        return pgPaymentCommandService.voidHold(pgTxnId, request, authorization, idempotencyKey);
    }

    @PostMapping("/payments/split")
    public PgSplitPaymentResultResponse splitAuthorize(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PgSplitPaymentRequest request) {
        validateAuthorization(authorization);
        validateIdempotencyKey(idempotencyKey);
        return pgSplitPaymentService.splitAuthorize(request, authorization, idempotencyKey);
    }

    @PostMapping("/payments/split/{pgGroupId}/cancel")
    public PgSplitPaymentResultResponse cancelSplitPayment(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable @Positive Long pgGroupId,
            @Valid @RequestBody PgPaymentCancelRequest request) {
        validateAuthorization(authorization);
        validateIdempotencyKey(idempotencyKey);
        return pgSplitPaymentService.cancelSplitPayment(pgGroupId, request, authorization, idempotencyKey);
    }

    private void validateAuthorization(String authorization) {
        if (authorization == null || authorization.isBlank() || !authorization.startsWith("Bearer ")) {
            throw new PgPaymentException(ErrorCode.UNAUTHORIZED, "Authorization 헤더는 Bearer 토큰 형식이어야 합니다.");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            throw new PgPaymentException(ErrorCode.UNAUTHORIZED, "Authorization 헤더에 Bearer 토큰 값이 필요합니다.");
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new PgPaymentException(ErrorCode.INVALID_REQUEST, "Idempotency-Key 헤더가 필요합니다.");
        }
        if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new PgPaymentException(ErrorCode.INVALID_REQUEST, "Idempotency-Key는 64자 이하여야 합니다.");
        }
    }
}
