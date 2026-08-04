package com.setaccio.lab.evaluation;

import java.util.Objects;

public record LocalFactCheckJudgeResult(
        String fixtureId,
        LocalFactCheckExpectedVerdict expectedVerdict,
        LocalFactCheckJudgeSettings settings,
        boolean invocationSucceeded,
        Boolean springEvaluatorPassed,
        LocalFactCheckJudgeVerdict normalizedJudgeVerdict,
        Boolean expectedVerdictMatched,
        LocalFactCheckDiagnosticCategory diagnosticCategory,
        String rawResponse,
        LocalFactCheckJudgeResponseMetadata responseMetadata,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        long latencyMillis,
        int attemptCount,
        String error
) {
    public LocalFactCheckJudgeResult {
        if (fixtureId == null || fixtureId.isBlank()) {
            throw new IllegalArgumentException("fixtureId must not be blank");
        }
        expectedVerdict = Objects.requireNonNull(expectedVerdict, "expectedVerdict must not be null");
        settings = Objects.requireNonNull(settings, "settings must not be null");
        diagnosticCategory = Objects.requireNonNull(
                diagnosticCategory,
                "diagnosticCategory must not be null");
        if (latencyMillis < 0) {
            throw new IllegalArgumentException("latencyMillis must not be negative");
        }
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must not be negative");
        }
    }
}
