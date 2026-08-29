package com.setaccio.lab.retrieval;

/** Complete recorded outcome of one R6 evaluator row, including Spring AI's separate pass/score fields. */
public record RetrievalRelevancyEvaluatorOutcome(
        RetrievalRelevancyModelIdentity modelIdentity,
        String promptId,
        String promptSha256,
        boolean invocationAttempted,
        boolean invocationSucceeded,
        Boolean springEvaluatorPassed,
        Float springEvaluatorScore,
        RetrievalRelevancyVerdict normalizedVerdict,
        RetrievalRelevancyDiagnosticCategory diagnosticCategory,
        String rawResponse,
        RetrievalRelevancyResponseMetadata responseMetadata,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        long latencyMillis,
        int attemptCount
) {

    public RetrievalRelevancyEvaluatorOutcome {
        if (modelIdentity == null || promptId == null || promptId.isBlank()
                || promptSha256 == null || !promptSha256.matches("[0-9a-f]{64}")
                || diagnosticCategory == null || latencyMillis < 0 || attemptCount < 0) {
            throw new IllegalArgumentException("Retrieval relevancy outcome has invalid required fields");
        }
        validateUsage(promptTokens, completionTokens, totalTokens);
        if (!invocationAttempted) {
            if (invocationSucceeded || attemptCount != 0 || springEvaluatorPassed != null || springEvaluatorScore != null
                    || normalizedVerdict != null || rawResponse != null || responseMetadata != null
                    || promptTokens != null || completionTokens != null || totalTokens != null || latencyMillis != 0
                    || (diagnosticCategory != RetrievalRelevancyDiagnosticCategory.NOT_ATTEMPTED_MISSING_CONTEXT
                    && diagnosticCategory != RetrievalRelevancyDiagnosticCategory.NOT_ATTEMPTED_NO_ANSWER)) {
                throw new IllegalArgumentException("Not-attempted relevance evaluation has invalid observation fields");
            }
        } else if (attemptCount != 1) {
            throw new IllegalArgumentException("Attempted relevance evaluation must record exactly one attempt");
        } else if (invocationSucceeded) {
            if (springEvaluatorPassed == null || springEvaluatorScore == null) {
                throw new IllegalArgumentException("Successful relevance evaluation must retain Spring evaluator output");
            }
        } else if (springEvaluatorPassed != null || springEvaluatorScore != null || normalizedVerdict != null) {
            throw new IllegalArgumentException("Failed relevance evaluation must not invent evaluator output");
        }
    }

    private static void validateUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        boolean allMissing = promptTokens == null && completionTokens == null && totalTokens == null;
        boolean allPresent = promptTokens != null && completionTokens != null && totalTokens != null;
        if (!allMissing && !allPresent) {
            throw new IllegalArgumentException("usage token counts must be all present or all absent");
        }
        if (allPresent && (promptTokens < 0 || completionTokens < 0 || totalTokens < 0)) {
            throw new IllegalArgumentException("usage token counts must not be negative");
        }
    }
}
