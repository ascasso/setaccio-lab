package com.setaccio.lab.retrieval;

import java.time.Instant;
import java.util.List;

/** Raw saved output for one R6 relevance-evaluation matrix. */
public record RetrievalRelevancyResult(
        int protocolVersion,
        String suite,
        Instant startedAt,
        Instant finishedAt,
        String executionEngine,
        String executionStrategy,
        RetrievalRelevancySourceEvidence sourceEvidence,
        RetrievalAnswerResult answerEvidence,
        RetrievalRelevancyPromptContract prompt,
        RetrievalRelevancyModelIdentity modelIdentity,
        RetrievalRelevancyRunSettings runSettings,
        List<RetrievalRelevancyRow> rows
) {

    public RetrievalRelevancyResult {
        if (startedAt == null || finishedAt == null || sourceEvidence == null || answerEvidence == null
                || prompt == null || modelIdentity == null || runSettings == null) {
            throw new IllegalArgumentException("retrieval relevancy result fields must not be null");
        }
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
