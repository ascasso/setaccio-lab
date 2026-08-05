package com.setaccio.lab.chatmatrix;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/** A pre-run token-cap estimate; it is deliberately separate from observed provider usage. */
record ChatEstimatedCost(
        String currency,
        long inputTokenCeiling,
        long outputTokenCeiling,
        BigDecimal inputUsdPerMillionTokens,
        BigDecimal outputUsdPerMillionTokens,
        Instant priceCheckedAt,
        String officialPriceSource
) {

    ChatEstimatedCost {
        if (!"USD".equals(currency)) {
            throw new IllegalArgumentException("currency must be USD");
        }
        if (inputTokenCeiling < 0 || outputTokenCeiling < 0) {
            throw new IllegalArgumentException("token ceilings must not be negative");
        }
        inputUsdPerMillionTokens = requireNonNegative(inputUsdPerMillionTokens, "inputUsdPerMillionTokens");
        outputUsdPerMillionTokens = requireNonNegative(outputUsdPerMillionTokens, "outputUsdPerMillionTokens");
        priceCheckedAt = Objects.requireNonNull(priceCheckedAt, "priceCheckedAt must not be null");
        if (officialPriceSource == null || !officialPriceSource.startsWith("https://")) {
            throw new IllegalArgumentException("officialPriceSource must be a public HTTPS URL");
        }
    }

    BigDecimal estimatedUsd() {
        return estimateUsd(inputTokenCeiling, outputTokenCeiling);
    }

    BigDecimal estimateUsd(long inputTokens, long outputTokens) {
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("observed token counts must not be negative");
        }
        BigDecimal input = inputUsdPerMillionTokens.multiply(BigDecimal.valueOf(inputTokens));
        BigDecimal output = outputUsdPerMillionTokens.multiply(BigDecimal.valueOf(outputTokens));
        return input.add(output).movePointLeft(6).setScale(8, RoundingMode.HALF_UP);
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }
}
