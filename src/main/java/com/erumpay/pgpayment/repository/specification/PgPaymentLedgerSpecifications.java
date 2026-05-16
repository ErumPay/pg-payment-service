package com.erumpay.pgpayment.repository.specification;

import com.erumpay.pgpayment.domain.entity.PgPaymentLedger;
import com.erumpay.pgpayment.dto.PgPaymentLedgerSearchCondition;
import java.time.LocalDateTime;
import org.springframework.data.jpa.domain.Specification;

public final class PgPaymentLedgerSpecifications {

    private PgPaymentLedgerSpecifications() {
    }

    public static Specification<PgPaymentLedger> search(PgPaymentLedgerSearchCondition condition) {
        return Specification.allOf(
                merchantIdEquals(condition.merchantId()),
                statusEquals(condition.status()),
                createdAtGreaterThanOrEqualTo(condition.from() == null ? null : condition.from().atStartOfDay()),
                createdAtLessThan(condition.to() == null ? null : condition.to().plusDays(1).atStartOfDay()),
                amountGreaterThanOrEqualTo(condition.minAmount()),
                amountLessThanOrEqualTo(condition.maxAmount())
        );
    }

    private static Specification<PgPaymentLedger> merchantIdEquals(Long merchantId) {
        return (root, query, criteriaBuilder) -> merchantId == null
                ? null
                : criteriaBuilder.equal(root.get("merchantId"), merchantId);
    }

    private static Specification<PgPaymentLedger> statusEquals(Object status) {
        return (root, query, criteriaBuilder) -> status == null
                ? null
                : criteriaBuilder.equal(root.get("status"), status);
    }

    private static Specification<PgPaymentLedger> createdAtGreaterThanOrEqualTo(LocalDateTime from) {
        return (root, query, criteriaBuilder) -> from == null
                ? null
                : criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    private static Specification<PgPaymentLedger> createdAtLessThan(LocalDateTime toExclusive) {
        return (root, query, criteriaBuilder) -> toExclusive == null
                ? null
                : criteriaBuilder.lessThan(root.get("createdAt"), toExclusive);
    }

    private static Specification<PgPaymentLedger> amountGreaterThanOrEqualTo(Long minAmount) {
        return (root, query, criteriaBuilder) -> minAmount == null
                ? null
                : criteriaBuilder.greaterThanOrEqualTo(root.get("amount"), minAmount);
    }

    private static Specification<PgPaymentLedger> amountLessThanOrEqualTo(Long maxAmount) {
        return (root, query, criteriaBuilder) -> maxAmount == null
                ? null
                : criteriaBuilder.lessThanOrEqualTo(root.get("amount"), maxAmount);
    }
}
