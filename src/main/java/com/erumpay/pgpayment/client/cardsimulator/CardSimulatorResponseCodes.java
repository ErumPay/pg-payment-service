package com.erumpay.pgpayment.client.cardsimulator;

import java.util.Set;

public final class CardSimulatorResponseCodes {

    private static final Set<String> SUCCESS_CODES = Set.of(
            "SIM-PAY-300",
            "SIM-TRX-400",
            "300",
            "400");

    private static final Set<String> PAYMENT_REJECTED_CODES = Set.of(
            "SIM-PAY-301",
            "SIM-PAY-302",
            "SIM-PAY-303",
            "SIM-PAY-304",
            "SIM-PAY-305",
            "SIM-PAY-306",
            "SIM-PAY-307",
            "301",
            "302",
            "303");

    private CardSimulatorResponseCodes() {
    }

    public static boolean isSuccess(String responseCode) {
        return SUCCESS_CODES.contains(normalize(responseCode));
    }

    public static boolean isPaymentRejected(String responseCode) {
        return PAYMENT_REJECTED_CODES.contains(normalize(responseCode));
    }

    private static String normalize(String responseCode) {
        return responseCode == null ? "" : responseCode.trim();
    }
}
