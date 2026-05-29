package com.erumpay.pgpayment.repository;

import com.erumpay.pgpayment.domain.entity.PgPaymentLedger;
import com.erumpay.pgpayment.domain.enums.PgPaymentStatus;
import com.erumpay.pgpayment.domain.enums.PgTxnType;
import java.time.LocalDateTime;
import java.util.List;
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
            select ledger
            from PgPaymentLedger ledger
            where ledger.status = :status
              and ledger.txnType in :txnTypes
              and ledger.failureCode in :failureCodes
              and ledger.retryCount < :maxAttempts
              and ledger.updatedAt <= :updatedBefore
            order by ledger.updatedAt asc, ledger.pgTxnId asc
            """)
    List<PgPaymentLedger> findReconciliationTargets(
            @Param("status") PgPaymentStatus status,
            @Param("txnTypes") List<PgTxnType> txnTypes,
            @Param("failureCodes") List<String> failureCodes,
            @Param("maxAttempts") int maxAttempts,
            @Param("updatedBefore") LocalDateTime updatedBefore,
            Pageable pageable
    );

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
