package com.erumpay.pgpayment.service;

import com.erumpay.pgpayment.domain.enums.PgTxnType;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class PgApprovalNumberGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    public String generate(PgTxnType txnType, Long pgTxnId) {
        String prefix = switch (txnType) {
            case AUTH -> "PG";
            case AUTH_ONLY -> "PGAUTH";
            case CANCEL -> "PGC";
            case VOID -> "PGV";
        };
        return prefix + LocalDate.now().format(DATE_FORMATTER) + String.format("%06d", pgTxnId);
    }
}
