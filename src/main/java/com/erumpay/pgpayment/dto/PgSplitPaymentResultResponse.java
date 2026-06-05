package com.erumpay.pgpayment.dto;

import com.erumpay.pgpayment.domain.entity.PgPaymentGroup;
import com.erumpay.pgpayment.domain.entity.PgPaymentLedger;
import com.erumpay.pgpayment.domain.enums.PgFailureCode;
import com.erumpay.pgpayment.domain.enums.PgPaymentGroupStatus;
import com.erumpay.pgpayment.domain.enums.PgTxnType;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record PgSplitPaymentResultResponse(
        Long pgGroupId,
        Long payPaymentId,
        Long merchantId,
        Long totalAmount,
        PgPaymentGroupStatus status,
        String failureCode,
        String failureReason,
        String failureMessage,
        List<PgPaymentResultResponse> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static PgSplitPaymentResultResponse from(PgPaymentGroup group, List<PgPaymentLedger> ledgers) {
        return new PgSplitPaymentResultResponse(
                group.getPgGroupId(),
                group.getPayPaymentId(),
                group.getMerchantId(),
                group.getTotalAmount(),
                group.getStatus(),
                group.getFailureCode(),
                failureReason(group.getFailureCode()),
                group.getFailureMessage(),
                summarizeBySplitSeq(ledgers).stream()
                        .map(PgPaymentResultResponse::from)
                        .toList(),
                group.getCreatedAt(),
                group.getUpdatedAt()
        );
    }

    private static List<PgPaymentLedger> summarizeBySplitSeq(List<PgPaymentLedger> ledgers) {
        return ledgers.stream()
                .filter(ledger -> ledger.getSplitSeq() != null)
                .collect(Collectors.toMap(
                        PgPaymentLedger::getSplitSeq,
                        Function.identity(),
                        PgSplitPaymentResultResponse::selectRepresentative
                ))
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    private static PgPaymentLedger selectRepresentative(PgPaymentLedger left, PgPaymentLedger right) {
        int leftPriority = txnTypePriority(left.getTxnType());
        int rightPriority = txnTypePriority(right.getTxnType());
        if (leftPriority != rightPriority) {
            return leftPriority > rightPriority ? left : right;
        }
        return Comparator.comparing(PgPaymentLedger::getPgTxnId)
                .compare(left, right) >= 0 ? left : right;
    }

    private static int txnTypePriority(PgTxnType txnType) {
        return switch (txnType) {
            case CANCEL -> 5;
            case VOID -> 4;
            case CAPTURE -> 3;
            case AUTH_ONLY -> 2;
            case AUTH -> 1;
        };
    }

    private static String failureReason(String failureCode) {
        return PgFailureCode.fromCode(failureCode)
                .map(PgFailureCode::getReason)
                .orElse(null);
    }
}
