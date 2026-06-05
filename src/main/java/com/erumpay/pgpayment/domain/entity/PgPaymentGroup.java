package com.erumpay.pgpayment.domain.entity;

import com.erumpay.pgpayment.domain.enums.PgFailureCode;
import com.erumpay.pgpayment.domain.enums.PgPaymentGroupStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "pg_payment_group", uniqueConstraints = {
        @UniqueConstraint(name = "uk_pg_payment_group_idempotency", columnNames = "idempotency_key")
}, indexes = {
        @Index(name = "idx_pg_payment_group_pay_payment", columnList = "pay_payment_id"),
        @Index(name = "idx_pg_payment_group_merchant_created", columnList = "merchant_id, created_at"),
        @Index(name = "idx_pg_payment_group_status", columnList = "status")
})
public class PgPaymentGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pg_group_id")
    private Long pgGroupId;

    @Column(name = "pay_payment_id", nullable = false)
    private Long payPaymentId;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "ENUM('REQUESTED','HOLDING','HELD','CAPTURING','APPROVED','REJECTED','FAILED','VOIDING','CANCELLING','CANCELLED','RECOVERY_REQUIRED','COMPENSATION_REQUIRED')")
    private PgPaymentGroupStatus status = PgPaymentGroupStatus.REQUESTED;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "failure_message", length = 255)
    private String failureMessage;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void changeStatus(PgPaymentGroupStatus status) {
        this.status = status;
        this.failureCode = null;
        this.failureMessage = null;
    }

    public void reject(String rejectReason) {
        this.status = PgPaymentGroupStatus.REJECTED;
        this.failureCode = null;
        this.failureMessage = rejectReason;
    }

    public void fail(PgFailureCode failureCode, String failureMessage) {
        this.status = PgPaymentGroupStatus.FAILED;
        this.failureCode = failureCode.getCode();
        this.failureMessage = failureMessage == null ? failureCode.getMessage() : failureMessage;
    }

    public void markRecoveryRequired(PgFailureCode failureCode, String failureMessage) {
        this.status = PgPaymentGroupStatus.RECOVERY_REQUIRED;
        this.failureCode = failureCode.getCode();
        this.failureMessage = failureMessage == null ? failureCode.getMessage() : failureMessage;
        this.retryCount = this.retryCount + 1;
    }

    public void markCompensationRequired(PgFailureCode failureCode, String failureMessage) {
        this.status = PgPaymentGroupStatus.COMPENSATION_REQUIRED;
        this.failureCode = failureCode.getCode();
        this.failureMessage = failureMessage == null ? failureCode.getMessage() : failureMessage;
        this.retryCount = this.retryCount + 1;
    }
}
