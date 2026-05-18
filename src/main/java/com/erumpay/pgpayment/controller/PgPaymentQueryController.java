package com.erumpay.pgpayment.controller;

import com.erumpay.pgpayment.domain.enums.PgPaymentStatus;
import com.erumpay.pgpayment.dto.AdminPgPaymentLedgerListResponse;
import com.erumpay.pgpayment.dto.MerchantPgPaymentLedgerListResponse;
import com.erumpay.pgpayment.dto.PgPaymentDetailResponse;
import com.erumpay.pgpayment.dto.PgPaymentLedgerSearchCondition;
import com.erumpay.pgpayment.dto.PgPaymentResultResponse;
import com.erumpay.pgpayment.global.exception.ErrorCode;
import com.erumpay.pgpayment.global.exception.PgPaymentException;
import com.erumpay.pgpayment.service.PgPaymentQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/pg")
public class PgPaymentQueryController {

    private final PgPaymentQueryService pgPaymentQueryService;

    @GetMapping("/payments/{pgTxnId}/result")
    public PgPaymentResultResponse getPaymentResult(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable @Positive Long pgTxnId
    ) {
        validateAuthorization(authorization);
        return pgPaymentQueryService.getPaymentResult(pgTxnId);
    }

    @GetMapping("/payments/{pgTxnId}")
    public PgPaymentDetailResponse getPaymentDetail(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable @Positive Long pgTxnId
    ) {
        validateAuthorization(authorization);
        return pgPaymentQueryService.getPaymentDetail(pgTxnId);
    }

    @GetMapping("/merchants/{merchantId}/payments")
    public MerchantPgPaymentLedgerListResponse getMerchantPayments(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable @Positive Long merchantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) PgPaymentStatus status,
            @RequestParam(required = false) @Min(0) Long minAmount,
            @RequestParam(required = false) @Min(0) Long maxAmount,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        validateAuthorization(authorization);
        PgPaymentLedgerSearchCondition condition = new PgPaymentLedgerSearchCondition(
                from,
                to,
                merchantId,
                status,
                minAmount,
                maxAmount
        );
        return pgPaymentQueryService.getMerchantPayments(merchantId, condition, page, size);
    }

    @GetMapping("/admin/payments")
    public AdminPgPaymentLedgerListResponse getAdminPayments(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @Positive Long merchantId,
            @RequestParam(required = false) PgPaymentStatus status,
            @RequestParam(required = false) @Min(0) Long minAmount,
            @RequestParam(required = false) @Min(0) Long maxAmount,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        validateAuthorization(authorization);
        PgPaymentLedgerSearchCondition condition = new PgPaymentLedgerSearchCondition(
                from,
                to,
                merchantId,
                status,
                minAmount,
                maxAmount
        );
        return pgPaymentQueryService.getAdminPayments(condition, page, size);
    }

    private void validateAuthorization(String authorization) {
        if (authorization == null || authorization.isBlank() || !authorization.startsWith("Bearer ")) {
            throw new PgPaymentException(ErrorCode.UNAUTHORIZED, "Authorization header must be a Bearer token.");
        }
    }
}
