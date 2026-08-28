package com.setaccio.lab.retrieval;

import java.util.List;

/** One ignored saved vector bound to an exact confirmed retrieval query identity. */
public record RetrievalEmbeddingQueryVector(
        String caseId,
        String querySha256,
        List<Float> values
) {

    public RetrievalEmbeddingQueryVector {
        values = values == null ? List.of() : List.copyOf(values);
    }
}
