package com.erumpay.pgpayment.service;

import java.security.SecureRandom;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class PgInternalIdempotencyKeyGenerator {

    private static final char[] CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    public String paymentKey(Long pgTxnId) {
        return "pg:payment:" + pgTxnId + ":" + ulid();
    }

    public String cancelKey(Long pgTxnId) {
        return "pg:cancel:" + pgTxnId + ":" + ulid();
    }

    private String ulid() {
        byte[] bytes = new byte[16];
        long timestamp = Instant.now().toEpochMilli();
        bytes[0] = (byte) (timestamp >>> 40);
        bytes[1] = (byte) (timestamp >>> 32);
        bytes[2] = (byte) (timestamp >>> 24);
        bytes[3] = (byte) (timestamp >>> 16);
        bytes[4] = (byte) (timestamp >>> 8);
        bytes[5] = (byte) timestamp;
        byte[] randomBytes = new byte[10];
        RANDOM.nextBytes(randomBytes);
        System.arraycopy(randomBytes, 0, bytes, 6, randomBytes.length);
        return encodeBase32(bytes);
    }

    private String encodeBase32(byte[] bytes) {
        StringBuilder builder = new StringBuilder(26);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                builder.append(CROCKFORD_BASE32[(buffer >> (bitsLeft - 5)) & 31]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            builder.append(CROCKFORD_BASE32[(buffer << (5 - bitsLeft)) & 31]);
        }
        return builder.substring(0, 26);
    }
}
