package com.setaccio.lab.evaluation;

import java.util.Objects;

public record LocalEvaluationScheduleEntry(
        int sequence,
        int repetition,
        int seed,
        String fixtureId,
        String pairId,
        String documentBlake3,
        String claimBlake3,
        LocalFactCheckExpectedVerdict expectedVerdict
) {

    public LocalEvaluationScheduleEntry {
        if (sequence < 1 || repetition < 1 || seed < 0) {
            throw new IllegalArgumentException("schedule sequence, repetition, and seed must be valid");
        }
        requireId(fixtureId, "fixtureId");
        requireId(pairId, "pairId");
        requireBlake3(documentBlake3, "documentBlake3");
        requireBlake3(claimBlake3, "claimBlake3");
        expectedVerdict = Objects.requireNonNull(expectedVerdict, "expectedVerdict must not be null");
    }

    private static void requireId(String value, String field) {
        if (value == null || !value.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException(field + " must be a lowercase kebab-case identifier");
        }
    }

    private static void requireBlake3(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a 64-character lowercase BLAKE3 hash");
        }
    }
}
