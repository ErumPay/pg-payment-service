package com.erumpay.pgpayment.domain.enums;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PgFailureCode {
    BILLING_KEY_LOOKUP_FAILED("PG-BILL-400", "BILLING_KEY_LOOKUP_FAILED", "빌링키 토큰 조회에 실패했습니다."),
    BILLING_KEY_CIRCUIT_OPEN("PG-BILL-401", "BILLING_KEY_CIRCUIT_OPEN", "빌링키 서비스 회로가 열려 있습니다."),
    CARD_REQUEST_FAILED("PG-CARD-400", "CARD_REQUEST_FAILED", "카드사 승인 요청에 실패했습니다."),
    CARD_CIRCUIT_OPEN("PG-CARD-401", "CARD_CIRCUIT_OPEN", "카드 시뮬레이터 회로가 열려 있습니다."),
    CARD_RESULT_UNKNOWN("PG-CARD-402", "CARD_RESULT_UNKNOWN", "카드사 승인 결과를 확인할 수 없습니다."),
    CARD_AUTH_ONLY_FAILED("PG-CARD-403", "CARD_AUTH_ONLY_FAILED", "카드사 가승인 요청에 실패했습니다."),
    CARD_AUTH_ONLY_RESULT_UNKNOWN("PG-CARD-404", "CARD_AUTH_ONLY_RESULT_UNKNOWN", "카드사 가승인 결과를 확인할 수 없습니다."),
    CARD_TIMEOUT_UNCONFIRMED("PG-CARD-405", "CARD_TIMEOUT_UNCONFIRMED", "카드사 응답 지연으로 거래 결과를 확정하지 못했습니다."),
    CARD_CAPTURE_FAILED("PG-CARD-407", "CARD_CAPTURE_FAILED", "카드사 매입 요청에 실패했습니다."),
    CARD_CAPTURE_RESULT_UNKNOWN("PG-CARD-408", "CARD_CAPTURE_RESULT_UNKNOWN", "카드사 매입 결과를 확인할 수 없습니다."),
    CARD_CANCEL_FAILED("PG-CMP-400", "CARD_CANCEL_FAILED", "카드사 결제 취소 요청에 실패했습니다."),
    CARD_CANCEL_RESULT_UNKNOWN("PG-CMP-401", "CARD_CANCEL_RESULT_UNKNOWN", "카드사 결제 취소 결과를 확인할 수 없습니다."),
    CARD_VOID_FAILED("PG-CMP-402", "CARD_VOID_FAILED", "카드사 가승인 해제 요청에 실패했습니다."),
    CARD_VOID_RESULT_UNKNOWN("PG-CMP-403", "CARD_VOID_RESULT_UNKNOWN", "카드사 가승인 해제 결과를 확인할 수 없습니다."),
    LEDGER_RECOVERY_REQUIRED("PG-REC-400", "LEDGER_RECOVERY_REQUIRED", "PG 원장 상태 보정이 필요합니다."),
    RECONCILIATION_RETRY_EXHAUSTED("PG-REC-401", "RECONCILIATION_RETRY_EXHAUSTED",
            "PG 원장 상태 자동 보정 재시도 횟수를 초과했습니다."),
    LEDGER_SAVE_FAILED("PG-LED-901", "LEDGER_SAVE_FAILED", "PG 결제 원장 저장에 실패했습니다."),
    COMPENSATION_CANCEL_FAILED("PG-CMP-404", "COMPENSATION_CANCEL_FAILED", "결제 취소 보상 트랜잭션에 실패했습니다."),
    COMPENSATION_VOID_FAILED("PG-CMP-405", "COMPENSATION_VOID_FAILED", "가승인 해제 보상 트랜잭션에 실패했습니다.");

    private final String code;
    private final String reason;
    private final String message;

    public static Optional<PgFailureCode> fromCode(String code) {
        return Arrays.stream(values())
                .filter(failureCode -> failureCode.code.equals(code))
                .findFirst();
    }
}
