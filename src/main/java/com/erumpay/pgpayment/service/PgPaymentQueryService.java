package com.erumpay.pgpayment.service;

import com.erumpay.pgpayment.domain.entity.PgPaymentLedger;
import com.erumpay.pgpayment.dto.AdminPgPaymentLedgerItemResponse;
import com.erumpay.pgpayment.dto.AdminPgPaymentLedgerListResponse;
import com.erumpay.pgpayment.dto.MerchantPgPaymentLedgerItemResponse;
import com.erumpay.pgpayment.dto.MerchantPgPaymentLedgerListResponse;
import com.erumpay.pgpayment.dto.PgPaymentDetailResponse;
import com.erumpay.pgpayment.dto.PgPaymentLedgerSearchCondition;
import com.erumpay.pgpayment.dto.PgPaymentResultResponse;
import com.erumpay.pgpayment.global.exception.ErrorCode;
import com.erumpay.pgpayment.global.exception.PgPaymentException;
import com.erumpay.pgpayment.repository.PgPaymentLedgerRepository;
import com.erumpay.pgpayment.repository.specification.PgPaymentLedgerSpecifications;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PgPaymentQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PgPaymentLedgerRepository pgPaymentLedgerRepository;

    public PgPaymentResultResponse getPaymentResult(Long pgTxnId) {
        return PgPaymentResultResponse.from(findLedger(pgTxnId));
    }

    public PgPaymentDetailResponse getPaymentDetail(Long pgTxnId) {
        return PgPaymentDetailResponse.from(findLedger(pgTxnId));
    }

    public MerchantPgPaymentLedgerListResponse getMerchantPayments(
            Long merchantId,
            PgPaymentLedgerSearchCondition condition,
            int page,
            int size
    ) {
        PgPaymentLedgerSearchCondition merchantCondition = new PgPaymentLedgerSearchCondition(
                condition.from(),
                condition.to(),
                merchantId,
                condition.status(),
                condition.minAmount(),
                condition.maxAmount()
        );
        validateCondition(merchantCondition, page, size);

        Page<PgPaymentLedger> ledgers = pgPaymentLedgerRepository.findAll(
                PgPaymentLedgerSpecifications.search(merchantCondition),
                pageRequest(page, size)
        );
        List<MerchantPgPaymentLedgerItemResponse> items = ledgers.getContent().stream()
                .map(MerchantPgPaymentLedgerItemResponse::from)
                .toList();

        return new MerchantPgPaymentLedgerListResponse(
                merchantId,
                condition.from(),
                condition.to(),
                page,
                size,
                ledgers.getTotalElements(),
                items
        );
    }

    public AdminPgPaymentLedgerListResponse getAdminPayments(
            PgPaymentLedgerSearchCondition condition,
            int page,
            int size
    ) {
        validateCondition(condition, page, size);

        Page<PgPaymentLedger> ledgers = pgPaymentLedgerRepository.findAll(
                PgPaymentLedgerSpecifications.search(condition),
                pageRequest(page, size)
        );
        List<AdminPgPaymentLedgerItemResponse> items = ledgers.getContent().stream()
                .map(AdminPgPaymentLedgerItemResponse::from)
                .toList();

        return new AdminPgPaymentLedgerListResponse(
                condition.from(),
                condition.to(),
                page,
                size,
                ledgers.getTotalElements(),
                items
        );
    }

    private PgPaymentLedger findLedger(Long pgTxnId) {
        return pgPaymentLedgerRepository.findById(pgTxnId)
                .orElseThrow(() -> new PgPaymentException(
                        ErrorCode.PG_PAYMENT_NOT_FOUND,
                        "PG payment transaction was not found. pgTxnId=" + pgTxnId
                ));
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("pgTxnId"))
        );
    }

    private void validateCondition(PgPaymentLedgerSearchCondition condition, int page, int size) {
        validateDateRange(condition.from(), condition.to());
        validateAmountRange(condition.minAmount(), condition.maxAmount());
        validatePage(page, size);
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new PgPaymentException(ErrorCode.INVALID_REQUEST, "from must be earlier than or equal to to.");
        }
    }

    private void validateAmountRange(Long minAmount, Long maxAmount) {
        if (minAmount != null && maxAmount != null && minAmount > maxAmount) {
            throw new PgPaymentException(ErrorCode.INVALID_REQUEST, "minAmount must be less than or equal to maxAmount.");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new PgPaymentException(ErrorCode.INVALID_REQUEST, "page must be greater than or equal to 0.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new PgPaymentException(ErrorCode.INVALID_REQUEST, "size must be between 1 and 100.");
        }
    }
}
