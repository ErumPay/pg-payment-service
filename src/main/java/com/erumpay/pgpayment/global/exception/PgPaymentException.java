package com.erumpay.pgpayment.global.exception;

import lombok.Getter;

@Getter
public class PgPaymentException extends RuntimeException {

    private final ErrorCode errorCode;

    public PgPaymentException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public PgPaymentException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public PgPaymentException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
