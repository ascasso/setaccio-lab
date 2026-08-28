package com.setaccio.lab.retrieval;

import java.time.Instant;
import java.util.List;

/** Raw saved output for one provider-free retrieval-only evaluation. */
public record RetrievalEvaluationResult(
        int protocolVersion,
        String suite,
        Instant startedAt,
        Instant finishedAt,
        String executionEngine,
        String executionStrategy,
        String corpusCatalogId,
        int corpusCatalogVersion,
        String corpusCatalogSha256,
        String queryCatalogId,
        int queryCatalogVersion,
        String queryCatalogSha256,
        RetrievalLexicalParameters lexicalParameters,
        List<RetrievalEvaluationRow> rows
) {

    public RetrievalEvaluationResult {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
