package com.setaccio.lab.retrieval;

import java.util.List;

/** A query identity and its complete fixed top-K embedding retrieval result. */
public record RetrievalEmbeddingRow(
        int sequence,
        String caseId,
        String querySha256,
        List<RetrievalEmbeddingHit> hits
) {

    public RetrievalEmbeddingRow {
        hits = hits == null ? List.of() : List.copyOf(hits);
    }
}
