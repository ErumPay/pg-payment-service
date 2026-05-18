package com.erumpay.pgpayment.repository;

import com.erumpay.pgpayment.domain.entity.PgPaymentLedger;
import com.erumpay.pgpayment.domain.enums.PgPaymentStatus;
import com.erumpay.pgpayment.domain.enums.PgTxnType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PgPaymentLedgerRepository extends JpaRepository<PgPaymentLedger, Long>,
        JpaSpecificationExecutor<PgPaymentLedger> {

    Optional<PgPaymentLedger> findByPayPaymentId(Long payPaymentId);

    Optional<PgPaymentLedger> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    boolean existsByOriginalTxnIdAndTxnTypeAndStatus(
            Long originalTxnId,
            PgTxnType txnType,
            PgPaymentStatus status
    );

    Page<PgPaymentLedger> findByMerchantId(Long merchantId, Pageable pageable);

    @Query("""
            select coalesce(sum(ledger.amount), 0)
            from PgPaymentLedger ledger
            where ledger.holdTxnId = :holdTxnId
              and ledger.txnType = :txnType
              and ledger.status = :status
            """)
    Long sumAmountByHoldTxnIdAndTxnTypeAndStatus(
            @Param("holdTxnId") Long holdTxnId,
            @Param("txnType") PgTxnType txnType,
            @Param("status") PgPaymentStatus status
    );
}
