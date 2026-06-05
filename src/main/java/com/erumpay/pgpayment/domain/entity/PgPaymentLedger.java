package com.erumpay.pgpayment.domain.entity;

import com.erumpay.pgpayment.domain.enums.PgFailureCode;
import com.erumpay.pgpayment.domain.enums.PgPaymentStatus;
import com.erumpay.pgpayment.domain.enums.PgTxnType;
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
@Table(name = "pg_payment_ledger", uniqueConstraints = {
        @UniqueConstraint(name = "uk_pg_payment_ledger_idempotency", columnNames = "idempotency_key")
}, indexes = {
        @Index(name = "idx_pg_payment_ledger_pay_payment", columnList = "pay_payment_id"),
        @Index(name = "idx_pg_payment_ledger_group", columnList = "pg_group_id"),
        @Index(name = "idx_pg_payment_ledger_group_split", columnList = "pg_group_id, split_seq"),
        @Index(name = "idx_pg_payment_ledger_original", columnList = "original_txn_id"),
        @Index(name = "idx_pg_payment_ledger_hold", columnList = "hold_txn_id"),
        @Index(name = "idx_pg_payment_ledger_merchant_created", columnList = "merchant_id, created_at"),
        @Index(name = "idx_pg_payment_ledger_status", columnList = "status")
})
public class PgPaymentLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pg_txn_id")
    private Long pgTxnId;

    @Column(name = "pg_group_id")
    private Long pgGroupId;

    @Column(name = "split_seq")
    private Integer splitSeq;

    @Column(name = "original_txn_id")
    private Long originalTxnId;

    @Column(name = "pay_payment_id", nullable = false)
    private Long payPaymentId;

    @Column(name = "hold_txn_id")
    private Long holdTxnId;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "billing_key", nullable = false, length = 100)
    private String billingKey;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, columnDefinition = "ENUM('AUTH','AUTH_ONLY','CAPTURE','VOID','CANCEL')")
    private PgTxnType txnType;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "ENUM('REQUESTED','HELD','APPROVED','CAPTURED','REJECTED','FAILED','CANCELLED','VOIDED','RECOVERY_REQUIRED','COMPENSATION_REQUIRED')")
    private PgPaymentStatus status = PgPaymentStatus.REQUESTED;

    @Column(name = "pg_approval_number", length = 50)
    private String pgApprovalNumber;

    @Column(name = "card_company", nullable = false, length = 50)
    private String cardCompany;

    @Column(name = "card_approval_number", length = 50)
    private String cardApprovalNumber;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "failure_message", length = 255)
    private String failureMessage;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void updateCardCompany(String cardCompany) {
        this.cardCompany = cardCompany;
    }

    public void updateIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public void approve(String pgApprovalNumber, String cardApprovalNumber, LocalDateTime approvedAt) {
        this.status = PgPaymentStatus.APPROVED;
        this.pgApprovalNumber = pgApprovalNumber;
        this.cardApprovalNumber = cardApprovalNumber;
        this.rejectReason = null;
        this.failureCode = null;
        this.failureMessage = null;
        this.approvedAt = approvedAt;
        this.processedAt = approvedAt;
    }

    public void hold(String pgApprovalNumber, String cardApprovalNumber, LocalDateTime approvedAt) {
        this.status = PgPaymentStatus.HELD;
        this.pgApprovalNumber = pgApprovalNumber;
        this.cardApprovalNumber = cardApprovalNumber;
        this.rejectReason = null;
        this.failureCode = null;
        this.failureMessage = null;
        this.approvedAt = approvedAt;
        this.processedAt = approvedAt;
    }

    public void capture(String pgApprovalNumber, String cardApprovalNumber, LocalDateTime processedAt) {
        this.status = PgPaymentStatus.CAPTURED;
        this.pgApprovalNumber = pgApprovalNumber;
        this.cardApprovalNumber = cardApprovalNumber;
        this.rejectReason = null;
        this.failureCode = null;
        this.failureMessage = null;
        this.approvedAt = null;
        this.processedAt = processedAt;
    }

    public void reject(String rejectReason, LocalDateTime processedAt) {
        this.status = PgPaymentStatus.REJECTED;
        this.pgApprovalNumber = null;
        this.cardApprovalNumber = null;
        this.rejectReason = rejectReason;
        this.failureCode = null;
        this.failureMessage = null;
        this.approvedAt = null;
        this.processedAt = processedAt;
    }

    public void cancel(String pgApprovalNumber, String cardApprovalNumber, LocalDateTime processedAt) {
        this.status = PgPaymentStatus.CANCELLED;
        this.pgApprovalNumber = pgApprovalNumber;
        this.cardApprovalNumber = cardApprovalNumber;
        this.rejectReason = null;
        this.failureCode = null;
        this.failureMessage = null;
        this.approvedAt = null;
        this.processedAt = processedAt;
    }

    public void voidHold(String pgApprovalNumber, String cardApprovalNumber, LocalDateTime processedAt) {
        this.status = PgPaymentStatus.VOIDED;
        this.pgApprovalNumber = pgApprovalNumber;
        this.cardApprovalNumber = cardApprovalNumber;
        this.rejectReason = null;
        this.failureCode = null;
        this.failureMessage = null;
        this.approvedAt = null;
        this.processedAt = processedAt;
    }

    public void fail(PgFailureCode failureCode, String failureMessage, LocalDateTime processedAt) {
        this.status = PgPaymentStatus.FAILED;
        this.pgApprovalNumber = null;
        this.cardApprovalNumber = null;
        this.rejectReason = null;
        this.failureCode = failureCode.getCode();
        this.failureMessage = failureMessage == null ? failureCode.getMessage() : failureMessage;
        this.approvedAt = null;
        this.processedAt = processedAt;
    }

    public void markRecoveryRequired(String failureMessage) {
        markRecoveryRequired(null, PgFailureCode.LEDGER_RECOVERY_REQUIRED, failureMessage);
    }

    public void markRecoveryRequired(String cardCompany, PgFailureCode failureCode, String failureMessage) {
        if (cardCompany != null && !cardCompany.isBlank()) {
            this.cardCompany = cardCompany;
        }
        this.failureCode = failureCode.getCode();
        this.failureMessage = failureMessage == null ? failureCode.getMessage() : failureMessage;
        this.retryCount = this.retryCount + 1;
    }

    public void markCompensationRequired(PgFailureCode failureCode, String failureMessage) {
        this.status = PgPaymentStatus.COMPENSATION_REQUIRED;
        this.failureCode = failureCode.getCode();
        this.failureMessage = failureMessage == null ? failureCode.getMessage() : failureMessage;
        this.retryCount = this.retryCount + 1;
    }

    public void markReconciliationRetryExhausted(String failureMessage) {
        this.failureCode = PgFailureCode.RECONCILIATION_RETRY_EXHAUSTED.getCode();
        this.failureMessage = failureMessage == null
                ? PgFailureCode.RECONCILIATION_RETRY_EXHAUSTED.getMessage()
                : failureMessage;
        this.retryCount = this.retryCount + 1;
    }
}
