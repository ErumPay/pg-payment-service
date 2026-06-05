package com.erumpay.pgpayment.domain.enums;

public enum PgPaymentGroupStatus {
    REQUESTED,
    HOLDING,
    HELD,
    CAPTURING,
    APPROVED,
    REJECTED,
    FAILED,
    VOIDING,
    CANCELLING,
    CANCELLED,
    RECOVERY_REQUIRED,
    COMPENSATION_REQUIRED
}
