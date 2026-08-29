package com.setaccio.lab.retrieval;

/** Invocation and response diagnostics, intentionally distinct from the evaluator verdict. */
public enum RetrievalRelevancyDiagnosticCategory {
    NONE,
    NOT_ATTEMPTED_MISSING_CONTEXT,
    NOT_ATTEMPTED_NO_ANSWER,
    EVALUATOR_MODEL_UNAVAILABLE,
    TIMEOUT,
    PROVIDER_FAILURE,
    EMPTY_RESPONSE,
    MALFORMED_VERDICT;

    /** Derives the only valid response diagnostic for a completed evaluator invocation. */
    static RetrievalRelevancyDiagnosticCategory fromRawResponse(
            String rawResponse,
            RetrievalRelevancyVerdict normalizedVerdict
    ) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return EMPTY_RESPONSE;
        }
        return normalizedVerdict == null ? MALFORMED_VERDICT : NONE;
    }
}
