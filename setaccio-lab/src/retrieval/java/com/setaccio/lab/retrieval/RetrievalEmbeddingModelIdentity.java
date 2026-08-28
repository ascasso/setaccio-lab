package com.setaccio.lab.retrieval;

/** Requested and effective installed local embedding model identity. */
public record RetrievalEmbeddingModelIdentity(
        String requestedModel,
        String effectiveModel,
        String digest
) {

    public RetrievalEmbeddingModelIdentity {
        requestedModel = requireText(requestedModel, "requestedModel");
        effectiveModel = requireText(effectiveModel, "effectiveModel");
        digest = requireText(digest, "digest");
        if (!digest.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("digest must be a full lowercase SHA-256-like value");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
