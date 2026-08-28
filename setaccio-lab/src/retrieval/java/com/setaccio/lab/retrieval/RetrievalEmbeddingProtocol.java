package com.setaccio.lab.retrieval;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Locked R4 local embedding retrieval protocol metadata. */
final class RetrievalEmbeddingProtocol {

    static final int VERSION = 1;
    static final String SUITE = "public-safe-retrieval-embedding";
    static final String PROVIDER = "ollama";
    static final String ENDPOINT_CATEGORY = "loopback-local";
    static final String EXECUTION_ENGINE = "spring-ai-ollama-api-embed";
    static final String EXECUTION_STRATEGY = "single-batch-one-attempt";
    static final String PULL_MODEL_STRATEGY = "never";
    static final String CHUNKING_POLICY = "whole-document-v1";
    static final String NORMALIZATION_POLICY = "unit-l2-v1";
    static final String DISTANCE_METRIC = "cosine-descending-document-id";
    static final int MAX_ATTEMPTS = 1;
    static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);
    static final String RAW_FILENAME = "retrieval-embedding-results.json";

    private RetrievalEmbeddingProtocol() {}

    static RetrievalEmbeddingRunSettings settings(int topK) {
        if (topK < 1) {
            throw new IllegalArgumentException("topK must be positive");
        }
        return new RetrievalEmbeddingRunSettings(
                PROVIDER,
                ENDPOINT_CATEGORY,
                topK,
                CHUNKING_POLICY,
                NORMALIZATION_POLICY,
                DISTANCE_METRIC,
                Math.toIntExact(REQUEST_TIMEOUT.toMillis()),
                MAX_ATTEMPTS);
    }

    static Map<String, Object> manifestSettings(RetrievalEmbeddingResult result) {
        LinkedHashMap<String, Object> settings = new LinkedHashMap<>();
        settings.put("protocolVersion", result.protocolVersion());
        settings.put("executionStrategy", result.executionStrategy());
        settings.put("pullModelStrategy", result.pullModelStrategy());
        settings.put("runSettings", result.runSettings());
        settings.put("modelIdentity", result.modelIdentity());
        settings.put("vectorDimension", result.vectorDimension());
        settings.put("corpusCatalogId", result.corpusCatalogId());
        settings.put("corpusCatalogVersion", result.corpusCatalogVersion());
        settings.put("corpusCatalogSha256", result.corpusCatalogSha256());
        settings.put("queryCatalogId", result.queryCatalogId());
        settings.put("queryCatalogVersion", result.queryCatalogVersion());
        settings.put("queryCatalogSha256", result.queryCatalogSha256());
        settings.put("documentCount", result.documentVectors().size());
        settings.put("queryCount", result.queryVectors().size());
        return Map.copyOf(settings);
    }
}
