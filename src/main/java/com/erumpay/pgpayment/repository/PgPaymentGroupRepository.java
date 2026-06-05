package com.erumpay.pgpayment.repository;

import com.erumpay.pgpayment.domain.entity.PgPaymentGroup;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PgPaymentGroupRepository extends JpaRepository<PgPaymentGroup, Long> {

    Optional<PgPaymentGroup> findByIdempotencyKey(String idempotencyKey);
}
