package com.setaccio.lab.retrieval;

/** Complete immutable settings for one R4 local embedding generation. */
public record RetrievalEmbeddingRunSettings(
        String provider,
        String endpointCategory,
        int topK,
        String chunkingPolicy,
        String normalizationPolicy,
        String distanceMetric,
        int requestTimeoutMillis,
        int maxAttempts
) {

    public RetrievalEmbeddingRunSettings {
        requireText(provider, "provider");
        requireText(endpointCategory, "endpointCategory");
        requireText(chunkingPolicy, "chunkingPolicy");
        requireText(normalizationPolicy, "normalizationPolicy");
        requireText(distanceMetric, "distanceMetric");
        if (topK < 1) {
            throw new IllegalArgumentException("topK must be positive");
        }
        if (requestTimeoutMillis < 1) {
            throw new IllegalArgumentException("requestTimeoutMillis must be positive");
        }
        if (maxAttempts != 1) {
            throw new IllegalArgumentException("maxAttempts must be exactly one");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
