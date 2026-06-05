package com.erumpay.pgpayment.client.cardsimulator;

import java.util.Set;

public final class CardSimulatorResponseCodes {

    private static final Set<String> SUCCESS_CODES = Set.of(
            "SIM-PAYMENT-300",
            "SIM-TRANSACTION-400",
            "300",
            "400");

    private static final Set<String> PAYMENT_REJECTED_CODES = Set.of(
            "SIM-PAYMENT-301",
            "SIM-PAYMENT-302",
            "SIM-PAYMENT-303",
            "SIM-PAYMENT-304",
            "SIM-PAYMENT-305",
            "SIM-PAYMENT-306",
            "SIM-PAYMENT-307",
            "301",
            "302",
            "303");

    private CardSimulatorResponseCodes() {
    }

    public static boolean isSuccess(String responseCode) {
        System.out.println("Response Code: " + responseCode); // 로그 추가
        return SUCCESS_CODES.contains(normalize(responseCode));
    }

    public static boolean isPaymentRejected(String responseCode) {
        return PAYMENT_REJECTED_CODES.contains(normalize(responseCode));
    }

    private static String normalize(String responseCode) {
        return responseCode == null ? "" : responseCode.trim();
    }
}
