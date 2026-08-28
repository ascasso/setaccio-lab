package com.setaccio.lab.retrieval;

import java.util.List;

/** One ignored saved vector bound to an exact approved corpus document identity. */
public record RetrievalEmbeddingDocumentVector(
        String documentId,
        String contentSha256,
        List<Float> values
) {

    public RetrievalEmbeddingDocumentVector {
        values = values == null ? List.of() : List.copyOf(values);
    }
}
