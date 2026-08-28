package com.setaccio.lab.retrieval;

import java.time.Instant;
import java.util.List;

/** Raw ignored evidence for one opt-in local R4 embedding retrieval generation. */
public record RetrievalEmbeddingResult(
        int protocolVersion,
        String suite,
        Instant startedAt,
        Instant finishedAt,
        String executionStrategy,
        String pullModelStrategy,
        RetrievalEmbeddingRunSettings runSettings,
        RetrievalEmbeddingModelIdentity modelIdentity,
        String corpusCatalogId,
        int corpusCatalogVersion,
        String corpusCatalogSha256,
        String queryCatalogId,
        int queryCatalogVersion,
        String queryCatalogSha256,
        int vectorDimension,
        Long providerTotalDurationNanos,
        Long providerLoadDurationNanos,
        Integer providerPromptEvalCount,
        List<RetrievalEmbeddingDocumentVector> documentVectors,
        List<RetrievalEmbeddingQueryVector> queryVectors,
        List<RetrievalEmbeddingRow> rows
) {

    public RetrievalEmbeddingResult {
        documentVectors = documentVectors == null ? List.of() : List.copyOf(documentVectors);
        queryVectors = queryVectors == null ? List.of() : List.copyOf(queryVectors);
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
