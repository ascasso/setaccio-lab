package com.setaccio.lab.thinking;

import com.setaccio.lab.evaluation.LocalFactCheckExpectedVerdict;
import java.util.Objects;

/** One pre-registered row position: which arm, which fixture, which seed, in which order. */
public record ThinkingDiagnosticScheduleEntry(
        int sequence,
        String armId,
        String fixtureId,
        String pairId,
        LocalFactCheckExpectedVerdict expectedVerdict,
        int seed
) {
    public ThinkingDiagnosticScheduleEntry {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
        if (armId == null || armId.isBlank()) {
            throw new IllegalArgumentException("armId must not be blank");
        }
        if (fixtureId == null || fixtureId.isBlank()) {
            throw new IllegalArgumentException("fixtureId must not be blank");
        }
        if (pairId == null || pairId.isBlank()) {
            throw new IllegalArgumentException("pairId must not be blank");
        }
        expectedVerdict = Objects.requireNonNull(expectedVerdict, "expectedVerdict must not be null");
        if (seed < 0) {
            throw new IllegalArgumentException("seed must not be negative");
        }
    }
}
