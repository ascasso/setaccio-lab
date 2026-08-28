package com.setaccio.lab.retrieval;

/** One cosine-ranked R4 retrieval hit. */
public record RetrievalEmbeddingHit(
        int rank,
        String documentId,
        String contentSha256,
        double cosineSimilarity
) {}
