package com.setaccio.lab.retrieval;

import java.time.Instant;
import java.util.List;

/** Raw saved output for one R5 answer-generation matrix. */
public record RetrievalAnswerResult(
        int protocolVersion,
        String suite,
        Instant startedAt,
        Instant finishedAt,
        String executionEngine,
        String executionStrategy,
        RetrievalAnswerSourceEvidence sourceEvidence,
        RetrievalEvaluationResult retrievalEvidence,
        RetrievalAnswerPromptContract prompt,
        RetrievalAnswerModelIdentity modelIdentity,
        RetrievalAnswerRunSettings runSettings,
        List<RetrievalAnswerRow> rows
) {

    public RetrievalAnswerResult {
        if (startedAt == null || finishedAt == null || sourceEvidence == null || retrievalEvidence == null
                || prompt == null || modelIdentity == null || runSettings == null) {
            throw new IllegalArgumentException("retrieval answer result fields must not be null");
        }
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
