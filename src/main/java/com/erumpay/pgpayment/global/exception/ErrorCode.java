package com.erumpay.pgpayment.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required."),
    PG_PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PG_PAYMENT_NOT_FOUND", "PG payment transaction was not found."),
    DUPLICATE_IDEMPOTENCY_KEY(HttpStatus.CONFLICT, "DUPLICATE_IDEMPOTENCY_KEY", "Idempotency key already exists."),
    LEDGER_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "LEDGER_SAVE_FAILED", "Failed to save PG payment ledger."),
    BILLING_KEY_CIRCUIT_OPEN(HttpStatus.SERVICE_UNAVAILABLE, "BILLING_KEY_CIRCUIT_OPEN", "Billing-key circuit is open."),
    CARD_CIRCUIT_OPEN(HttpStatus.SERVICE_UNAVAILABLE, "CARD_CIRCUIT_OPEN", "Card circuit is open."),
    EXTERNAL_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "EXTERNAL_TIMEOUT", "External service timed out."),
    COMPENSATION_FAILED(HttpStatus.BAD_GATEWAY, "COMPENSATION_FAILED", "Compensation transaction failed."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "Internal server error.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
