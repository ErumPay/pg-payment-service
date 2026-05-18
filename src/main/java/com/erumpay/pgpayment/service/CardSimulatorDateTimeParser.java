package com.erumpay.pgpayment.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class CardSimulatorDateTimeParser {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public LocalDateTime parseOrNow(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        return LocalDateTime.parse(value, FORMATTER);
    }
}
