package com.erumpay.pgpayment.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "PG-REQ-001", "INVALID_REQUEST", "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "PG-AUTH-100", "AUTHORIZATION_REQUIRED", "인증 정보가 필요합니다."),
    PG_PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PG-TXN-201", "PG_PAYMENT_NOT_FOUND", "PG 결제 거래를 찾을 수 없습니다."),
    INVALID_TRANSACTION_STATE(HttpStatus.CONFLICT, "PG-TXN-203", "INVALID_TRANSACTION_STATE",
            "현재 거래 상태에서는 요청한 작업을 처리할 수 없습니다."),
    PAYMENT_ALREADY_CANCELLED(HttpStatus.CONFLICT, "PG-TXN-204", "PAYMENT_ALREADY_CANCELLED",
            "이미 취소된 결제 거래입니다."),
    AUTH_ONLY_ALREADY_VOIDED(HttpStatus.CONFLICT, "PG-TXN-205", "AUTH_ONLY_ALREADY_VOIDED",
            "이미 해제된 가승인 거래입니다."),
    ORIGINAL_TRANSACTION_MISMATCH(HttpStatus.CONFLICT, "PG-TXN-206", "ORIGINAL_TRANSACTION_MISMATCH",
            "요청 정보가 원거래 정보와 일치하지 않습니다."),
    DUPLICATE_IDEMPOTENCY_KEY(HttpStatus.CONFLICT, "PG-IDM-301", "DUPLICATE_IDEMPOTENCY_KEY",
            "이미 사용된 멱등키입니다."),
    BILLING_KEY_CIRCUIT_OPEN(HttpStatus.SERVICE_UNAVAILABLE, "PG-BILL-401", "BILLING_KEY_CIRCUIT_OPEN",
            "빌링키 서비스 회로가 열려 있습니다."),
    BILLING_KEY_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "PG-BILL-402", "BILLING_KEY_TIMEOUT",
            "빌링키 서비스 응답 시간이 초과되었습니다."),
    CARD_CIRCUIT_OPEN(HttpStatus.SERVICE_UNAVAILABLE, "PG-CARD-401", "CARD_CIRCUIT_OPEN",
            "카드 시뮬레이터 회로가 열려 있습니다."),
    CARD_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "PG-CARD-406", "CARD_TIMEOUT",
            "카드 시뮬레이터 응답 시간이 초과되었습니다."),
    COMPENSATION_FAILED(HttpStatus.BAD_GATEWAY, "PG-CMP-406", "COMPENSATION_FAILED",
            "보상 트랜잭션 처리에 실패했습니다."),
    LEDGER_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PG-LED-901", "LEDGER_SAVE_FAILED",
            "PG 결제 원장 저장에 실패했습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "PG-SYS-900", "INTERNAL_SERVER_ERROR",
            "알 수 없는 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String reason;
    private final String message;
}
